package com.frozendawn.recipe;

import com.frozendawn.data.WinConditionState;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.level.Level;

import java.util.Map;

public abstract class BlueprintLockedPatternRecipe extends CustomRecipe {
    protected BlueprintLockedPatternRecipe(CraftingBookCategory category) {
        super(category);
    }

    protected abstract String[] pattern();

    protected abstract Map<Character, Item> key();

    protected abstract ItemStack result();

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.size() < 9 || level == null || level.getServer() == null) {
            return false;
        }
        if (!WinConditionState.get(level.getServer()).isRocketBlueprintUnlocked()) {
            return false;
        }
        return matchesPattern(input, false) || matchesPattern(input, true);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result().copy();
    }

    private boolean matchesPattern(CraftingInput input, boolean mirrored) {
        String[] rows = pattern();
        Map<Character, Item> keys = key();
        for (int row = 0; row < 3; row++) {
            String patternRow = rows[row];
            for (int col = 0; col < 3; col++) {
                int patternCol = mirrored ? 2 - col : col;
                char symbol = patternRow.charAt(patternCol);
                ItemStack stack = input.getItem(row * 3 + col);
                if (symbol == ' ') {
                    if (!stack.isEmpty()) {
                        return false;
                    }
                    continue;
                }
                Item expected = keys.get(symbol);
                if (expected == null || !stack.is(expected)) {
                    return false;
                }
            }
        }
        return true;
    }
}
