package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthArchitectManager;
import com.frozendawn.homo.HearthArchitectPolicy;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.HearthTargetPolicy;
import com.frozendawn.homo.HearthTargetPolicy.Candidate;
import com.frozendawn.homo.HearthTransmissionManager;
import com.frozendawn.homo.HeartScavengerWaveManager;
import com.frozendawn.homo.OrsaEquipmentDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Non-destructive Major-Hearth behavior for the Architect's first assessment.
 */
final class ArchitectHearthAssessmentController {
    private static final double HOSTILE_ACQUISITION_RANGE = 96.0D;
    private static final double ASSESSMENT_SPEED = 0.55D;
    private static final double RETURN_SPEED = 0.7D;
    private static final int PATROL_DELAY_MIN = 100;
    private static final int PATROL_DELAY_VARIANCE = 100;

    private final ArchitectEntity architect;
    private final ArchitectAssessmentCommitment commitment =
            new ArchitectAssessmentCommitment();
    private int patrolCooldown;

    ArchitectHearthAssessmentController(ArchitectEntity architect) {
        this.architect = architect;
    }

    /**
     * @return true when neutral assessment mode handled this tick; false lets the normal combat brain run.
     */
    boolean tick(ServerLevel level) {
        LivingEntity attacker = architect.getLastHurtByMob();
        if (attacker != null && attacker.isAlive()) {
            resetAssessmentCycle();
            return false;
        }

        UUID activeHearthId = architect.getHearthAssessorId().orElse(null);
        if (HeartScavengerWaveManager.isHeartScavenger(
                architect.getTarget(), activeHearthId)) {
            resetAssessmentCycle();
            return false;
        }
        if (findHostileTarget(level) != null) {
            resetAssessmentCycle();
            return false;
        }

        architect.prepareHearthAssessmentMode();
        BlockPos hearth = architect.getHearthAssessorCenter().orElse(null);
        UUID hearthId = architect.getHearthAssessorId().orElse(null);
        if (hearth == null || hearthId == null) {
            return true;
        }

        if (HearthArchitectPolicy.shouldReturnHome(
                architect.position().distanceToSqr(hearth.getCenter()))) {
            commitment.resetTicks();
            architect.getNavigation().moveTo(
                    hearth.getX() + 0.5D, hearth.getY(), hearth.getZ() + 0.5D, RETURN_SPEED);
            return true;
        }

        ServerPlayer player = mostVulnerablePlayer(level);
        if (player == null) {
            resetAssessmentTarget();
            patrol(hearth);
            return true;
        }

        architect.getLookControl().setLookAt(player, 30.0F, 30.0F);
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        boolean alreadyAssessed = data.hearth(hearthId)
                .flatMap(record -> record.playerContact(player.getUUID()))
                .map(ReturnedHearthSavedData.HearthContactMemory::architectAssessmentComplete)
                .orElse(false);
        if (alreadyAssessed) {
            resetAssessmentTarget();
            HearthTransmissionManager.tryStart(level, architect, player, hearthId);
            holdWatchfulPerimeter(player, hearth);
            return true;
        }

        double distanceSquared = architect.distanceToSqr(player);
        if (distanceSquared < (double) HearthArchitectPolicy.ASSESSMENT_MIN_DISTANCE
                * HearthArchitectPolicy.ASSESSMENT_MIN_DISTANCE) {
            commitment.resetTicks();
            retreatFrom(player, hearth);
            return true;
        }
        if (!HearthArchitectPolicy.isAssessmentDistance(distanceSquared)
                || !architect.hasLineOfSight(player)) {
            commitment.resetTicks();
            architect.getNavigation().moveTo(player, ASSESSMENT_SPEED);
            return true;
        }

        architect.getNavigation().stop();
        int assessmentTicks = commitment.advanceTicks();
        if (assessmentTicks % 20 == 0) {
            level.sendParticles(ParticleTypes.SOUL,
                    architect.getX(), architect.getY() + 1.8D, architect.getZ(),
                    1, 0.08D, 0.08D, 0.08D, 0.005D);
        }
        if (assessmentTicks < HearthArchitectPolicy.ASSESSMENT_TICKS) {
            return true;
        }

        boolean orsaDetected = OrsaEquipmentDetector.hasOrsaTechnology(player);
        ReturnedHearthSavedData.AssessmentResult result = data.recordArchitectAssessment(
                player.getUUID(), hearthId, level.getGameTime(), orsaDetected);
        if (result.completedNow()) {
            HearthArchitectManager.recordCompletedAssessment();
            level.sendParticles(ParticleTypes.ENCHANT,
                    architect.getX(), architect.getY() + 1.65D, architect.getZ(),
                    8, 0.25D, 0.2D, 0.25D, 0.04D);
            FrozenDawn.LOGGER.info(
                    "Hearth Architect {} assessed player {} at Hearth {} | orsa={} relationship={}",
                    shortId(architect.getUUID()), player.getGameProfile().getName(),
                    shortId(hearthId), orsaDetected,
                    result.currentRelationship().name().toLowerCase());
            HearthTransmissionManager.tryStart(level, architect, player, hearthId);
        }
        resetAssessmentTarget();
        return true;
    }

