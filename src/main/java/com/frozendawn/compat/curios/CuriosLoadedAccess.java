package com.frozendawn.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.function.Predicate;

final class CuriosLoadedAccess implements CuriosAccess {

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Stage-specific Curios capabilities are registered as the related items are added.
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
}
