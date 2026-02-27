package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheronForgeMenu;
import com.frozendawn.block.GeothermalCoreMenu;
import com.frozendawn.block.ThermalHeaterMenu;
import com.frozendawn.block.TransponderMenu;
import com.frozendawn.item.ThermalContainerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, FrozenDawn.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<GeothermalCoreMenu>> GEOTHERMAL_CORE =
            MENU_TYPES.register("geothermal_core",
                    () -> IMenuTypeExtension.create(GeothermalCoreMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ThermalContainerMenu>> THERMAL_CONTAINER =
            MENU_TYPES.register("thermal_container",
                    () -> IMenuTypeExtension.create(ThermalContainerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AcheronForgeMenu>> ACHERON_FORGE =
            MENU_TYPES.register("acheron_forge",
                    () -> IMenuTypeExtension.create(AcheronForgeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TransponderMenu>> TRANSPONDER =
            MENU_TYPES.register("transponder",
                    () -> IMenuTypeExtension.create(TransponderMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ThermalHeaterMenu>> THERMAL_HEATER =
            MENU_TYPES.register("thermal_heater",
                    () -> IMenuTypeExtension.create(ThermalHeaterMenu::new));
}
