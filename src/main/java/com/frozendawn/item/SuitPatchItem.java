package com.frozendawn.item;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.event.SuitIntegrityHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/** Hold-to-use EVA repair material. ORSA kits seal permanently; scrap may reopen. */
public final class SuitPatchItem extends Item {

    private final boolean permanent;

    public SuitPatchItem(Properties properties, boolean permanent) {
        super(properties);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return permanent
                ? FrozenDawnConfig.ORSA_PATCH_DURATION_TICKS.get()
                : FrozenDawnConfig.IMPROVISED_PATCH_DURATION_TICKS.get();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()
                && entity instanceof ServerPlayer player
                && SuitIntegrityHandler.completePatch(player, permanent)) {
            stack.consume(1, player);
        }
        return stack;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(permanent
                        ? "tooltip.frozendawn.suit_patch.permanent"
                        : "tooltip.frozendawn.suit_patch.improvised")
                .withStyle(permanent ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.frozendawn.suit_patch.channel")
                .withStyle(ChatFormatting.GRAY));
    }
}
