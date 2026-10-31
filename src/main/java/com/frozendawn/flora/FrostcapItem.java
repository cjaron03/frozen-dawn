package com.frozendawn.flora;

import com.frozendawn.init.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * Frozen Frostcap: eaten raw, or planted onto snow, ice or a Rimewood log side. In warmth it spoils, frozen to thawed
 * to mush, five minutes each; {@link Thawed} is the middle step and cannot be planted.
 */
public final class FrostcapItem extends ItemNameBlockItem {
    public FrostcapItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        warmth(stack, lines, "item.frozendawn.frozen_frostcap.thaws");
        lines.add(Component.translatable("item.frozendawn.frozen_frostcap.plant").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Thaws in N min if kept warm", gold once the stack has started warming. */
    static void warmth(ItemStack stack, List<Component> lines, String key) {
        int warm = stack.getOrDefault(ModDataComponents.FROSTCAP_WARMTH.get(), 0);
        int left = Math.max(0, ColdFloraPolicy.THAW_SECONDS - warm);
        lines.add(Component.translatable(key, (left + 59) / 60).withStyle(warm > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    public static final class Thawed extends Item {
        public Thawed(Properties properties) { super(properties); }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            warmth(stack, lines, "item.frozendawn.thawed_frostcap.spoils");
        }
    }
}
