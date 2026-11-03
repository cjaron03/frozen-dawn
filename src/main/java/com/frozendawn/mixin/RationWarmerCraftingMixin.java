package com.frozendawn.mixin;

import com.frozendawn.recipe.RationWarmerChargingRecipe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla removes one per occupied slot; charging instead pays exactly one dust per added charge. */
@Mixin(ResultSlot.class)
public abstract class RationWarmerCraftingMixin {
    @Shadow @Final private CraftingContainer craftSlots;
    @Unique private int[] frozendawn$chargingConsumption;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void frozendawn$prepareChargingConsumption(Player player, ItemStack result, CallbackInfo ci) {
        frozendawn$chargingConsumption = null;
        var positioned = craftSlots.asPositionedCraftInput();
        var input = positioned.input();
        var holder = player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level());
        if (holder.isEmpty() || !(holder.get().value() instanceof RationWarmerChargingRecipe charging)) return;
        int[] amounts = charging.consumption(input);
        if (amounts == null) return;
        frozendawn$chargingConsumption = new int[craftSlots.getContainerSize()];
        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                int slot = positioned.left() + column + (positioned.top() + row) * craftSlots.getWidth();
                frozendawn$chargingConsumption[slot] = amounts[column + row * input.width()];
            }
        }
    }

    @Redirect(method = "onTake", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/CraftingContainer;removeItem(II)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack frozendawn$consumeChargingIngredients(CraftingContainer container, int slot, int vanillaAmount) {
        int amount = frozendawn$chargingConsumption == null ? vanillaAmount : frozendawn$chargingConsumption[slot];
        return amount == 0 ? ItemStack.EMPTY : container.removeItem(slot, amount);
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void frozendawn$finishChargingConsumption(Player player, ItemStack result, CallbackInfo ci) {
        frozendawn$chargingConsumption = null;
    }
}
