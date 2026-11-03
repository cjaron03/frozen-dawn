package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Holds up to four spent rods and blocks about 99% of their radiation. Heavy: Slowness I while carried loaded.
 * Fills from a Reactor Controller's output slot. */
public final class ShieldedCaskItem extends Item {
    public static final int CAPACITY = 4;
    public ShieldedCaskItem(Properties p) { super(p); }
    public static int rods(ItemStack s) { return s.getOrDefault(ModDataComponents.CASK_RODS, 0); }
    public static boolean add(ItemStack s) {
        int rods = rods(s); if (rods >= CAPACITY) return false;
        s.set(ModDataComponents.CASK_RODS, rods + 1); return true;
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && rods(stack) > 0 && entity instanceof Player p && level.getGameTime() % 20 == 0)
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false, true));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozendawn.shielded_cask.rods", rods(stack), CAPACITY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.frozendawn.shielded_cask.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
