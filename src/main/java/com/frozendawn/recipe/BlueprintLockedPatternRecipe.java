package com.frozendawn.recipe;

import com.frozendawn.data.WinConditionState;
import com.frozendawn.init.ModRecipeSerializers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

public class BlueprintLockedPatternRecipe extends ShapedRecipe {
    private final ItemStack displayResult;

    public BlueprintLockedPatternRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.displayResult = result.copy();
    }

    public static BlueprintLockedPatternRecipe fromShaped(ShapedRecipe recipe) {
        return new BlueprintLockedPatternRecipe(
                recipe.getGroup(),
                recipe.category(),
                recipe.pattern,
                recipe.getResultItem(null).copy(),
                recipe.showNotification()
        );
    }

    public ShapedRecipe asVanillaShaped() {
        return new ShapedRecipe(getGroup(), category(), pattern, displayResult.copy(), showNotification());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (level == null || level.getServer() == null) {
            return false;
        }
        if (!WinConditionState.get(level.getServer()).isRocketBlueprintUnlocked()) {
            return false;
        }
        return super.matches(input, level);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return displayResult.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.BLUEPRINT_LOCKED_SHAPED.get();
    }

    public static class Serializer implements RecipeSerializer<BlueprintLockedPatternRecipe> {
        private static final MapCodec<BlueprintLockedPatternRecipe> CODEC = ShapedRecipe.Serializer.CODEC
                .xmap(BlueprintLockedPatternRecipe::fromShaped, BlueprintLockedPatternRecipe::asVanillaShaped);

        private static final StreamCodec<RegistryFriendlyByteBuf, BlueprintLockedPatternRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(BlueprintLockedPatternRecipe::fromShaped, BlueprintLockedPatternRecipe::asVanillaShaped);

        @Override
        public MapCodec<BlueprintLockedPatternRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BlueprintLockedPatternRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
