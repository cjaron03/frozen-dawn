package com.frozendawn.client.compat.jei;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public record GeothermalCoreUpgradeDisplay(List<ItemStack> inputs, String slotName, String description) {
}
