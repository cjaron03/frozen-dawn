package com.frozendawn.fluid;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModFluids;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import net.neoforged.neoforge.fluids.FluidType;

public abstract class VentLavaFluid extends LavaFluid {

    @Override
    public Fluid getFlowing() {
        return ModFluids.FLOWING_VENT_LAVA.get();
    }

    @Override
    public Fluid getSource() {
        return ModFluids.SOURCE_VENT_LAVA.get();
    }

    @Override
    public Item getBucket() {
        return Items.AIR;
    }

    @Override
    public FluidType getFluidType() {
        return ModFluids.VENT_LAVA_TYPE.get();
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return ModBlocks.VENT_LAVA.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == ModFluids.SOURCE_VENT_LAVA.get() || fluid == ModFluids.FLOWING_VENT_LAVA.get();
    }

    public static final class Flowing extends VentLavaFluid {
        @Override
        protected void createFluidStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static final class Source extends VentLavaFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
