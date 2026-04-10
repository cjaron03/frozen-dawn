package com.frozendawn.compat.curios;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Predicate;

public final class CuriosCompat {

    private static final CuriosAccess ACCESS = createAccess();

    private CuriosCompat() {}

    public static boolean isLoaded() {
        return ACCESS.isLoaded();
    }

    public static boolean isCuriosLoaded() {
        return isLoaded();
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        ACCESS.registerCapabilities(event);
    }

    public static boolean isItemEquipped(Player player, Item item) {
        return ACCESS.isItemEquipped(player, item);
    }

    public static boolean isItemEquipped(Player player, Predicate<ItemStack> filter) {
        return ACCESS.isItemEquipped(player, filter);
    }

    public static boolean hasSnowshoesEquipped(Player player) {
        return isItemEquipped(player, ModItems.SNOWSHOES.get());
    }

    public static boolean hasBlizzardGogglesEquipped(Player player) {
        return isItemEquipped(player, ModItems.BLIZZARD_GOGGLES.get());
    }

    public static boolean hasIceClawsEquipped(Player player) {
        return isItemEquipped(player, ModItems.ICE_CLAWS.get());
    }

    private static CuriosAccess createAccess() {
        if (!ModList.get().isLoaded("curios")) {
            return new NoOpCuriosAccess();
        }

        try {
            return (CuriosAccess) Class.forName("com.frozendawn.compat.curios.CuriosLoadedAccess")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            FrozenDawn.LOGGER.error("Failed to initialize Curios compatibility. Falling back to no-op mode.", e);
            return new NoOpCuriosAccess();
        }
    }
}
