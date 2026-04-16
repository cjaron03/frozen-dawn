package com.frozendawn.client.compat.jei;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.frozendawn.recipe.FuelProcessingSiloRecipes.IngredientRequirement;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class FuelProcessingSiloCategory implements IRecipeCategory<FuelProcessingSiloRecipeDisplay> {
    public static final RecipeType<FuelProcessingSiloRecipeDisplay> RECIPE_TYPE =
            RecipeType.create(FrozenDawn.MOD_ID, "fuel_processing_silo", FuelProcessingSiloRecipeDisplay.class);

    private static final int[][] INPUT_SLOTS = {
            { 4, 12 }, { 24, 12 },
            { 4, 32 }, { 24, 32 }
    };

    private final IDrawable icon;

    public FuelProcessingSiloCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.FUEL_PROCESSING_SILO_CONTROLLER.get()));
    }

    @Override
    public RecipeType<FuelProcessingSiloRecipeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.literal("Fuel Processing Silo");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 164;
    }

    @Override
    public int getHeight() {
        return 78;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FuelProcessingSiloRecipeDisplay recipe, IFocusGroup focuses) {
        List<IngredientRequirement> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size() && i < INPUT_SLOTS.length; i++) {
            int[] slot = INPUT_SLOTS[i];
            builder.addSlot(RecipeIngredientRole.INPUT, slot[0], slot[1])
                    .addItemStacks(stacksWithCount(ingredients.get(i)));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 130, 22)
                .addItemStack(recipe.output().copy());
        builder.moveRecipeTransferButton(146, 54);
    }

    @Override
    public void draw(FuelProcessingSiloRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        int arrowX = 56;
        int arrowY = 26;
        int primary = 0xFF7FDCE8;
        int shadow = 0xFF1E4A56;
        guiGraphics.fill(arrowX, arrowY + 5, arrowX + 42, arrowY + 9, shadow);
        guiGraphics.fill(arrowX + 28, arrowY + 1, arrowX + 34, arrowY + 13, shadow);
        guiGraphics.fill(arrowX + 34, arrowY + 3, arrowX + 40, arrowY + 11, shadow);
        guiGraphics.fill(arrowX + 40, arrowY + 6, arrowX + 46, arrowY + 8, shadow);
        guiGraphics.fill(arrowX + 1, arrowY + 6, arrowX + 38, arrowY + 8, primary);
        guiGraphics.fill(arrowX + 32, arrowY + 3, arrowX + 37, arrowY + 11, primary);
        guiGraphics.fill(arrowX + 37, arrowY + 5, arrowX + 42, arrowY + 9, primary);

        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, recipe.label(), 50, 7, 0xFFFFFFFF, false);
        guiGraphics.drawString(font, recipe.durationLabel() + " at x1", 63, 43, 0xFFB7C8D0, false);
        guiGraphics.drawString(font, "Needs lit Thermal Heater", 0, 57, 0xFF93B8C0, false);
        guiGraphics.drawString(font, "Drains heater fuel while processing", 0, 66, 0xFF93B8C0, false);
    }

    private static List<ItemStack> stacksWithCount(IngredientRequirement requirement) {
        return Arrays.stream(requirement.ingredient().getItems())
                .map(ItemStack::copy)
                .peek(stack -> stack.setCount(requirement.count()))
                .toList();
    }
}
