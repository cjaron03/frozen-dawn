package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.ThermalContainerItem;
import com.frozendawn.hearthrot.HearthrotManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles food frost accumulation, eating penalties, and thawing.
 * Food in player inventory gains frost_ticks when temp < -30C (phase 4+).
 *
 * Frost components remain authoritative across inventory moves, copies and saves.
 * The client suppresses hand re-equip only for food frost metadata changes.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class FoodFrostHandler {

    private static final TagKey<Item> FROST_RESISTANT =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frost_resistant"));

    /** Pre-eat food levels for halving nutrition on frozen food. */
    private static final Map<UUID, Integer> preFoodLevels = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;

        ApocalypseState state = ApocalypseState.get(server);
        if (state.getPhase() < 4) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (player.level().dimension() != Level.OVERWORLD) continue;
            tickInventory(player, WorldTickHandler.getLastTemperature(player.getUUID()));
        }
    }

    // The stack is authoritative. Slot caches lost four seconds of progress at each sync
    // and could assign another stack's frost after an inventory move or replacement.
    static void tickInventory(ServerPlayer player, float temp) {
        long now = player.getServer().overworld().getGameTime();
        float multiplier = HearthrotManager.foodFreezeMultiplier(player);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (updateFood(stack, temp, multiplier, now)) {
                WorldTickHandler.grantAdvancement(player, "food_spoiled");
            }
        }
    }

    /** One second of actual food exposure; returns true on first permanent damage. */
    public static boolean updateFood(ItemStack stack, float temp, float multiplier, long now) {
        if (!isAffectedFood(stack) || RationWarmerHandler.isThawing(stack)) return false;
        long warmUntil = stack.getOrDefault(ModDataComponents.FOOD_WARM_UNTIL, 0L);
        if (warmUntil != 0 && now >= warmUntil) stack.remove(ModDataComponents.FOOD_WARM_UNTIL);
        int frost = stack.getOrDefault(ModDataComponents.FROST_TICKS, 0);
        if (temp < -30f && now >= warmUntil) {
            int rate = (int) Math.min(60, 15 + (-30f - temp) * (35f / 60f));
            rate = (int) (rate * multiplier);
            frost = (int) Math.min(Integer.MAX_VALUE, (long) frost + rate);
        } else if (temp > 10f && frost > 0) {
            frost = Math.max(isFrostRuined(stack) ? 2400 : 0, frost - (temp > 30f ? 80 : 40));
        }
        if (frost > 0) stack.set(ModDataComponents.FROST_TICKS, frost);
        else stack.remove(ModDataComponents.FROST_TICKS);
        if (frost >= 6000 && !isFrostRuined(stack)) {
            markFrostRuined(stack);
            return true;
        }
        return false;
    }

    public static boolean isAffectedFood(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD)
                && !stack.is(FROST_RESISTANT) && !(stack.getItem() instanceof ThermalContainerItem);
    }

    public static boolean needsPortableThaw(ItemStack stack) {
        return isAffectedFood(stack) && stack.getOrDefault(ModDataComponents.FROST_TICKS, 0) >= 600;
    }

    /** Permanent damage is retained; the warm window uses the persistent Overworld clock. */
    public static void portableThaw(ItemStack stack, long now) {
        if (isFrostRuined(stack)) stack.set(ModDataComponents.FROST_TICKS, 2400);
        else stack.remove(ModDataComponents.FROST_TICKS);
        stack.set(ModDataComponents.FOOD_WARM_UNTIL, now + RationWarmerHandler.WARM_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        preFoodLevels.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        preFoodLevels.clear();
    }

    /** Frost-Ruined food is inedible. Frozen food takes 2x longer to eat. */
    @SubscribeEvent
    public static void onEatStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem();
        if (!stack.has(DataComponents.FOOD)) return;

        int frostTicks = stack.getOrDefault(ModDataComponents.FROST_TICKS, 0);
        if (frostTicks <= 0) return;

        if (frostTicks >= 6000) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("This food is frost-ruined and inedible.")
                            .withStyle(ChatFormatting.GRAY), true);
        } else if (frostTicks >= 2400) {
            event.setDuration(event.getDuration() * 2);
            preFoodLevels.put(player.getUUID(), player.getFoodData().getFoodLevel());
        }
    }

    /** Halve nutrition gained from frozen food. */
    @SubscribeEvent
    public static void onEatFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Integer pre = preFoodLevels.remove(player.getUUID());
        if (pre == null) return;

        FoodData food = player.getFoodData();
        int gained = food.getFoodLevel() - pre;
        if (gained > 0) {
            food.setFoodLevel(food.getFoodLevel() - gained / 2);
        }
    }

    /** Clean up pre-food map if eating is interrupted. */
    @SubscribeEvent
    public static void onEatStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            preFoodLevels.remove(player.getUUID());
        }
    }

    /** Grant advancement when thermal container is crafted. */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getCrafting().getItem() instanceof ThermalContainerItem) {
                WorldTickHandler.grantAdvancement(player, "thermal_container");
            }
        }
    }

    public static boolean isFrostRuined(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            return data.copyTag().getBoolean("frost_ruined");
        }
        return false;
    }

    private static void markFrostRuined(ItemStack stack) {
        CompoundTag tag;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            tag = existing.copyTag();
        } else {
            tag = new CompoundTag();
        }
        tag.putBoolean("frost_ruined", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
