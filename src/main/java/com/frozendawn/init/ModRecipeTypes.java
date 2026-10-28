package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.recipe.FabricatorRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FrozenDawn.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FabricatorRecipe>> FABRICATING =
            RECIPE_TYPES.register("fabricating", () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "fabricating")));

    private ModRecipeTypes() {
    }
}
