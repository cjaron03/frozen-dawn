package com.frozendawn.item;

import com.frozendawn.shepherd.ShepherdPolicy;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** CH-38: an ORSA Thermal Blanket. Its only text is the stitched K. */
public final class ThermalBlanketItem extends Item {

    public ThermalBlanketItem(Properties properties) {
        super(properties);
    }

    /** Body warmth while the blanket is anywhere in the player's own inventory: Aurora's heater value, never more. */
    public static float warmth(ServerPlayer player) {
        return player.getInventory().hasAnyMatching(stack -> stack.getItem() instanceof ThermalBlanketItem)
                ? ShepherdPolicy.WARMTH_C : 0f;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.orsa_thermal_blanket.stitch")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
