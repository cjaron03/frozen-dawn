package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.ThermalContainerItem;
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
 * Uses a server-side cache to avoid writing DataComponents every tick,
 * which would cause the item to visually "bob" in the player's hand.
 * Components are only written when the frost stage changes or every 5 seconds.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class FoodFrostHandler {

    private static final TagKey<Item> FROST_RESISTANT =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frost_resistant"));

    /** Server-side frost tick cache: player UUID -> slot index -> frost ticks. */
    private static final Map<UUID, int[]> frostCache = new HashMap<>();
    /** Sync components every 100 ticks (5 seconds) for persistence. */
    private static final int SYNC_INTERVAL = 100;

    /** Pre-eat food levels for halving nutrition on frozen food. */
    private static final Map<UUID, Integer> preFoodLevels = new HashMap<>();

    private static int getStage(int ticks) {
        if (ticks >= 6000) return 3; // Frost-Ruined
        if (ticks >= 2400) return 2; // Frozen
        if (ticks >= 600) return 1;  // Chilled
        return 0;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;

        ApocalypseState state = ApocalypseState.get(server);
        if (state.getPhase() < 4) return;

        boolean periodicSync = server.getTickCount() % SYNC_INTERVAL == 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (player.level().dimension() != Level.OVERWORLD) continue;

            float temp = WorldTickHandler.getLastTemperature(player.getUUID());

            boolean freezing = temp < -30f;
            boolean thawing = temp > 10f;
            if (!freezing && !thawing && !periodicSync) continue;

            // Frost rate scales with temperature: -30C=15/s, -60C=30/s, -90C=50/s
            int frostRate = 0;
            if (freezing) {
                frostRate = (int) Math.min(60, 15 + (-30f - temp) * (35f / 60f));
            }
            int thawRate = thawing ? (temp > 30f ? 80 : 40) : 0;

            int invSize = player.getInventory().getContainerSize();
            int[] cache = frostCache.computeIfAbsent(player.getUUID(), k -> new int[invSize]);
            // Resize cache if needed
            if (cache.length < invSize) {
                int[] newCache = new int[invSize];
                System.arraycopy(cache, 0, newCache, 0, cache.length);
                cache = newCache;
                frostCache.put(player.getUUID(), cache);
            }

            for (int i = 0; i < invSize; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty() || !stack.has(DataComponents.FOOD)
                        || stack.is(FROST_RESISTANT) || stack.getItem() instanceof ThermalContainerItem) {
                    cache[i] = 0;
                    continue;
                }

                // Sync cache from component values periodically to handle slot movement
                int componentVal = stack.getOrDefault(ModDataComponents.FROST_TICKS.get(), 0);
                if (periodicSync) {
                    cache[i] = componentVal;
                } else if (cache[i] == 0 && componentVal > 0) {
                    cache[i] = componentVal;
                }

                int oldStage = getStage(cache[i]);

                if (freezing) {
                    cache[i] += frostRate;
                } else if (thawing && cache[i] > 0) {
                    int min = isFrostRuined(stack) ? 2400 : 0;
                    cache[i] = Math.max(min, cache[i] - thawRate);
                }

                int newStage = getStage(cache[i]);
                boolean stageChanged = oldStage != newStage;

                // Only write component on stage change or periodic sync
                if (stageChanged || periodicSync) {
                    if (cache[i] > 0) {
                        stack.set(ModDataComponents.FROST_TICKS.get(), cache[i]);
                    } else {
                        stack.remove(ModDataComponents.FROST_TICKS.get());
                    }

                    // Mark frost-ruined on first crossing into stage 3
                    if (cache[i] >= 6000 && !isFrostRuined(stack)) {
                        markFrostRuined(stack);
                        WorldTickHandler.grantAdvancement(player, "food_spoiled");
                    }
                }
            }
        }
    }

    /** Flush frost cache to components when a player logs out. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            flushCache(player);
            frostCache.remove(player.getUUID());
        }
    }

    /** Clear cache on server stop. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        frostCache.clear();
        preFoodLevels.clear();
    }

    private static void flushCache(ServerPlayer player) {
        int[] cache = frostCache.get(player.getUUID());
        if (cache == null) return;
        int invSize = Math.min(cache.length, player.getInventory().getContainerSize());
        for (int i = 0; i < invSize; i++) {
            if (cache[i] > 0) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                    stack.set(ModDataComponents.FROST_TICKS.get(), cache[i]);
                }
            }
        }
    }

    /** Frost-Ruined food is inedible. Frozen food takes 2x longer to eat. */
    @SubscribeEvent
    public static void onEatStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem();
        if (!stack.has(DataComponents.FOOD)) return;

        // Check cache first for most accurate value, fall back to component
        int frostTicks = getCachedFrost(player, stack);
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

    /** Get frost ticks from cache (most accurate) or component (fallback). */
    private static int getCachedFrost(ServerPlayer player, ItemStack target) {
        int[] cache = frostCache.get(player.getUUID());
        if (cache != null) {
            // Check active hand slots first (most likely during eating)
            int mainSlot = player.getInventory().selected;
            if (mainSlot < cache.length && player.getInventory().getItem(mainSlot) == target && cache[mainSlot] > 0) {
                return cache[mainSlot];
            }
            // Offhand
            int offSlot = player.getInventory().getContainerSize() - 1;
            if (offSlot < cache.length && player.getInventory().getItem(offSlot) == target && cache[offSlot] > 0) {
                return cache[offSlot];
            }
            // Fallback: scan remaining slots
            int invSize = Math.min(cache.length, player.getInventory().getContainerSize());
            for (int i = 0; i < invSize; i++) {
                if (player.getInventory().getItem(i) == target && cache[i] > 0) {
                    return cache[i];
                }
            }
        }
        return target.getOrDefault(ModDataComponents.FROST_TICKS.get(), 0);
    }

    private static boolean isFrostRuined(ItemStack stack) {
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
