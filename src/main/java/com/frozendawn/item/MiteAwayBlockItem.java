package com.frozendawn.item;

import com.frozendawn.block.MiteAwayBlockEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

public class MiteAwayBlockItem extends BlockItem {

    public MiteAwayBlockItem(Properties properties) {
        super(ModBlocks.MITEAWAY.get(), properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.getBlockState(context.getClickedPos()).is(ModBlocks.MITEAWAY.get())
                && isFresh(context.getItemInHand())) {
            if (!level.isClientSide()
                    && level.getBlockEntity(context.getClickedPos()) instanceof MiteAwayBlockEntity burner
                    && burner.refreshToFullCharge()) {
                Player player = context.getPlayer();
                level.playSound(null, context.getClickedPos(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.85f, 1.05f);
                if (player == null || !player.getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.miteaway.use")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.frozendawn.miteaway.radius")
                .withStyle(ChatFormatting.AQUA));

        int remaining = getBurnTicks(stack);
        if (remaining <= 0) {
            tooltip.add(Component.translatable("tooltip.frozendawn.miteaway.spent")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.frozendawn.miteaway.remaining", formatDuration(remaining))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int remaining = getBurnTicks(stack);
        return remaining > 0 && remaining < MiteAwayBlockEntity.MAX_BURN_TICKS;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * getBurnTicks(stack) / MiteAwayBlockEntity.MAX_BURN_TICKS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = (float) getBurnTicks(stack) / MiteAwayBlockEntity.MAX_BURN_TICKS;
        int r = (int) Mth.lerp(1.0f - ratio, 110, 54);
        int g = (int) Mth.lerp(1.0f - ratio, 110, 197);
        int b = (int) Mth.lerp(1.0f - ratio, 110, 197);
        return (r << 16) | (g << 8) | b;
    }

    public static int getBurnTicks(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MITEAWAY_BURN_TICKS.get(), MiteAwayBlockEntity.MAX_BURN_TICKS);
    }

    public static boolean isFresh(ItemStack stack) {
        return getBurnTicks(stack) >= MiteAwayBlockEntity.MAX_BURN_TICKS;
    }

    private static String formatDuration(int ticks) {
        int totalSeconds = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
