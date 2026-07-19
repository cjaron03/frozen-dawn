package com.frozendawn.homo;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.network.MasterArchitectFightMusicPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps each nearby hostile client synchronized to the Master's score movement. */
public final class MasterArchitectFightMusicManager {
    private static final int HEARTBEAT_INTERVAL_TICKS = 20;
    private static final double MUSIC_RANGE_SQUARED = 144.0D * 144.0D;
    private static final double STOP_RANGE_SQUARED = 192.0D * 192.0D;

    private MasterArchitectFightMusicManager() {
    }

    public static void start(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new MasterArchitectFightMusicPayload(MasterArchitectMusicStage.KIT.id()));
    }

    public static void heartbeat(
            ServerLevel level,
            ArchitectEntity architect,
            MasterArchitectMusicStage stage) {
        if (architect.tickCount % HEARTBEAT_INTERVAL_TICKS == 0) {
            pushStage(level, architect, stage);
        }
    }

    public static void pushStage(
            ServerLevel level,
            ArchitectEntity architect,
            MasterArchitectMusicStage stage) {
        for (ServerPlayer player : level.players()) {
            if (eligibleListener(level, architect, player)) {
                PacketDistributor.sendToPlayer(player,
                        new MasterArchitectFightMusicPayload(stage.id()));
            }
        }
    }

    public static void stopNearby(ServerLevel level, ArchitectEntity architect) {
        for (ServerPlayer player : level.players()) {
            if (architect.distanceToSqr(player) <= STOP_RANGE_SQUARED) {
                PacketDistributor.sendToPlayer(player,
                        MasterArchitectFightMusicPayload.inactive());
            }
        }
    }

    private static boolean eligibleListener(
            ServerLevel level, ArchitectEntity architect, ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && architect.distanceToSqr(player) <= MUSIC_RANGE_SQUARED
                && HearthMemoryManager.isPermanentOrsathae(level, player.getUUID());
    }
}
