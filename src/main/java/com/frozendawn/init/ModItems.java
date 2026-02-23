package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FrozenDawn.MOD_ID);

    // --- Block Items ---
    public static final DeferredItem<BlockItem> DEAD_GRASS_BLOCK = ITEMS.registerSimpleBlockItem("dead_grass_block", ModBlocks.DEAD_GRASS_BLOCK);
    public static final DeferredItem<BlockItem> FROZEN_DIRT = ITEMS.registerSimpleBlockItem("frozen_dirt", ModBlocks.FROZEN_DIRT);
    public static final DeferredItem<BlockItem> FROZEN_SAND = ITEMS.registerSimpleBlockItem("frozen_sand", ModBlocks.FROZEN_SAND);
    public static final DeferredItem<BlockItem> DEAD_LOG = ITEMS.registerSimpleBlockItem("dead_log", ModBlocks.DEAD_LOG);
    public static final DeferredItem<BlockItem> FROZEN_LOG = ITEMS.registerSimpleBlockItem("frozen_log", ModBlocks.FROZEN_LOG);
    public static final DeferredItem<BlockItem> DEAD_LEAVES = ITEMS.registerSimpleBlockItem("dead_leaves", ModBlocks.DEAD_LEAVES);
    public static final DeferredItem<BlockItem> FROZEN_LEAVES = ITEMS.registerSimpleBlockItem("frozen_leaves", ModBlocks.FROZEN_LEAVES);
    public static final DeferredItem<BlockItem> FROZEN_OBSIDIAN = ITEMS.registerSimpleBlockItem("frozen_obsidian", ModBlocks.FROZEN_OBSIDIAN);

    // --- Items ---
    public static final DeferredItem<Item> ICE_SHARD = ITEMS.registerSimpleItem("ice_shard");

    // --- Creative Tab ---
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FrozenDawn.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FROZEN_DAWN_TAB = CREATIVE_TABS.register("frozen_dawn_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + FrozenDawn.MOD_ID))
                    .withTabsBefore(CreativeModeTabs.NATURAL_BLOCKS)
                    .icon(() -> ICE_SHARD.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(DEAD_GRASS_BLOCK.get());
                        output.accept(FROZEN_DIRT.get());
                        output.accept(FROZEN_SAND.get());
                        output.accept(DEAD_LOG.get());
                        output.accept(FROZEN_LOG.get());
                        output.accept(DEAD_LEAVES.get());
                        output.accept(FROZEN_LEAVES.get());
                        output.accept(FROZEN_OBSIDIAN.get());
                        output.accept(ICE_SHARD.get());
                    }).build());
}
