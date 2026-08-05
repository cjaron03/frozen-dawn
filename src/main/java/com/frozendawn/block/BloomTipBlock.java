package com.frozendawn.block;

import com.frozendawn.init.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A fresh Bloom tip whose small pulse makes the frontier read as actively growing. */
public final class BloomTipBlock extends Block {
    public BloomTipBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        double phase = (level.getGameTime() + Math.floorMod(pos.asLong(), 37L)) * 0.13D;
        double radius = 0.12D + (Math.sin(phase) + 1.0D) * 0.055D;
        double angle = phase + random.nextDouble() * 0.4D;
        level.addParticle(ModParticles.BLOOM_DRIFT.get(),
                pos.getX() + 0.5D + Math.cos(angle) * radius,
                pos.getY() + 0.38D + random.nextDouble() * 0.38D,
                pos.getZ() + 0.5D + Math.sin(angle) * radius,
                Math.cos(angle) * 0.002D, 0.003D,
                Math.sin(angle) * 0.002D);
    }
}
