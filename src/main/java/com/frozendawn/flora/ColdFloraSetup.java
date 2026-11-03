package com.frozendawn.flora;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/** Rimewood burns like other wood, potted saplings work, and Rimewood signs use the vanilla sign block entities. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ColdFloraSetup {
    private ColdFloraSetup() { }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var fire = (FireBlock) Blocks.FIRE;
            for (var log : new net.minecraft.world.level.block.Block[] {ModBlocks.RIMEWOOD_LOG.get(), ModBlocks.RIMEWOOD_WOOD.get(),
                    ModBlocks.STRIPPED_RIMEWOOD_LOG.get(), ModBlocks.STRIPPED_RIMEWOOD_WOOD.get()}) fire.setFlammable(log, 5, 5);
            for (var wood : new net.minecraft.world.level.block.Block[] {ModBlocks.RIMEWOOD_PLANKS.get(), ModBlocks.RIMEWOOD_STAIRS.get(),
                    ModBlocks.RIMEWOOD_SLAB.get(), ModBlocks.RIMEWOOD_FENCE.get(), ModBlocks.RIMEWOOD_FENCE_GATE.get()}) fire.setFlammable(wood, 5, 20);
            fire.setFlammable(ModBlocks.RIMEWOOD_LEAVES.get(), 30, 60);
            ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(BuiltInRegistries.BLOCK.getKey(ModBlocks.RIMEWOOD_SAPLING.get()), ModBlocks.POTTED_RIMEWOOD_SAPLING);
        });
    }

    @SubscribeEvent
    public static void signs(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SIGN, ModBlocks.RIMEWOOD_SIGN.get(), ModBlocks.RIMEWOOD_WALL_SIGN.get());
        event.modify(BlockEntityType.HANGING_SIGN, ModBlocks.RIMEWOOD_HANGING_SIGN.get(), ModBlocks.RIMEWOOD_WALL_HANGING_SIGN.get());
    }
}
