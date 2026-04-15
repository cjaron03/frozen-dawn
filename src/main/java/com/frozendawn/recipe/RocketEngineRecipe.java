package com.frozendawn.recipe;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModRecipeSerializers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.Map;

public class RocketEngineRecipe extends BlueprintLockedPatternRecipe {
    private static final String[] PATTERN = {
            "OBO",
            "ATA",
            "CFC"
    };

    private static final Map<Character, Item> KEY = Map.of(
            'O', Items.OBSIDIAN,
            'B', Items.BLAZE_POWDER,
            'A', ModItems.REFINED_ACHERONITE.get(),
            'T', ModItems.THERMAL_CORE.get(),
            'C', Items.COPPER_INGOT,
            'F', Items.BLAST_FURNACE
    );

    public RocketEngineRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    protected String[] pattern() {
        return PATTERN;
    }

    @Override
    protected Map<Character, Item> key() {
        return KEY;
    }

    @Override
    protected ItemStack result() {
        return new ItemStack(ModItems.ROCKET_ENGINE.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ROCKET_ENGINE.get();
    }
}
