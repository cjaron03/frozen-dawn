package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.power.*;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

/** Selection lives on the individual tool and is dimension-qualified, including across reloads. */
public final class ConduitBinderItem extends Item {
    public ConduitBinderItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        var level = player.serverLevel(); var pos = context.getClickedPos(); var stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            int removed = PowerNetworks.clear(level, pos); stack.remove(ModDataComponents.CONDUIT_ANCHOR);
            tell(player, "cleared", removed); return InteractionResult.CONSUME;
        }
        if (PowerLinkRules.range(level.getBlockState(pos)) == 0) {
            tell(player, "not_node"); return InteractionResult.CONSUME;
        }
        var anchor = stack.get(ModDataComponents.CONDUIT_ANCHOR);
        if (anchor == null || anchor.dimension() != level.dimension()) {
            stack.set(ModDataComponents.CONDUIT_ANCHOR, GlobalPos.of(level.dimension(), pos));
            tell(player, "selected", pos.getX(), pos.getY(), pos.getZ()); return InteractionResult.CONSUME;
        }
        var result = PowerNetworks.link(level, anchor.pos(), pos);
        if (result == PowerLinkRules.Result.OK || result == PowerLinkRules.Result.SELF) stack.remove(ModDataComponents.CONDUIT_ANCHOR);
        if (result == PowerLinkRules.Result.NOT_NODE) stack.remove(ModDataComponents.CONDUIT_ANCHOR);
        tell(player, result.name().toLowerCase(java.util.Locale.ROOT)); return InteractionResult.CONSUME;
    }
    private static void tell(ServerPlayer player, String key, Object... args) {
        int count = key.equals("cleared") && args.length > 0 ? ((Number) args[0]).intValue() : 0;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.frozendawn.network.PowerBinderNoticePayload(key, count));
    }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!stack.has(ModDataComponents.CONDUIT_ANCHOR)) return net.minecraft.world.InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            stack.remove(ModDataComponents.CONDUIT_ANCHOR); tell(serverPlayer, "self");
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozendawn.conduit_binder.controls").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.frozendawn.conduit_binder.range").withStyle(ChatFormatting.AQUA));
        if (stack.has(ModDataComponents.CONDUIT_ANCHOR))
            tooltip.add(Component.translatable("hud.frozendawn.binder.cancel_hint").withStyle(ChatFormatting.YELLOW));
    }
}
