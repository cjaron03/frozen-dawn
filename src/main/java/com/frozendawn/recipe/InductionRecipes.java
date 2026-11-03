package com.frozendawn.recipe;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/** Smelting recipes only the ORSA Induction Furnace runs. They are never registered with the RecipeManager,
 * so fire-fed furnaces cannot use them: refining fuel needs a working power grid. */
public final class InductionRecipes {
    /** Uranium refining is a high-temperature process: the furnace runs hotter and draws 8 kW instead of 2. */
    public static final int REFINING_C = 1200, REFINING_WATTS = 8000;
    private static List<RecipeHolder<SmeltingRecipe>> recipes;
    private InductionRecipes() {}

    public static List<RecipeHolder<SmeltingRecipe>> all() {
        if (recipes == null) recipes = List.of(
                recipe("uranium_concentrate", Ingredient.of(ModItems.RAW_URANIUM.get()), new ItemStack(ModItems.URANIUM_CONCENTRATE.get()), 0.7f, 1200));
        return recipes;
    }
    public static Optional<RecipeHolder<SmeltingRecipe>> find(ItemStack input) {
        return input.isEmpty() ? Optional.empty() : all().stream().filter(r -> r.value().getIngredients().getFirst().test(input)).findFirst();
    }
    /** Electrical draw while running this recipe; ordinary smelting keeps the furnace's 2 kW. */
    public static int watts(RecipeHolder<?> holder) {
        return holder != null && byId(holder.id()).isPresent() ? REFINING_WATTS : com.frozendawn.block.InductionFurnaceBlockEntity.DEMAND_WATTS;
    }
    public static Optional<RecipeHolder<SmeltingRecipe>> byId(ResourceLocation id) {
        return all().stream().filter(r -> r.id().equals(id)).findFirst();
    }
    private static RecipeHolder<SmeltingRecipe> recipe(String name, Ingredient input, ItemStack output, float xp, int ticks) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "induction/" + name),
                new SmeltingRecipe("", CookingBookCategory.MISC, input, output, xp, ticks));
    }
}
