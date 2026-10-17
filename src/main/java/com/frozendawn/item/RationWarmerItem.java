package com.frozendawn.item;

import com.frozendawn.event.RationWarmerHandler;
import com.frozendawn.init.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class RationWarmerItem extends Item {
    public static final int MAX_CHARGES = 4;

    public RationWarmerItem(Properties properties) { super(properties); }

    public static int charges(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(ModDataComponents.RATION_WARMER_CHARGES, 0), 0, MAX_CHARGES);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.fail(stack);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (player instanceof ServerPlayer serverPlayer && RationWarmerHandler.start(serverPlayer)) {
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * charges(stack) / MAX_CHARGES); }
    @Override public int getBarColor(ItemStack stack) { return 0xE35B36; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozendawn.ration_warmer.charges", charges(stack), MAX_CHARGES)
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.frozendawn.ration_warmer.controls").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.frozendawn.ration_warmer.timing").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.frozendawn.ration_warmer.cancel").withStyle(ChatFormatting.DARK_GRAY));
    }
}
