package com.frozendawn.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.function.Predicate;

final class NoOpCuriosAccess implements CuriosAccess {

    @Override
    public boolean isLoaded() {
        return false;
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Intentionally empty when Curios is unavailable.
    }

    @Override
    public boolean isItemEquipped(Player player, Item item) {
        return false;
    }

    @Override
    public boolean isItemEquipped(Player player, Predicate<ItemStack> filter) {
        return false;
    }
}
