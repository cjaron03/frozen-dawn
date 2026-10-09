package com.frozendawn.block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
public final class ExtinguishedTorchBlock extends TorchBlock {
    public ExtinguishedTorchBlock(Properties properties) { super(ParticleTypes.SMOKE, properties); }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {}
}
