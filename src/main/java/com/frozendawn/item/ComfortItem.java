package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
            grantAdvancement(serverPlayer, "wilson_dropped");
        }
        return super.onDroppedByPlayer(item, player);
    }

    private static void grantAdvancement(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name);
        AdvancementHolder holder = server.getAdvancements().get(loc);
        if (holder == null) return;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }
}
