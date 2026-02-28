package com.frozendawn.client.compat.jei;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
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

public class AcheronForgeCategory implements IRecipeCategory<AcheronForgeRecipeDisplay> {

    public static final RecipeType<AcheronForgeRecipeDisplay> RECIPE_TYPE =
            RecipeType.create(FrozenDawn.MOD_ID, "acheron_forge", AcheronForgeRecipeDisplay.class);

    private final IDrawable icon;

    public AcheronForgeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.ACHERON_FORGE.get()));
    }

    @Override
    public RecipeType<AcheronForgeRecipeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.literal("Acheron Forge");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return 61;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AcheronForgeRecipeDisplay recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 19, 5)
                .addItemStack(recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 5)
                .addItemStack(recipe.output());
    }

    @Override
    public void draw(AcheronForgeRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // Programmatic arrow (shaft + triangular head)
        int ax = 46, ay = 5, c = 0xFFB8B8B8;
        guiGraphics.fill(ax, ay + 6, ax + 15, ay + 10, c);
        guiGraphics.fill(ax + 12, ay + 3, ax + 16, ay + 13, c);
        guiGraphics.fill(ax + 16, ay + 5, ax + 20, ay + 11, c);
        guiGraphics.fill(ax + 20, ay + 7, ax + 24, ay + 9, c);

        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, "10s", 52, 28, 0xFF808080, false);
        guiGraphics.drawString(font, "Below Y=0", 0, 40, 0xFFAAAAAA, false);
        guiGraphics.drawString(font, "Heat source within 8 blocks", 0, 51, 0xFFAAAAAA, false);
    }
}
