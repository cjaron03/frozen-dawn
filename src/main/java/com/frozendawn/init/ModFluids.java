package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.fluid.VentLavaFluid;
import com.frozendawn.fluid.VentLavaFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, FrozenDawn.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, FrozenDawn.MOD_ID);

    public static final DeferredHolder<FluidType, VentLavaFluidType> VENT_LAVA_TYPE =
            FLUID_TYPES.register("vent_lava", VentLavaFluidType::new);

    public static final DeferredHolder<Fluid, VentLavaFluid.Source> SOURCE_VENT_LAVA =
            FLUIDS.register("vent_lava", VentLavaFluid.Source::new);

    public static final DeferredHolder<Fluid, VentLavaFluid.Flowing> FLOWING_VENT_LAVA =
            FLUIDS.register("flowing_vent_lava", VentLavaFluid.Flowing::new);

    private ModFluids() {
    }
}
