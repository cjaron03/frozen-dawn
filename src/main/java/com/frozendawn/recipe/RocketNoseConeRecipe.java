package com.frozendawn.recipe;

import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModRecipeSerializers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.Map;

public class RocketNoseConeRecipe extends BlueprintLockedPatternRecipe {
    private static final String[] PATTERN = {
            " A ",
            "IGI",
            "ICI"
    };

    private static final Map<Character, Item> KEY = Map.of(
            'A', ModItems.REFINED_ACHERONITE.get(),
            'I', Items.IRON_INGOT,
            'G', ModItems.INSULATED_GLASS.get(),
            'C', Items.COPPER_INGOT
    );

    public RocketNoseConeRecipe(CraftingBookCategory category) {
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
        return new ItemStack(ModItems.ROCKET_NOSE_CONE.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ROCKET_NOSE_CONE.get();
    }
}
