package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.compat.curios.CuriosCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Data-driven detection for active ORSA equipment carried or equipped by a player.
 */
public final class OrsaEquipmentDetector {
    public static final TagKey<Item> ORSA_TECHNOLOGY = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "orsa_technology"));

    private OrsaEquipmentDetector() {
    }

    public static boolean hasOrsaTechnology(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isOrsaTechnology(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return CuriosCompat.isItemEquipped(player, OrsaEquipmentDetector::isOrsaTechnology);
    }

    static boolean isOrsaTechnology(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ORSA_TECHNOLOGY);
    }
}
