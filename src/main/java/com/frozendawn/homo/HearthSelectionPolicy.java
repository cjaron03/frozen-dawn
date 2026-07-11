package com.frozendawn.homo;

import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.Optional;
import java.util.UUID;

/**
 * Pure, deterministic policy for selecting the world's rare Returned Hearth sites.
 */
public final class HearthSelectionPolicy {
    public static final long LATE_PHASE_DELAY_TICKS = 15L * 60L * 20L;
    public static final int MAJOR_MIN_RADIUS = 512;
    public static final int MAJOR_MAX_RADIUS = 1024;
    public static final int MINOR_MIN_RADIUS = 1024;
    public static final int MINOR_MAX_RADIUS = 2048;
    public static final int MINIMUM_SITE_SEPARATION = 640;
    public static final float MINOR_HEARTH_CHANCE = 0.50F;

    private static final long SELECTION_SALT = 0x4852454C49515553L;
    private static final long MAJOR_SALT = 0x4D414A4F525F3031L;
    private static final long MINOR_SALT = 0x4D494E4F525F3031L;
    private static final int MINOR_POSITION_ATTEMPTS = 16;

    private HearthSelectionPolicy() {
    }

    public static long selectionEligibilityTick(int totalDays) {
        if (totalDays <= 0) {
            return Long.MAX_VALUE;
        }
        long latePhaseStart = Math.round(totalDays * 24000.0D * PhaseManager.PHASE6_VACUUM_START);
        return latePhaseStart + LATE_PHASE_DELAY_TICKS;
    }

    public static boolean isSelectionEligible(long apocalypseTicks, int totalDays) {
        return apocalypseTicks >= selectionEligibilityTick(totalDays);
    }

    public static SelectionPlan createPlan(long worldSeed, BlockPos transponderAnchor) {
        long rootSeed = mix(worldSeed ^ transponderAnchor.asLong() ^ SELECTION_SALT);
        SiteCandidate major = createCandidate(rootSeed ^ MAJOR_SALT, HearthType.MAJOR,
                transponderAnchor, MAJOR_MIN_RADIUS, MAJOR_MAX_RADIUS);

        RandomSource minorRoll = RandomSource.create(mix(rootSeed ^ MINOR_SALT));
        if (minorRoll.nextFloat() >= MINOR_HEARTH_CHANCE) {
            return new SelectionPlan(major, Optional.empty());
        }

        SiteCandidate minor = null;
        for (int attempt = 0; attempt < MINOR_POSITION_ATTEMPTS; attempt++) {
            long attemptSeed = mix(rootSeed ^ MINOR_SALT ^ ((long) attempt * 0x9E3779B97F4A7C15L));
            SiteCandidate candidate = createCandidate(attemptSeed, HearthType.MINOR,
                    transponderAnchor, MINOR_MIN_RADIUS, MINOR_MAX_RADIUS);
            if (horizontalDistanceSquared(candidate.center(), major.center())
                    >= (long) MINIMUM_SITE_SEPARATION * MINIMUM_SITE_SEPARATION) {
                minor = candidate;
                break;
            }
        }

        return new SelectionPlan(major, Optional.ofNullable(minor));
    }

    private static SiteCandidate createCandidate(long seed, HearthType type, BlockPos anchor,
                                                 int minRadius, int maxRadius) {
        RandomSource random = RandomSource.create(seed);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int radius = minRadius + random.nextInt(maxRadius - minRadius + 1);
        int x = anchor.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * radius);
        BlockPos unresolvedCenter = new BlockPos(x, 0, z);
        long layoutSeed = mix(seed ^ unresolvedCenter.asLong());
        UUID id = new UUID(mix(layoutSeed ^ 0x6D73625F68656172L),
                mix(layoutSeed ^ 0x6C73625F74686561L));
        return new SiteCandidate(id, type, unresolvedCenter, layoutSeed);
    }

    private static long horizontalDistanceSquared(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public enum HearthType {
        MAJOR,
        MINOR
    }

    public record SiteCandidate(UUID id, HearthType type, BlockPos center, long layoutSeed) {
    }

    public record SelectionPlan(SiteCandidate major, Optional<SiteCandidate> minor) {
    }
}
