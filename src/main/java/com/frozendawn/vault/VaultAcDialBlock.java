package com.frozendawn.vault;

import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The cold store's dial, two blocks tall beside its A/C: lamps and a word over the dial itself. A click turns it one step on,
 * LOW, MID, MAX and round. The room reads the lower block. */
public final class VaultAcDialBlock extends Block {
    public static final int LOW = 0, MID = 1, MAX = 2;
    public static final IntegerProperty SETTING = IntegerProperty.create("setting", LOW, MAX);
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    private static final VoxelShape SHAPE = box(1, 0, 0, 15, 16, 7);

    public VaultAcDialBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SETTING, LOW).setValue(UPPER, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(SETTING, UPPER); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            var lower = state.getValue(UPPER) ? pos.below() : pos;
            int next = (state.getValue(SETTING) + 1) % (MAX + 1);
            set(server, lower, next);
            server.playSound(null, pos, ModSounds.VAULT_FUSE.get(), SoundSource.BLOCKS, .7F, .8F + next * .3F);
            com.mojang.logging.LogUtils.getLogger().info("[Morgue] The A/C goes to {}", next == MAX ? "MAX" : next == MID ? "MID" : "LOW");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    /** Both blocks, from the lower, and the unit to the west of them. */
    public static void set(ServerLevel level, BlockPos lower, int setting) {
        for (var at : BlockPos.betweenClosed(lower.offset(-3, 0, 0), lower.above()))
            level.setBlock(at.immutable(), level.getBlockState(at).trySetValue(SETTING, setting), 3);
    }
}
