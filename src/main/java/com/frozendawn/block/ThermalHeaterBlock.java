package com.frozendawn.block;

import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.data.PlayerEndStats;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A player-craftable heater block. Right-click with fuel to add burn time.
 * Base: radius 7, +35C. Higher tiers produce more heat but consume fuel faster.
 * No GUI, no hopper interaction.
 * Fuel does NOT burn while chunk is unloaded (vanilla BlockEntity default).
 */
public class ThermalHeaterBlock extends Block implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final IntegerProperty GLOW_STAGE = IntegerProperty.create("glow_stage", 0, 4);
    private final float fuelMultiplier;

    public ThermalHeaterBlock(Properties properties) {
        this(properties, 1.0f);
    }

    public ThermalHeaterBlock(Properties properties, float fuelMultiplier) {
        super(properties);
        this.fuelMultiplier = fuelMultiplier;
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(GLOW_STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, GLOW_STAGE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThermalHeaterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.THERMAL_HEATER.get()) return null;
        return level.isClientSide()
                ? (lvl, pos, st, be) -> ((ThermalHeaterBlockEntity) be).clientTick()
                : (lvl, pos, st, be) -> ((ThermalHeaterBlockEntity) be).serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Thermal Capacitor install
        if (stack.is(ModItems.THERMAL_CAPACITOR.get())) {
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ThermalHeaterBlockEntity heater) {
                    if (heater.hasCapacitor()) {
                        player.displayClientMessage(Component.translatable("message.frozendawn.capacitor.already_installed"), true);
                    } else {
                        heater.installCapacitor();
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
                        player.displayClientMessage(Component.translatable("message.frozendawn.capacitor.installed"), true);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        int burnTime = (int) (getFuelBurnTime(stack) / fuelMultiplier);
        if (burnTime <= 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ThermalHeaterBlockEntity heater) {
                // Check before shrink since shrink may empty the stack
                boolean isCryoFuel = stack.is(ModItems.CRYO_FUEL.get());
                boolean wasUnlit = !heater.isLit();
                heater.addFuel(burnTime);
                if (wasUnlit && player instanceof ServerPlayer serverPlayer) {
                    PlayerEndStats.incrementHeatersLit(serverPlayer);
                }
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (isCryoFuel && player instanceof ServerPlayer serverPlayer) {
                    WorldTickHandler.grantAdvancement(serverPlayer, "cryo_fuel_heater");
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ThermalHeaterBlockEntity heater) {
                serverPlayer.openMenu(heater, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static int getFuelBurnTime(ItemStack stack) {
        if (stack.is(ModItems.CRYO_FUEL.get())) return 96000;
        if (stack.is(Items.COAL)) return 24000;
        if (stack.is(Items.CHARCOAL)) return 24000;
        if (stack.is(Items.BLAZE_POWDER)) return 12000;
        if (stack.is(Items.COAL_BLOCK)) return 240000;
        return 0;
    }

}
