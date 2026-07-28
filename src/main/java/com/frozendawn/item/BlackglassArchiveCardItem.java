package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/** Hearth-held archive card that enters the existing BLACKGLASS clue chain. */
public final class BlackglassArchiveCardItem extends Item {
    private static final String RECOVERED_TAG = "hearth_blackglass_packet_recovered";

    public BlackglassArchiveCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack card = player.getItemInHand(hand);
        if (!level.isClientSide() && !hasRecoveredPacket(card)) {
            ItemStack packet = BoardPacketItem.createHearthRecoveredPacket();
            if (!player.addItem(packet)) {
                player.drop(packet, false);
            }
            markRecovered(card);
            level.playSound(null, player.blockPosition(), SoundEvents.VAULT_OPEN_SHUTTER,
                    SoundSource.PLAYERS, 0.65F, 0.72F);
            player.displayClientMessage(Component.translatable(
                    "message.frozendawn.blackglass_archive_card.recovered")
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return InteractionResultHolder.sidedSuccess(card, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.blackglass_archive_card")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        if (hasRecoveredPacket(stack)) {
            tooltip.add(Component.translatable(
                    "tooltip.frozendawn.blackglass_archive_card.recovered")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean hasRecoveredPacket(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(RECOVERED_TAG);
    }

    private static void markRecovered(ItemStack stack) {
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        tag.putBoolean(RECOVERED_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
