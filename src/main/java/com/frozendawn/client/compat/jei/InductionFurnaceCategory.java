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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/** Recipes only the Induction Furnace runs; ordinary furnace recipes stay in JEI's smelting category. */
public class InductionFurnaceCategory implements IRecipeCategory<RecipeHolder<SmeltingRecipe>> {
    @SuppressWarnings("unchecked")
    public static final RecipeType<RecipeHolder<SmeltingRecipe>> RECIPE_TYPE =
            RecipeType.create(FrozenDawn.MOD_ID, "induction_furnace", (Class<? extends RecipeHolder<SmeltingRecipe>>) (Class<?>) RecipeHolder.class);
    private final IDrawable icon;

    public InductionFurnaceCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.INDUCTION_FURNACE.get()));
    }
    @Override public RecipeType<RecipeHolder<SmeltingRecipe>> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.frozendawn.induction_furnace"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 150; }
    @Override public int getHeight() { return 42; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<SmeltingRecipe> recipe, IFocusGroup focuses) {
        var access = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess();
        builder.addSlot(RecipeIngredientRole.INPUT, 19, 5).addIngredients(recipe.value().getIngredients().getFirst());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 5).addItemStack(recipe.value().getResultItem(access));
    }
    @Override public void draw(RecipeHolder<SmeltingRecipe> recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        int ax = 46, ay = 5, c = 0xFFB8B8B8;
        g.fill(ax, ay + 6, ax + 15, ay + 10, c);
        g.fill(ax + 12, ay + 3, ax + 16, ay + 13, c);
        g.fill(ax + 16, ay + 5, ax + 20, ay + 11, c);
        g.fill(ax + 20, ay + 7, ax + 24, ay + 9, c);
        var font = Minecraft.getInstance().font;
        int seconds = Math.max(1, (recipe.value().getCookingTime() + 1) / 2 / 20);
        g.drawString(font, seconds + "s · " + com.frozendawn.recipe.InductionRecipes.watts(recipe) / 1000 + " kW", 46, 24, 0xFF808080, false);
        g.drawString(font, Component.translatable("jei.frozendawn.induction_furnace.note", com.frozendawn.recipe.InductionRecipes.REFINING_C), 0, 33, 0xFFAAAAAA, false);
    }
}
