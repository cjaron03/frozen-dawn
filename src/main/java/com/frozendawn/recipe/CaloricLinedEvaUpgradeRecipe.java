package com.frozendawn.recipe;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class CaloricLinedEvaUpgradeRecipe extends CustomRecipe {
    public CaloricLinedEvaUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findUpgradableSuit(input) >= 0 && containsExactlyOne(input, ModItems.SULFUR_CRUST.get())
                && containsExactlyOne(input, ModItems.HYDROTHERMAL_ROCK.get())
                && input.ingredientCount() == 3;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int suitSlot = findUpgradableSuit(input);
        if (suitSlot < 0) {
            return ItemStack.EMPTY;
        }

        ItemStack upgraded = input.getItem(suitSlot).copyWithCount(1);
        upgraded.set(ModDataComponents.CALORIC_RESISTANCE.get(), true);
        return upgraded;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        ItemStack preview = new ItemStack(ModItems.LINED_EVA_CHESTPLATE.get());
        preview.set(ModDataComponents.CALORIC_RESISTANCE.get(), true);
        return preview;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CALORIC_LINED_EVA_UPGRADE.get();
    }

    private static int findUpgradableSuit(CraftingInput input) {
        int match = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.is(ModItems.LINED_EVA_CHESTPLATE.get())) {
                continue;
            }
            if (stack.getOrDefault(ModDataComponents.CALORIC_RESISTANCE.get(), false)) {
                return -1;
            }
            if (match != -1) {
                return -1;
            }
            match = i;
        }
        return match;
    }

    private static boolean containsExactlyOne(CraftingInput input, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).is(item)) {
                count++;
            }
        }
        return count == 1;
    }
}
