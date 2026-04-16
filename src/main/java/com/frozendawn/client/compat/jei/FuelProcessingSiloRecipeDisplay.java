package com.frozendawn.client.compat.jei;

import com.frozendawn.recipe.FuelProcessingSiloRecipes.FuelProcessingSiloRecipe;
import com.frozendawn.recipe.FuelProcessingSiloRecipes.IngredientRequirement;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record FuelProcessingSiloRecipeDisplay(
        String id,
        String label,
        List<IngredientRequirement> ingredients,
        ItemStack output,
        int baseTicks
) {
    public static FuelProcessingSiloRecipeDisplay from(FuelProcessingSiloRecipe recipe) {
        return new FuelProcessingSiloRecipeDisplay(
                recipe.id(),
                recipe.label(),
                recipe.ingredients(),
                recipe.output().copy(),
                recipe.baseTicks()
        );
    }

    public String durationLabel() {
        return (baseTicks / 20) + "s";
    }
}
