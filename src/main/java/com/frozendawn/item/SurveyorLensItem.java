package com.frozendawn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class SurveyorLensItem extends Item {

    private static final int COOLDOWN_TICKS = 20;
    private final SurveyorLensScanner.LensProfile lensProfile;

    public SurveyorLensItem(Properties properties, SurveyorLensScanner.LensProfile lensProfile) {
        super(properties);
        this.lensProfile = lensProfile;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            List<SurveyorLensScanner.HeatSignature> signatures = SurveyorLensScanner.collectHeatSignatures(
                    serverLevel,
                    serverPlayer.position(),
                    serverPlayer.blockPosition(),
                    lensProfile
            );

            if (signatures.isEmpty()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.frozendawn.surveyor_lens.none")
                                .withStyle(ChatFormatting.GRAY),
                        true
                );
            } else {
                SurveyorLensScanner.HeatSignature primary = signatures.getFirst();
                MutableComponent message = signatures.size() == 1
                        ? Component.translatable(
                                "message.frozendawn.surveyor_lens.detected",
                                primary.displayName(),
                                primary.distanceBlocks(),
                                primary.direction()
                        )
                        : Component.translatable(
                                "message.frozendawn.surveyor_lens.detected_many",
                                signatures.size(),
                                primary.displayName(),
                                primary.distanceBlocks(),
                                primary.direction()
                        );

                serverPlayer.displayClientMessage(message.withStyle(ChatFormatting.AQUA), true);
                markHeatSources(serverLevel, serverPlayer, signatures);
            }

            serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void markHeatSources(ServerLevel level, ServerPlayer player, List<SurveyorLensScanner.HeatSignature> signatures) {
        int markers = Math.min(lensProfile.maxMarkers(), signatures.size());
        for (int i = 0; i < markers; i++) {
            SurveyorLensScanner.HeatSignature signature = signatures.get(i);
            double x = signature.pos().getX() + 0.5D;
            double y = signature.pos().getY() + 1.05D;
            double z = signature.pos().getZ() + 0.5D;
            level.sendParticles(player, signature.sourceType().markerParticle(), true, x, y, z, 10, 0.18D, 0.18D, 0.18D, 0.003D);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(lensProfile.tooltipKey())
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(lensProfile.tooltipUseKey())
                .withStyle(ChatFormatting.AQUA));
    }

    public SurveyorLensScanner.LensProfile lensProfile() {
        return lensProfile;
    }
}
