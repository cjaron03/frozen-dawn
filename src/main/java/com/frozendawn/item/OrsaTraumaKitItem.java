package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/** Scarce ORSA field medicine: a committed use animation followed by a large heal. */
public final class OrsaTraumaKitItem extends Item {
    private static final int USE_TICKS = 32;
    private static final float HEAL_AMOUNT = 10.0F;

    public OrsaTraumaKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getHealth() >= player.getMaxHealth()) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) {
            player.heal(HEAL_AMOUNT);
            if (!level.isClientSide()) {
                level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                        SoundSource.PLAYERS, 0.8F, 1.15F);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.orsa_trauma_kit")
                .withStyle(ChatFormatting.RED));
    }
}
