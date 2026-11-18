package com.frozendawn.item;

import com.frozendawn.network.OpenEvacuationNoticePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** CH-38: the UN Emergency Evacuation Notice Kevin left in the player's hand. Right-click to read it. */
public final class EvacuationNoticeItem extends Item {

    public EvacuationNoticeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenEvacuationNoticePayload.INSTANCE);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
