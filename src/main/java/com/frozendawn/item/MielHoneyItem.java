package com.frozendawn.item;

import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Jar of Miel: a comfort item eaten a spoonful at a time. Not food, so it never freezes or spoils. */
public final class MielHoneyItem extends ComfortItem {
    public static final int SPOONFULS = 8, NUTRITION = 2, WARM_TICKS = 600, COOLDOWN_TICKS = 20;
    public static final float WARMTH = 5.0f;

    public MielHoneyItem(Properties properties) {
        super(properties, "tooltip.frozendawn.miel_honey");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int left = stack.getOrDefault(ModDataComponents.MIEL_SPOONFULS, SPOONFULS);
        if (left <= 0) return InteractionResultHolder.pass(stack);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.6f, 1.0f);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(stack);
        serverPlayer.getFoodData().eat(NUTRITION, 0.1f);
        serverPlayer.setData(ModAttachments.MIEL_WARM_UNTIL, level.getGameTime() + WARM_TICKS);
        if (left > 1) {
            stack.set(ModDataComponents.MIEL_SPOONFULS, left - 1);
            return InteractionResultHolder.success(stack);
        }
        WorldTickHandler.grantAdvancement(serverPlayer, "still_sweet");
        return InteractionResultHolder.success(new ItemStack(ModItems.EMPTY_MIEL_JAR.get()));
    }

    /** Body warmth from a recent spoonful, added to the player's felt temperature. */
    public static float warmth(ServerPlayer player) {
        return player.getData(ModAttachments.MIEL_WARM_UNTIL) > player.level().getGameTime() ? WARMTH : 0f;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.frozendawn.miel_honey.spoonfuls",
                stack.getOrDefault(ModDataComponents.MIEL_SPOONFULS, SPOONFULS), SPOONFULS).withStyle(ChatFormatting.DARK_GRAY));
        // Tooltips are only built on the client, so the Screen lookup is safe here
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown())
            tooltip.add(Component.translatable("tooltip.frozendawn.miel_honey.dedication").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}
