package com.frozendawn.item;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.WinConditionState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Acheronite Compass: points toward the crashed ORSA satellite.
 * Uses the LodestoneTracker data component so vanilla compass rendering handles the needle.
 * Server-side inventoryTick writes the satellite position into the item's data;
 * if win condition is disabled or position not yet chosen, the tracker is cleared
 * so the compass spins aimlessly (same behavior as compass in the Nether).
 */
public class AcheroniteCompassItem extends Item {

    private static final String ENGRAVING_SEEN_TAG = "engraving_seen";

    public AcheroniteCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Only update every 20 ticks to avoid per-tick overhead
        if (level.getGameTime() % 20 != 0) return;

        ServerLevel overworld = serverLevel.getServer().overworld();
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);

        if (!FrozenDawnConfig.ENABLE_WIN_CONDITION.get()) {
            // Win condition disabled — clear tracker so compass spins
            if (current != null) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            return;
        }

        WinConditionState winState = WinConditionState.get(serverLevel.getServer());
        BlockPos satellitePos = winState.getSatellitePos();

        if (satellitePos == null) {
            // No satellite yet — spin
            if (current != null) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            return;
        }

        // Set lodestone tracker pointing to satellite in overworld
        // tracked=false so it doesn't validate a lodestone block at the position
        GlobalPos target = GlobalPos.of(Level.OVERWORLD, satellitePos);
        if (current == null || current.target().isEmpty()
                || !current.target().get().equals(target)) {
            stack.set(DataComponents.LODESTONE_TRACKER,
                    new LodestoneTracker(Optional.of(target), false));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Enchantment glint when tracking a target
        return stack.has(DataComponents.LODESTONE_TRACKER) || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        markEngravingSeen(stack);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.METAL_HIT, SoundSource.PLAYERS, 0.45f, 1.75f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 0.35f, 1.4f);
            serverPlayer.sendSystemMessage(Component.literal("You turn the compass over.")
                    .withStyle(ChatFormatting.GRAY));
            serverPlayer.sendSystemMessage(Component.literal("INNER CASING ETCHING")
                    .withStyle(ChatFormatting.AQUA));
            serverPlayer.sendSystemMessage(Component.literal("1.3.1 | 1.1.7 | 2.4.1 | 2.2.4 | 1.1.10 | 1.4.3")
                    .withStyle(ChatFormatting.DARK_AQUA));
            serverPlayer.sendSystemMessage(Component.literal("1.2.6 | 1.1.1 | 3.2.1 | 1.2.1 | -- | --")
                    .withStyle(ChatFormatting.DARK_AQUA));
            serverPlayer.sendSystemMessage(Component.literal("ORDER MATTERS.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (hasSeenEngraving(stack)) {
            tooltip.add(Component.literal("Inner casing engraving recorded.")
                    .withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.literal("Sneak-use to inspect again.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.literal("Something scratches against the backplate.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return super.getDescriptionId(stack);
    }

    private static boolean hasSeenEngraving(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(ENGRAVING_SEEN_TAG);
    }

    private static void markEngravingSeen(ItemStack stack) {
        CompoundTag tag;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            tag = existing.copyTag();
        } else {
            tag = new CompoundTag();
        }
        tag.putBoolean(ENGRAVING_SEEN_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
