package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** Portable module: its switch and prepaid half-joule travel with the item. */
public final class SuitHeadlampItem extends Item {
    public SuitHeadlampItem() {
        super(new Properties().stacksTo(1).component(ModDataComponents.HEADLAMP_ENABLED.get(),false)
                .component(ModDataComponents.HEADLAMP_CREDIT.get(),0));
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flag) {
        text.add(Component.translatable(stack.getOrDefault(ModDataComponents.HEADLAMP_ENABLED.get(),false)
                ?"tooltip.frozendawn.headlamp.on":"tooltip.frozendawn.headlamp.off").withStyle(ChatFormatting.AQUA));
        text.add(Component.translatable("tooltip.frozendawn.headlamp.equipment").withStyle(ChatFormatting.GRAY));
        text.add(Component.translatable("tooltip.frozendawn.headlamp.power").withStyle(ChatFormatting.GRAY));
        CuriosAccessoryTooltip.appendRequirement(text);
    }
}
