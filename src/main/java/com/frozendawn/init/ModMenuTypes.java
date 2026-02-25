package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.GeothermalCoreMenu;
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
}
