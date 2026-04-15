package com.frozendawn.recipe;

import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Small recipe registry for the Fuel Processing Silo.
 * Phase 4 only needs the sulfur-lava utility loop, and later fuel recipes can
 * be added here without changing the machine logic again.
 */
public final class FuelProcessingSiloRecipes {
    public static final int PROGRESS_UNITS_PER_TICK = 20;

    private static final List<FuelProcessingSiloRecipe> RECIPES = List.of(
            new FuelProcessingSiloRecipe(
                    "sulfur_lava_reduction",
                    "Sulfur Lava Reduction",
                    List.of(new IngredientRequirement(Ingredient.of(ModItems.SULFUR_LAVA_BUCKET.get()), 1)),
                    new ItemStack(ModItems.IMPURE_SULFUR.get(), 2),
                    1800
            )
    );

    private FuelProcessingSiloRecipes() {
    }

    public static List<FuelProcessingSiloRecipe> all() {
        return RECIPES;
    }

    public static @Nullable FuelProcessingSiloRecipe findMatch(List<ItemStack> inputs, ItemStack outputSlot) {
        for (FuelProcessingSiloRecipe recipe : RECIPES) {
            if (recipe.matches(inputs, outputSlot)) {
                return recipe;
            }
        }
        return null;
    }

    public record IngredientRequirement(Ingredient ingredient, int count) {
    }

    public record FuelProcessingSiloRecipe(
            String id,
            String label,
            List<IngredientRequirement> ingredients,
            ItemStack output,
            int baseTicks
    ) {
        public boolean matches(List<ItemStack> inputs, ItemStack outputSlot) {
            if (!canAcceptOutput(outputSlot)) {
                return false;
            }

            for (ItemStack stack : inputs) {
                if (stack.isEmpty()) {
                    continue;
                }
                boolean fitsAnyIngredient = false;
                for (IngredientRequirement requirement : ingredients) {
                    if (requirement.ingredient().test(stack)) {
                        fitsAnyIngredient = true;
                        break;
                    }
                }
                if (!fitsAnyIngredient) {
                    return false;
                }
            }

            for (IngredientRequirement requirement : ingredients) {
                int available = 0;
                for (ItemStack stack : inputs) {
                    if (requirement.ingredient().test(stack)) {
                        available += stack.getCount();
                    }
                }
                if (available < requirement.count()) {
                    return false;
                }
            }
            return true;
        }

        public void consumeInputs(NonNullList<ItemStack> machineItems, int inputSlots, Level level, BlockPos dropPos) {
            List<ItemStack> remainders = new ArrayList<>();
            for (IngredientRequirement requirement : ingredients) {
                int needed = requirement.count();
                for (int slot = 0; slot < inputSlots && needed > 0; slot++) {
                    ItemStack stack = machineItems.get(slot);
                    if (stack.isEmpty() || !requirement.ingredient().test(stack)) {
                        continue;
                    }

                    int remove = Math.min(needed, stack.getCount());
                    ItemStack remainder = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
                    stack.shrink(remove);
                    needed -= remove;

                    if (!remainder.isEmpty()) {
                        for (int i = 0; i < remove; i++) {
                            remainders.add(remainder.copy());
                        }
                    }
                }
            }

            for (ItemStack remainder : remainders) {
                if (remainder.isEmpty()) {
                    continue;
                }
                if (!insertIntoInputs(machineItems, inputSlots, remainder.copy())) {
                    Containers.dropItemStack(level,
                            dropPos.getX() + 0.5D,
                            dropPos.getY() + 0.5D,
                            dropPos.getZ() + 0.5D,
                            remainder.copy());
                }
            }
        }

        public ItemStack createOutput() {
            return output.copy();
        }

        private boolean canAcceptOutput(ItemStack outputSlot) {
            if (outputSlot.isEmpty()) {
                return true;
            }
            return ItemStack.isSameItemSameComponents(outputSlot, output)
                    && outputSlot.getCount() + output.getCount() <= outputSlot.getMaxStackSize();
        }

        private static boolean insertIntoInputs(NonNullList<ItemStack> machineItems, int inputSlots, ItemStack stack) {
            for (int slot = 0; slot < inputSlots; slot++) {
                ItemStack existing = machineItems.get(slot);
                if (existing.isEmpty()) {
                    machineItems.set(slot, stack);
                    return true;
                }
                if (ItemStack.isSameItemSameComponents(existing, stack)
                        && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(stack.getCount());
                    return true;
                }
            }
            return false;
        }
    }
}
