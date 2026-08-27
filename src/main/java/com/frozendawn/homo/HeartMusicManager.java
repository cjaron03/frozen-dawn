package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.network.HeartMusicStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Synchronizes the world-persistent Heart music claim without chunk loading. */
public final class HeartMusicManager {
    private static final Map<UUID, MusicState> LAST_SENT_STATE = new HashMap<>();

    private HeartMusicManager() {
    }

    public static void tick(MinecraftServer server) {
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(server).hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .orElse(null);
        boolean heartbeat = server.overworld().getGameTime() % 20L == 0L;
        Set<UUID> online = heartbeat ? new HashSet<>() : null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean inRange = hearth != null
                    && player.level().dimension() == Level.OVERWORLD
                    && player.position().distanceToSqr(
                    hearth.heartAnchor().orElse(hearth.center()).getCenter())
                    <= HeartFormationPolicy.AURA_RADIUS
                    * HeartFormationPolicy.AURA_RADIUS;
            MusicState state = hearth == null
                    ? new MusicState(false, false)
                    : new MusicState(hearth.heartMusicActive() && inRange,
                    hearth.heartMaeveErasureComplete());
            UUID playerId = player.getUUID();
            if (online != null) {
                online.add(playerId);
            }
            MusicState previous = LAST_SENT_STATE.get(playerId);
            if (heartbeat || !state.equals(previous)) {
                PacketDistributor.sendToPlayer(
                        player, new HeartMusicStatePayload(
                                state.active(), state.erased()));
                LAST_SENT_STATE.put(playerId, state);
            }
        }
        if (online != null) {
            LAST_SENT_STATE.keySet().retainAll(online);
        }
    }

    public static void reset() {
        LAST_SENT_STATE.clear();
    }

    private record MusicState(boolean active, boolean erased) {
    }
}
