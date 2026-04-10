package com.frozendawn.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.function.Predicate;

interface CuriosAccess {

    boolean isLoaded();

    void registerCapabilities(RegisterCapabilitiesEvent event);

    boolean isItemEquipped(Player player, Item item);

    boolean isItemEquipped(Player player, Predicate<ItemStack> filter);
}
