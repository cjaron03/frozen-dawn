package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.recipe.CaloricLinedEvaUpgradeRecipe;
import com.frozendawn.recipe.RocketEngineRecipe;
import com.frozendawn.recipe.RocketFinRecipe;
import com.frozendawn.recipe.RocketHullRecipe;
import com.frozendawn.recipe.RocketNoseConeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, FrozenDawn.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<CaloricLinedEvaUpgradeRecipe>> CALORIC_LINED_EVA_UPGRADE =
            RECIPE_SERIALIZERS.register("caloric_lined_eva_upgrade",
                    () -> new SimpleCraftingRecipeSerializer<>(CaloricLinedEvaUpgradeRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<RocketEngineRecipe>> ROCKET_ENGINE =
            RECIPE_SERIALIZERS.register("rocket_engine",
                    () -> new SimpleCraftingRecipeSerializer<>(RocketEngineRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<RocketFinRecipe>> ROCKET_FIN =
            RECIPE_SERIALIZERS.register("rocket_fin",
                    () -> new SimpleCraftingRecipeSerializer<>(RocketFinRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<RocketHullRecipe>> ROCKET_HULL =
            RECIPE_SERIALIZERS.register("rocket_hull",
                    () -> new SimpleCraftingRecipeSerializer<>(RocketHullRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<RocketNoseConeRecipe>> ROCKET_NOSE_CONE =
            RECIPE_SERIALIZERS.register("rocket_nose_cone",
                    () -> new SimpleCraftingRecipeSerializer<>(RocketNoseConeRecipe::new));

    private ModRecipeSerializers() {
    }
}
