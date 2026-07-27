package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.mixin.BlockDisplayAccessor;
import com.frozendawn.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Bounded, non-damaging debris and temporary impact scars for the storm collapse. */
final class MasterArchitectStormDebrisManager {
    private static final String ENTITY_TAG = "frozendawn_master_storm_debris";
    private static final int MAX_DEBRIS = 18;
    private static final int TEMPORARY_ICE_TICKS = 30 * 20;
    private static final List<DebrisPiece> DEBRIS = new ArrayList<>();
    private static final Map<BlockPos, Long> TEMPORARY_ICE = new HashMap<>();

    private MasterArchitectStormDebrisManager() {
    }

    static void tick(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord hearth,
            int elapsedTicks) {
        expireTemporaryIce(level);
        tickDebris(level);
        if (hearth == null || !hearth.masterStormAftermathActive()) {
            return;
        }

        float strength = hearth.masterStormAftermathStrength();
        MasterArchitectStormAftermathPolicy.Stage stage =
                MasterArchitectStormAftermathPolicy.stage(elapsedTicks, strength);
        if (stage == MasterArchitectStormAftermathPolicy.Stage.STILLNESS
                || stage == MasterArchitectStormAftermathPolicy.Stage.COMPLETE) {
            clearDebris();
            return;
        }
        if (strength <= 0.001F
                || (stage != MasterArchitectStormAftermathPolicy.Stage.RUPTURE
                && stage != MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE)) {
            return;
        }

        int cap = Mth.clamp(Math.round(5.0F + strength * 13.0F), 5, MAX_DEBRIS);
        int cadence = stage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE
                ? Math.max(1, Math.round(4.0F - strength * 3.0F))
                : Math.max(2, Math.round(8.0F - strength * 5.0F));
        if (elapsedTicks % cadence == 0 && DEBRIS.size() < cap) {
            spawnDebris(level, hearth.center(), strength);
        }
    }

    static void reset() {
        DEBRIS.clear();
        TEMPORARY_ICE.clear();
    }

    private static void spawnDebris(ServerLevel level, BlockPos center, float strength) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double radius = 7.0D + level.random.nextDouble() * 34.0D;
        double x = center.getX() + 0.5D + Math.cos(angle) * radius;
        double z = center.getZ() + 0.5D + Math.sin(angle) * radius;
        BlockPos targetColumn = BlockPos.containing(x, center.getY(), z);
        if (!level.hasChunkAt(targetColumn)) {
            return;
        }
        double y = center.getY() + 27.0D + level.random.nextDouble() * 34.0D;
        BlockState state = level.random.nextInt(5) == 0
                ? Blocks.ICE.defaultBlockState()
                : Blocks.PACKED_ICE.defaultBlockState();
        float scale = 0.42F + level.random.nextFloat() * (0.38F + strength * 0.28F);
        Display.BlockDisplay display = new Display.BlockDisplay(
                EntityType.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) (Object) display).frozendawn$setBlockState(state);
        ((DisplayAccessor) (Object) display).frozendawn$setPosRotInterpolationDuration(2);
        float half = scale * 0.5F;
        ((DisplayAccessor) (Object) display).frozendawn$setTransformation(
                new Transformation(
                        new Vector3f(-half, -half, -half),
                        new Quaternionf(),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()));
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.addTag(ENTITY_TAG);
        display.setPos(x, y, z);
        if (!level.addFreshEntity(display)) {
            display.discard();
            return;
        }

        Vec3 velocity = new Vec3(
                -Math.sin(angle) * (0.10D + level.random.nextDouble() * 0.24D),
                -0.42D - level.random.nextDouble() * 0.34D,
                Math.cos(angle) * (0.10D + level.random.nextDouble() * 0.24D));
        DEBRIS.add(new DebrisPiece(display, state, velocity,
                level.random.nextFloat() * 24.0F - 12.0F));
    }

    private static void tickDebris(ServerLevel level) {
        Iterator<DebrisPiece> iterator = DEBRIS.iterator();
        while (iterator.hasNext()) {
            DebrisPiece piece = iterator.next();
            if (piece.display.isRemoved() || piece.display.level() != level) {
                iterator.remove();
                continue;
            }
            Vec3 next = piece.display.position().add(piece.velocity);
            BlockPos nextPos = BlockPos.containing(next);
            if (!level.hasChunkAt(nextPos)) {
                piece.display.discard();
                iterator.remove();
                continue;
            }
            BlockPos ground = findGround(level, nextPos);
            if (ground != null && next.y <= ground.getY() + 1.15D) {
                impact(level, piece, ground.above());
                iterator.remove();
                continue;
            }
            piece.display.setPos(next);
            piece.display.setYRot(piece.display.getYRot() + piece.spin);
            piece.velocity = piece.velocity.add(0.0D, -0.025D, 0.0D);
        }
    }

    private static BlockPos findGround(ServerLevel level, BlockPos from) {
        for (int depth = 0; depth < 5; depth++) {
            BlockPos candidate = from.below(depth);
            if (level.getBlockState(candidate).isFaceSturdy(
                    level, candidate, Direction.UP)) {
                return candidate;
            }
        }
        return null;
    }

    private static void impact(ServerLevel level, DebrisPiece piece, BlockPos impactPos) {
        piece.display.discard();
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, piece.state),
                impactPos.getX() + 0.5D,
                impactPos.getY() + 0.2D,
                impactPos.getZ() + 0.5D,
                18, 0.55D, 0.20D, 0.55D, 0.18D);
        level.sendParticles(
                ParticleTypes.SNOWFLAKE,
                impactPos.getX() + 0.5D,
                impactPos.getY() + 0.3D,
                impactPos.getZ() + 0.5D,
                12, 0.45D, 0.22D, 0.45D, 0.12D);

        if (TEMPORARY_ICE.size() >= 12 || level.random.nextInt(3) != 0
                || !safeTemporaryIcePosition(level, impactPos)) {
            return;
        }
        level.setBlock(impactPos, Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
        TEMPORARY_ICE.put(impactPos.immutable(),
                level.getGameTime() + TEMPORARY_ICE_TICKS);
    }

    private static boolean safeTemporaryIcePosition(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.below()).isFaceSturdy(
                level, pos.below(), Direction.UP)) {
            return false;
        }
        AABB block = new AABB(pos);
        return level.getEntities((Entity) null, block.inflate(0.35D),
                entity -> entity.isAlive()).isEmpty();
    }

    private static void expireTemporaryIce(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Long>> iterator = TEMPORARY_ICE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (now < entry.getValue()) {
                continue;
            }
            BlockPos pos = entry.getKey();
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.ICE)) {
                level.removeBlock(pos, false);
                level.sendParticles(
                        ParticleTypes.CLOUD,
                        pos.getX() + 0.5D,
                        pos.getY() + 0.35D,
                        pos.getZ() + 0.5D,
                        5, 0.2D, 0.14D, 0.2D, 0.02D);
            }
            iterator.remove();
        }
    }

    private static void clearDebris() {
        DEBRIS.forEach(piece -> piece.display.discard());
        DEBRIS.clear();
    }

    private static final class DebrisPiece {
        private final Display.BlockDisplay display;
        private final BlockState state;
        private Vec3 velocity;
        private final float spin;

        private DebrisPiece(
                Display.BlockDisplay display,
                BlockState state,
                Vec3 velocity,
                float spin) {
            this.display = display;
            this.state = state;
            this.velocity = velocity;
            this.spin = spin;
        }
    }
}
