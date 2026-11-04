package com.frozendawn.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A plate of the Morgue's cold vent. It only breathes; {@link VaultVitalsBlockEntity} does the harm. */
public final class VaultVentBlock extends Block {
    public VaultVentBlock(Properties properties) { super(properties); }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) level.addParticle(random.nextBoolean() ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD,
                pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, .04 + random.nextDouble() * .06, 0);
    }
}
