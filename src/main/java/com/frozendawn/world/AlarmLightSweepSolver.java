package com.frozendawn.world;

import com.frozendawn.block.AlarmBeaconBlockEntity;
import com.frozendawn.init.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AlarmLightSweepSolver {

    private static final float[] YAW_OFFSETS_DEG = {0.0f, -8.0f, 8.0f, -16.0f, 16.0f, -24.0f, 24.0f, -30.0f, 30.0f};
    private static final float[] YAW_WEIGHTS = {1.0f, 0.92f, 0.92f, 0.78f, 0.78f, 0.58f, 0.58f, 0.42f, 0.42f};
    private static final float[] PITCH_BIASES = {0.08f, 0.22f, 0.36f};
    private static final float[] PITCH_WEIGHTS = {1.0f, 0.80f, 0.60f};
    private static final float[] WALL_PITCH_BIASES = {0.18f, 0.34f, 0.50f, 0.66f};
    private static final float[] WALL_PITCH_WEIGHTS = {1.0f, 0.88f, 0.68f, 0.46f};
    private static final float MIN_INTENSITY = 0.06f;
    private static final double SAMPLE_STEP = 0.72;
    private static final int GROUND_SEARCH_DEPTH = 3;

    private AlarmLightSweepSolver() {
    }

    public static SweepResult solve(Level level, AlarmBeaconBlockEntity beacon, float partialTick) {
        Long2IntOpenHashMap worldLights = new Long2IntOpenHashMap();
        worldLights.defaultReturnValue(0);
        Long2IntOpenHashMap dynamicLights = new Long2IntOpenHashMap();
        dynamicLights.defaultReturnValue(0);
        Map<SurfaceKey, Float> paints = new HashMap<>();

        float beamIntensity = beacon.getBeamIntensity(partialTick);
        if (beamIntensity <= MIN_INTENSITY) {
            return new SweepResult(worldLights, dynamicLights, List.of());
        }

        float[] pitchBiases = beacon.isWallMounted() ? WALL_PITCH_BIASES : PITCH_BIASES;
        float[] pitchWeights = beacon.isWallMounted() ? WALL_PITCH_WEIGHTS : PITCH_WEIGHTS;
        Vec3 headPos = beacon.getHeadWorldPos();
        Vec3 forward = beacon.getBeamDirection(partialTick).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x).normalize();
        double range = Mth.lerp(beamIntensity, 4.8f, 7.2f);

        for (int yawIndex = 0; yawIndex < YAW_OFFSETS_DEG.length; yawIndex++) {
            float yawRadians = YAW_OFFSETS_DEG[yawIndex] * Mth.DEG_TO_RAD;
            Vec3 horizontalDir = forward.scale(Mth.cos(yawRadians)).add(right.scale(Mth.sin(yawRadians))).normalize();
            float yawWeight = YAW_WEIGHTS[yawIndex];

            for (int pitchIndex = 0; pitchIndex < pitchBiases.length; pitchIndex++) {
                float pitchBias = pitchBiases[pitchIndex];
                float pitchWeight = pitchWeights[pitchIndex];
                Vec3 rayDir = horizontalDir.scale(1.0 - pitchBias).add(0.0, -pitchBias, 0.0).normalize();
                Vec3 rayEnd = headPos.add(rayDir.scale(range));

                sampleGroundSweep(level, beacon.getBlockPos(), headPos, rayDir, range, beamIntensity, yawWeight,
                        pitchWeight, worldLights, dynamicLights, paints);

                BlockHitResult hit = clipIgnoringAlarmObjects(level, headPos, rayEnd);
                if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() == Direction.DOWN) {
                    continue;
                }

                double hitDistance = headPos.distanceTo(hit.getLocation());
                float distanceWeight = Mth.clamp((float) (1.0 - (hitDistance / range)), 0.15f, 1.0f);
                float contribution = beamIntensity * yawWeight * pitchWeight * distanceWeight;
                if (contribution <= 0.12f) {
                    continue;
                }

                BlockPos surfacePos = hit.getBlockPos();
                Direction hitFace = hit.getDirection();
                if (!isSweepSurface(level, surfacePos, hitFace)) {
                    continue;
                }
                BlockPos lightPos = surfacePos.relative(hitFace);
                if (!isValidLightCell(level, lightPos, beacon.getBlockPos())) {
                    continue;
                }

                int dynamicLevel = Mth.clamp(Math.round(Mth.lerp(contribution, 6.0f, 15.0f)), 0, 15);
                int worldLevel = Mth.clamp(dynamicLevel - 1, 0, 15);
                if (dynamicLevel <= 0 || worldLevel <= 0) {
                    continue;
                }

                mergeMax(worldLights, lightPos.asLong(), worldLevel);
                addDynamicContribution(dynamicLights, surfacePos, hitFace, dynamicLevel);
                mergePaint(paints, surfacePos, hitFace, contribution);
            }
        }

        List<SurfacePaint> surfacePaints = new ArrayList<>(paints.size());
        for (Map.Entry<SurfaceKey, Float> entry : paints.entrySet()) {
            surfacePaints.add(new SurfacePaint(entry.getKey().pos(), entry.getKey().face(), entry.getValue()));
        }

        return new SweepResult(worldLights, dynamicLights, surfacePaints);
    }

    private static void sampleGroundSweep(Level level, BlockPos beaconPos, Vec3 headPos, Vec3 rayDir, double range,
                                          float beamIntensity, float yawWeight, float pitchWeight,
                                          Long2IntOpenHashMap worldLights, Long2IntOpenHashMap dynamicLights,
                                          Map<SurfaceKey, Float> paints) {
        int steps = Math.max(1, Mth.ceil(range / SAMPLE_STEP));
        for (int step = 1; step <= steps; step++) {
            double distance = Math.min(range, step * SAMPLE_STEP);
            Vec3 samplePoint = headPos.add(rayDir.scale(distance));
            if (isObstructed(level, headPos, samplePoint, distance)) {
                break;
            }

            float distanceWeight = Mth.clamp((float) (1.0 - (distance / range) * 0.72f), 0.18f, 1.0f);
            float contribution = beamIntensity * yawWeight * pitchWeight * distanceWeight;
            if (contribution <= 0.10f) {
                continue;
            }

            addGroundContribution(level, beaconPos, samplePoint, contribution, worldLights, dynamicLights, paints);
        }
    }

    private static void addGroundContribution(Level level, BlockPos beaconPos, Vec3 samplePoint, float contribution,
                                              Long2IntOpenHashMap worldLights, Long2IntOpenHashMap dynamicLights,
                                              Map<SurfaceKey, Float> paints) {
        BlockPos samplePos = BlockPos.containing(samplePoint);
        for (int depth = 0; depth <= GROUND_SEARCH_DEPTH; depth++) {
            BlockPos surfacePos = samplePos.below(depth);
            if (!isSweepSurface(level, surfacePos, Direction.UP)) {
                continue;
            }

            BlockPos lightPos = surfacePos.above();
            if (!isValidLightCell(level, lightPos, beaconPos)) {
                continue;
            }

            int dynamicLevel = Mth.clamp(Math.round(Mth.lerp(contribution, 6.0f, 15.0f)), 0, 15);
            int worldLevel = Mth.clamp(dynamicLevel - 1, 0, 15);
            if (dynamicLevel <= 0 || worldLevel <= 0) {
                return;
            }

            mergeMax(worldLights, lightPos.asLong(), worldLevel);
            addDynamicContribution(dynamicLights, surfacePos, Direction.UP, dynamicLevel);
            mergePaint(paints, surfacePos, Direction.UP, contribution);
            return;
        }
    }

    private static boolean isValidLightCell(Level level, BlockPos pos, BlockPos beaconPos) {
        if (pos.equals(beaconPos)) {
            return false;
        }
        var state = level.getBlockState(pos);
        return (state.isAir() || state.is(Blocks.LIGHT)) && state.getFluidState().isEmpty();
    }

    private static boolean isSweepSurface(Level level, BlockPos pos, Direction face) {
        var state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || isIgnoredAlarmObject(state)) {
            return false;
        }
        if (face == Direction.UP) {
            VoxelShape shape = getSurfaceShape(level, pos);
            return !shape.isEmpty() && shape.max(Direction.Axis.Y) > 0.05;
        }
        return state.isFaceSturdy(level, pos, face);
    }

    private static VoxelShape getSurfaceShape(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (!shape.isEmpty()) {
            return shape;
        }
        return state.getCollisionShape(level, pos);
    }

    private static BlockHitResult clipIgnoringAlarmObjects(Level level, Vec3 start, Vec3 end) {
        Vec3 rayDir = end.subtract(start);
        if (rayDir.lengthSqr() < 1.0E-6) {
            return BlockHitResult.miss(end, Direction.NORTH, BlockPos.containing(end));
        }

        Vec3 normalizedDir = rayDir.normalize();
        Vec3 currentStart = start;
        for (int attempts = 0; attempts < 8; attempts++) {
            BlockHitResult hit = level.clip(new ClipContext(
                    currentStart,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            ));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return hit;
            }
            if (!isIgnoredAlarmObject(level.getBlockState(hit.getBlockPos()))) {
                return hit;
            }

            currentStart = hit.getLocation().add(normalizedDir.scale(0.08));
            if (currentStart.distanceToSqr(end) < 0.01) {
                break;
            }
        }

        Direction missDirection = Direction.getNearest(normalizedDir.x, normalizedDir.y, normalizedDir.z);
        return BlockHitResult.miss(end, missDirection, BlockPos.containing(end));
    }

    private static boolean isObstructed(Level level, Vec3 start, Vec3 end, double targetDistance) {
        BlockHitResult hit = clipIgnoringAlarmObjects(level, start, end);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        return start.distanceTo(hit.getLocation()) + 0.08 < targetDistance;
    }

    private static boolean isIgnoredAlarmObject(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(ModBlocks.ALARM_BEACON.get())
                || state.is(ModBlocks.WALL_ALARM_BEACON.get())
                || state.is(ModBlocks.ORSA_FLAG.get())
                || state.is(ModBlocks.EMERGENCY_LIGHT.get())
                || state.is(ModBlocks.WALL_EMERGENCY_LIGHT.get())
                || state.is(ModBlocks.STREET_LIGHT.get())
                || state.is(ModBlocks.TOWN_PA_SPEAKER.get())
                || state.is(BlockTags.ALL_SIGNS);
    }

    private static void addDynamicContribution(Long2IntOpenHashMap dynamicLights, BlockPos surfacePos, Direction face, int dynamicLevel) {
        mergeMax(dynamicLights, surfacePos.asLong(), dynamicLevel);

        int spill = Math.max(0, dynamicLevel - 2);
        if (spill <= 0) {
            return;
        }

        if (face == Direction.UP) {
            mergeMax(dynamicLights, surfacePos.north().asLong(), spill - 1);
            mergeMax(dynamicLights, surfacePos.south().asLong(), spill - 1);
            mergeMax(dynamicLights, surfacePos.east().asLong(), spill - 1);
            mergeMax(dynamicLights, surfacePos.west().asLong(), spill - 1);
        } else if (face.getAxis().isHorizontal()) {
            mergeMax(dynamicLights, surfacePos.above().asLong(), spill);
            mergeMax(dynamicLights, surfacePos.relative(face.getClockWise()).asLong(), spill - 1);
            mergeMax(dynamicLights, surfacePos.relative(face.getCounterClockWise()).asLong(), spill - 1);
        }
    }

    private static void mergeMax(Long2IntOpenHashMap map, long key, int value) {
        if (value <= 0) {
            return;
        }
        int existing = map.get(key);
        if (value > existing) {
            map.put(key, value);
        }
    }

    private static void mergePaint(Map<SurfaceKey, Float> paints, BlockPos surfacePos, Direction face, float contribution) {
        if (contribution <= 0.0f) {
            return;
        }
        SurfaceKey key = new SurfaceKey(surfacePos.immutable(), face);
        paints.merge(key, contribution, Math::max);
    }

    public record SweepResult(Long2IntOpenHashMap worldLights, Long2IntOpenHashMap dynamicLights,
                              List<SurfacePaint> surfacePaints) {
    }

    private record SurfaceKey(BlockPos pos, Direction face) {
    }

    public record SurfacePaint(BlockPos pos, Direction face, float strength) {
    }
}
