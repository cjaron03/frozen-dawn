package com.frozendawn.block;

import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

public class VentLavaBlock extends LiquidBlock {

    public VentLavaBlock(BlockBehaviour.Properties properties) {
        super(net.minecraft.world.level.material.Fluids.LAVA, properties);
    }

    private FlowingFluid ventFluid() {
        return (FlowingFluid) ModFluids.SOURCE_VENT_LAVA.get();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!net.neoforged.neoforge.fluids.FluidInteractionRegistry.canInteract(level, pos)) {
            level.scheduleTick(pos, ventFluid(), ventFluid().getTickDelay(level));
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction facing, BlockState facingState,
                                     LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (state.getFluidState().isSource() || facingState.getFluidState().isSource()) {
            level.scheduleTick(currentPos, ventFluid(), ventFluid().getTickDelay(level));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!net.neoforged.neoforge.fluids.FluidInteractionRegistry.canInteract(level, pos)) {
            level.scheduleTick(pos, ventFluid(), ventFluid().getTickDelay(level));
        }
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (living.tickCount % 4 != 0) {
            return;
        }

        DamageSource damageSource = new DamageSource(
                level.registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(ModDamageTypes.HYPERTHERMIA)
        );
        living.hurt(damageSource, 8.0f);
        living.setRemainingFireTicks(Math.max(living.getRemainingFireTicks(), 80));
    }
}
