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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class FuelProcessingSiloCategory implements IRecipeCategory<FuelProcessingSiloRecipeDisplay> {
    public static final RecipeType<FuelProcessingSiloRecipeDisplay> RECIPE_TYPE =
            RecipeType.create(FrozenDawn.MOD_ID, "fuel_processing_silo", FuelProcessingSiloRecipeDisplay.class);
    private static final ResourceLocation VANILLA_FURNACE_ARROW =
            ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    private static final int[][] INPUT_SLOTS = {
            { 10, 15 }, { 30, 15 },
            { 10, 35 }, { 30, 35 }
    };
    private static final int WIDTH = 170;
    private static final int HEIGHT = 60;
    private static final int OUTPUT_X = 140;
    private static final int OUTPUT_Y = 25;

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
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FuelProcessingSiloRecipeDisplay recipe, IFocusGroup focuses) {
        List<IngredientRequirement> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size() && i < INPUT_SLOTS.length; i++) {
            int[] slot = INPUT_SLOTS[i];
            builder.addSlot(RecipeIngredientRole.INPUT, slot[0], slot[1])
                    .addItemStacks(stacksWithCount(ingredients.get(i)));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .addItemStack(recipe.output().copy());
        builder.moveRecipeTransferButton(150, 43);
    }

    @Override
    public void draw(FuelProcessingSiloRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        drawCentered(guiGraphics, font, recipe.label(), 0, 5, WIDTH, 0xFFFFFFFF);
        guiGraphics.fill(54, 16, 116, 17, 0x3369DCE8);
        guiGraphics.blitSprite(VANILLA_FURNACE_ARROW, 81, 25, 24, 16);
        drawCentered(guiGraphics, font, recipe.durationLabel() + " base", 55, 46, 76, 0xFF9EB4BE);
    }

    private static List<ItemStack> stacksWithCount(IngredientRequirement requirement) {
        return Arrays.stream(requirement.ingredient().getItems())
                .map(ItemStack::copy)
                .peek(stack -> stack.setCount(requirement.count()))
                .toList();
    }

    private static void drawCentered(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font,
                                     String text, int x, int y, int width, int color) {
        int textX = x + (width - font.width(text)) / 2;
        guiGraphics.drawString(font, text, textX, y, color, false);
    }
}
