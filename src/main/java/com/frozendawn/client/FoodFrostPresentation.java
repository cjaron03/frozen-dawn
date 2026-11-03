package com.frozendawn.client;

import com.frozendawn.init.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class FoodFrostPresentation {
    private FoodFrostPresentation() {}
    public static boolean onlyFrostChanged(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || oldStack.isEmpty() || newStack.isEmpty()
                || !oldStack.has(DataComponents.FOOD) || !newStack.has(DataComponents.FOOD)
                || ItemStack.matches(oldStack, newStack)) return false;
        ItemStack oldCopy = oldStack.copy();
        ItemStack newCopy = newStack.copy();
        oldCopy.remove(ModDataComponents.FROST_TICKS);
        newCopy.remove(ModDataComponents.FROST_TICKS);
        oldCopy.remove(ModDataComponents.FOOD_WARM_UNTIL);
        newCopy.remove(ModDataComponents.FOOD_WARM_UNTIL);
        return ItemStack.matches(oldCopy, newCopy);
    }
}
