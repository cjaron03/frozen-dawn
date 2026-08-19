package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnClientConfig;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/** Depth-aware full-screen composite for the spherical Stillpoint sanctuary. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class StillpointFieldRenderer {
    private static ShaderInstance shader;
    private static TextureTarget sceneCopy;

    private StillpointFieldRenderer() {
    }

    public static void setShader(ShaderInstance loadedShader) {
        shader = loadedShader;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || shader == null
                || !FrozenDawnClientConfig.ENABLE_STILLPOINT_FIELD_EFFECTS.get()
                || !StillpointClientState.isRenderableHere()) {
            return;
        }
        render(event);
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ensureSceneCopy(mainTarget.width, mainTarget.height);
        if (sceneCopy == null || mainTarget.getDepthTextureId() < 0) return;

        copySceneColor(mainTarget, sceneCopy);
        mainTarget.bindWrite(true);
        Vec3 camera = event.getCamera().getPosition();
        Vec3 center = StillpointClientState.renderCenter().getCenter();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = (minecraft.level.getGameTime() + partialTick) / 20.0F;
        float formationAge = StillpointClientState.formationAgeTicks(partialTick);
        float expansion = formationAge >= 24.0F ? 1.0F
                : 1.0F - (float) Math.pow(1.0F - formationAge / 24.0F, 3.0D);
        float intensity = FrozenDawnClientConfig.STILLPOINT_DISTORTION_INTENSITY
                .get().floatValue();

        Matrix4f inverseProjection = new Matrix4f(event.getProjectionMatrix()).invert();
        Matrix4f cameraWorld = new Matrix4f(event.getModelViewMatrix()).invert();
        cameraWorld.setTranslation((float) camera.x, (float) camera.y, (float) camera.z);

        shader.setSampler("uScene", sceneCopy.getColorTextureId());
        shader.setSampler("uDepth", mainTarget.getDepthTextureId());
        shader.safeGetUniform("uInverseProjection").set(inverseProjection);
        shader.safeGetUniform("uCameraWorld").set(cameraWorld);
        shader.safeGetUniform("uCameraPosition").set(
                (float) camera.x, (float) camera.y, (float) camera.z);
        shader.safeGetUniform("uFar").set(minecraft.gameRenderer.getRenderDistance());
        shader.safeGetUniform("uCenter").set(
                (float) center.x, (float) center.y, (float) center.z);
        shader.safeGetUniform("uRadius").set(
                Math.max(0.05F, StillpointClientState.renderRadius()
                        * expansion * StillpointClientState.collapseScale(partialTick)));
        shader.safeGetUniform("uTime").set(time);
        shader.safeGetUniform("uIntensity").set(intensity);
        setRippleUniforms(minecraft.level.getGameTime() + partialTick);

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
        RenderSystem.defaultBlendFunc();
        mainTarget.bindWrite(true);
    }

    private static void setRippleUniforms(float gameTime) {
        List<StillpointClientState.Ripple> ripples = StillpointClientState.ripples();
        float[] ages = new float[]{99.0F, 99.0F, 99.0F, 99.0F};
        for (int index = 0; index < 4; index++) {
            Vec3 position = Vec3.ZERO;
            float strength = 0.0F;
            if (index < ripples.size()) {
                StillpointClientState.Ripple ripple = ripples.get(index);
                position = ripple.position();
                strength = ripple.strength();
                ages[index] = Math.max(0.0F, (gameTime - ripple.gameTime()) / 20.0F);
            }
            shader.safeGetUniform("uRipple" + index).set(
                    (float) position.x, (float) position.y,
                    (float) position.z, strength);
        }
        shader.safeGetUniform("uRippleAges").set(
                ages[0], ages[1], ages[2], ages[3]);
    }

    public static void clear() {
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void ensureSceneCopy(int width, int height) {
        if (width <= 0 || height <= 0) return;
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
        GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height,
                0, 0, destination.width, destination.height, 16384, 9728);
        GlStateManager._glBindFramebuffer(36160, 0);
    }
}
