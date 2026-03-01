package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.SanityState;
import com.frozendawn.network.SanityStagePayload;
import com.frozendawn.world.GeothermalCoreRegistry;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side isolation/sanity tracking.
 * Tracks how long each player has been isolated and computes a sanity stage (0-3).
 * Isolation ticks and comfort grace are persisted via SanityState (SavedData).
 * Stage is synced to the client via SanityStagePayload when it changes.
 */
final class SanityHandler {

    private SanityHandler() {}

    // Only lastSanityStage is in-memory (used for change detection, re-sent on login)
    private static final Map<UUID, Integer> lastSanityStage = new HashMap<>();

    // Thresholds (base ticks at 1.0x speed, phase 5+)
    static final int BASE_STAGE1 = 24000;  // 20 min
    static final int BASE_STAGE2 = 48000;  // 40 min
    static final int BASE_STAGE3 = 72000;  // 60 min

    private static final int COMFORT_GRACE_DURATION = 6000; // 5 min grace after removing comfort item

    private static final TagKey<Item> COMFORT_ITEMS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "comfort_items"));

    static void reset() {
        lastSanityStage.clear();
    }

    /**
     * Called every server tick from PlayerTickHandler.
     */
    static void tick(ServerPlayer player, int phase, float speedMultiplier) {
        if (player.isCreative() || player.isSpectator()) return;
        if (!FrozenDawnConfig.ENABLE_SANITY.get()) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;
        SanityState state = SanityState.get(server);

        UUID id = player.getUUID();

        // Phase < 3: no sanity effects
        if (phase < 3) {
            state.clearPlayer(id);
            updateStage(player, 0, state);
            return;
        }

        // Check suppression conditions
        boolean hasComfortItem = hasComfortItem(player);
        boolean nearHeaterAndLight = isNearLitHeaterWithLight(player);
        boolean nearPlayer = isNearOtherPlayer(player);

        // Comfort grace tracking
        if (hasComfortItem) {
            state.setComfortGrace(id, COMFORT_GRACE_DURATION);
        } else {
            int grace = state.getComfortGrace(id);
            if (grace > 0) {
                state.setComfortGrace(id, grace - 1);
            }
        }

        boolean comfortSuppressed = hasComfortItem || state.getComfortGrace(id) > 0;
        boolean suppressed = comfortSuppressed || nearHeaterAndLight || nearPlayer;

        int ticks = state.getIsolationTicks(id);

        if (suppressed) {
            // Decay at 3x rate
            ticks = Math.max(0, ticks - 3);
        } else {
            ticks++;
        }

        state.setIsolationTicks(id, ticks);

        // Compute stage from thresholds, adjusted for phase and speed
        int stage = computeStage(ticks, phase, speedMultiplier);
        updateStage(player, stage, state);
    }

    static int getStage(ServerPlayer player) {
        return lastSanityStage.getOrDefault(player.getUUID(), 0);
    }

    /**
     * Re-sync sanity stage to a player on login (since lastSanityStage is in-memory).
     */
    static void onPlayerLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        SanityState state = SanityState.get(server);
        int ticks = state.getIsolationTicks(player.getUUID());
        float speed = (float) FrozenDawnConfig.SANITY_SPEED_MULTIPLIER.get().doubleValue();
        int phase = com.frozendawn.data.ApocalypseState.get(server).getPhase();
        int stage = computeStage(ticks, phase, speed);
        lastSanityStage.put(player.getUUID(), stage);
        PacketDistributor.sendToPlayer(player, new SanityStagePayload(stage));
    }

    private static int computeStage(int ticks, int phase, float speedMultiplier) {
        float phaseFactor = phase < 5 ? 1.5f : 1.0f;
        float divisor = Math.max(0.01f, speedMultiplier);

        int stage1 = (int) (BASE_STAGE1 * phaseFactor / divisor);
        int stage2 = (int) (BASE_STAGE2 * phaseFactor / divisor);
        int stage3 = (int) (BASE_STAGE3 * phaseFactor / divisor);

        if (ticks >= stage3) return 3;
        if (ticks >= stage2) return 2;
        if (ticks >= stage1) return 1;
        return 0;
    }

    private static void updateStage(ServerPlayer player, int stage, SanityState state) {
        UUID id = player.getUUID();
        int lastStage = lastSanityStage.getOrDefault(id, -1);
        if (stage != lastStage) {
            lastSanityStage.put(id, stage);
            PacketDistributor.sendToPlayer(player, new SanityStagePayload(stage));
            FrozenDawn.LOGGER.info("[Sanity] {} -> Stage {} (ticks: {})", player.getName().getString(), stage, state.getIsolationTicks(id));
        }
    }

    static boolean hasComfortItem(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(COMFORT_ITEMS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNearLitHeaterWithLight(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        for (BlockPos heaterPos : HeaterRegistry.getHeaters(player.level())) {
            if (playerPos.distSqr(heaterPos) <= 16 * 16) {
                BlockEntity be = player.level().getBlockEntity(heaterPos);
                if (be instanceof ThermalHeaterBlockEntity heater && heater.isLit()) {
                    if (player.level().getBrightness(LightLayer.BLOCK, playerPos) >= 12) {
                        return true;
                    }
                }
            }
        }

        for (BlockPos corePos : GeothermalCoreRegistry.getCores(player.level())) {
            if (playerPos.distSqr(corePos) <= 16 * 16) {
                if (player.level().getBrightness(LightLayer.BLOCK, playerPos) >= 12) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isNearOtherPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) continue;
            if (other.level().dimension() != player.level().dimension()) continue;
            if (player.distanceToSqr(other) <= 32.0 * 32.0) {
                return true;
            }
        }
        return false;
    }
}
