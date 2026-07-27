package com.frozendawn.entity;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthMasterArchitectPolicy;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.MasterArchitectAuraTier;
import com.frozendawn.homo.MasterArchitectFightMusicManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;

/** Owns both peaceful watch and hostile combat for the INTACT Hearth apex. */
final class ArchitectHearthMasterController {
    private static final double HOSTILE_ACQUISITION_RANGE = 112.0D;
    private static final double WALK_SPEED = 0.45D;
    private static final double RETURN_SPEED = 0.65D;
    private static final int PATROL_DELAY_MIN = 180;
    private static final int PATROL_DELAY_VARIANCE = 180;

    private final ArchitectEntity architect;
    private final MasterArchitectCombatController combatController;
    private int patrolCooldown;
    private boolean combatMusicActive;

    ArchitectHearthMasterController(ArchitectEntity architect) {
        this.architect = architect;
        this.combatController = new MasterArchitectCombatController(architect);
    }

    /** @return always true because the Master never falls through to ordinary Architect AI. */
    boolean tick(ServerLevel level) {
        combatController.tickPersistentState(level);
        BlockPos boundaryCenter = hearthBoundaryCenter(level);
        enforceStormBoundary(boundaryCenter);
        if (combatController.isMindSessionActive()) {
            updateAuraTier(level, MasterArchitectAuraTier.FIGHT);
            architect.setMasterBossBarProvoked(true);
            combatController.tickFolded(level);
            if (!combatMusicActive) {
                MasterArchitectFightMusicManager.pushStage(
                        level, architect, combatController.musicStage());
            } else {
                MasterArchitectFightMusicManager.heartbeat(
                        level, architect, combatController.musicStage());
            }
            combatMusicActive = true;
            return true;
        }
        ServerPlayer hostileTarget = findHostileTarget(level);
        if (hostileTarget != null) {
            updateAuraTier(level, MasterArchitectAuraTier.FIGHT);
            architect.getHearthMasterArchitectId().ifPresent(
                    hearthId -> HearthCombatRosterManager.ensureRoster(
                            level, hearthId, hostileTarget));
            architect.setMasterBossBarProvoked(true);
            patrolCooldown = 0;
            combatController.tick(level, hostileTarget, boundaryCenter);
            enforceStormBoundary(boundaryCenter);
            if (!combatMusicActive) {
                MasterArchitectFightMusicManager.pushStage(
                        level, architect, combatController.musicStage());
            } else {
                MasterArchitectFightMusicManager.heartbeat(
                        level, architect, combatController.musicStage());
            }
            combatMusicActive = true;
            return true;
        }

        if (combatMusicActive) {
            MasterArchitectFightMusicManager.stopNearby(level, architect);
            combatMusicActive = false;
        }
        architect.setMasterBossBarProvoked(false);
        combatController.leaveCombat(level);
        updateAuraTier(level, peacefulAuraTier(level));
        architect.prepareHearthAssessmentMode();
        BlockPos home = architect.getHearthMasterArchitectHome().orElse(null);
        if (home == null) {
            return true;
        }

        if (architect.position().distanceToSqr(home.getCenter())
                > (double) HearthMasterArchitectPolicy.HOME_RADIUS
                * HearthMasterArchitectPolicy.HOME_RADIUS) {
            architect.getNavigation().moveTo(
                    home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, RETURN_SPEED);
            return true;
        }

        ServerPlayer player = nearestPlayer(level);
        if (player != null) {
            architect.getLookControl().setLookAt(player, 8.0F, 6.0F);
            if (architect.distanceToSqr(player)
                    < (double) HearthMasterArchitectPolicy.RETREAT_DISTANCE
                    * HearthMasterArchitectPolicy.RETREAT_DISTANCE) {
                retreatFrom(player, home);
            } else {
                architect.getNavigation().stop();
            }
            return true;
        }

        patrol(home);
        return true;
    }

    void onHurt(ServerLevel level, @Nullable ServerPlayer attacker) {
        ServerPlayer floodTarget = attacker != null ? attacker : findHostileTarget(level);
        combatController.onHurt(level, floodTarget);
    }

    MasterArchitectCombatController.TetherDamageResult redistributeIncomingDamage(
            ServerLevel level, float incomingDamage) {
        return combatController.redistributeIncomingDamage(level, incomingDamage);
    }

    float prepareIncomingDamage(float incomingDamage, boolean bypassesInvulnerability) {
        return combatController.prepareIncomingDamage(
                incomingDamage, bypassesInvulnerability);
    }

    void onDeath(ServerLevel level, @Nullable ServerPlayer killer) {
        MasterArchitectFightMusicManager.stopNearby(level, architect);
        combatMusicActive = false;
        combatController.onDeath(level, killer);
        architect.getHearthMasterArchitectId().ifPresent(
                hearthId -> HearthCombatRosterManager.setFloodKneeling(
                        level, hearthId, true));
    }

    void onMindCopyHurt(
            ServerLevel level,
            ArchitectEntity copy,
            net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        combatController.onMindCopyHurt(level, copy, source, amount);
    }

