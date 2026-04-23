package com.frozendawn.item;

import com.frozendawn.data.SanityState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class MirroredFragmentItem extends Item {

    private static final int SANITY_RESTORE = 12000; // 10 minutes of isolation ticks

    public MirroredFragmentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (BoardPacketItem.tryRevealHeldPacket(level, player, hand)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            SanityState state = SanityState.get(serverPlayer.getServer());
            int current = state.getIsolationTicks(serverPlayer.getUUID());

            if (current <= 0) {
                // No sanity to restore — don't consume
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.mirrored_fragment.no_effect")
                                .withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.pass(stack);
            }

            // Restore sanity
            int restored = Math.min(current, SANITY_RESTORE);
            state.setIsolationTicks(serverPlayer.getUUID(), current - restored);

            // Consume the item
            stack.shrink(1);

            // Glass shatter sound + amethyst chime
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 1.5f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);

            // Mirror-shard particles
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        20, 0.5, 0.8, 0.5, 0.05);
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        player.getX(), player.getY() + 1.5, player.getZ(),
                        15, 0.4, 0.6, 0.4, 0.5);
            }

            player.displayClientMessage(
                    Component.translatable("message.frozendawn.mirrored_fragment.used")
                            .withStyle(ChatFormatting.AQUA), true);

            return InteractionResultHolder.consume(stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.mirrored_fragment")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.frozendawn.mirrored_fragment.reflection")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.frozendawn.mirrored_fragment.use")
                .withStyle(ChatFormatting.AQUA));
    }
}
