package com.frozendawn.recipe;

import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModRecipeSerializers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/** Shaped recipe whose ORSA Multitool slot is a non-consuming catalyst. */
public final class ThaevenTranslatorRecipe extends ShapedRecipe {
    private final ItemStack displayResult;

    public ThaevenTranslatorRecipe(String group, CraftingBookCategory category,
                                   ShapedRecipePattern pattern, ItemStack result,
                                   boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.displayResult = result.copy();
    }

    public static ThaevenTranslatorRecipe fromShaped(ShapedRecipe recipe) {
        return new ThaevenTranslatorRecipe(recipe.getGroup(), recipe.category(),
                recipe.pattern, recipe.getResultItem(null).copy(),
                recipe.showNotification());
    }

    private ShapedRecipe asVanillaShaped() {
        return new ShapedRecipe(getGroup(), category(), pattern,
                displayResult.copy(), showNotification());
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return displayResult.copy();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(input);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack ingredient = input.getItem(slot);
            if (ingredient.is(ModItems.ORSA_MULTITOOL.get())) {
                remaining.set(slot, ingredient.copyWithCount(1));
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.THAEVEN_TRANSLATOR_SHAPED.get();
    }

    public static final class Serializer
            implements RecipeSerializer<ThaevenTranslatorRecipe> {
        private static final MapCodec<ThaevenTranslatorRecipe> CODEC =
                ShapedRecipe.Serializer.CODEC.xmap(
                        ThaevenTranslatorRecipe::fromShaped,
                        ThaevenTranslatorRecipe::asVanillaShaped);
        private static final StreamCodec<RegistryFriendlyByteBuf,
                ThaevenTranslatorRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(
                        ThaevenTranslatorRecipe::fromShaped,
                        ThaevenTranslatorRecipe::asVanillaShaped);

        @Override
        public MapCodec<ThaevenTranslatorRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ThaevenTranslatorRecipe>
        streamCodec() {
            return STREAM_CODEC;
        }
    }
}
