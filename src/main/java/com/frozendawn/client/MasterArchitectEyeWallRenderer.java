package com.frozendawn.client;

import com.frozendawn.homo.MasterArchitectAuraTier;
import com.frozendawn.homo.MasterArchitectEyeWallPolicy;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Depth-aware screen composite for the hostile Master Architect eye wall. */
public final class MasterArchitectEyeWallRenderer {
    private static final float WALL_THICKNESS = 6.0F;
    private static final float FULL_DENSITY = 1.15F;
    private static final float STORM_RED = 63.0F / 255.0F;
    private static final float STORM_GREEN = 75.0F / 255.0F;
    private static final float STORM_BLUE = 88.0F / 255.0F;

    private static ShaderInstance shader;
    private static TextureTarget sceneCopy;

    private MasterArchitectEyeWallRenderer() {
    }

    public static void setShader(ShaderInstance loadedShader) {
        shader = loadedShader;
    }

    public static void render(
            RenderLevelStageEvent event,
            BlockPos centerPos,
            float visualTier,
            int aftermathTicks,
            float aftermathStrength,
            float stormStrength) {
        Minecraft minecraft = Minecraft.getInstance();
        if (shader == null
                || minecraft.level == null
                || minecraft.player == null
                || !MasterArchitectEyeWallPolicy.isVisible(
                        visualTier, aftermathTicks, aftermathStrength)) {
            return;
        }

        float fade = MasterArchitectEyeWallPolicy.emptyFade(
                aftermathTicks, aftermathStrength);
        float strength = Mth.clamp(stormStrength * fade, 0.0F, 1.0F);
        if (strength <= 0.01F) {
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ensureSceneCopy(mainTarget.width, mainTarget.height);
        if (sceneCopy == null || mainTarget.getDepthTextureId() < 0) {
            return;
        }

        copySceneColor(mainTarget, sceneCopy);
        mainTarget.bindWrite(true);

        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 eyeCenter = centerPos.getCenter();
        float radius = MasterArchitectEyeWallPolicy.radius(
                visualTier, aftermathTicks, aftermathStrength);
        float halfThickness = WALL_THICKNESS * 0.5F;
        float minimumY = (float) eyeCenter.y - 1.0F;
        float maximumY = minimumY + MasterArchitectEyeWallPolicy.height(visualTier)
                * (1.0F - MasterArchitectEyeWallPolicy.collapseProgress(
                        aftermathTicks, aftermathStrength) * 0.62F);
        float partialTick = event.getPartialTick()
                .getGameTimeDeltaPartialTick(false);
        float time = (minecraft.level.getGameTime() + partialTick) / 20.0F;
        float windAngle = BlizzardWindHelper.getWindAngleRad(
                minecraft.level.getGameTime());

        Matrix4f inverseProjection = new Matrix4f(
                event.getProjectionMatrix()).invert();
        Matrix4f cameraWorld = new Matrix4f(
                event.getModelViewMatrix()).invert();
        cameraWorld.setTranslation(
                (float) cameraPosition.x,
                (float) cameraPosition.y,
                (float) cameraPosition.z);

        shader.setSampler("uScene", sceneCopy.getColorTextureId());
        shader.setSampler("uDepth", mainTarget.getDepthTextureId());
        shader.safeGetUniform("uInverseProjection").set(inverseProjection);
        shader.safeGetUniform("uCameraWorld").set(cameraWorld);
        shader.safeGetUniform("uCameraPosition").set(
                (float) cameraPosition.x,
                (float) cameraPosition.y,
                (float) cameraPosition.z);
        shader.safeGetUniform("uFar").set(minecraft.gameRenderer.getRenderDistance());
        shader.safeGetUniform("uEyeCenter").set(
                (float) eyeCenter.x,
                (float) eyeCenter.y,
                (float) eyeCenter.z);
        shader.safeGetUniform("uInnerRadius").set(
                Math.max(1.0F, radius - halfThickness));
        shader.safeGetUniform("uOuterRadius").set(radius + halfThickness);
        shader.safeGetUniform("uMinimumY").set(minimumY);
        shader.safeGetUniform("uMaximumY").set(maximumY);
        shader.safeGetUniform("uTime").set(time);
        shader.safeGetUniform("uWind").set(
                Mth.sin(windAngle),
                Mth.cos(windAngle));
        shader.safeGetUniform("uDensity").set(FULL_DENSITY * strength);
        shader.safeGetUniform("uStormColor").set(
                STORM_RED, STORM_GREEN, STORM_BLUE);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        shader.apply();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F);
        buffer.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.draw(buffer.buildOrThrow());
        shader.clear();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        // defaultBlendFunc only sets the equation; without enableBlend the disableBlend above
        // leaks out of this renderer and leaves blending off for the rest of the frame.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        mainTarget.bindWrite(true);
    }

    public static void clear() {
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
    }

    private static void ensureSceneCopy(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (sceneCopy == null) {
            sceneCopy = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            sceneCopy.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        } else if (sceneCopy.width != width || sceneCopy.height != height) {
            sceneCopy.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void copySceneColor(RenderTarget source, RenderTarget destination) {
        GlStateManager._glBindFramebuffer(36008, source.frameBufferId);
        GlStateManager._glBindFramebuffer(36009, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0,
                0,
                source.width,
                source.height,
                0,
                0,
                destination.width,
                destination.height,
                16384,
                9728);
        GlStateManager._glBindFramebuffer(36160, 0);
    }
}