    float prepareMindCopyDamage(
            ServerLevel level,
            ArchitectEntity copy,
            net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        return combatController.prepareMindCopyDamage(level, copy, source, amount);
    }

    void onMindCopyDefeated(
            ServerLevel level,
            ArchitectEntity copy,
            @Nullable ServerPlayer killer) {
        combatController.onMindCopyDefeated(level, copy, killer);
    }

    void onMindParticipantFailed(
            ServerLevel level, ServerPlayer player, String reason) {
        combatController.onMindParticipantFailed(level, player, reason);
    }

    void addSaveData(net.minecraft.nbt.CompoundTag tag) {
        combatController.addSaveData(tag);
    }

    void readSaveData(net.minecraft.nbt.CompoundTag tag) {
        combatController.readSaveData(tag);
    }

    @Nullable
    ServerPlayer findHostileTarget(ServerLevel level) {
        return level.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator())
                .filter(player -> architect.distanceToSqr(player)
                        <= HOSTILE_ACQUISITION_RANGE * HOSTILE_ACQUISITION_RANGE)
                .filter(player -> HearthMasterArchitectPolicy.isHostileRelationship(
                        HearthMemoryManager.relationship(level, player.getUUID())))
                .min(Comparator.comparingDouble(architect::distanceToSqr))
                .orElse(null);
    }

    @Nullable
    private ServerPlayer nearestPlayer(ServerLevel level) {
        return level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .filter(player -> architect.distanceToSqr(player)
                        <= (double) HearthMasterArchitectPolicy.WATCH_DISTANCE
                        * HearthMasterArchitectPolicy.WATCH_DISTANCE)
                .min(Comparator.comparingDouble(architect::distanceToSqr))
                .orElse(null);
    }

    private void retreatFrom(ServerPlayer player, BlockPos home) {
        Vec3 away = architect.position().subtract(player.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 desired = architect.position().add(away.normalize().scale(5.0D));
        moveWithinHome(desired.x, desired.z, home, RETURN_SPEED);
    }

    private void patrol(BlockPos home) {
        if (patrolCooldown > 0) {
            patrolCooldown--;
            return;
        }
        if (architect.getNavigation().isInProgress()) {
            return;
        }
        double angle = architect.nextRandomFloat() * Math.PI * 2.0D;
        double radius = 2.0D + architect.nextRandomFloat() * 4.0D;
        architect.getNavigation().moveTo(
                home.getX() + 0.5D + Math.cos(angle) * radius,
                home.getY(),
                home.getZ() + 0.5D + Math.sin(angle) * radius,
                WALK_SPEED);
        patrolCooldown = PATROL_DELAY_MIN
                + architect.nextRandomInt(PATROL_DELAY_VARIANCE + 1);
    }

    private void moveWithinHome(double x, double z, BlockPos home, double speed) {
        Vec3 center = home.getCenter();
        Vec3 offset = new Vec3(x - center.x, 0.0D, z - center.z);
        double maxRadius = HearthMasterArchitectPolicy.HOME_RADIUS - 1.0D;
        if (offset.horizontalDistanceSqr() > maxRadius * maxRadius) {
            offset = offset.normalize().scale(maxRadius);
        }
        architect.getNavigation().moveTo(
                center.x + offset.x, home.getY(), center.z + offset.z, speed);
    }

    @Nullable
    private BlockPos hearthBoundaryCenter(ServerLevel level) {
        return architect.getHearthMasterArchitectId()
                .flatMap(id -> ReturnedHearthSavedData.get(level.getServer()).hearth(id))
                .map(ReturnedHearthSavedData.HearthRecord::center)
                .orElseGet(() -> architect.getHearthMasterArchitectHome().orElse(null));
    }

    private void enforceStormBoundary(@Nullable BlockPos center) {
        if (center == null
                || HearthMasterArchitectPolicy.isInsideStormBoundary(
                        center, architect.position())) {
            return;
        }
        Vec3 clamped = HearthMasterArchitectPolicy.clampToStormBoundary(
                center, architect.position());
        architect.getNavigation().stop();
        architect.setDeltaMovement(0.0D, architect.getDeltaMovement().y, 0.0D);
        architect.setPos(clamped.x, clamped.y, clamped.z);
    }

    private int peacefulAuraTier(ServerLevel level) {
        ReturnedHearthSavedData.HearthDisposition mood = architect
                .getHearthMasterArchitectId()
                .flatMap(id -> ReturnedHearthSavedData.get(level.getServer()).hearth(id))
                .map(ReturnedHearthSavedData.HearthRecord::mood)
                .orElse(ReturnedHearthSavedData.HearthDisposition.DORMANT);
        return MasterArchitectAuraTier.fromMood(mood, false);
    }

    private void updateAuraTier(ServerLevel level, int tier) {
        int previous = architect.getMasterAuraTier();
        if (previous == tier) {
            return;
        }
        architect.setMasterAuraTier(tier);
        architect.getHearthMasterArchitectId().ifPresent(
                hearthId -> HearthCombatRosterManager.signalAuraTierChange(
                        level, hearthId, architect, tier));
    }
}
