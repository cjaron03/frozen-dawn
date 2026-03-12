package com.frozendawn.item;

import com.frozendawn.world.AcheronForgeRegistry;
import com.frozendawn.world.GeothermalCoreRegistry;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TransponderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SurveyorLensItem extends Item {

    private static final double MAX_RANGE = 48.0D;
    private static final double MAX_RANGE_SQR = MAX_RANGE * MAX_RANGE;
    private static final int COOLDOWN_TICKS = 20;
    private static final int MAX_MARKERS = 8;

    public SurveyorLensItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            List<HeatSignature> signatures = collectHeatSignatures(serverLevel, serverPlayer);

            if (signatures.isEmpty()) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.frozendawn.surveyor_lens.none")
                                .withStyle(ChatFormatting.GRAY),
                        true
                );
            } else {
                HeatSignature nearest = signatures.getFirst();
                MutableComponent message = signatures.size() == 1
                        ? Component.translatable(
                                "message.frozendawn.surveyor_lens.detected",
                                nearest.displayName(),
                                nearest.distanceBlocks(),
                                nearest.direction()
                        )
                        : Component.translatable(
                                "message.frozendawn.surveyor_lens.detected_many",
                                signatures.size(),
                                nearest.displayName(),
                                nearest.distanceBlocks(),
                                nearest.direction()
                        );

                serverPlayer.displayClientMessage(message.withStyle(ChatFormatting.AQUA), true);
                markHeatSources(serverLevel, serverPlayer, signatures);
            }

            serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static List<HeatSignature> collectHeatSignatures(ServerLevel level, ServerPlayer player) {
        List<HeatSignature> signatures = new ArrayList<>();

        for (BlockPos pos : HeaterRegistry.getHeaters(level)) {
            addSignature(level, player, signatures, pos, HeatSourceType.THERMAL_HEATER);
        }

        for (BlockPos pos : GeothermalCoreRegistry.getCores(level)) {
            addSignature(level, player, signatures, pos, HeatSourceType.GEOTHERMAL_CORE);
        }

        for (BlockPos pos : TransponderRegistry.getTransponders(level)) {
            addSignature(level, player, signatures, pos, HeatSourceType.TRANSPONDER);
        }

        for (BlockPos pos : AcheronForgeRegistry.getForges(level)) {
            addSignature(level, player, signatures, pos, HeatSourceType.ACHERON_FORGE);
        }

        signatures.sort(Comparator.comparingInt(HeatSignature::distanceBlocks));
        return signatures;
    }

    private static void addSignature(ServerLevel level, ServerPlayer player, List<HeatSignature> signatures,
                                     BlockPos pos, HeatSourceType sourceType) {
        double distanceSqr = player.position().distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (distanceSqr > MAX_RANGE_SQR) {
            return;
        }

        int distanceBlocks = Mth.floor(Math.sqrt(distanceSqr));
        Component direction = describeDirection(player, pos);
        signatures.add(new HeatSignature(pos.immutable(), sourceType, distanceBlocks, direction));
    }

    private static Component describeDirection(ServerPlayer player, BlockPos pos) {
        double dx = pos.getX() + 0.5D - player.getX();
        double dy = pos.getY() + 0.5D - player.getY();
        double dz = pos.getZ() + 0.5D - player.getZ();

        String horizontal = horizontalDirection(dx, dz);
        String vertical = "";
        if (dy >= 6.0D) {
            vertical = ", above";
        } else if (dy <= -6.0D) {
            vertical = ", below";
        }

        return Component.literal(horizontal + vertical);
    }

    private static String horizontalDirection(double dx, double dz) {
        String northSouth = dz < -2.0D ? "north" : dz > 2.0D ? "south" : "";
        String eastWest = dx > 2.0D ? "east" : dx < -2.0D ? "west" : "";

        if (!northSouth.isEmpty() && !eastWest.isEmpty()) {
            return northSouth + eastWest;
        }
        if (!northSouth.isEmpty()) {
            return northSouth;
        }
        if (!eastWest.isEmpty()) {
            return eastWest;
        }
        return "nearby";
    }

    private static void markHeatSources(ServerLevel level, ServerPlayer player, List<HeatSignature> signatures) {
        int markers = Math.min(MAX_MARKERS, signatures.size());
        for (int i = 0; i < markers; i++) {
            HeatSignature signature = signatures.get(i);
            double x = signature.pos().getX() + 0.5D;
            double y = signature.pos().getY() + 1.05D;
            double z = signature.pos().getZ() + 0.5D;
            level.sendParticles(player, signature.sourceType().markerParticle(), true, x, y, z, 10, 0.18D, 0.18D, 0.18D, 0.003D);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.surveyor_lens")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.frozendawn.surveyor_lens.use")
                .withStyle(ChatFormatting.AQUA));
    }

    private enum HeatSourceType {
        THERMAL_HEATER("block.frozendawn.thermal_heater", ParticleTypes.FLAME),
        GEOTHERMAL_CORE("block.frozendawn.geothermal_core", ParticleTypes.SOUL_FIRE_FLAME),
        TRANSPONDER("block.frozendawn.transponder", ParticleTypes.END_ROD),
        ACHERON_FORGE("block.frozendawn.acheron_forge", ParticleTypes.ENCHANT);

        private final String translationKey;
        private final ParticleOptions markerParticle;

        HeatSourceType(String translationKey, ParticleOptions markerParticle) {
            this.translationKey = translationKey;
            this.markerParticle = markerParticle;
        }

        public Component displayName() {
            return Component.translatable(translationKey);
        }

        public ParticleOptions markerParticle() {
            return markerParticle;
        }
    }

    private record HeatSignature(BlockPos pos, HeatSourceType sourceType, int distanceBlocks, Component direction) {
        Component displayName() {
            return sourceType.displayName();
        }
    }
}
