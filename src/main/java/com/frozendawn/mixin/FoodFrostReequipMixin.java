package com.frozendawn.mixin;

import com.frozendawn.client.FoodFrostPresentation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ClientHooks.class, remap = false)
public abstract class FoodFrostReequipMixin {
    @Redirect(method = "shouldCauseReequipAnimation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;shouldCauseReequipAnimation(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Z"))
    private static boolean frozendawn$ignoreFoodFrostOnly(Item item, ItemStack from, ItemStack to, boolean slotChanged) {
        return !FoodFrostPresentation.onlyFrostChanged(from, to, slotChanged)
                && item.shouldCauseReequipAnimation(from, to, slotChanged);
    }
}
