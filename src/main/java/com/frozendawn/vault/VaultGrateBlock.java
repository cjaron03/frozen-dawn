package com.frozendawn.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** The drain under the Confinement Core. It lifts by hand, and with the Core out a little cold air comes up through it. */
public final class VaultGrateBlock extends TrapDoorBlock {
    public VaultGrateBlock(Properties properties) { super(BlockSetType.COPPER, properties); }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0 && level.getBlockState(pos.above()).isAir())
            level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + random.nextDouble(), pos.getY() + 1, pos.getZ() + random.nextDouble(), 0, .03, 0);
    }
}
