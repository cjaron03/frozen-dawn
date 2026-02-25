package com.frozendawn.mixin;

import com.frozendawn.client.ApocalypseClientData;
import com.frozendawn.phase.FrozenDawnPhaseTracker;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides clouds and celestial bodies (sun/moon) in phase 5+.
 *
 * Phase 5: blizzard whiteout — clouds aren't visible anyway, moon hidden by storm.
 * Phase 6 early (progress ≤ 0.72): same blizzard whiteout.
 * Phase 6 mid+ (progress > 0.72): atmosphere thins, stars become visible on black sky.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow private VertexBuffer starBuffer;
    @Shadow private Minecraft minecraft;

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void frozendawn$hideClouds(PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                       float partialTick, double camX, double camY, double camZ,
                                       CallbackInfo ci) {
        if (FrozenDawnPhaseTracker.getPhase() >= 5) {
            ci.cancel();
        }
    }

    /**
     * Scales the sun quad size (vanilla 30.0F) based on apocalypse sun scale.
     * Phase 0: full size. Phase 1-2: dramatically smaller. Phase 3+: tiny.
     */
    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 30.0F))
    private float frozendawn$scaleSun(float original) {
        float sunScale = ApocalypseClientData.getSunScale();
        return original * sunScale;
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void frozendawn$hideSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                    float partialTick, Camera camera, boolean isFoggy,
                                    Runnable skyFogSetup, CallbackInfo ci) {
        int phase = FrozenDawnPhaseTracker.getPhase();
        if (phase < 5) return;

        float progress = ApocalypseClientData.getProgress();

        // Phase 5 + phase 6 early: full cancel (blizzard whiteout)
        if (phase == 5 || progress <= 0.72f) {
            ci.cancel();
            return;
        }

        // Phase 6 mid+: cancel default sky, render stars only
        ci.cancel();
        renderPhase6Stars(frustumMatrix, projectionMatrix, partialTick, skyFogSetup);
    }

    /**
     * Renders only stars on a black sky for phase 6 late.
     * Replicates vanilla's exact star rendering pipeline.
     */
    private void renderPhase6Stars(Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                   float partialTick, Runnable skyFogSetup) {
        float progress = ApocalypseClientData.getProgress();

        // Star brightness: fades in from 0 at progress=0.72 to 1.0 at progress=0.90
        float starAlpha = Math.min(1.0f, (progress - 0.72f) / 0.18f);

        if (starAlpha <= 0.0f || starBuffer == null) return;

        // Build model-view matrix exactly like vanilla:
        // frustumMatrix first (camera), then celestial rotations
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(frustumMatrix);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
        float timeOfDay = minecraft.level.getTimeOfDay(partialTick);
        poseStack.mulPose(Axis.XP.rotationDegrees(timeOfDay * 360.0f));

        // Sky rendering state: no depth writes, additive blending
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        RenderSystem.setShaderColor(starAlpha, starAlpha, starAlpha, starAlpha);
        FogRenderer.setupNoFog();

        // Draw stars using POSITION shader (vanilla star buffer is POSITION format, not POSITION_COLOR)
        starBuffer.bind();
        starBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, GameRenderer.getPositionShader());
        VertexBuffer.unbind();

        // Restore fog
        skyFogSetup.run();

        // Reset render state
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);

        poseStack.popPose();
    }
}
