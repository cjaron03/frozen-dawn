package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

public class ThermalContainerItem extends Item {
    public static final int SLOTS = 8;

    public ThermalContainerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ThermalContainerMenu(id, inv, stack, slot),
                    stack.getHoverName()
            ), buf -> buf.writeInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        int count = 0;
        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
            contents.copyInto(items);
            for (ItemStack item : items) {
                if (!item.isEmpty()) count++;
            }
        }
        tooltip.add(Component.literal(count + "/" + SLOTS + " food items stored")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Insulates food from frost")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
    }
}
