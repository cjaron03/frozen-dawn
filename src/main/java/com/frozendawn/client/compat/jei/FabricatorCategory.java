package com.frozendawn.client.compat.jei;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.FabricatorBlueprints;
import com.frozendawn.init.ModItems;
import com.frozendawn.recipe.FabricatorRecipe;
import java.util.Arrays;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** ORSA Fabricator jobs: up to six counted inputs, the printed part, and the blueprint the world must have archived. */
public class FabricatorCategory implements IRecipeCategory<RecipeHolder<FabricatorRecipe>> {
    @SuppressWarnings("unchecked")
    public static final RecipeType<RecipeHolder<FabricatorRecipe>> RECIPE_TYPE =
            RecipeType.create(FrozenDawn.MOD_ID, "fabricating", (Class<? extends RecipeHolder<FabricatorRecipe>>) (Class<?>) RecipeHolder.class);
    private final IDrawable icon;

    public FabricatorCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.FABRICATOR.get()));
    }
    @Override public RecipeType<RecipeHolder<FabricatorRecipe>> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.frozendawn.fabricator"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 150; }
    @Override public int getHeight() { return 58; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<FabricatorRecipe> recipe, IFocusGroup focuses) {
        var counted = recipe.value().counted();
        for (int i = 0; i < counted.size(); i++) {
            var c = counted.get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + i % 3 * 18, 1 + i / 3 * 18)
                    .addItemStacks(Arrays.stream(c.ingredient().getItems()).map(s -> s.copyWithCount(c.count())).toList());
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 10).addItemStack(recipe.value().result());
    }
    @Override public void draw(RecipeHolder<FabricatorRecipe> recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        int ax = 63, ay = 10, c = 0xFFB8B8B8;
        g.fill(ax, ay + 6, ax + 15, ay + 10, c);
        g.fill(ax + 12, ay + 3, ax + 16, ay + 13, c);
        g.fill(ax + 16, ay + 5, ax + 20, ay + 11, c);
        g.fill(ax + 20, ay + 7, ax + 24, ay + 9, c);
        var font = Minecraft.getInstance().font;
        g.drawString(font, recipe.value().seconds() + "s · " + recipe.value().watts() / 1000 + " kW", 116, 14, 0xFF808080, false);
        var id = recipe.value().blueprint();
        Component part = FabricatorBlueprints.ROCKET.equals(id) ? Component.translatable("jei.frozendawn.fabricator.rocket")
                : BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, id)).getDescription();
        g.drawString(font, Component.translatable("jei.frozendawn.fabricator.blueprint", part), 0, 40, 0xFFAAAAAA, false);
    }
}
