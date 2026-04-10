package com.frozendawn.compat.curios;

import com.frozendawn.init.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.function.Predicate;

final class CuriosLoadedAccess implements CuriosAccess {

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(CuriosCapability.ITEM, (stack, context) -> new SnowshoesCurio(stack), ModItems.SNOWSHOES.get());
    }

    @Override
    public boolean isItemEquipped(Player player, Item item) {
        return CuriosApi.getCuriosInventory(player)
                .map(inventory -> inventory.isEquipped(item))
                .orElse(false);
    }

    @Override
    public boolean isItemEquipped(Player player, Predicate<ItemStack> filter) {
        return CuriosApi.getCuriosInventory(player)
                .map(inventory -> inventory.isEquipped(filter))
                .orElse(false);
    }

    private record SnowshoesCurio(ItemStack stack) implements ICurio {

        @Override
        public ItemStack getStack() {
            return this.stack;
        }

        @Override
        public boolean canEquipFromUse(SlotContext slotContext) {
            return true;
        }

        @Override
        public boolean canWalkOnPowderedSnow(SlotContext slotContext) {
            return true;
        }
    }
}
