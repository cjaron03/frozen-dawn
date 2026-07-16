package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.network.MasterArchitectFourthWallRequestPayload;
import com.frozendawn.network.MasterArchitectFourthWallStatePayload;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server authority for persistence and advancement only; camera state never leaves the client. */
public final class MasterArchitectFourthWallManager {
    private static final ResourceLocation ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "host_has_joined_the_game");

    private MasterArchitectFourthWallManager() {
    }

    public static void handleRequest(
            ServerPlayer player, MasterArchitectFourthWallRequestPayload payload) {
        AdvancementHolder advancement = advancement(player);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements()
                .getOrStartProgress(advancement);
        if (progress.isDone()) {
            sendState(player, payload.entityId(),
                    MasterArchitectFourthWallStatePayload.COMPLETED);
            return;
        }

        Entity candidate = player.serverLevel().getEntity(payload.entityId());
        if (!(candidate instanceof ArchitectEntity master)
                || !MasterArchitectFourthWallPolicy.isPeacefulWatchEligible(
                        master.isHearthMasterArchitect(),
                        master.isAlive(),
                        master.getDeathTicks(),
                        master.getMasterCombatAction(),
                        HearthMemoryManager.relationship(
                                player.serverLevel(), player.getUUID()),
                        master.distanceToSqr(player))) {
            return;
        }

        if (!payload.complete()) {
            sendState(player, master.getId(),
                    MasterArchitectFourthWallStatePayload.ELIGIBLE);
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
        if (!progress.isDone()) {
            return;
        }

        sendState(player, master.getId(),
                MasterArchitectFourthWallStatePayload.TRIGGERED);
        FrozenDawn.LOGGER.info(
                "Master Architect {} completed its one-time perception moment for player {}",
                shortId(master.getUUID()), shortId(player.getUUID()));
    }

    private static AdvancementHolder advancement(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : server.getAdvancements().get(ADVANCEMENT);
    }

    private static void sendState(ServerPlayer player, int entityId, int state) {
        PacketDistributor.sendToPlayer(player,
                new MasterArchitectFourthWallStatePayload(entityId, state));
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }
}
