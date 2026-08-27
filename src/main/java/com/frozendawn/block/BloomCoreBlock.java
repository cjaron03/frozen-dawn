package com.frozendawn.block;

import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A rare active knot inside mature Bloom geometry. */
public final class BloomCoreBlock extends Block {
    public BloomCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.GLOW,
                    x + random.nextGaussian() * 0.24D,
                    y + random.nextGaussian() * 0.24D,
                    z + random.nextGaussian() * 0.24D,
                    0.0D, 0.008D, 0.0D);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.WAX_ON,
                    x + random.nextGaussian() * 0.32D,
                    y + random.nextGaussian() * 0.32D,
                    z + random.nextGaussian() * 0.32D,
                    0.0D, 0.012D, 0.0D);
        }
        if (random.nextInt(180) == 0) {
            level.playLocalSound(x, y, z, ModSounds.BLOOM_CORE_PULSE.get(),
                    SoundSource.BLOCKS, 0.8F, 0.94F + random.nextFloat() * 0.10F, false);
        }
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos,
                                   ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        popResource(level, pos, new ItemStack(
                ModItems.SPENT_LATTICE.get(), 2 + level.random.nextInt(2)));
        level.playSound(null, pos, ModSounds.BLOOM_CORE_BREAK.get(),
                SoundSource.BLOCKS, 1.5F, 0.92F + level.random.nextFloat() * 0.12F);
        level.sendParticles(ParticleTypes.GLOW,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                28, 0.45D, 0.45D, 0.45D, 0.08D);
        level.sendParticles(ParticleTypes.WAX_ON,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                36, 0.55D, 0.55D, 0.55D, 0.11D);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide() && !newState.is(this) && level instanceof ServerLevel server) {
            BloomGrowthManager.reactivateAround(server, pos);
        }
    }
}
