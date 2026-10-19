package com.frozendawn.flora;

import com.frozendawn.init.ModFeatures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Grows at -20C or colder with sky access; sunlight is not needed. Warmth pauses it without harming it, and bone meal
 * only works in the cold. Planted on dirt, dead grass or frozen dirt.
 */
public final class RimewoodSaplingBlock extends SaplingBlock {
    public static final TreeGrower GROWER = new TreeGrower("frozendawn_rimewood", Optional.empty(), Optional.of(ModFeatures.RIMEWOOD_TREE), Optional.empty());

    public RimewoodSaplingBlock(Properties properties) { super(GROWER, properties); }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return ColdFlora.rimewoodSoil(state) && !state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK);
    }

    /** Cold enough and open to the sky. */
    public static boolean canGrow(ServerLevel level, BlockPos pos) {
        return ColdFloraPolicy.saplingGrows(level.canSeeSky(pos), ColdFlora.temperature(level, pos));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(ColdFloraPolicy.SAPLING_ADVANCE_ONE_IN) == 0 && canGrow(level, pos)) advanceTree(level, pos, state, random);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return level instanceof ServerLevel server && canGrow(server, pos) && super.isBonemealSuccess(level, random, pos, state);
    }
}
