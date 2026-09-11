package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthArchitectManager;
import com.frozendawn.homo.HearthArchitectPolicy;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.HearthPopulationPolicy;
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
 * Quiet workshop/perimeter behavior for the ordinary INTACT-Hearth Architect.
 */
final class ArchitectHearthResidentController {
    private static final double HOSTILE_ACQUISITION_RANGE = 96.0D;
    private static final double WALK_SPEED = 0.55D;
    private static final double RETURN_SPEED = 0.7D;
    private static final int PATROL_DELAY_MIN = 120;
    private static final int PATROL_DELAY_VARIANCE = 120;

    private final ArchitectEntity architect;
    private final ArchitectAssessmentCommitment commitment =
            new ArchitectAssessmentCommitment();
    private int patrolCooldown;

    ArchitectHearthResidentController(ArchitectEntity architect) {
        this.architect = architect;
    }

    /**
     * @return true when neutral resident behavior handled this tick.
     */
    boolean tick(ServerLevel level) {
        LivingEntity attacker = architect.getLastHurtByMob();
        if (attacker != null && attacker.isAlive()) {
            resetAssessmentTarget();
            patrolCooldown = 0;
            return false;
        }

        UUID hearthId = architect.getHearthPopulationId().orElse(null);
        if (HeartScavengerWaveManager.isHeartScavenger(
                architect.getTarget(), hearthId)) {
            resetAssessmentTarget();
            patrolCooldown = 0;
            return false;
        }
        if (findHostileTarget(level) != null) {
            patrolCooldown = 0;
            return false;
        }

        architect.prepareHearthAssessmentMode();
        BlockPos home = architect.getHearthPopulationHome().orElse(null);
        if (home == null) {
            return true;
        }

        if (architect.position().distanceToSqr(home.getCenter())
                > (double) HearthPopulationPolicy.ARCHITECT_HOME_RADIUS
                * HearthPopulationPolicy.ARCHITECT_HOME_RADIUS) {
            architect.getNavigation().moveTo(
                    home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, RETURN_SPEED);
            return true;
        }

        ServerPlayer player = mostVulnerablePlayer(level);
        if (player != null) {
            architect.getLookControl().setLookAt(player, 30.0F, 30.0F);
            assessPlayer(level, player, home);
            return true;
        }

        resetAssessmentTarget();
        patrol(home);
        return true;
    }

    private void assessPlayer(ServerLevel level, ServerPlayer player, BlockPos home) {
        UUID hearthId = architect.getHearthPopulationId().orElse(null);
        if (hearthId == null) {
            resetAssessmentTarget();
            return;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        boolean alreadyAssessed = data.hearth(hearthId)
                .flatMap(record -> record.playerContact(player.getUUID()))
                .map(ReturnedHearthSavedData.HearthContactMemory::architectAssessmentComplete)
                .orElse(false);
        if (alreadyAssessed) {
            resetAssessmentTarget();
            HearthTransmissionManager.tryStart(level, architect, player, hearthId);
            holdWatchfulPerimeter(player, home);
            return;
        }

        double distanceSquared = architect.distanceToSqr(player);
        if (distanceSquared < (double) HearthArchitectPolicy.ASSESSMENT_MIN_DISTANCE
                * HearthArchitectPolicy.ASSESSMENT_MIN_DISTANCE) {
            commitment.resetTicks();
            retreatFrom(player, home);
            return;
        }
        if (!HearthArchitectPolicy.isAssessmentDistance(distanceSquared)
                || !architect.hasLineOfSight(player)) {
            commitment.resetTicks();
            architect.getNavigation().moveTo(player, WALK_SPEED);
            return;
        }

        architect.getNavigation().stop();
        int assessmentTicks = commitment.advanceTicks();
        if (assessmentTicks % 20 == 0) {
            level.sendParticles(ParticleTypes.SOUL,
                    architect.getX(), architect.getY() + 1.8D, architect.getZ(),
                    1, 0.08D, 0.08D, 0.08D, 0.005D);
        }
        if (assessmentTicks < HearthArchitectPolicy.ASSESSMENT_TICKS) {
            return;
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
                    "Hearth population Architect {} assessed player {} at Hearth {} | orsa={} relationship={}",
                    shortId(architect.getUUID()), player.getGameProfile().getName(),
                    shortId(hearthId), orsaDetected,
                    result.currentRelationship().name().toLowerCase());
            HearthTransmissionManager.tryStart(level, architect, player, hearthId);
        }
        resetAssessmentTarget();
    }

