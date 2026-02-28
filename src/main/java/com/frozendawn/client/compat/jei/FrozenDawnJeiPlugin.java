package com.frozendawn.client.compat.jei;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheronForgeMenu;
import com.frozendawn.block.GeothermalCoreMenu;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModMenuTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

@JeiPlugin
public class FrozenDawnJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new AcheronForgeCategory(guiHelper),
                new GeothermalCoreCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(AcheronForgeCategory.RECIPE_TYPE, List.of(
                new AcheronForgeRecipeDisplay(
                        new ItemStack(ModItems.ACHERONITE_SHARD.get(), 2),
                        new ItemStack(ModItems.REFINED_ACHERONITE.get()))
        ));

        registration.addRecipes(GeothermalCoreCategory.RECIPE_TYPE, List.of(
                new GeothermalCoreUpgradeDisplay(
                        List.of(new ItemStack(Items.OBSIDIAN)),
                        "Range Upgrade", "+1 block radius"),
                new GeothermalCoreUpgradeDisplay(
                        List.of(new ItemStack(Items.DIAMOND_BLOCK)),
                        "Range Upgrade", "+4 block radius"),
                new GeothermalCoreUpgradeDisplay(
                        List.of(new ItemStack(Items.BLAZE_POWDER)),
                        "Temp Upgrade", "+5\u00B0C"),
                new GeothermalCoreUpgradeDisplay(
                        List.of(new ItemStack(ModItems.THERMAL_CORE.get())),
                        "Temp Upgrade", "+10\u00B0C"),
                new GeothermalCoreUpgradeDisplay(
                        List.of(new ItemStack(Items.NETHER_STAR)),
                        "O2 Upgrade", "+1 O2 level"),
                new GeothermalCoreUpgradeDisplay(
                        List.of(new ItemStack(ModItems.O2_TANK.get()),
                                new ItemStack(ModItems.O2_TANK_MK2.get()),
                                new ItemStack(ModItems.O2_TANK_MK3.get())),
                        "O2 Refill", "Refills O2 at +10/tick")
        ));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.ACHERON_FORGE.get()),
                AcheronForgeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.GEOTHERMAL_CORE.get()),
                GeothermalCoreCategory.RECIPE_TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                AcheronForgeMenu.class, ModMenuTypes.ACHERON_FORGE.get(),
                AcheronForgeCategory.RECIPE_TYPE,
                0, 1, 2, 36);
        registration.addRecipeTransferHandler(
                GeothermalCoreMenu.class, ModMenuTypes.GEOTHERMAL_CORE.get(),
                GeothermalCoreCategory.RECIPE_TYPE,
                0, 4, 4, 36);
    }
}
