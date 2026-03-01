package com.frozendawn;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModArmorMaterials;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModLootModifiers;
import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.init.ModSounds;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(FrozenDawn.MOD_ID)
public class FrozenDawn {
    public static final String MOD_ID = "frozendawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FrozenDawn(IEventBus modEventBus, ModContainer modContainer) {
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, FrozenDawnConfig.SPEC);

        LOGGER.info("Frozen Dawn initialized. The sun grows cold...");
    }
}
