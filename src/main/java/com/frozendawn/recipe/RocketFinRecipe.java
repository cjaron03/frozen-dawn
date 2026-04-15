package com.frozendawn.recipe;

import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModRecipeSerializers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.Map;

public class RocketFinRecipe extends BlueprintLockedPatternRecipe {
    private static final String[] PATTERN = {
            "I  ",
            "II ",
            "CAC"
    };

    private static final Map<Character, Item> KEY = Map.of(
            'I', Items.IRON_INGOT,
            'C', Items.COPPER_INGOT,
            'A', ModItems.REFINED_ACHERONITE.get()
    );

    public RocketFinRecipe(CraftingBookCategory category) {
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
        return new ItemStack(ModItems.ROCKET_FIN.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ROCKET_FIN.get();
    }
}
