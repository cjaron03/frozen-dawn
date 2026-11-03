package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** Charge travels with the unstackable item, in whole joules (500 kJ capacity). */
public final class PowerCellItem extends Item {
    public static final int CAPACITY_JOULES = 500_000;
    public PowerCellItem(Properties p) { super(p.stacksTo(1)); }
    public static int energy(ItemStack s) { return s.is(ModItems.POWER_CELL) ? Math.clamp(s.getOrDefault(ModDataComponents.CELL_JOULES, 0), 0, CAPACITY_JOULES) : 0; }
    public static void setEnergy(ItemStack s, int joules) {
        if (s.is(ModItems.POWER_CELL)) s.set(ModDataComponents.CELL_JOULES, Math.clamp(joules, 0, CAPACITY_JOULES));
    }
    public static ItemStack charged(int joules) { var s = new ItemStack(ModItems.POWER_CELL.get()); setEnergy(s, joules); return s; }
    @Override public boolean isBarVisible(ItemStack s) { return true; }
    @Override public int getBarWidth(ItemStack s) { return Math.round(13f * energy(s) / CAPACITY_JOULES); }
    @Override public int getBarColor(ItemStack s) { return 0x64C9CC; }
    @Override public void appendHoverText(ItemStack s, TooltipContext c, List<Component> text, TooltipFlag flag) {
        text.add(Component.literal(String.format(Locale.ROOT, "%.1f / 500 kJ", energy(s) / 1000.0)));
        text.add(Component.translatable("item.frozendawn.power_cell.hint"));
    }
}
