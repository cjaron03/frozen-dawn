package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.CombustionAtmosphere;
import com.frozendawn.world.RoomThermalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Molten core after a CFU-7 meltdown. The head sinks one meltable block per step (casing, floor, stone; never
 * obsidian or bedrock) up to MAX_SINK, falls freely through air, then spreads into a small puddle and cools into
 * solid Corium. Water, ice and snow it touches flash to steam; enough water nearby causes a block-breaking steam
 * explosion. With oxygen present it ignites flammable surroundings; in vacuum it only glows and heats. */
public final class MoltenCoriumBlock extends Block {
    public static final int MAX_SINK = 8, MAX_AGE = 30, STEP_TICKS = 60, STEAM_RADIUS = 2;
    public static final double STEAM_EXPLOSION_MASS = 4, HEAT_WATTS = 10_000;
    public static final IntegerProperty SUNK = IntegerProperty.create("sunk", 0, MAX_SINK);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, MAX_AGE);
    public static final BooleanProperty HEAD = BooleanProperty.create("head");

    public MoltenCoriumBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(SUNK, 0).setValue(AGE, 0).setValue(HEAD, true)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(SUNK, AGE, HEAD); }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide() && !old.is(this)) level.scheduleTick(pos, this, STEP_TICKS);
        if (level instanceof ServerLevel l && !old.is(this))
            com.frozendawn.radiation.RadiationSourceData.get(l).put(pos, com.frozendawn.radiation.RadiationSourceData.Kind.MOLTEN_CORIUM);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (level instanceof ServerLevel l && !next.is(this)) com.frozendawn.radiation.RadiationSourceData.get(l).remove(pos);
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (step(level, pos)) level.scheduleTick(pos, this, STEP_TICKS);
    }

    /** One flow step. Returns true while this cell is still molten and needs another step. Public for GameTests. */
    public static boolean step(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MoltenCoriumBlock)) return false;
        react(level, pos);
        state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MoltenCoriumBlock)) return false;
        if (state.getValue(HEAD) && sink(level, pos, state)) return false;
        if (state.getValue(HEAD)) { spread(level, pos, state); state = state.setValue(HEAD, false); }
        int age = state.getValue(AGE) + 1;
        if (age >= MAX_AGE) { level.setBlock(pos, ModBlocks.CORIUM.get().defaultBlockState(), 3); level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, .6f, .6f); return false; }
        level.setBlock(pos, state.setValue(AGE, age), 2);
        return true;
    }

    public static boolean meltable(Level level, BlockPos pos, BlockState s) {
        if (s.isAir() || s.getBlock() instanceof MoltenCoriumBlock || s.getBlock() instanceof CoriumBlock) return false;
        float hardness = s.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness < 50; // obsidian (50) and bedrock (-1) hold it
    }
    private static boolean open(BlockState s) { return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty(); }

    private static boolean sink(ServerLevel level, BlockPos pos, BlockState state) {
        var below = pos.below();
        if (!level.isLoaded(below) || below.getY() <= level.getMinBuildHeight()) return false;
        var target = level.getBlockState(below);
        int sunk = state.getValue(SUNK);
        if (open(target)) { move(level, pos, below, state); return true; }
        if (sunk >= MAX_SINK || !meltable(level, below, target)) return false;
        level.levelEvent(2001, below, Block.getId(target)); // break particles, no drops
        move(level, pos, below, state.setValue(SUNK, sunk + 1));
        level.playSound(null, below, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.2f, .7f);
        return true;
    }
    private static void move(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        level.setBlock(to, state, 3);
        level.setBlock(from, Blocks.AIR.defaultBlockState(), 3);
    }
    /** Settling head spreads once into open floor cells beside it. */
    private static void spread(ServerLevel level, BlockPos pos, BlockState state) {
        for (var d : Direction.Plane.HORIZONTAL) {
            var side = pos.relative(d);
            if (!level.isLoaded(side) || !open(level.getBlockState(side)) || open(level.getBlockState(side.below()))) continue;
            level.setBlock(side, state.setValue(HEAD, false).setValue(SUNK, MAX_SINK), 3);
        }
    }

    private static double steamMass(BlockState s) {
        if (s.getFluidState().is(FluidTags.WATER)) return s.getFluidState().isSource() ? 1 : .5;
        if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) return 1;
        return s.getBlock() instanceof SnowLayerBlock ? .25 : 0;
    }
    /** Steam, steam explosion, fire and room heat for one step. */
    private static void react(ServerLevel level, BlockPos pos) {
        double mass = 0;
        for (var p : BlockPos.betweenClosed(pos.offset(-STEAM_RADIUS, -STEAM_RADIUS, -STEAM_RADIUS), pos.offset(STEAM_RADIUS, STEAM_RADIUS, STEAM_RADIUS)))
            if (level.isLoaded(p)) mass += steamMass(level.getBlockState(p));
        if (mass >= STEAM_EXPLOSION_MASS) {
            for (var p : BlockPos.betweenClosed(pos.offset(-STEAM_RADIUS, -STEAM_RADIUS, -STEAM_RADIUS), pos.offset(STEAM_RADIUS, STEAM_RADIUS, STEAM_RADIUS)))
                if (level.isLoaded(p) && steamMass(level.getBlockState(p)) > 0) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            var c = pos.getCenter();
            level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1, c.z, 60, 1.5, 1, 1.5, .08);
            // Detonate in the flashed steam just above the melt; rays starting inside the blast-proof melt would be absorbed.
            level.explode(null, c.x, c.y + 1, c.z, (float) Math.min(6, 2 + mass / 3), false, Level.ExplosionInteraction.BLOCK);
            // The blast throws contamination out, through any walls it broke.
            com.frozendawn.radiation.Contamination.spread(level, java.util.List.of(pos.above()), pos, com.frozendawn.radiation.Contamination.BLAST_RADIUS, true);
            return;
        }
        boolean hissed = false;
        for (var d : Direction.values()) {
            var p = pos.relative(d);
            if (!level.isLoaded(p)) continue;
            var s = level.getBlockState(p);
            if (steamMass(s) > 0 || s.getBlock() instanceof FrozenAtmosphereBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                if (steamMass(s) > 0) { hissed = true; var c = p.getCenter(); level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 12, .3, .3, .3, .03); }
            }
        }
        if (hissed) level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.5f, .8f);
        if (CombustionAtmosphere.canBurnAt(level, pos)) ignite(level, pos);
        var state = level.getBlockState(pos);
        double cooling = state.getBlock() instanceof MoltenCoriumBlock ? 1 - state.getValue(AGE) / (double) MAX_AGE : 0;
        RoomThermalManager.addMachineHeat(level, pos, HEAT_WATTS * cooling * STEP_TICKS / 20.0 * ResistiveHeaterBlockEntity.HEAT_PER_JOULE);
    }
    private static void ignite(ServerLevel level, BlockPos pos) {
        var c = pos.getCenter();
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + .8, c.z, 6, .3, .2, .3, .02);
        int lit = 0;
        for (var p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
            if (lit >= 3 || !level.isLoaded(p) || !level.getBlockState(p).isAir()) continue;
            boolean fuel = false;
            for (var d : Direction.values()) {
                var n = p.relative(d);
                if (level.isLoaded(n) && level.getBlockState(n).isFlammable(level, n, d.getOpposite())) { fuel = true; break; }
            }
            var fire = BaseFireBlock.getState(level, p);
            if (fuel && fire.canSurvive(level, p)) { level.setBlock(p.immutable(), fire, 3); lit++; }
        }
    }

    @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof LivingEntity living && !living.fireImmune()) { entity.hurt(level.damageSources().hotFloor(), 6f); entity.igniteForSeconds(5); }
        super.stepOn(level, pos, state, entity);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.LAVA, pos.getX() + random.nextDouble(), pos.getY() + 1, pos.getZ() + random.nextDouble(), 0, 0, 0);
        if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, .04, 0);
    }
}
