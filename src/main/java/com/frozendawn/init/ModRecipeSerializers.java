package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.recipe.BlueprintLockedPatternRecipe;
import com.frozendawn.recipe.CaloricLinedEvaUpgradeRecipe;
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

    public static final DeferredHolder<RecipeSerializer<?>, BlueprintLockedPatternRecipe.Serializer> BLUEPRINT_LOCKED_SHAPED =
            RECIPE_SERIALIZERS.register("blueprint_locked_shaped", BlueprintLockedPatternRecipe.Serializer::new);

    private ModRecipeSerializers() {
    }
}
