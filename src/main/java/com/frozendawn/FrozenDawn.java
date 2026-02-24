package com.frozendawn;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModLootModifiers;
import com.frozendawn.integration.TaNCompat;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(FrozenDawn.MOD_ID)
public class FrozenDawn {
    public static final String MOD_ID = "frozendawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FrozenDawn(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, FrozenDawnConfig.SPEC);

        modEventBus.addListener(this::commonSetup);

        LOGGER.info("Frozen Dawn initialized. The sun grows cold...");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("toughasnails")) {
            TaNCompat.init();
        }
    }
}
