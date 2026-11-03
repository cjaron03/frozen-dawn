package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Cures raw Longsleeper the way hakarl is made: sealed in a barrel with gravel.
 * Closing a barrel that holds raw Longsleeper and enough gravel starts the
 * timer; opening it after {@link #CURE_TICKS} turns every raw Longsleeper
 * inside into Cured Longsleeper. Adding more raw fish restarts the batch, and
 * taking out the fish or the gravel stops it. Hoppers are not part of it.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class LongsleeperCuringHandler {

    static final int MIN_GRAVEL = 8;
    static final long CURE_TICKS = 3L * 24000L;
    static final String START_KEY = "frozendawn_longsleeper_cure_start";
    static final String COUNT_KEY = "frozendawn_longsleeper_cure_count";

    private LongsleeperCuringHandler() {
    }

    @SubscribeEvent
    public static void onBarrelClose(PlayerContainerEvent.Close event) {
        BarrelBlockEntity barrel = barrelOf(event.getContainer());
        if (barrel == null || barrel.getLevel() == null || barrel.getLevel().isClientSide) return;

        CompoundTag data = barrel.getPersistentData();
        int fish = count(barrel, ModItems.LONGSLEEPER.get());
        if (fish == 0 || count(barrel, Items.GRAVEL) < MIN_GRAVEL) {
            if (data.contains(START_KEY)) {
                data.remove(START_KEY);
                data.remove(COUNT_KEY);
                barrel.setChanged();
            }
            return;
        }

        if (!data.contains(START_KEY) || fish > data.getInt(COUNT_KEY)) {
            data.putLong(START_KEY, barrel.getLevel().getGameTime());
        }
        data.putInt(COUNT_KEY, fish);
        barrel.setChanged();
    }

    @SubscribeEvent
    public static void onBarrelOpen(PlayerContainerEvent.Open event) {
        BarrelBlockEntity barrel = barrelOf(event.getContainer());
        if (barrel == null) return;
        Level level = barrel.getLevel();
        if (level == null || level.isClientSide) return;

        CompoundTag data = barrel.getPersistentData();
        if (!data.contains(START_KEY)) return;
        if (level.getGameTime() - data.getLong(START_KEY) < CURE_TICKS) return;

        if (count(barrel, Items.GRAVEL) >= MIN_GRAVEL) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (stack.is(ModItems.LONGSLEEPER.get())) {
                    barrel.setItem(slot, new ItemStack(ModItems.CURED_LONGSLEEPER.get(), stack.getCount()));
                }
            }
        }
        data.remove(START_KEY);
        data.remove(COUNT_KEY);
        barrel.setChanged();
    }

    private static BarrelBlockEntity barrelOf(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chest && chest.getContainer() instanceof BarrelBlockEntity barrel) {
            return barrel;
        }
        return null;
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
}
