package com.frozendawn.item;

import com.frozendawn.lore.ThaevenLoreManager;
import com.frozendawn.lore.ThaevenRecordId;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A physical record carrier. Possession alone never changes archive state. */
public final class ThaevenCarrierItem extends Item {
    private final ThaevenRecordId record;

    public ThaevenCarrierItem(Properties properties, ThaevenRecordId record) {
        super(properties);
        this.record = record;
    }

    public ThaevenRecordId record() {
        return record;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ThaevenLoreManager.examineCarrier(serverPlayer, record);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozendawn.thaeven_carrier.hint")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
