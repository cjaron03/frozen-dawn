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

public class GeothermalCoreCategory implements IRecipeCategory<GeothermalCoreUpgradeDisplay> {

    public static final RecipeType<GeothermalCoreUpgradeDisplay> RECIPE_TYPE =
            RecipeType.create(FrozenDawn.MOD_ID, "geothermal_core", GeothermalCoreUpgradeDisplay.class);

    private final IDrawable icon;

    public GeothermalCoreCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.GEOTHERMAL_CORE.get()));
    }

    @Override
    public RecipeType<GeothermalCoreUpgradeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.literal("Geothermal Core Upgrades");
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
        return 28;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GeothermalCoreUpgradeDisplay recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 5)
                .addItemStacks(recipe.inputs());
    }

    @Override
    public void draw(GeothermalCoreUpgradeDisplay recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, recipe.slotName(), 26, 2, 0xFFFFFFFF, false);
        guiGraphics.drawString(font, recipe.description(), 26, 14, 0xFFAAAAAA, false);
    }
}
