package com.frozendawn.world;

import com.frozendawn.init.ModBlocks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;

/** Bounded directional block light: one forward ray without a head-centered glow. */
public final class HeadlampBeam {
    public static final int RANGE = 16, MAX_BRIGHTNESS = 15, FIRST_DISTANCE = 4;
    public static final int MAX_SOURCES = (RANGE - FIRST_DISTANCE) / 2 + 1;
    private HeadlampBeam() {}

    public static boolean replaceable(ServerLevel level, BlockPos pos) {
        if (!ReadyChunks.ready(level,pos)) return false;
        var state=level.getBlockState(pos);
        return state.isAir() || state.is(ModBlocks.HELD_LANTERN_LIGHT)
                || state.is(Blocks.WATER)&&state.getFluidState().isSource();
    }

    public static Map<BlockPos,Integer> project(ServerLevel level,Vec3 eye,Vec3 direction) {
        if (direction.lengthSqr()<1e-8) return Map.of();
        // The clipper sees an unloaded chunk as a wall, never a request to load it.
        BlockGetter geometry=new BlockGetter() {
            public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            public BlockState getBlockState(BlockPos pos) {
                if (!ReadyChunks.ready(level,pos)) return Blocks.BARRIER.defaultBlockState();
                var state=level.getBlockState(pos);
                return state.canOcclude()?state:Blocks.AIR.defaultBlockState();
            }
            public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
            public int getHeight() { return level.getHeight(); }
            public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        };
        var forward=direction.normalize();
        var result=new HashMap<BlockPos,Integer>();
        var end=eye.add(forward.scale(RANGE));
        var hit=geometry.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
        double limit=hit.getType()==HitResult.Type.MISS?RANGE:eye.distanceTo(hit.getLocation())-.02;
        for (int distance=FIRST_DISTANCE;distance<=RANGE&&distance<=limit;distance+=2) {
            var pos=BlockPos.containing(eye.add(forward.scale(distance)));
            int brightness=MAX_BRIGHTNESS-(distance-FIRST_DISTANCE)/2;
            if (result.getOrDefault(pos,0)>=brightness || !replaceable(level,pos)) continue;
            // A voxel center must also be visible, preventing edge sampling from placing light around a wall.
            var visible=geometry.clip(new ClipContext(eye,pos.getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (visible.getType()==HitResult.Type.MISS) result.merge(pos.immutable(),brightness,Math::max);
        }
        // A short, obstructed beam needs two sources even before the second regular sample at six blocks.
        if (limit>0 && limit<FIRST_DISTANCE+2) {
            double frontDistance=Math.min(limit,FIRST_DISTANCE);
            // Placement against the surface can overwrite its light before the next server tick.
            // Keep a second, visible voxel on the same ray so client prediction cannot erase the only source.
            // More than a voxel diagonal guarantees a distinct cell unless the whole ray is in the eye cell.
            for(double distance:new double[]{frontDistance,Math.max(0,frontDistance-1.75)}) {
                var pos=BlockPos.containing(eye.add(forward.scale(distance)));
                var visible=geometry.clip(new ClipContext(eye,pos.getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
                if (replaceable(level,pos)&&visible.getType()==HitResult.Type.MISS)
                    result.put(pos.immutable(),MAX_BRIGHTNESS);
            }
        }
        return Map.copyOf(result);
    }
}