    @Nullable
    ServerPlayer findHostileTarget(ServerLevel level) {
        if (architect.getLastHurtByMob() instanceof ServerPlayer hostile
                && hostile.isAlive() && !hostile.isCreative() && !hostile.isSpectator()) {
            return hostile;
        }
        UUID hearthId = architect.getHearthAssessorId().orElse(null);
        if (hearthId != null && !HearthCombatRosterManager.canEngagePlayer(
                level, hearthId, architect.getUUID())) {
            return null;
        }
        return level.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator())
                .filter(player -> architect.distanceToSqr(player)
                        <= HOSTILE_ACQUISITION_RANGE * HOSTILE_ACQUISITION_RANGE)
                .filter(player -> HearthMemoryManager.isPermanentOrsathae(
                        level, player.getUUID()))
                .min(Comparator.comparing(this::toCandidate,
                        HearthTargetPolicy.BY_VULNERABILITY))
                .orElse(null);
    }

    @Nullable
    private ServerPlayer mostVulnerablePlayer(ServerLevel level) {
        List<ServerPlayer> inRange = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .filter(player -> architect.distanceToSqr(player)
                        <= (double) HearthArchitectPolicy.WATCH_DISTANCE
                        * HearthArchitectPolicy.WATCH_DISTANCE)
                .toList();
        UUID targetId = commitment.resolve(
                inRange.stream().map(this::toCandidate).toList(),
                assessablePlayerIds(level));
        if (targetId == null) {
            return null;
        }
        for (ServerPlayer player : inRange) {
            if (player.getUUID().equals(targetId)) {
                return player;
            }
        }
        return null;
    }

    private Candidate toCandidate(ServerPlayer player) {
        return new Candidate(player.getUUID(), player.getArmorValue(),
                architect.distanceToSqr(player));
    }

    /**
     * Every player still assessable anywhere on the server. A committed target
     * missing from this set has died or logged out and is forgotten; one that is
     * present but out of range is only suspended.
     */
    private static Set<UUID> assessablePlayerIds(ServerLevel level) {
        Set<UUID> ids = new HashSet<>();
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            for (ServerPlayer player : dimension.players()) {
                if (player.isAlive() && !player.isSpectator()) {
                    ids.add(player.getUUID());
                }
            }
        }
        return ids;
    }

    private void holdWatchfulPerimeter(ServerPlayer player, BlockPos hearth) {
        double distanceSquared = architect.distanceToSqr(player);
        if (distanceSquared < (double) HearthArchitectPolicy.ASSESSMENT_MIN_DISTANCE
                * HearthArchitectPolicy.ASSESSMENT_MIN_DISTANCE) {
            retreatFrom(player, hearth);
        } else {
            architect.getNavigation().stop();
        }
    }

    private void retreatFrom(ServerPlayer player, BlockPos hearth) {
        Vec3 away = architect.position().subtract(player.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 desired = architect.position().add(away.normalize().scale(7.0D));
        moveWithinHearth(desired.x, desired.z, hearth, RETURN_SPEED);
    }

    private void patrol(BlockPos hearth) {
        if (patrolCooldown > 0) {
            patrolCooldown--;
            return;
        }
        if (architect.getNavigation().isInProgress()) {
            return;
        }
        double angle = architect.nextRandomFloat() * Math.PI * 2.0D;
        double radius = 8.0D + architect.nextRandomFloat() * 10.0D;
        architect.getNavigation().moveTo(
                hearth.getX() + 0.5D + Math.cos(angle) * radius,
                hearth.getY(),
                hearth.getZ() + 0.5D + Math.sin(angle) * radius,
                ASSESSMENT_SPEED);
        patrolCooldown = PATROL_DELAY_MIN
                + architect.nextRandomInt(PATROL_DELAY_VARIANCE + 1);
    }

    private void moveWithinHearth(double x, double z, BlockPos hearth, double speed) {
        Vec3 center = hearth.getCenter();
        Vec3 offset = new Vec3(x - center.x, 0.0D, z - center.z);
        double maxRadius = HearthArchitectPolicy.HOME_RADIUS - 2.0D;
        if (offset.horizontalDistanceSqr() > maxRadius * maxRadius) {
            offset = offset.normalize().scale(maxRadius);
        }
        architect.getNavigation().moveTo(
                center.x + offset.x, hearth.getY(), center.z + offset.z, speed);
    }

    private void resetAssessmentCycle() {
        resetAssessmentTarget();
        patrolCooldown = 0;
    }

    private void resetAssessmentTarget() {
        commitment.release();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
