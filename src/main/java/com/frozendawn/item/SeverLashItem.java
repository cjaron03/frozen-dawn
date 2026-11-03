package com.frozendawn.item;

import com.frozendawn.defense.*;
import com.frozendawn.init.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** ORSA LC-4 Sever Lash: hold use to sweep a cutting beam, paid from carried Power Cells. */
public final class SeverLashItem extends Item {
    public SeverLashItem(Properties p) { super(p.stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (stack.getOrDefault(ModDataComponents.LASH_HEAT.get(), LashHeat.COOL).lockedOut(level.getGameTime()))
            return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof ServerPlayer player && !SeverLash.fire(player, stack, level.getGameTime())) player.stopUsingItem();
    }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        SeverLash.forget(entity.getUUID());
    }
    /**
     * Whether the holder is firing this stack, for the {@code frozendawn:firing} model predicate. It asks the hand, not
     * the use: each firing tick the server's heat rewrite replaces the held stack a client tick before the use follows.
     */
    public static boolean firing(LivingEntity holder, ItemStack stack) {
        return holder.isUsingItem() && holder.getItemInHand(holder.getUsedItemHand()) == stack;
    }
    /** The server rewrites the held stack every firing tick as it heats; only a change beyond heat re-equips. */
    @Override public boolean shouldCauseReequipAnimation(ItemStack from, ItemStack to, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItemSameComponents(withoutHeat(from), withoutHeat(to));
    }
    private static ItemStack withoutHeat(ItemStack stack) {
        var copy = stack.copy();
        copy.remove(ModDataComponents.LASH_HEAT.get());
        return copy;
    }
    @Override public void appendHoverText(ItemStack s, TooltipContext c, List<Component> text, TooltipFlag flag) {
        text.add(Component.translatable("item.frozendawn.sever_lash.serial").withStyle(ChatFormatting.DARK_GRAY));
        text.add(Component.translatable("item.frozendawn.sever_lash.label").withStyle(ChatFormatting.GRAY));
        text.add(Component.translatable("item.frozendawn.sever_lash.warning").withStyle(ChatFormatting.RED));
    }
}
