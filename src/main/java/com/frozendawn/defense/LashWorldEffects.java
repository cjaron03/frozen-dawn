package com.frozendawn.defense;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.CombustionAtmosphere;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * What the beam does to the blocks it lands on, server only. Snow and plain ice flash to steam at once, leaving no
 * meltwater to flood a room; packed and blue ice melt, and stone scorches, only under a held beam ({@link BlockHeat}).
 * Where there is oxygen it sets wood and other fuel alight; in vacuum nothing burns. The Master Architect's Last Wall
 * stands, and every change must pass the protection a player's block break does.
 */
public final class LashWorldEffects {
    public static final TagKey<Block> SCORCHES = BlockTags.create(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "sever_lash_scorches"));
    private static final int INSTANT = 0, UNAFFECTED = -1, MAX_TRACKED = 4096;
    private record Heat(int heat, long last) {}
    private static final Map<ServerLevel, Map<BlockPos, Heat>> HEAT = new WeakHashMap<>();
    private LashWorldEffects() {}

    /** {@code hits}: each block a ray landed on this tick, with the face it struck. */
    static void land(ServerLevel level, ServerPlayer player, Map<BlockPos, Direction> hits, long now) {
        if (hits.isEmpty()) return;
        var heat = HEAT.computeIfAbsent(level, k -> new HashMap<>());
        List<ArchitectEntity> masters = null;
        for (var hit : hits.entrySet()) {
            var pos = hit.getKey(); var face = hit.getValue(); var state = level.getBlockState(pos);
            int dwell = dwell(state);
            var flame = pos.relative(face);
            boolean fuel = dwell == UNAFFECTED && CombustionAtmosphere.canBurnAt(level, flame) && catches(level, pos, state, face, flame, player.getDirection());
            if (dwell == UNAFFECTED && !fuel) continue;
            if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)) {
                if (masters == null) masters = level.getEntitiesOfClass(ArchitectEntity.class, player.getBoundingBox().inflate(SeverLash.REACH + 32),
                        ArchitectEntity::isHearthMasterArchitect);
                if (masters.stream().anyMatch(m -> m.holdsLastWall(pos))) continue;
            }
            if (dwell > INSTANT) {
                var was = heat.get(pos);
                int held = BlockHeat.land(was == null ? 0 : was.heat(), was == null ? now : was.last(), now);
                if (!BlockHeat.gives(held, dwell)) { heat.put(pos.immutable(), new Heat(held, now)); continue; }
                heat.remove(pos);
            }
            if (!level.mayInteract(player, pos) || CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()) continue;
            boolean air = !fuel && CombustionAtmosphere.canBurnAt(level, flame);
            if (fuel) ignite(level, pos, state, flame);
            else if (state.is(SCORCHES)) {
                level.setBlock(pos, ModBlocks.SCORCHED_GROUND.get().defaultBlockState(), Block.UPDATE_ALL);
                if (air) puff(level, flame, ParticleTypes.SMOKE);
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (air) puff(level, pos, ParticleTypes.CLOUD);
            }
        }
        if (heat.size() > MAX_TRACKED) heat.values().removeIf(h -> BlockHeat.cold(h.heat(), h.last(), now));
    }

    /** Ticks of held beam before the block gives way, {@code INSTANT}, or {@code UNAFFECTED}. */
    private static int dwell(BlockState state) {
        if (state.is(BlockTags.SNOW) || state.is(Blocks.SNOW) || state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) return INSTANT;
        if (state.is(Blocks.PACKED_ICE)) return BlockHeat.PACKED_ICE_TICKS;
        if (state.is(Blocks.BLUE_ICE)) return BlockHeat.BLUE_ICE_TICKS;
        if (state.is(SCORCHES)) return BlockHeat.STONE_TICKS;
        return UNAFFECTED;
    }

    private static boolean catches(ServerLevel level, BlockPos pos, BlockState state, Direction face, BlockPos flame, Direction facing) {
        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) return true;
        return state.isFlammable(level, pos, face) && BaseFireBlock.canBePlacedAt(level, flame, facing);
    }

    private static void ignite(ServerLevel level, BlockPos pos, BlockState state, BlockPos flame) {
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT))
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
        else level.setBlock(flame, BaseFireBlock.getState(level, flame), Block.UPDATE_ALL_IMMEDIATE);
    }

    private static void puff(ServerLevel level, BlockPos at, net.minecraft.core.particles.SimpleParticleType type) {
        level.sendParticles(type, at.getX() + .5, at.getY() + .5, at.getZ() + .5, 2, .2, .2, .2, .01);
    }
}
