package com.frozendawn.item;

import com.frozendawn.event.SuitIntegrityHandler;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Single-use emergency oxygen reserve. It restores air, never suit integrity. */
public final class EmergencyO2CartridgeItem extends Item {

    public EmergencyO2CartridgeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        SuitIntegrityHandler.EmergencyRefillResult result =
                SuitIntegrityHandler.useEmergencyO2Cartridge(serverPlayer);
        if (result == SuitIntegrityHandler.EmergencyRefillResult.NO_SEALED_SUIT) {
            player.displayClientMessage(
                    Component.translatable("message.frozendawn.emergency_o2.requires_eva"),
                    true);
            return InteractionResultHolder.fail(stack);
        }
        if (result == SuitIntegrityHandler.EmergencyRefillResult.NO_CAPACITY) {
            player.displayClientMessage(
                    Component.translatable("message.frozendawn.emergency_o2.requires_canister"),
                    true);
            return InteractionResultHolder.fail(stack);
        }
        if (result == SuitIntegrityHandler.EmergencyRefillResult.FULL) {
            player.displayClientMessage(
                    Component.translatable("message.frozendawn.emergency_o2.full"),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_o2.refill")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_o2.puncture")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.frozendawn.emergency_o2.single_use")
                .withStyle(ChatFormatting.GRAY));
    }
}
