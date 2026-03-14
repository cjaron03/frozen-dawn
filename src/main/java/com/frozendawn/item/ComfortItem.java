package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ComfortItem extends Item {

    private final String tooltipKey;
    private final boolean isWilson;

    public ComfortItem(Properties properties, String tooltipKey) {
        this(properties, tooltipKey, false);
    }

    public ComfortItem(Properties properties, String tooltipKey, boolean isWilson) {
        super(properties);
        this.tooltipKey = tooltipKey;
        this.isWilson = isWilson;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        if (isWilson && player instanceof ServerPlayer serverPlayer) {
            // Don't grant advancement immediately — track the item entity and check
            // if it enters lava. If it does, suppress "WILSON!" and schedule a
            // delayed sanity whisper instead. See EasterEggHandler.
            // The ItemEntity hasn't been created yet at this point, so we need to
            // defer tracking via an entity join event in EasterEggHandler.
            serverPlayer.getPersistentData().putBoolean("frozendawn:wilson_just_dropped", true);
        }
        return super.onDroppedByPlayer(item, player);
    }
}
