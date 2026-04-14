package com.frozendawn.block;

import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModFluids;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class VentLavaBlock extends LiquidBlock {

    public VentLavaBlock(BlockBehaviour.Properties properties) {
        super((FlowingFluid) ModFluids.SOURCE_VENT_LAVA.get(), properties);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(LEVEL) != 0) {
            return ItemStack.EMPTY;
        }
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 11);
        return new ItemStack(ModItems.SULFUR_LAVA_BUCKET.get());
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return ModFluids.SOURCE_VENT_LAVA.get().getPickupSound();
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