    private void holdWatchfulPerimeter(ServerPlayer player, BlockPos home) {
        double distanceSquared = architect.distanceToSqr(player);
        if (distanceSquared < (double) HearthPopulationPolicy.RETREAT_DISTANCE
                * HearthPopulationPolicy.RETREAT_DISTANCE) {
            retreatFrom(player, home);
        } else {
            architect.getNavigation().stop();
        }
    }

    private void resetAssessmentTarget() {
        commitment.release();
    }

    @Nullable
    ServerPlayer findHostileTarget(ServerLevel level) {
        if (architect.getLastHurtByMob() instanceof ServerPlayer attacker
                && attacker.isAlive() && !attacker.isCreative() && !attacker.isSpectator()) {
            return attacker;
        }
        java.util.UUID hearthId = architect.getHearthPopulationId().orElse(null);
        if (hearthId != null && !HearthCombatRosterManager.canEngagePlayer(
                level, hearthId, architect.getUUID())) {
            return null;
        }
        return level.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator())
                .filter(player -> architect.distanceToSqr(player)
                        <= HOSTILE_ACQUISITION_RANGE * HOSTILE_ACQUISITION_RANGE)
                .filter(player -> HearthPopulationPolicy.isHostileRelationship(
                        HearthMemoryManager.relationship(level, player.getUUID())))
                .min(Comparator.comparing(this::toCandidate,
                        HearthTargetPolicy.BY_VULNERABILITY))
                .orElse(null);
    }

    /**
     * The player this Architect is assessing, or null when nobody qualifies.
     *
     * <p>Creative players are excluded for the same reason {@link #findHostileTarget}
     * excludes them: they are not participants. Creative also means no armor, which
     * wins {@link HearthTargetPolicy#BY_VULNERABILITY} outright, so an admin or builder
     * standing near a Hearth would take the commitment and hold it while every survival
     * player in range went unassessed.
     */
    @Nullable
    private ServerPlayer mostVulnerablePlayer(ServerLevel level) {
        List<ServerPlayer> inRange = level.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator())
                .filter(player -> architect.distanceToSqr(player)
                        <= (double) HearthPopulationPolicy.WATCH_DISTANCE
                        * HearthPopulationPolicy.WATCH_DISTANCE)
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
     * missing from this set has died, logged out or switched to creative and is
     * forgotten; one that is present but out of range is only suspended.
     *
     * <p>Creative is a forget rather than a suspend on purpose. A bookmark lets its
     * owner reclaim the commitment the instant it returns, without being re-scored
     * against whoever the Architect picked up meanwhile. Someone who toggled into
     * creative and back should not jump that queue.
     */
    private static Set<UUID> assessablePlayerIds(ServerLevel level) {
        Set<UUID> ids = new HashSet<>();
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            for (ServerPlayer player : dimension.players()) {
                if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
                    ids.add(player.getUUID());
                }
            }
        }
        return ids;
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
        double radius = 3.0D + architect.nextRandomFloat() * 5.0D;
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
        double maxRadius = HearthPopulationPolicy.ARCHITECT_HOME_RADIUS - 1.0D;
        if (offset.horizontalDistanceSqr() > maxRadius * maxRadius) {
            offset = offset.normalize().scale(maxRadius);
        }
        architect.getNavigation().moveTo(
                center.x + offset.x, home.getY(), center.z + offset.z, speed);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
