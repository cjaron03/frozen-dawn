package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.AcheronForgeRegistry;
import com.frozendawn.world.GeothermalCoreRegistry;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TransponderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SurveyorLensVision {

    private static final double MAX_RANGE = 48.0D;
    private static final double MAX_RANGE_SQR = MAX_RANGE * MAX_RANGE;
    private static final int SCAN_INTERVAL = 8;
    private static final int MAX_MARKERS = 8;

    private static final List<HeatSignature> cachedSignatures = new ArrayList<>();
    private static float overlayStrength = 0.0F;

    private SurveyorLensVision() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            fadeOut();
            return;
        }

        if (!isLensActive(mc.player.getMainHandItem(), mc.player.getOffhandItem())) {
            cachedSignatures.clear();
            fadeOut();
            return;
        }

        overlayStrength = Math.min(1.0F, overlayStrength + 0.08F);

        long gameTime = mc.level.getGameTime();
        if (gameTime % SCAN_INTERVAL != 0) {
            return;
        }

        cachedSignatures.clear();
        collectSignatures(mc);
        cachedSignatures.sort(Comparator.comparingDouble(HeatSignature::distanceSqr));

        int markers = Math.min(MAX_MARKERS, cachedSignatures.size());
        for (int i = 0; i < markers; i++) {
            HeatSignature signature = cachedSignatures.get(i);
            double x = signature.pos().getX() + 0.5D;
            double y = signature.pos().getY() + 1.05D;
            double z = signature.pos().getZ() + 0.5D;
            mc.level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.01D, 0.0D);
            mc.level.addParticle(ParticleTypes.SMALL_FLAME, x, y + 0.1D, z, 0.0D, 0.01D, 0.0D);
        }
    }

    public static boolean isActive() {
        return overlayStrength > 0.01F;
    }

    public static float getOverlayStrength() {
        return overlayStrength;
    }

    private static void collectSignatures(Minecraft mc) {
        BlockPos playerPos = mc.player.blockPosition();

        for (BlockPos pos : HeaterRegistry.getHeaters(mc.level)) {
            addIfNear(playerPos, pos);
        }
        for (BlockPos pos : GeothermalCoreRegistry.getCores(mc.level)) {
            addIfNear(playerPos, pos);
        }
        for (BlockPos pos : TransponderRegistry.getTransponders(mc.level)) {
            addIfNear(playerPos, pos);
        }
        for (BlockPos pos : AcheronForgeRegistry.getForges(mc.level)) {
            addIfNear(playerPos, pos);
        }
    }

    private static void addIfNear(BlockPos playerPos, BlockPos pos) {
        double distanceSqr = playerPos.distSqr(pos);
        if (distanceSqr <= MAX_RANGE_SQR) {
            cachedSignatures.add(new HeatSignature(pos.immutable(), distanceSqr));
        }
    }

    private static boolean isLensActive(ItemStack mainHand, ItemStack offHand) {
        return mainHand.is(ModItems.SURVEYOR_LENS.get()) || offHand.is(ModItems.SURVEYOR_LENS.get());
    }

    private static void fadeOut() {
        overlayStrength = Math.max(0.0F, overlayStrength - 0.08F);
    }

    private record HeatSignature(BlockPos pos, double distanceSqr) {
    }
}
