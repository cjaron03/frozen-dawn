package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.item.SurveyorLensScanner;
import com.frozendawn.mixin.GameRendererAccessor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SurveyorLensVision {

    private static final int SCAN_INTERVAL = 8;
    private static final int MAX_SHADER_FIELDS = 6;
    private static final int MAX_COLD_FIELDS = 24;
    private static final int THERMAL_BOOT_TICKS = 42;
    private static final float THERMAL_FADE_IN_STEP = 0.035F;
    private static final float THERMAL_FADE_OUT_STEP = 0.028F;
    private static final ResourceLocation THERMAL_POST_EFFECT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shaders/post/orsa_thermal_v8a.json");
    private static final KeyMapping THERMAL_MODE_KEY = new KeyMapping(
            "key.frozendawn.toggle_thermal_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.frozendawn"
    );

    private static final List<SurveyorLensScanner.HeatSignature> cachedSignatures = new ArrayList<>();
    private static final List<ColdAnchor> cachedColdAnchors = new ArrayList<>();
    private static float overlayStrength = 0.0F;
    private static float thermalModeStrength = 0.0F;
    private static boolean thermalModeEnabled = false;
    private static int thermalBootTicksRemaining = 0;
    private static int thermalShutdownTicksRemaining = 0;

    private SurveyorLensVision() {}

    public static KeyMapping thermalModeKey() {
        return THERMAL_MODE_KEY;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            syncThermalPostEffect(mc, false);
            fadeOut();
            fadeThermal();
            return;
        }

        ItemStack headArmor = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        boolean visorEquipped = headArmor.is(ModItems.ORSA_THERMAL_VISOR.get());
        SurveyorLensScanner.LensProfile heldProfile = SurveyorLensScanner.heldProfile(
                mc.player.getMainHandItem(),
                mc.player.getOffhandItem()
        );
        while (THERMAL_MODE_KEY.consumeClick()) {
            if (visorEquipped) {
                thermalModeEnabled = !thermalModeEnabled;
                if (thermalModeEnabled) {
                    thermalBootTicksRemaining = THERMAL_BOOT_TICKS;
                    thermalShutdownTicksRemaining = 0;
                } else {
                    thermalBootTicksRemaining = 0;
                    thermalShutdownTicksRemaining = THERMAL_BOOT_TICKS;
                }
            }
        }

        boolean visorThermalActive = visorEquipped && (
                thermalModeEnabled
                        || thermalBootTicksRemaining > 0
                        || thermalShutdownTicksRemaining > 0
                        || thermalModeStrength > 0.01F
        );
        SurveyorLensScanner.LensProfile activeProfile = visorThermalActive
                ? SurveyorLensScanner.LensProfile.VISOR
                : heldProfile;

        if (activeProfile == null) {
            cachedSignatures.clear();
            cachedColdAnchors.clear();
            if (!visorEquipped) {
                thermalModeEnabled = false;
                thermalBootTicksRemaining = 0;
                thermalShutdownTicksRemaining = 0;
            }
            syncThermalPostEffect(mc, false);
            fadeOut();
            fadeThermal();
            return;
        }

        overlayStrength = Math.min(1.0F, overlayStrength + 0.08F);
        if (!visorEquipped) {
            thermalModeEnabled = false;
            thermalBootTicksRemaining = 0;
            thermalShutdownTicksRemaining = 0;
        }

        if (thermalModeEnabled) {
            thermalModeStrength = Math.min(1.0F, thermalModeStrength + THERMAL_FADE_IN_STEP);
            if (thermalBootTicksRemaining > 0) {
                thermalBootTicksRemaining--;
            }
            thermalShutdownTicksRemaining = 0;
        } else if (thermalShutdownTicksRemaining > 0) {
            thermalModeStrength = Math.max(0.0F, thermalModeStrength - (THERMAL_FADE_OUT_STEP * 0.78F));
            thermalShutdownTicksRemaining--;
        } else {
            fadeThermal();
        }

        syncThermalPostEffect(mc, thermalModeStrength > 0.01F);

        long gameTime = mc.level.getGameTime();
        if (gameTime % SCAN_INTERVAL != 0) {
            return;
        }

        cachedSignatures.clear();
        cachedSignatures.addAll(SurveyorLensScanner.collectHeatSignatures(
                mc.level,
                mc.player.position(),
                mc.player.blockPosition(),
                activeProfile
        ));
        cachedColdAnchors.clear();
        if (visorEquipped) {
            cachedColdAnchors.addAll(collectColdAnchors(mc));
        }

        int markers = Math.min(activeProfile.maxMarkers(), cachedSignatures.size());
        for (int i = 0; i < markers; i++) {
            SurveyorLensScanner.HeatSignature signature = cachedSignatures.get(i);
            double x = signature.pos().getX() + 0.5D;
            double y = signature.pos().getY() + 1.05D;
            double z = signature.pos().getZ() + 0.5D;
            if (activeProfile != SurveyorLensScanner.LensProfile.VISOR && thermalModeStrength <= 0.05F) {
                mc.level.addParticle(signature.sourceType().markerParticle(), x, y, z, 0.0D, 0.01D, 0.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderThermalSignatures(event);
        }
    }

    public static boolean isActive() {
        return overlayStrength > 0.01F;
    }

    public static float getOverlayStrength() {
        return overlayStrength;
    }

    public static boolean isThermalModeVisible() {
        return thermalModeStrength > 0.01F || thermalBootTicksRemaining > 0 || thermalShutdownTicksRemaining > 0;
    }

    public static float getThermalModeStrength() {
        return easedThermalStrength();
    }

    public static boolean isThermalBooting() {
        return thermalBootTicksRemaining > 0;
    }

    public static boolean isThermalShuttingDown() {
        return thermalShutdownTicksRemaining > 0;
    }

    public static float getThermalBootProgress() {
        if (!isThermalBooting()) {
            return 0.0F;
        }
        return 1.0F - (thermalBootTicksRemaining / (float) THERMAL_BOOT_TICKS);
    }

    public static float getThermalShutdownProgress() {
        if (!isThermalShuttingDown()) {
            return 0.0F;
        }
        return 1.0F - (thermalShutdownTicksRemaining / (float) THERMAL_BOOT_TICKS);
    }

    public static List<SurveyorLensScanner.HeatSignature> getCachedSignatures() {
        return Collections.unmodifiableList(cachedSignatures);
    }

    public static void syncThermalShaderUniforms(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain currentEffect = accessor.frozendawn$getPostEffect();
        if (currentEffect == null || !THERMAL_POST_EFFECT.toString().equals(currentEffect.getName())) {
            return;
        }

        currentEffect.setUniform("ThermalAmount", easedThermalStrength());
        currentEffect.setUniform("AmbientBaseline", ambientBaselineFromTemp(TemperatureHud.getDisplayedTemp()));
        clearThermalShaderFields(currentEffect);
    }

    private static void syncThermalPostEffect(Minecraft mc, boolean shouldEnable) {
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain currentEffect = accessor.frozendawn$getPostEffect();
        boolean thermalEffectActive = currentEffect != null && THERMAL_POST_EFFECT.toString().equals(currentEffect.getName());

        if (shouldEnable) {
            if (!thermalEffectActive) {
                mc.gameRenderer.loadEffect(THERMAL_POST_EFFECT);
                currentEffect = accessor.frozendawn$getPostEffect();
                thermalEffectActive = currentEffect != null && THERMAL_POST_EFFECT.toString().equals(currentEffect.getName());
            }

            if (thermalEffectActive && currentEffect != null) {
                currentEffect.setUniform("ThermalAmount", easedThermalStrength());
                clearThermalShaderFields(currentEffect);
            }
            return;
        }

        if (thermalEffectActive) {
            accessor.frozendawn$shutdownEffect();
        }
    }

    private static void fadeOut() {
        overlayStrength = Math.max(0.0F, overlayStrength - 0.08F);
    }

    private static void fadeThermal() {
        thermalModeStrength = Math.max(0.0F, thermalModeStrength - THERMAL_FADE_OUT_STEP);
    }

    private static float easedThermalStrength() {
        float t = Mth.clamp(thermalModeStrength, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float ambientBaselineFromTemp(float ambientTemp) {
        float normalized = Mth.clamp((ambientTemp + 200.0F) / 260.0F, 0.0F, 1.0F);
        return Mth.lerp(normalized, 0.03F, 0.16F);
    }

    private static float displayHeat(SurveyorLensScanner.HeatSignature signature) {
        float heatValue = signature.heatValue();
        return switch (signature.sourceType()) {
            case GEOTHERMAL_CORE -> {
                float actual = displayHeatForTemperature(Math.max(50.0F, heatValue));
                yield actual * 1.08F;
            }
            case THERMAL_HEATER -> {
                yield displayHeatForTemperature(Math.max(35.0F, heatValue));
            }
            case LAVA -> {
                yield displayHeatForTemperature(Math.max(30.0F, heatValue));
            }
            case ACHERON_FORGE -> displayHeatForTemperature(95.0F);
            case SOUL_CAMPFIRE -> displayHeatForTemperature(28.0F);
            case CAMPFIRE -> displayHeatForTemperature(25.0F);
            case SOUL_FIRE -> displayHeatForTemperature(22.0F);
            case FIRE -> displayHeatForTemperature(20.0F);
            case ACHERONITE_BLOCK -> displayHeatForTemperature(10.0F) * 0.90F;
            case TRANSPONDER -> 0.22F;
            case SOUL_LANTERN -> displayHeatForTemperature(18.0F) * 0.82F;
            case LANTERN -> displayHeatForTemperature(16.0F) * 0.76F;
            case SOUL_TORCH -> displayHeatForTemperature(17.0F) * 0.78F;
            case TORCH -> displayHeatForTemperature(15.0F) * 0.72F;
        };
    }

    private static Vec3 heatSourceWorldPos(SurveyorLensScanner.HeatSignature signature) {
        double x = signature.pos().getX() + 0.5D;
        double z = signature.pos().getZ() + 0.5D;
        double y = switch (signature.sourceType()) {
            case GEOTHERMAL_CORE -> signature.pos().getY() + 1.01D;
            case TRANSPONDER -> signature.pos().getY() + 0.08D;
            case ACHERON_FORGE -> signature.pos().getY() + 1.01D;
            case THERMAL_HEATER -> signature.pos().getY() + 1.01D;
            case ACHERONITE_BLOCK -> signature.pos().getY() + 1.01D;
            case LAVA -> signature.pos().getY() + 0.93D;
            case SOUL_FIRE, FIRE -> signature.pos().getY() + 0.08D;
            case SOUL_CAMPFIRE, CAMPFIRE -> signature.pos().getY() + 0.46D;
            case SOUL_LANTERN, LANTERN -> signature.pos().getY() + 0.08D;
            case SOUL_TORCH, TORCH -> signature.pos().getY() + 0.08D;
        };
        return new Vec3(x, y, z);
    }

    private static float displayHeatForTemperature(float heatValue) {
        if (heatValue <= 0.0F) {
            return 0.0F;
        }
        if (heatValue <= 10.0F) {
            return lerpHeatBand(heatValue, 0.0F, 10.0F, 0.08F, 0.20F);
        }
        if (heatValue <= 25.0F) {
            return lerpHeatBand(heatValue, 10.0F, 25.0F, 0.20F, 0.40F);
        }
        if (heatValue <= 35.0F) {
            return lerpHeatBand(heatValue, 25.0F, 35.0F, 0.40F, 0.58F);
        }
        if (heatValue <= 50.0F) {
            return lerpHeatBand(heatValue, 35.0F, 50.0F, 0.58F, 0.78F);
        }
        if (heatValue <= 65.0F) {
            return lerpHeatBand(heatValue, 50.0F, 65.0F, 0.78F, 0.98F);
        }
        if (heatValue <= 80.0F) {
            return lerpHeatBand(heatValue, 65.0F, 80.0F, 0.98F, 1.34F);
        }
        if (heatValue <= 100.0F) {
            return lerpHeatBand(heatValue, 80.0F, 100.0F, 1.34F, 1.58F);
        }
        return lerpHeatBand(Math.min(heatValue, 120.0F), 100.0F, 120.0F, 1.58F, 1.84F);
    }

    private static float lerpHeatBand(float heatValue, float startHeat, float endHeat, float startDisplay, float endDisplay) {
        float normalized = Mth.clamp((heatValue - startHeat) / Math.max(0.001F, endHeat - startHeat), 0.0F, 1.0F);
        float eased = normalized * normalized * (3.0F - 2.0F * normalized);
        return Mth.lerp(eased, startDisplay, endDisplay);
    }

    private static void clearThermalShaderFields(PostChain currentEffect) {
        for (int i = 0; i < MAX_SHADER_FIELDS; i++) {
            currentEffect.setUniform("HeatField" + i + "X", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Y", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Radius", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Intensity", 0.0F);
        }
        for (int i = 0; i < MAX_COLD_FIELDS; i++) {
            currentEffect.setUniform("ColdField" + i + "X", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Y", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Radius", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Intensity", 0.0F);
        }
    }

    private static List<ColdAnchor> collectColdAnchors(Minecraft mc) {
        List<ColdAnchor> anchors = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int dy = -12; dy <= 12; dy++) {
            int y = playerPos.getY() + dy;
            if (y < mc.level.getMinBuildHeight() || y >= mc.level.getMaxBuildHeight()) {
                continue;
            }

            for (int dz = -24; dz <= 24; dz++) {
                for (int dx = -24; dx <= 24; dx++) {
                    mutablePos.set(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    if (!mc.level.hasChunkAt(mutablePos)) {
                        continue;
                    }

                    var state = mc.level.getBlockState(mutablePos);
                    if (!state.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        continue;
                    }

                    float strength = switch (state.getValue(AcheroniteCrystalBlock.AGE)) {
                        case 0 -> 0.55F;
                        case 1 -> 0.82F;
                        case 2 -> 1.08F;
                        default -> 1.32F;
                    };
                    addColdAnchor(anchors, mutablePos, strength);
                }
            }
        }

        anchors.sort((left, right) -> Float.compare(right.strength(), left.strength()));
        if (anchors.size() > MAX_COLD_FIELDS) {
            return new ArrayList<>(anchors.subList(0, MAX_COLD_FIELDS));
        }
        return anchors;
    }

    private static void addColdAnchor(List<ColdAnchor> anchors, BlockPos pos, float strength) {
        int clusterRadius = 3;
        int clusterRadiusSqr = clusterRadius * clusterRadius;
        ColdAnchor candidate = new ColdAnchor(pos.immutable(), strength);

        for (int i = 0; i < anchors.size(); i++) {
            ColdAnchor existing = anchors.get(i);
            if (existing.pos().distSqr(pos) <= clusterRadiusSqr) {
                if (candidate.strength() > existing.strength()) {
                    anchors.set(i, candidate);
                }
                return;
            }
        }

        anchors.add(candidate);
    }

    private static void renderThermalSignatures(RenderLevelStageEvent event) {
        if (!isThermalModeVisible() || thermalModeStrength <= 0.01F) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || (cachedSignatures.isEmpty() && cachedColdAnchors.isEmpty())) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        poseStack.pushPose();

        BufferBuilder heatBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean renderedHeat = false;
        int heatCount = Math.min(MAX_SHADER_FIELDS, cachedSignatures.size());
        for (int i = 0; i < heatCount; i++) {
            SurveyorLensScanner.HeatSignature signature = cachedSignatures.get(i);
            renderedHeat |= addHeatSignatureQuad(heatBuffer, poseStack, event, cameraPos, signature, i == 0);
        }
        if (renderedHeat) {
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            BufferUploader.drawWithShader(heatBuffer.buildOrThrow());
        }

        BufferBuilder coldBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean renderedCold = false;
        int coldCount = Math.min(MAX_COLD_FIELDS, cachedColdAnchors.size());
        for (int i = 0; i < coldCount; i++) {
            renderedCold |= addColdAnchorQuad(coldBuffer, poseStack, event, cameraPos, cachedColdAnchors.get(i));
        }
        if (renderedCold) {
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            BufferUploader.drawWithShader(coldBuffer.buildOrThrow());
        }

        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static boolean addHeatSignatureQuad(BufferBuilder buffer, PoseStack poseStack, RenderLevelStageEvent event, Vec3 cameraPos,
                                             SurveyorLensScanner.HeatSignature signature, boolean primary) {
        float displayHeat = displayHeat(signature);
        if (displayHeat <= 0.0F) {
            return false;
        }

        Vec3 worldPos = heatSourceWorldPos(signature);
        float distance = Math.max(1.0F, signature.distanceBlocks());
        float distanceAttenuation = Mth.clamp(1.16F - distance / 90.0F, 0.42F, 1.0F);
        float heatWeight = Mth.clamp(displayHeat / 1.84F, 0.0F, 1.0F);
        float radius = Mth.lerp(heatWeight, 0.56F, primary ? 1.86F : 1.58F)
                * Mth.lerp(heatWeight, 0.98F, 1.18F)
                * distanceAttenuation;
        float intensity = (Mth.lerp(heatWeight, 0.70F, primary ? 1.82F : 1.48F)
                + Math.max(0.0F, displayHeat - 0.90F) * 0.92F)
                * easedThermalStrength();
        AABB bounds = new AABB(worldPos, worldPos).inflate(radius);
        if (!event.getFrustum().isVisible(bounds)) {
            return false;
        }

        HeatRenderStyle style = heatRenderStyle(signature.sourceType(), displayHeat, primary);
        return renderHeatSplat(buffer, poseStack, event, cameraPos, worldPos, radius, style.outerColor(), style.midColor(), style.coreColor(),
                style.outerAlpha() * intensity, style.midAlpha() * intensity, style.coreAlpha() * intensity);
    }

    private static boolean addColdAnchorQuad(BufferBuilder buffer, PoseStack poseStack, RenderLevelStageEvent event, Vec3 cameraPos,
                                          ColdAnchor anchor) {
        Vec3 worldPos = new Vec3(anchor.pos().getX() + 0.5D, anchor.pos().getY() + 0.86D, anchor.pos().getZ() + 0.5D);
        float distance = (float) Math.sqrt(cameraPos.distanceToSqr(worldPos));
        float distanceAttenuation = Mth.clamp(1.10F - distance / 64.0F, 0.38F, 1.0F);
        float clampedStrength = Mth.clamp(anchor.strength(), 0.0F, 1.4F);
        float radius = Mth.lerp(Math.min(clampedStrength, 1.0F), 0.52F, 1.12F) * distanceAttenuation;
        float intensity = Mth.lerp(Math.min(clampedStrength, 1.0F), 0.34F, 0.78F) * easedThermalStrength();
        AABB bounds = new AABB(worldPos, worldPos).inflate(radius);
        if (!event.getFrustum().isVisible(bounds)) {
            return false;
        }

        return renderHeatSplat(buffer, poseStack, event, cameraPos, worldPos, radius,
                0x03050B, 0x0A1732, 0x173A72,
                0.34F * intensity, 0.48F * intensity, 0.56F * intensity);
    }

    private static HeatRenderStyle heatRenderStyle(SurveyorLensScanner.HeatSourceType sourceType, float displayHeat, boolean primary) {
        float outerAlpha = (primary ? 0.54F : 0.44F) + (displayHeat * 0.20F);
        float midAlpha = (primary ? 0.80F : 0.64F) + (displayHeat * 0.24F);
        float coreAlpha = (primary ? 1.16F : 0.94F) + (displayHeat * 0.32F);
        return switch (sourceType) {
            case GEOTHERMAL_CORE -> new HeatRenderStyle(
                    0x8C149F,
                    displayHeat >= 1.28F ? 0xFFA63A : 0xFF6A2E,
                    displayHeat >= 1.44F ? 0xFFFFFF : 0xFFF6CA,
                    outerAlpha * 1.20F,
                    midAlpha * 1.34F,
                    coreAlpha * (1.48F + Math.max(0.0F, displayHeat - 1.0F) * 0.52F)
            );
            case THERMAL_HEATER -> new HeatRenderStyle(
                    0x7E119A,
                    displayHeat >= 1.10F ? 0xFF8B1A : 0xFF553A,
                    displayHeat >= 1.24F ? 0xFFFFFF : displayHeat >= 1.00F ? 0xFFF5BA : 0xFFE27A,
                    outerAlpha * 1.20F,
                    midAlpha * 1.34F,
                    coreAlpha * (1.42F + Math.max(0.0F, displayHeat - 0.88F) * 0.72F)
            );
            case LAVA, ACHERON_FORGE -> new HeatRenderStyle(
                    0x8E0E74,
                    displayHeat >= 1.02F ? 0xFF8712 : 0xFF6B21,
                    displayHeat >= 1.22F ? 0xFFFCEC : 0xFFF1A8,
                    outerAlpha * 1.20F,
                    midAlpha * 1.30F,
                    coreAlpha * (1.42F + Math.max(0.0F, displayHeat - 0.90F) * 0.52F)
            );
            case SOUL_FIRE, SOUL_CAMPFIRE, SOUL_LANTERN, SOUL_TORCH -> new HeatRenderStyle(0x3A4CC4, 0x4EC2FF, 0xE5F7FF,
                    outerAlpha * 0.86F, midAlpha * 0.94F, coreAlpha * 0.86F);
            case FIRE, CAMPFIRE, LANTERN, TORCH -> new HeatRenderStyle(0x6C1568, 0xD22A2A, 0xFFB53C,
                    outerAlpha * 0.94F, midAlpha * 1.02F, coreAlpha * 0.94F);
            case ACHERONITE_BLOCK -> new HeatRenderStyle(0x49308A, 0x9A5AE2, 0xE1C6FF,
                    outerAlpha * 0.60F, midAlpha * 0.64F, coreAlpha * 0.52F);
            case TRANSPONDER -> new HeatRenderStyle(0x22538C, 0x4BCBFF, 0xE7FDFF,
                    outerAlpha * 0.52F, midAlpha * 0.58F, coreAlpha * 0.48F);
        };
    }

    private static boolean renderHeatSplat(BufferBuilder buffer, PoseStack poseStack, RenderLevelStageEvent event, Vec3 cameraPos, Vec3 worldPos,
                                           float radius, int outerColor, int midColor, int coreColor,
                                           float outerAlpha, float midAlpha, float coreAlpha) {
        int outerA = alphaInt(outerAlpha);
        int midA = alphaInt(midAlpha);
        int coreA = alphaInt(coreAlpha);
        if (outerA <= 0 && midA <= 0 && coreA <= 0) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(worldPos.x - cameraPos.x, worldPos.y - cameraPos.y, worldPos.z - cameraPos.z);

        Matrix4f pose = poseStack.last().pose();
        boolean emitted = false;
        if (outerA > 0) emitted |= addSplatQuad(buffer, pose, radius * 1.70F, 0.000F, outerColor, outerA);
        if (midA > 0) emitted |= addSplatQuad(buffer, pose, radius * 1.04F, 0.002F, midColor, midA);
        if (coreA > 0) emitted |= addSplatQuad(buffer, pose, radius * 0.54F, 0.004F, coreColor, coreA);
        float wallHeight = Math.min(1.18F, Math.max(0.42F, radius * 1.12F));
        float wallRise = Math.min(0.20F, radius * 0.18F);
        int wallOuterA = alphaInt(outerAlpha * 0.62F);
        int wallMidA = alphaInt(midAlpha * 0.72F);
        int wallCoreA = alphaInt(coreAlpha * 0.80F);
        if (wallOuterA > 0) emitted |= addVerticalCross(buffer, pose, radius * 0.92F, wallHeight, wallRise, outerColor, wallOuterA);
        if (wallMidA > 0) emitted |= addVerticalCross(buffer, pose, radius * 0.62F, wallHeight * 0.86F, wallRise, midColor, wallMidA);
        if (wallCoreA > 0) emitted |= addVerticalCross(buffer, pose, radius * 0.30F, wallHeight * 0.72F, wallRise, coreColor, wallCoreA);
        poseStack.popPose();
        return emitted;
    }

    private static boolean addSplatQuad(VertexConsumer consumer, Matrix4f pose, float radius, float yOffset, int rgb, int a) {
        if (radius <= 0.0001F || a <= 0) {
            return false;
        }

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        consumer.addVertex(pose, -radius, yOffset, -radius).setColor(r, g, b, a);
        consumer.addVertex(pose, radius, yOffset, -radius).setColor(r, g, b, a);
        consumer.addVertex(pose, radius, yOffset, radius).setColor(r, g, b, a);
        consumer.addVertex(pose, -radius, yOffset, radius).setColor(r, g, b, a);
        return true;
    }

    private static boolean addVerticalCross(VertexConsumer consumer, Matrix4f pose, float radius, float depth, float rise, int rgb, int a) {
        boolean emitted = false;
        emitted |= addVerticalQuadX(consumer, pose, radius, depth, rise, rgb, a);
        emitted |= addVerticalQuadZ(consumer, pose, radius, depth, rise, rgb, a);
        return emitted;
    }

    private static boolean addVerticalQuadX(VertexConsumer consumer, Matrix4f pose, float halfWidth, float depth, float rise, int rgb, int a) {
        if (halfWidth <= 0.0001F || depth <= 0.0001F || a <= 0) {
            return false;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        consumer.addVertex(pose, 0.0F, rise, -halfWidth).setColor(r, g, b, a);
        consumer.addVertex(pose, 0.0F, rise, halfWidth).setColor(r, g, b, a);
        consumer.addVertex(pose, 0.0F, -depth, halfWidth).setColor(r, g, b, a);
        consumer.addVertex(pose, 0.0F, -depth, -halfWidth).setColor(r, g, b, a);
        return true;
    }

    private static boolean addVerticalQuadZ(VertexConsumer consumer, Matrix4f pose, float halfWidth, float depth, float rise, int rgb, int a) {
        if (halfWidth <= 0.0001F || depth <= 0.0001F || a <= 0) {
            return false;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        consumer.addVertex(pose, -halfWidth, rise, 0.0F).setColor(r, g, b, a);
        consumer.addVertex(pose, halfWidth, rise, 0.0F).setColor(r, g, b, a);
        consumer.addVertex(pose, halfWidth, -depth, 0.0F).setColor(r, g, b, a);
        consumer.addVertex(pose, -halfWidth, -depth, 0.0F).setColor(r, g, b, a);
        return true;
    }

    private static int alphaInt(float alpha) {
        return Mth.clamp((int) (alpha * 255.0F), 0, 255);
    }

    private record ColdAnchor(BlockPos pos, float strength) {}
    private record HeatRenderStyle(int outerColor, int midColor, int coreColor, float outerAlpha, float midAlpha, float coreAlpha) {}
}
