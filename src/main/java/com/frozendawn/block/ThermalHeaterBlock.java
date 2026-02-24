package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A player-craftable heater block. Right-click with fuel to add burn time.
 * When lit: radius 7, +35C heat source. No GUI, no hopper interaction.
 * Fuel does NOT burn while chunk is unloaded (vanilla BlockEntity default).
 */
public class ThermalHeaterBlock extends Block implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ThermalHeaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThermalHeaterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == ModBlockEntities.THERMAL_HEATER.get()
                ? (lvl, pos, st, be) -> ((ThermalHeaterBlockEntity) be).serverTick()
                : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        int burnTime = getFuelBurnTime(stack);
        if (burnTime <= 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ThermalHeaterBlockEntity heater) {
                heater.addFuel(burnTime);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private static int getFuelBurnTime(ItemStack stack) {
        if (stack.is(Items.COAL)) return 24000;
        if (stack.is(Items.CHARCOAL)) return 24000;
        if (stack.is(Items.BLAZE_POWDER)) return 12000;
        if (stack.is(Items.COAL_BLOCK)) return 240000;
        return 0;
    }

    @Override
    public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) ? 13 : 0;
    }
}
