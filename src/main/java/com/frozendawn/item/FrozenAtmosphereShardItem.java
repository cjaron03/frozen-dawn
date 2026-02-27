package com.frozendawn.item;

import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Frozen Atmosphere Shard: sublimates from player inventory if player
 * temperature is above -150C. Has a 150-tick (7.5s) grace period before
 * sublimation. Timer pauses when stored in a Thermal Container (since
 * inventoryTick is not called for nested items).
 */
public class FrozenAtmosphereShardItem extends Item {

    private static final float SUBLIMATION_TEMP = -150f;
    private static final int GRACE_TICKS = 150;

    public FrozenAtmosphereShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        float temp = WorldTickHandler.getLastTemperature(player.getUUID());

        if (temp > SUBLIMATION_TEMP) {
            // Warm — advance grace timer
            int ticks = stack.getOrDefault(ModDataComponents.SUBLIMATION_TICKS.get(), 0) + 1;
            stack.set(ModDataComponents.SUBLIMATION_TICKS.get(), ticks);

            // Cracking particle effects as timer progresses
            if (ticks % 20 == 0 && ticks < GRACE_TICKS) {
                ServerLevel serverLevel = (ServerLevel) level;
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        3 + (ticks / 30), 0.3, 0.3, 0.3, 0.02);
            }

            // Grace period expired — sublimate
            if (ticks >= GRACE_TICKS) {
                ServerLevel serverLevel = (ServerLevel) level;
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        10, 0.4, 0.4, 0.4, 0.05);
                level.playSound(null, player.blockPosition(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.5f);
                player.displayClientMessage(
                        Component.translatable("message.frozendawn.shard_sublimated"), true);
                stack.shrink(1);
            }
        } else {
            // Cold enough — reset timer
            if (stack.has(ModDataComponents.SUBLIMATION_TICKS.get())) {
                stack.remove(ModDataComponents.SUBLIMATION_TICKS.get());
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Shimmer effect when sublimation timer is active
        return stack.getOrDefault(ModDataComponents.SUBLIMATION_TICKS.get(), 0) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("\u2744 Sublimates above -150\u00B0C")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("  Store in Thermal Container to preserve.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        int ticks = stack.getOrDefault(ModDataComponents.SUBLIMATION_TICKS.get(), 0);
        if (ticks > 0) {
            int remaining = Math.max(0, GRACE_TICKS - ticks);
            float seconds = remaining / 20f;
            tooltip.add(Component.literal(String.format("\u26A0 Sublimating in %.1fs", seconds))
                    .withStyle(ChatFormatting.RED));
        }
    }
}
