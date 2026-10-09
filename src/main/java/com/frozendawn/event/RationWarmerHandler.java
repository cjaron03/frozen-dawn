package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.RationWarmerItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Charge is paid at activation. Jobs are deliberately not persisted or refunded. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class RationWarmerHandler {
    public static final int THAW_TICKS = 200;
    public static final int WARM_TICKS = 600;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record Session(ServerPlayer player, ItemStack food, int count,
                           ResourceKey<Level> dimension, long started) {
        boolean valid() {
            return player.isAlive() && !player.isRemoved()
                    && player.level().dimension().equals(dimension)
                    && player.getOffhandItem() == food && food.getCount() == count
                    && FoodFrostHandler.isAffectedFood(food);
        }
    }

    public static boolean start(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        ItemStack warmer = player.getMainHandItem();
        ItemStack food = player.getOffhandItem();
        if (!(warmer.getItem() instanceof RationWarmerItem) || warmer.getCount() != 1) return false;
        if (!FoodFrostHandler.needsPortableThaw(food)) {
            message(player, "ration_warmer.no_thaw", ChatFormatting.GRAY);
            return false;
        }
        int charges = RationWarmerItem.charges(warmer);
        if (charges == 0) {
            message(player, "ration_warmer.empty", ChatFormatting.RED);
            return false;
        }
        warmer.set(ModDataComponents.RATION_WARMER_CHARGES, charges - 1);
        SESSIONS.put(player.getUUID(), new Session(player, food, food.getCount(),
                player.level().dimension(), player.getServer().overworld().getGameTime()));
        progress(player, 0);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        for (Iterator<Session> iterator = SESSIONS.values().iterator(); iterator.hasNext();) {
            Session session = iterator.next();
            if (advance(session, now)) iterator.remove();
        }
    }

    /** Shared by the real server tick and deterministic native replay checks. */
    public static void tickPlayer(ServerPlayer player, long now) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && advance(session, now)) SESSIONS.remove(player.getUUID());
    }

    private static boolean advance(Session session, long now) {
        if (!session.valid()) {
            message(session.player, "ration_warmer.cancelled", ChatFormatting.GRAY);
            return true;
        }
        long elapsed = now - session.started;
        if (elapsed < 0) return true;
        if (elapsed >= THAW_TICKS) {
            FoodFrostHandler.portableThaw(session.food, now);
            message(session.player, FoodFrostHandler.isFrostRuined(session.food)
                    ? "ration_warmer.damaged_done" : "ration_warmer.done", ChatFormatting.GREEN);
            return true;
        }
        if (elapsed % 10 == 0) progress(session.player, (int) elapsed);
        return false;
    }

    private static void progress(ServerPlayer player, int elapsed) {
        int filled = elapsed * 10 / THAW_TICKS;
        String bar = "■".repeat(filled) + "□".repeat(10 - filled);
        player.displayClientMessage(Component.translatable("ration_warmer.progress", bar,
                elapsed * 100 / THAW_TICKS).withStyle(ChatFormatting.GOLD), true);
    }

    private static void message(ServerPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable("message.frozendawn." + key).withStyle(color), true);
    }

    public static boolean isThawing(ItemStack food) {
        for (Session session : SESSIONS.values()) {
            if (session.food == food && session.valid()) return true;
        }
        return false;
    }

    public static void cancel(ServerPlayer player) { SESSIONS.remove(player.getUUID()); }
    public static boolean isActive(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }
    @SubscribeEvent public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }
    @SubscribeEvent public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }
    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) { SESSIONS.clear(); }
}
