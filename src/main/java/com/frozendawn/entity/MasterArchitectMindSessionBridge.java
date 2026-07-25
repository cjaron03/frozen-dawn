package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.world.ThaeIvenMindDimension;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.UUID;

/** Resolves cross-dimension copy events back to the force-loaded real Master. */
public final class MasterArchitectMindSessionBridge {
    private MasterArchitectMindSessionBridge() {
    }

    public static void onCopyHurt(
            ServerLevel copyLevel,
            ArchitectEntity copy,
            DamageSource source,
            float amount) {
        findRealMaster(copyLevel.getServer(), copy.getMindCopyRealMasterId().orElse(null))
                .ifPresent(master -> master.onMindCopyHurt(copy, source, amount));
    }

    public static float prepareCopyDamage(
            ServerLevel copyLevel,
            ArchitectEntity copy,
            DamageSource source,
            float amount) {
        return findRealMaster(
                copyLevel.getServer(), copy.getMindCopyRealMasterId().orElse(null))
                .map(master -> master.prepareMindCopyDamage(copy, source, amount))
                .orElse(amount);
    }

    public static void onCopyDefeated(
            ServerLevel copyLevel,
            ArchitectEntity copy,
            DamageSource source) {
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player : null;
        findRealMaster(copyLevel.getServer(), copy.getMindCopyRealMasterId().orElse(null))
                .ifPresentOrElse(
                        master -> master.onMindCopyDefeated(copy, killer),
                        () -> recoverParticipantsForMissingMaster(copyLevel.getServer(), copy));
    }

    public static void participantFailed(ServerPlayer player, String reason) {
        UUID masterId = ThaeIvenMindDimension.storedMasterId(player);
        findRealMaster(player.getServer(), masterId).ifPresentOrElse(
                master -> master.onMindParticipantFailed(player, reason),
                () -> ThaeIvenMindDimension.returnToOrigin(player, masterId));
    }

    public static void recoverStrandedPlayer(ServerPlayer player) {
        if (!ThaeIvenMindDimension.hasStoredOrigin(player)) {
            return;
        }
        UUID masterId = ThaeIvenMindDimension.storedMasterId(player);
        findRealMaster(player.getServer(), masterId).ifPresent(
                master -> master.onMindParticipantFailed(player, "login-or-respawn-recovery"));
        if (ThaeIvenMindDimension.hasStoredOrigin(player)) {
            ThaeIvenMindDimension.returnToOrigin(player, masterId);
        }
    }

    private static java.util.Optional<ArchitectEntity> findRealMaster(
            MinecraftServer server, @Nullable UUID masterId) {
        if (server == null || masterId == null) {
            return java.util.Optional.empty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(masterId);
            if (entity instanceof ArchitectEntity architect
                    && architect.isHearthMasterArchitect()) {
                return java.util.Optional.of(architect);
            }
        }
        return java.util.Optional.empty();
    }

    private static void recoverParticipantsForMissingMaster(
            MinecraftServer server, ArchitectEntity copy) {
        FrozenDawn.LOGGER.warn(
                "Discarding orphaned Master Architect mind-copy {}",
                copy.getStringUUID().substring(0, 8));
        UUID realMasterId = copy.getMindCopyRealMasterId().orElse(null);
        if (realMasterId == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (realMasterId.equals(ThaeIvenMindDimension.storedMasterId(player))) {
                ThaeIvenMindDimension.returnToOrigin(
                        player, realMasterId);
            }
        }
    }
}
