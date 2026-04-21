package com.frozendawn.block;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FuelProcessingSiloControllerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FuelProcessingSiloControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelProcessingSiloBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == ModBlockEntities.FUEL_PROCESSING_SILO.get()
                ? (lvl, pos, st, be) -> ((FuelProcessingSiloBlockEntity) be).serverTick()
                : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!FuelProcessingSiloMultiblock.isValid(level, pos, state.getValue(FACING))) {
            return;
        }

        if (state.getValue(LIT)) {
            spawnVentSmoke(level, pos, state, random);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof FuelProcessingSiloBlockEntity silo) {
                FuelProcessingSiloMultiblock.Diagnostic diagnostic = FuelProcessingSiloMultiblock.diagnose(level, pos, state.getValue(FACING));
                if (!diagnostic.valid()) {
                    FrozenDawn.LOGGER.info("[SiloDiag] Controller at ({}, {}, {}) invalid: {}",
                            pos.getX(), pos.getY(), pos.getZ(), diagnostic.message());
                    serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00A77[\u00A76ORSA\u00A77] \u00A7c" + diagnostic.message()));
                }
                serverPlayer.openMenu(silo, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof FuelProcessingSiloBlockEntity silo) {
                for (ItemStack stack : silo.getItems()) {
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void spawnVentSmoke(Level level, BlockPos pos, BlockState state, RandomSource random) {
        BlockPos ventCenter = FuelProcessingSiloMultiblock.getTopVentCenter(level, pos, state.getValue(FACING));
        if (ventCenter == null) {
            return;
        }

        double centerX = ventCenter.getX() + 0.5D;
        double centerY = ventCenter.getY() + 0.03D;
        double centerZ = ventCenter.getZ() + 0.5D;

        for (int i = 0; i < 5; i++) {
            double x = centerX + (random.nextDouble() - 0.5D) * 0.34D;
            double z = centerZ + (random.nextDouble() - 0.5D) * 0.34D;
            double upward = 0.07D + random.nextDouble() * 0.05D;
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, centerY, z, 0.0D, upward, 0.0D);
        }

        for (int i = 0; i < 2; i++) {
            double x = centerX + (random.nextDouble() - 0.5D) * 0.26D;
            double z = centerZ + (random.nextDouble() - 0.5D) * 0.26D;
            double upward = 0.09D + random.nextDouble() * 0.04D;
            level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, centerY, z, 0.0D, upward, 0.0D);
        }

        if (random.nextBoolean()) {
            double x = centerX + (random.nextDouble() - 0.5D) * 0.2D;
            double z = centerZ + (random.nextDouble() - 0.5D) * 0.2D;
            level.addParticle(ParticleTypes.SMOKE, x, centerY, z, 0.0D, 0.05D, 0.0D);
        }
    }
}
