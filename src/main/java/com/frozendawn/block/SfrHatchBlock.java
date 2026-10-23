package com.frozendawn.block;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Two-high maintenance hatch in the outer ring, lower half at Y2. Opens into the vacuum channel. */
public final class SfrHatchBlock extends DoorBlock {
    public static final com.mojang.serialization.MapCodec<SfrHatchBlock> CODEC = simpleCodec(SfrHatchBlock::new);
    @Override public com.mojang.serialization.MapCodec<? extends DoorBlock> codec() { return CODEC; }
    public SfrHatchBlock(Properties p) { super(BlockSetType.IRON, p); }
}
