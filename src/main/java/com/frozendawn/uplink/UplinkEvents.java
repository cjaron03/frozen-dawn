package com.frozendawn.uplink;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.CargoDropState;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.CargoDropPlacement;
import com.frozendawn.world.CargoDropStructureBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Builds requested supply drops once their area is loaded by play (never force-loaded) and runs their locator beacons. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class UplinkEvents {
    private static final int[][] NUDGES = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {24, 24}, {-24, 24}, {24, -24}, {-24, -24}};
    private UplinkEvents() { }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var level = event.getServer().overworld();
        if (level.players().isEmpty() || level.getGameTime() % 40 != 0) return;
        var data = UplinkSavedData.get(event.getServer());
        for (var drop : data.drops()) {
            if (drop.retrieved) continue;
            if (drop.center == null) { build(level, data, drop); continue; }
            beacon(level, data, drop);
        }
    }

    /** Spawn-chance multiplier for natural Architects near a position: the lagged pressure of nearby transmitting dishes. */
    public static double spawnMultiplier(ServerLevel level, BlockPos pos) {
        return UplinkPolicy.spawnMultiplier(UplinkSavedData.get(level.getServer())
                .pressure(level.dimension().location().toString(), pos, level.getGameTime()));
    }

    private static void build(ServerLevel level, UplinkSavedData data, UplinkSavedData.Drop drop) {
        int x = drop.target.getX(), z = drop.target.getZ();
        if (!loaded(level, x, z, 40)) return;
        int bestX = x, bestZ = z;
        for (var n : NUDGES)
            if (CargoDropPlacement.isEligibleCargoDropSite(level, x + n[0], z + n[1])) { bestX = x + n[0]; bestZ = z + n[1]; break; }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bestX, bestZ);
        var center = new BlockPos(bestX, Math.max(y, level.getMinBuildHeight() + 6), bestZ);
        CargoDropStructureBuilder.placeUplinkResupply(level, center);
        CargoDropState.get(level.getServer()).markCargoDropBuilt(bestX >> 4, bestZ >> 4, CargoDropPlacement.getCargoDropDisplayPos(level.getSeed(), center));
        data.built(drop, center);
        FrozenDawn.LOGGER.info("[Uplink] Supply drop placed at ({}, {}, {})", center.getX(), center.getY(), center.getZ());
    }

    private static void beacon(ServerLevel level, UplinkSavedData data, UplinkSavedData.Drop drop) {
        boolean pinged = false;
        for (var player : level.players()) {
            if (player.isSpectator()) continue;
            double sq = player.position().distanceToSqr(drop.center.getCenter());
            if (sq > (double) UplinkPolicy.BEACON_RANGE * UplinkPolicy.BEACON_RANGE) continue;
            if (sq <= (double) UplinkPolicy.RETRIEVE_RANGE * UplinkPolicy.RETRIEVE_RANGE) {
                data.retrieved(drop);
                player.displayClientMessage(Component.translatable("uplink.frozendawn.beacon.found"), true);
                level.playSound(null, drop.center, ModSounds.RADIO_CUTOFF.get(), SoundSource.BLOCKS, 0.7f, 1.4f);
                return;
            }
            double dx = drop.center.getX() + .5 - player.getX(), dz = drop.center.getZ() + .5 - player.getZ();
            player.displayClientMessage(Component.translatable("uplink.frozendawn.beacon.ping",
                    (int) Math.round(Math.sqrt(dx * dx + dz * dz)), UplinkPolicy.sector(dx, dz)), true);
            pinged = true;
        }
        if (pinged) level.playSound(null, drop.center, ModSounds.RADIO_SIGNAL_LOCK.get(), SoundSource.BLOCKS, 0.9f, 1.7f);
    }

    private static boolean loaded(ServerLevel level, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx += 16) for (int dz = -radius; dz <= radius; dz += 16)
            if (!level.isLoaded(new BlockPos(x + dx, 0, z + dz))) return false;
        return level.isLoaded(new BlockPos(x + radius, 0, z + radius));
    }
}
