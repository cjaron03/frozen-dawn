package com.frozendawn.bloom;

import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic signature geometry for the Major Hearth's Bloom origin. */
public final class BloomOriginRootPolicy {
    public static final int MAX_RADIUS = 12;
    public static final int MAX_HEIGHT = 24;
    public static final long FORMATION_DELAY_TICKS = 200L;
    public static final int MAX_PLACEMENTS_PER_TICK = 2;

    private BloomOriginRootPolicy() {
    }

    public static List<Placement> layout(long seed) {
        Map<BlockPos, Material> placements = new LinkedHashMap<>();
        long mixed = BloomGrowthPolicy.mix(seed ^ 0x4F524947494E524FL);
        double rotation = unit(mixed) * Math.PI * 2.0D;

        addButtressRoots(placements, mixed, rotation);
        addRootBulb(placements, mixed);
        addSplitTrunk(placements, mixed, rotation);
        addCrown(placements, mixed, rotation);

        // The exposed inner spine makes the landmark readable through dense growth.
        put(placements, new BlockPos(0, 5, 0), Material.CORE);
        put(placements, new BlockPos(0, 7, 0), Material.CORE);
        put(placements, new BlockPos(0, 9, 0), Material.CORE);
        return List.copyOf(placements.entrySet().stream()
                .map(entry -> new Placement(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingInt((Placement placement) -> placement.offset().getY())
                        .thenComparingInt(placement -> horizontalDistanceSq(
                                placement.offset()))
                        .thenComparingInt(placement -> placement.offset().getX())
                        .thenComparingInt(placement -> placement.offset().getZ()))
                .toList());
    }

    public static boolean canForm(long activeTicks, boolean seeded) {
        return seeded && activeTicks >= FORMATION_DELAY_TICKS;
    }

    /** Irregular upper-canopy clearance; lower growth remains free to merge with the root. */
    public static boolean reservesCrownClearance(long seed, BlockPos relative) {
        if (relative.getY() < 11 || relative.getY() > MAX_HEIGHT + 2) {
            return false;
        }
        long edge = BloomGrowthPolicy.mix(seed
                ^ new BlockPos(relative.getX(), 0, relative.getZ()).asLong()
                ^ 0x434C454152414E43L);
        int radius = 8 + (int) Math.floorMod(edge, 3L);
        return horizontalDistanceSq(relative) <= radius * radius;
    }

    private static int horizontalDistanceSq(BlockPos pos) {
        return pos.getX() * pos.getX() + pos.getZ() * pos.getZ();
    }

    private static void addButtressRoots(Map<BlockPos, Material> placements,
                                         long seed, double rotation) {
        for (int arm = 0; arm < 8; arm++) {
            long armSeed = BloomGrowthPolicy.mix(seed + arm * 0x9E3779B97F4A7C15L);
            double jitter = (unit(armSeed) - 0.5D) * 0.20D;
            double angle = rotation + arm * Math.PI / 4.0D + jitter;
            double sideX = -Math.sin(angle);
            double sideZ = Math.cos(angle);
            int length = 8 + (int) Math.floorMod(armSeed >>> 11, 5L);
            for (int step = 0; step <= length; step++) {
                double bend = Math.sin(step * 0.65D + arm) * 0.22D;
                int x = (int) Math.round(Math.cos(angle) * step + sideX * bend);
                int z = (int) Math.round(Math.sin(angle) * step + sideZ * bend);
                int y = step < 3 ? 2 - step / 2 : step < 6 ? 1 : 0;
                put(placements, new BlockPos(x, y, z), Material.MASS);
                if (step <= 5) {
                    int width = step <= 2 ? 2 : 1;
                    put(placements, new BlockPos(
                            x + (int) Math.round(sideX * width), y, z
                            + (int) Math.round(sideZ * width)), Material.MASS);
                    put(placements, new BlockPos(
                            x - (int) Math.round(sideX * width), y, z
                            - (int) Math.round(sideZ * width)), Material.MASS);
                }
                if (step == length) {
                    put(placements, new BlockPos(x, y + 1, z), Material.TIP);
                }
            }
        }
    }

    private static void addRootBulb(Map<BlockPos, Material> placements, long seed) {
        for (int y = 0; y <= 4; y++) {
            int radius = y <= 1 ? 3 : 2;
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    int distance = x * x + z * z;
                    if (distance > radius * radius + 1) {
                        continue;
                    }
                    long cell = BloomGrowthPolicy.mix(seed ^ new BlockPos(x, y, z).asLong());
                    if (y >= 2 && distance > 1 && Math.floorMod(cell, 7L) == 0L) {
                        continue;
                    }
                    put(placements, new BlockPos(x, y, z), Material.MASS);
                }
            }
        }
    }

    private static void addSplitTrunk(Map<BlockPos, Material> placements,
                                      long seed, double rotation) {
        for (int rib = 0; rib < 5; rib++) {
            long ribSeed = BloomGrowthPolicy.mix(seed + rib * 0x632BE59BD9B4E019L);
            double angle = rotation + rib * Math.PI * 2.0D / 5.0D
                    + (unit(ribSeed) - 0.5D) * 0.24D;
            for (int y = 3; y <= 16; y++) {
                double radius = y < 7 ? 2.4D : y < 12 ? 1.8D : 2.2D + (y - 12) * 0.22D;
                int x = (int) Math.round(Math.cos(angle) * radius);
                int z = (int) Math.round(Math.sin(angle) * radius);
                put(placements, new BlockPos(x, y, z), Material.MASS);
                if (y <= 7 || (y + rib) % 3 == 0) {
                    int innerX = (int) Math.round(Math.cos(angle) * Math.max(1.0D, radius - 1.0D));
                    int innerZ = (int) Math.round(Math.sin(angle) * Math.max(1.0D, radius - 1.0D));
                    put(placements, new BlockPos(innerX, y, innerZ), Material.MASS);
                }
            }
        }
    }

    private static void addCrown(Map<BlockPos, Material> placements,
                                 long seed, double rotation) {
        for (int branch = 0; branch < 6; branch++) {
            long branchSeed = BloomGrowthPolicy.mix(seed - branch * 0x3C79AC492BA7B653L);
            double angle = rotation + branch * Math.PI / 3.0D
                    + (unit(branchSeed) - 0.5D) * 0.30D;
            int length = 5 + (int) Math.floorMod(branchSeed >>> 9, 4L);
            for (int step = 0; step <= length; step++) {
                int x = (int) Math.round(Math.cos(angle) * step);
                int z = (int) Math.round(Math.sin(angle) * step);
                int y = 15 + step / 2 + (step > 4 ? 1 : 0);
                put(placements, new BlockPos(x, y, z), Material.MASS);
                if (step == length) {
                    put(placements, new BlockPos(x, Math.min(MAX_HEIGHT, y + 1), z),
                            Material.TIP);
                }
            }
        }
    }

    private static double unit(long value) {
        return ((value >>> 11) & 0xFFFFL) / 65535.0D;
    }

    private static void put(Map<BlockPos, Material> placements, BlockPos pos,
                            Material material) {
        if (Math.abs(pos.getX()) > MAX_RADIUS || Math.abs(pos.getZ()) > MAX_RADIUS
                || pos.getY() < 0 || pos.getY() > MAX_HEIGHT) {
            return;
        }
        placements.merge(pos, material,
                (existing, replacement) -> replacement.priority > existing.priority
                        ? replacement : existing);
    }

    public enum Material {
        MASS(0),
        TIP(1),
        CORE(2);

        private final int priority;

        Material(int priority) {
            this.priority = priority;
        }
    }

    public record Placement(BlockPos offset, Material material) {
    }
}
