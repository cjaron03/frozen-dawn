package com.frozendawn.flora;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.world.TemperatureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

/** Shared Cold Flora tags, the Rimewood wood type, and the temperature a plant feels. */
public final class ColdFlora {
    private ColdFlora() { }

    public static final BlockSetType RIMEWOOD_SET = BlockSetType.register(new BlockSetType(FrozenDawn.MOD_ID + ":rimewood"));
    public static final WoodType RIMEWOOD = WoodType.register(new WoodType(FrozenDawn.MOD_ID + ":rimewood", RIMEWOOD_SET));

    /** Living Rimewood (logs, wood, leaves, sapling) and Frostcap: vegetation decay and epoch catch-up leave them alone. */
    public static final TagKey<Block> COLD_FLORA = block("cold_flora");
    /** Rimewood logs and wood, stripped or not: Frostcap grows on their sides. */
    public static final TagKey<Block> RIMEWOOD_LOGS = block("rimewood_logs");
    /** Ground Frostcap grows on: snow, ice and Rimewood. Full snow layers also count. */
    public static final TagKey<Block> FROSTCAP_GROUND = block("frostcap_grows_on");
    public static final TagKey<Item> RIMEWOOD_LOG_ITEMS = item("rimewood_logs");
    /** Frozen or thawed caps, for the stew and drying recipes. */
    public static final TagKey<Item> FROSTCAPS = item("frostcaps");

    private static TagKey<Block> block(String name) { return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name)); }
    private static TagKey<Item> item(String name) { return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name)); }

    /** Ground Rimewood roots in: any dirt, dead grass or frozen dirt, and packed snow. */
    public static boolean rimewoodSoil(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(com.frozendawn.init.ModBlocks.DEAD_GRASS_BLOCK.get())
                || state.is(com.frozendawn.init.ModBlocks.FROZEN_DIRT.get()) || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK);
    }

    /** What a plant at {@code pos} feels: the full local temperature, reading only loaded neighbours. */
    public static float temperature(ServerLevel level, BlockPos pos) {
        var state = ApocalypseState.get(level.getServer());
        return TemperatureManager.getLoadedTemperatureAt(level, pos, state.getCurrentDay(), state.getTotalDays());
    }
}
