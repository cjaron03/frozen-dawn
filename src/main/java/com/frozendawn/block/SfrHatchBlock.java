package com.frozendawn.block;

import com.frozendawn.airlock.AirlockManager;
import com.frozendawn.network.AirlockStatusPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Two-high maintenance hatch in the outer ring, lower half at Y2. Opens into the vacuum channel. */
public final class SfrHatchBlock extends DoorBlock {
    public static final com.mojang.serialization.MapCodec<SfrHatchBlock> CODEC = simpleCodec(SfrHatchBlock::new);
    @Override public com.mojang.serialization.MapCodec<? extends DoorBlock> codec() { return CODEC; }
    public SfrHatchBlock(Properties p) { super(BlockSetType.IRON, p); }

    /** Opens by hand or redstone only when the channel matches the outside by the airlock rule. A hatch outside a valid torus opens freely. */
    public static boolean canOpen(ServerLevel level, BlockPos lower) {
        return SfrControllerBlockEntity.forHatch(level, lower).map(c -> AirlockManager.matchingPressure(c.channelPressure(), c.outsidePressure(level))).orElse(true);
    }
    private static BlockPos lower(BlockPos pos, BlockState state) { return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below(); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            boolean open = !state.getValue(OPEN);
            if (open && !canOpen(server, lower(pos, state))) AirlockManager.refuse(server, pos, player, AirlockStatusPayload.Status.VACUUM_DIFFERENCE);
            else setOpen(player, level, state, pos, open);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!(level instanceof ServerLevel server) || block == this) return;
        var lower = lower(pos, state); var current = level.getBlockState(lower);
        if (!current.is(this)) return;
        boolean powered = level.hasNeighborSignal(lower) || level.hasNeighborSignal(lower.above());
        if (powered == current.getValue(POWERED)) return;
        boolean open = powered && (current.getValue(OPEN) || canOpen(server, lower));
        if (!powered && current.getValue(OPEN)) open = false;
        if (open && !current.getValue(OPEN) || !open && current.getValue(OPEN) && !powered)
            level.playSound(null, lower, open ? type().doorOpen() : type().doorClose(), SoundSource.BLOCKS, 1f, 1f);
        level.setBlock(lower, current.setValue(POWERED, powered).setValue(OPEN, open), 10);
    }
}
