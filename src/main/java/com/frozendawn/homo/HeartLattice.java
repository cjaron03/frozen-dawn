package com.frozendawn.homo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared deterministic geometry and interaction policy for the Thae Iven Heart. */
public final class HeartLattice {
    public static final int NODE_COUNT = 5;
    public static final int HITS_PER_NODE = 3;
    public static final double MAX_STRIKE_DISTANCE = 40.0D;
    public static final double NODE_HIT_RADIUS = 3.0D;
    private static final double MAX_AIM_ASSIST_RADIUS = 4.5D;
    private static final double AIM_ASSIST_SLOPE = 0.10D;

    private static final float[] NODE_LOAD_THRESHOLDS = {
            25.0F, 42.0F, 58.0F, 75.0F, 90.0F
    };

    private HeartLattice() {
    }

    public static Lattice create(long seed) {
        RandomSource random = RandomSource.create(seed);
        List<Segment> segments = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        for (int trunk = 0; trunk < 12; trunk++) {
            float angle = (float) (trunk * Math.PI * 2.0D / 12.0D
                    + (random.nextFloat() - 0.5F) * 0.42F);
            float startRadius = 2.0F + random.nextFloat() * 3.0F;
            float x = Mth.cos(angle) * startRadius;
            float y = -13.0F + random.nextFloat() * 8.0F;
            float z = Mth.sin(angle) * startRadius * 0.78F;
            for (int step = 0; step < 5; step++) {
                float nextAngle = angle + (random.nextFloat() - 0.5F) * 0.7F;
                float radial = 4.0F + step * (2.8F + random.nextFloat() * 0.65F);
                float nx = Mth.cos(nextAngle) * radial
                        + (random.nextFloat() - 0.5F) * 3.0F;
                float ny = y + 3.0F + random.nextFloat();
                float nz = Mth.sin(nextAngle) * radial * 0.78F
                        + (random.nextFloat() - 0.5F) * 2.4F;
                float thickness = 1.4F - step * 0.14F
                        + random.nextFloat() * 0.45F;
                segments.add(new Segment(
                        x, y, z, nx, ny, nz, thickness, trunk));
                if (step >= 1 && random.nextFloat() < 0.78F) {
                    float bx = nx + (random.nextFloat() - 0.5F) * 8.0F;
                    float by = ny + 1.0F + random.nextFloat() * 5.0F;
                    float bz = nz + (random.nextFloat() - 0.5F) * 8.0F;
                    segments.add(new Segment(
                            nx, ny, nz, bx, by, bz,
                            thickness * 0.55F, trunk));
                }
                x = nx;
                y = ny;
                z = nz;
            }
        }
        for (int index = 0; index < NODE_COUNT; index++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float radius = 3.0F + random.nextFloat() * 9.0F;
            nodes.add(new Node(
                    index,
                    Mth.cos(angle) * radius,
                    -4.0F + random.nextFloat() * 18.0F,
                    Mth.sin(angle) * radius,
                    0.48F + index * 0.105F,
                    random.nextFloat() * Mth.TWO_PI));
        }
        return new Lattice(List.copyOf(segments), List.copyOf(nodes));
    }

    public static float requiredLoad(int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= NODE_COUNT) {
            return Float.POSITIVE_INFINITY;
        }
        return NODE_LOAD_THRESHOLDS[nodeIndex];
    }

    public static int nextNode(int destroyedMask) {
        for (int index = 0; index < NODE_COUNT; index++) {
            if (!isDestroyed(destroyedMask, index)) {
                return index;
            }
        }
        return -1;
    }

    public static boolean isDestroyed(int destroyedMask, int nodeIndex) {
        return nodeIndex >= 0 && nodeIndex < NODE_COUNT
                && (destroyedMask & (1 << nodeIndex)) != 0;
    }

    public static int destroyedCount(int destroyedMask) {
        return Integer.bitCount(destroyedMask & ((1 << NODE_COUNT) - 1));
    }

    public static Vec3 heartOrigin(BlockPos anchor, float load) {
        return Vec3.atCenterOf(anchor).add(
                0.0D,
                30.0D - CognitiveLoadPolicy.heartDescentBlocks(load),
                0.0D);
    }

    public static Vec3 nodePosition(
            BlockPos anchor, long seed, float load, int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= NODE_COUNT) {
            return heartOrigin(anchor, load);
        }
        Node node = create(seed).nodes().get(nodeIndex);
        return heartOrigin(anchor, load).add(node.x(), node.y(), node.z());
    }

    public static boolean raySelectsNode(
            Vec3 eye, Vec3 look, Vec3 nodePosition) {
        if (eye.distanceToSqr(nodePosition)
                <= NODE_HIT_RADIUS * NODE_HIT_RADIUS) {
            return true;
        }
        Vec3 direction = look.normalize();
        Vec3 toNode = nodePosition.subtract(eye);
        double alongRay = toNode.dot(direction);
        if (alongRay < 0.0D || alongRay > MAX_STRIKE_DISTANCE) {
            return false;
        }
        Vec3 nearest = eye.add(direction.scale(alongRay));
        double effectiveRadius = Math.min(
                MAX_AIM_ASSIST_RADIUS,
                Math.max(NODE_HIT_RADIUS, alongRay * AIM_ASSIST_SLOPE));
        return nearest.distanceToSqr(nodePosition)
                <= effectiveRadius * effectiveRadius;
    }

    public record Segment(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float thickness, int group) {
    }

    public record Node(
            int index, float x, float y, float z,
            float revealAt, float phase) {
    }

    public record Lattice(List<Segment> segments, List<Node> nodes) {
    }
}
