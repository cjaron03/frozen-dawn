package com.frozendawn.vault;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;

/** The technician's cot. A rest here is not vanilla sleep: it works at any hour, sets no respawn and skips no night.
 * The player lies down through the entity's own sleeping state, so the body, the pose and Leave Bed all behave. */
@EventBusSubscriber(modid = com.frozendawn.FrozenDawn.MOD_ID)
public final class VaultCotBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final MapCodec<VaultCotBlock> CODEC = simpleCodec(VaultCotBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 7, 16);

    public VaultCotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.HEAD));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            var head = state.getValue(PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(FACING));
            rest(serverPlayer, server, head);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Lies the player down on the cot whose head is at {@code head}, unless someone is on it already. */
    public static boolean rest(ServerPlayer player, ServerLevel level, BlockPos head) {
        if (player.isSleeping()) return false;
        for (var other : level.players()) if (other.getSleepingPos().filter(head::equals).isPresent()) return false;
        player.startSleeping(head);
        return true;
    }

    /** Vanilla wakes a sleeper at dawn; this rest does not care what time it is. */
    @SubscribeEvent public static void onContinueSleeping(CanContinueSleepingEvent event) {
        var at = event.getEntity().getSleepingPos();
        if (at.isPresent() && event.getEntity().level().getBlockState(at.get()).getBlock() instanceof VaultCotBlock) event.setContinueSleeping(true);
    }

    @Override public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) { return true; }
    @Override public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) { }
    @Override public Direction getBedDirection(BlockState state, LevelReader level, BlockPos pos) { return state.getValue(FACING); }
}
