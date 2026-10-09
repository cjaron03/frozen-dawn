package com.frozendawn.client.compat.jei;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.recipe.RationWarmerChargingRecipe;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Native crafting transfer with charge-aware inputs and linked charge-aware previews. */
public final class RationWarmerJeiExtension implements ICraftingCategoryExtension<RationWarmerChargingRecipe> {
    @Override
    public void setRecipe(RecipeHolder<RationWarmerChargingRecipe> holder, IRecipeLayoutBuilder builder,
                          ICraftingGridHelper grid, IFocusGroup focuses) {
        List<List<ItemStack>> ingredients = holder.value().getIngredients().stream()
                .map(ingredient -> List.of(ingredient.getItems())).toList();
        var inputs = grid.createAndSetInputs(builder, ingredients, 0, 0);
        var outputs = new ArrayList<ItemStack>();
        int dust = holder.value().getIngredients().size() - 1;
        for (int charge = 0; charge < 4; charge++) {
            var output = new ItemStack(ModItems.RATION_WARMER.get());
            output.set(ModDataComponents.RATION_WARMER_CHARGES, Math.min(4, charge + dust));
            outputs.add(output);
        }
        var output = grid.createAndSetOutputs(builder, outputs);
        builder.createFocusLink(inputs.getFirst(), output);
    }
}
