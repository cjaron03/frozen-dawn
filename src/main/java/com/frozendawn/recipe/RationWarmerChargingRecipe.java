package com.frozendawn.recipe;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModRecipeSerializers;
import com.frozendawn.item.RationWarmerItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/** A native shapeless recipe so Patchouli and JEI retain their ordinary crafting transfer. */
public class RationWarmerChargingRecipe extends ShapelessRecipe {
    public RationWarmerChargingRecipe(String group, CraftingBookCategory category, ItemStack result,
                                     NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
    }

    private static RationWarmerChargingRecipe fromVanilla(ShapelessRecipe recipe) {
        return new RationWarmerChargingRecipe(recipe.getGroup(), recipe.category(),
                recipe.getResultItem(null).copy(), recipe.getIngredients());
    }

    private ShapelessRecipe asVanilla() {
        return new ShapelessRecipe(getGroup(), category(), getResultItem(null).copy(), getIngredients());
    }

    private static ItemStack device(CraftingInput input) {
        for (ItemStack stack : input.items()) if (stack.is(ModItems.RATION_WARMER)) return stack;
        return ItemStack.EMPTY;
    }

    /** Exact per-slot debits. Queries and previews never mutate the crafting input. */
    public int[] consumption(CraftingInput input) {
        int deviceSlot = -1;
        int dustSlots = 0;
        int dustCount = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(ModItems.RATION_WARMER)) {
                if (deviceSlot != -1 || stack.getCount() != 1 || !getIngredients().getFirst().test(stack)) return null;
                deviceSlot = i;
            } else if (stack.is(Items.REDSTONE)) {
                dustSlots++;
                dustCount += stack.getCount();
            } else return null;
        }
        if (deviceSlot == -1 || dustSlots != getIngredients().size() - 1 || dustCount == 0) return null;
        int needed = RationWarmerItem.MAX_CHARGES - RationWarmerItem.charges(input.getItem(deviceSlot));
        if (needed <= 0) return null;
        int remaining = Math.min(needed, dustCount);
        int[] amounts = new int[input.size()];
        amounts[deviceSlot] = 1;
        for (int i = 0; i < input.size() && remaining > 0; i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.is(Items.REDSTONE)) continue;
            amounts[i] = Math.min(remaining, stack.getCount());
            remaining -= amounts[i];
        }
        return amounts;
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        return consumption(input) != null;
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int[] amounts = consumption(input);
        if (amounts == null) return ItemStack.EMPTY;
        int added = java.util.Arrays.stream(amounts).sum() - 1;
        ItemStack original = device(input);
        ItemStack result = original.copyWithCount(1);
        result.set(ModDataComponents.RATION_WARMER_CHARGES, RationWarmerItem.charges(original) + added);
        return result;
    }

    @Override public RecipeSerializer<?> getSerializer() { return ModRecipeSerializers.RATION_WARMER_CHARGING.get(); }

    public static class Serializer implements RecipeSerializer<RationWarmerChargingRecipe> {
        private static final MapCodec<RationWarmerChargingRecipe> CODEC = new ShapelessRecipe.Serializer().codec()
                .xmap(RationWarmerChargingRecipe::fromVanilla, RationWarmerChargingRecipe::asVanilla);
        private static final StreamCodec<RegistryFriendlyByteBuf, RationWarmerChargingRecipe> STREAM_CODEC =
                ShapelessRecipe.Serializer.STREAM_CODEC.map(RationWarmerChargingRecipe::fromVanilla, RationWarmerChargingRecipe::asVanilla);
        @Override public MapCodec<RationWarmerChargingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, RationWarmerChargingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
