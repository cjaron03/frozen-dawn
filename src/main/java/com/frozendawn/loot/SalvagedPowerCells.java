package com.frozendawn.loot;

import com.frozendawn.item.PowerCellItem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** One finite, partially charged cell. A separate stream preserves existing structure randomness. */
public final class SalvagedPowerCells {
    public static final int MIN_JOULES = 100_000, MAX_JOULES = 300_000;
    private SalvagedPowerCells() { }
    public static ItemStack create(long cacheSeed) {
        var random = RandomSource.create(cacheSeed ^ 0x53414C564147454CL);
        return PowerCellItem.charged(MIN_JOULES + 1000 * random.nextInt((MAX_JOULES - MIN_JOULES) / 1000 + 1));
    }
}
