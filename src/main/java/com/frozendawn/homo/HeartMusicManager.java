package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.network.HeartMusicStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Synchronizes the world-persistent Heart music claim without chunk loading. */
public final class HeartMusicManager {
    private static final Map<UUID, Boolean> LAST_SENT_STATE = new HashMap<>();

    private HeartMusicManager() {
    }

    public static void tick(MinecraftServer server) {
        boolean active = ReturnedHearthSavedData.get(server)
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .map(ReturnedHearthSavedData.HearthRecord::heartMusicActive)
                .orElse(false);
        boolean heartbeat = server.overworld().getGameTime() % 20L == 0L;
        Set<UUID> online = heartbeat ? new HashSet<>() : null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (online != null) {
                online.add(playerId);
            }
            Boolean previous = LAST_SENT_STATE.get(playerId);
            if (heartbeat || previous == null || previous != active) {
                PacketDistributor.sendToPlayer(
                        player, new HeartMusicStatePayload(active));
                LAST_SENT_STATE.put(playerId, active);
            }
        }
        if (online != null) {
            LAST_SENT_STATE.keySet().retainAll(online);
        }
    }

    public static void reset() {
        LAST_SENT_STATE.clear();
    }
}
