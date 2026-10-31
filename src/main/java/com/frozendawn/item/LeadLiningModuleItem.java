package com.frozendawn.item;

import com.frozendawn.event.SuitIntegrityHandler;
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

/** Lead sheet lining for a sealed EVA suit. Carried anywhere it counts as installed, like the O2 Efficiency Module.
 * Worn under a full sealed suit it halves incoming radiation; it is heavy either way (Slowness I). A real lining
 * that halves fission-product gamma would be several millimetres of lead and far heavier; this is the game cost. */
public final class LeadLiningModuleItem extends Item {
    public static final double RATE_MULTIPLIER = 0.5;
    public LeadLiningModuleItem(Properties p) { super(p); }

    public static boolean isInstalled(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof LeadLiningModuleItem) return true;
        return false;
    }
    /** Radiation multiplier for this player: halved only with the module carried and a full sealed suit worn. */
    public static double rateMultiplier(Player player) {
        return isInstalled(player) && SuitIntegrityHandler.isWearingSealedSuit(player) ? RATE_MULTIPLIER : 1;
    }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && entity instanceof Player p && level.getGameTime() % 20 == 0)
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false, true));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.lead_lining_module").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.frozendawn.lead_lining_module.weight").withStyle(ChatFormatting.DARK_GRAY));
    }
}
