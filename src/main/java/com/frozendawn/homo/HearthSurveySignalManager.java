package com.frozendawn.homo;

import com.frozendawn.item.HearthSurveyScanner;
import com.frozendawn.item.SurveyorLensScanner;
import com.frozendawn.network.HearthSurveyAudioPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Samples world-level Hearth records for held survey lenses without loading chunks.
 */
public final class HearthSurveySignalManager {
    private static final int SAMPLE_INTERVAL_TICKS = 10;
    private static final Set<UUID> activePlayers = new HashSet<>();

    private HearthSurveySignalManager() {
    }

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % SAMPLE_INTERVAL_TICKS != 0L) {
            return;
        }

        Set<UUID> onlinePlayers = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlinePlayers.add(player.getUUID());
            updatePlayer(player);
        }
        activePlayers.retainAll(onlinePlayers);
    }

    private static void updatePlayer(ServerPlayer player) {
        SurveyorLensScanner.LensProfile profile = SurveyorLensScanner.heldProfile(
                player.getMainHandItem(), player.getOffhandItem());
        if (profile == null || player.level().dimension() != Level.OVERWORLD) {
            deactivate(player);
            return;
        }

        if (PostMaeveWorldState.isErased(player.serverLevel())) {
            PacketDistributor.sendToPlayer(player,
                    new HearthSurveyAudioPayload(true, 0.08F));
            activePlayers.add(player.getUUID());
            return;
        }

        HearthSurveyScanner.HearthSignal signal = HearthSurveyScanner.sample(player, profile).orElse(null);
        if (signal == null) {
            deactivate(player);
            return;
        }

        PacketDistributor.sendToPlayer(player, new HearthSurveyAudioPayload(
                true,
                signal.proximity()
        ));
        activePlayers.add(player.getUUID());
    }

    private static void deactivate(ServerPlayer player) {
        if (activePlayers.remove(player.getUUID())) {
            PacketDistributor.sendToPlayer(player, HearthSurveyAudioPayload.inactive());
        }
    }

    public static void reset() {
        activePlayers.clear();
    }
}
