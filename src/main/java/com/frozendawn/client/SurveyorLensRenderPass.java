package com.frozendawn.client;

import com.frozendawn.item.SurveyorLensScanner;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

final class SurveyorLensRenderPass {

    private SurveyorLensRenderPass() {
    }

    static void clearThermalShaderFields(PostChain currentEffect, int maxShaderFields, int maxColdFields) {
        for (int i = 0; i < maxShaderFields; i++) {
            currentEffect.setUniform("HeatField" + i + "X", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Y", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Radius", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Intensity", 0.0F);
        }
        for (int i = 0; i < maxColdFields; i++) {
            currentEffect.setUniform("ColdField" + i + "X", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Y", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Radius", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Intensity", 0.0F);
        }
    }

    static void renderThermalSignatures(
            RenderLevelStageEvent event,
            List<SurveyorLensScanner.HeatSignature> cachedSignatures,
            List<SurveyorLensTargetCollector.ColdAnchor> cachedColdAnchors,
            float thermalModeStrength,
            int maxShaderFields,
            int maxColdFields
    ) {
        if (thermalModeStrength <= 0.01F || (cachedSignatures.isEmpty() && cachedColdAnchors.isEmpty())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
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
        int heatCount = Math.min(maxShaderFields, cachedSignatures.size());
        for (int i = 0; i < heatCount; i++) {
            SurveyorLensScanner.HeatSignature signature = cachedSignatures.get(i);
            renderedHeat |= addHeatSignatureQuad(heatBuffer, poseStack, event, cameraPos, signature, thermalModeStrength, i == 0);
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
        int coldCount = Math.min(maxColdFields, cachedColdAnchors.size());
        for (int i = 0; i < coldCount; i++) {
            renderedCold |= addColdAnchorQuad(coldBuffer, poseStack, event, cameraPos, cachedColdAnchors.get(i), thermalModeStrength);
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

    private static boolean addHeatSignatureQuad(
            BufferBuilder buffer,
            PoseStack poseStack,
            RenderLevelStageEvent event,
            Vec3 cameraPos,
            SurveyorLensScanner.HeatSignature signature,
            float thermalModeStrength,
            boolean primary
    ) {
        float displayHeat = SurveyorLensProjectionMath.displayHeat(signature);
        if (displayHeat <= 0.0F) {
            return false;
        }

        Vec3 worldPos = SurveyorLensProjectionMath.heatSourceWorldPos(signature);
        float distance = Math.max(1.0F, signature.distanceBlocks());
        float distanceAttenuation = Mth.clamp(1.16F - distance / 90.0F, 0.42F, 1.0F);
        float heatWeight = Mth.clamp(displayHeat / 1.84F, 0.0F, 1.0F);
        float radius = Mth.lerp(heatWeight, 0.56F, primary ? 1.86F : 1.58F)
                * Mth.lerp(heatWeight, 0.98F, 1.18F)
                * distanceAttenuation;
        float intensity = (Mth.lerp(heatWeight, 0.70F, primary ? 1.82F : 1.48F)
                + Math.max(0.0F, displayHeat - 0.90F) * 0.92F)
                * easedThermalStrength(thermalModeStrength);
        AABB bounds = new AABB(worldPos, worldPos).inflate(radius);
        if (!event.getFrustum().isVisible(bounds)) {
            return false;
        }

        HeatRenderStyle style = heatRenderStyle(signature.sourceType(), displayHeat, primary);
        return renderHeatSplat(
                buffer, poseStack, event, cameraPos, worldPos, radius,
                style.outerColor(), style.midColor(), style.coreColor(),
                style.outerAlpha() * intensity, style.midAlpha() * intensity, style.coreAlpha() * intensity
        );
    }

    private static boolean addColdAnchorQuad(
            BufferBuilder buffer,
            PoseStack poseStack,
            RenderLevelStageEvent event,
            Vec3 cameraPos,
            SurveyorLensTargetCollector.ColdAnchor anchor,
            float thermalModeStrength
    ) {
        Vec3 worldPos = new Vec3(anchor.pos().getX() + 0.5D, anchor.pos().getY() + 0.86D, anchor.pos().getZ() + 0.5D);
        float distance = (float) Math.sqrt(cameraPos.distanceToSqr(worldPos));
        float distanceAttenuation = Mth.clamp(1.10F - distance / 64.0F, 0.38F, 1.0F);
        float clampedStrength = Mth.clamp(anchor.strength(), 0.0F, 1.4F);
        float radius = Mth.lerp(Math.min(clampedStrength, 1.0F), 0.52F, 1.12F) * distanceAttenuation;
        float intensity = Mth.lerp(Math.min(clampedStrength, 1.0F), 0.34F, 0.78F) * easedThermalStrength(thermalModeStrength);
        AABB bounds = new AABB(worldPos, worldPos).inflate(radius);
        if (!event.getFrustum().isVisible(bounds)) {
            return false;
        }

        return renderHeatSplat(
                buffer, poseStack, event, cameraPos, worldPos, radius,
                0x03050B, 0x0A1732, 0x173A72,
                0.34F * intensity, 0.48F * intensity, 0.56F * intensity
        );
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
            case SOUL_FIRE, SOUL_CAMPFIRE, SOUL_LANTERN, SOUL_TORCH -> new HeatRenderStyle(
                    0x3A4CC4, 0x4EC2FF, 0xE5F7FF,
                    outerAlpha * 0.86F, midAlpha * 0.94F, coreAlpha * 0.86F
            );
            case FIRE, CAMPFIRE, LANTERN, TORCH -> new HeatRenderStyle(
                    0x6C1568, 0xD22A2A, 0xFFB53C,
                    outerAlpha * 0.94F, midAlpha * 1.02F, coreAlpha * 0.94F
            );
            case ACHERONITE_BLOCK -> new HeatRenderStyle(
                    0x49308A, 0x9A5AE2, 0xE1C6FF,
                    outerAlpha * 0.60F, midAlpha * 0.64F, coreAlpha * 0.52F
            );
            case TRANSPONDER -> new HeatRenderStyle(
                    0x22538C, 0x4BCBFF, 0xE7FDFF,
                    outerAlpha * 0.52F, midAlpha * 0.58F, coreAlpha * 0.48F
            );
        };
    }

    private static boolean renderHeatSplat(
            BufferBuilder buffer,
            PoseStack poseStack,
            RenderLevelStageEvent event,
            Vec3 cameraPos,
            Vec3 worldPos,
            float radius,
            int outerColor,
            int midColor,
            int coreColor,
            float outerAlpha,
            float midAlpha,
            float coreAlpha
    ) {
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
        if (outerA > 0) {
            emitted |= addSplatQuad(buffer, pose, radius * 1.70F, 0.000F, outerColor, outerA);
        }
        if (midA > 0) {
            emitted |= addSplatQuad(buffer, pose, radius * 1.04F, 0.002F, midColor, midA);
        }
        if (coreA > 0) {
            emitted |= addSplatQuad(buffer, pose, radius * 0.54F, 0.004F, coreColor, coreA);
        }
        float wallHeight = Math.min(1.18F, Math.max(0.42F, radius * 1.12F));
        float wallRise = Math.min(0.20F, radius * 0.18F);
        int wallOuterA = alphaInt(outerAlpha * 0.62F);
        int wallMidA = alphaInt(midAlpha * 0.72F);
        int wallCoreA = alphaInt(coreAlpha * 0.80F);
        if (wallOuterA > 0) {
            emitted |= addVerticalCross(buffer, pose, radius * 0.92F, wallHeight, wallRise, outerColor, wallOuterA);
        }
        if (wallMidA > 0) {
            emitted |= addVerticalCross(buffer, pose, radius * 0.62F, wallHeight * 0.86F, wallRise, midColor, wallMidA);
        }
        if (wallCoreA > 0) {
            emitted |= addVerticalCross(buffer, pose, radius * 0.30F, wallHeight * 0.72F, wallRise, coreColor, wallCoreA);
        }
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

    private static float easedThermalStrength(float thermalModeStrength) {
        float t = Mth.clamp(thermalModeStrength, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record HeatRenderStyle(
            int outerColor,
            int midColor,
            int coreColor,
            float outerAlpha,
            float midAlpha,
            float coreAlpha
    ) {
    }
}
