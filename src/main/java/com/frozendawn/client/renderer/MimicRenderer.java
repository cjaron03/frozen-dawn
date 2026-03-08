package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.MimicEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;

import java.util.Optional;
import java.util.UUID;

public class MimicRenderer extends EntityRenderer<MimicEntity> {

    private static final ResourceLocation SHADOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/shadow_blank.png");

    private final MimicModel model;
    private final ModelPart playerRoot;

    public MimicRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.playerRoot = context.bakeLayer(ModelLayers.PLAYER);
        this.model = new MimicModel(this.playerRoot);
    }

    @Override
    public ResourceLocation getTextureLocation(MimicEntity entity) {
        return SHADOW_TEXTURE;
    }

    @Override
    public void render(MimicEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        int phase = entity.getMimicPhase();

        if (phase == MimicEntity.PHASE_BURROW) {
            renderBurrow(entity, entityYaw, partialTick, poseStack, bufferSource);
        } else if (phase == MimicEntity.PHASE_OBSERVATION) {
            renderObservation(entity, partialTick, poseStack, bufferSource);
        } else if (phase == MimicEntity.PHASE_MIMICRY) {
            renderMimicry(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } else {
            renderCombat(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    /**
     * Observation phase: identical to ShadowFigureRenderer.
     */
    private void renderObservation(MimicEntity entity, float partialTick,
                                    PoseStack poseStack, MultiBufferSource bufferSource) {
        float alpha = 0.7f;
        float cameraYaw = this.entityRenderDispatcher.camera.getYRot();

        poseStack.pushPose();

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - cameraYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        resetModelPose(playerRoot);

        VertexConsumer bodyVC = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(SHADOW_TEXTURE));
        int bodyColor = FastColor.ARGB32.color((int) (alpha * 180), 10, 10, 15);
        playerRoot.render(poseStack, bodyVC, 0xF000F0, OverlayTexture.NO_OVERLAY, bodyColor);

        renderEyes(entity, poseStack, bufferSource, 200, 40, 40, alpha, 320);

        poseStack.popPose();
    }

    /**
     * Burrow phase: shadow figure looks down, then sinks into the ground.
     * Uses 3D rotation (not billboard) so the head tilt is visible.
     */
    private void renderBurrow(MimicEntity entity, float entityYaw, float partialTick,
                               PoseStack poseStack, MultiBufferSource bufferSource) {
        int burrowTicks = entity.getBurrowTicks();
        float alpha = 0.7f;

        poseStack.pushPose();

        // 3D rotation so head-down is visible from the side
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        // Reset pose, then animate head looking down
        resetModelPose(playerRoot);

        // Head tilts down over first 15 ticks (0 → ~80 degrees)
        float lookProgress = Math.min(1.0f, burrowTicks / 15.0f);
        float headPitch = lookProgress * 1.4f; // ~80 degrees in radians
        ModelPart head = playerRoot.getChild("head");
        ModelPart hat = playerRoot.getChild("hat");
        head.xRot = headPitch;
        hat.xRot = headPitch;

        // Slight body lean forward
        ModelPart body = playerRoot.getChild("body");
        body.xRot = lookProgress * 0.3f;

        // Arms reach down during dig
        if (burrowTicks > 10) {
            float armProgress = Math.min(1.0f, (burrowTicks - 10) / 5.0f);
            ModelPart rightArm = playerRoot.getChild("right_arm");
            ModelPart leftArm = playerRoot.getChild("left_arm");
            rightArm.xRot = armProgress * 1.2f; // arms swing forward/down
            leftArm.xRot = armProgress * 1.2f;
        }

        // Fade out as entity sinks underground
        if (burrowTicks > 16) {
            float fadeProgress = (burrowTicks - 16) / 24.0f;
            alpha = 0.7f * (1.0f - Math.min(1.0f, fadeProgress));
        }

        VertexConsumer bodyVC = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(SHADOW_TEXTURE));
        int bodyColor = FastColor.ARGB32.color((int) (alpha * 180), 10, 10, 15);
        playerRoot.render(poseStack, bodyVC, 0xF000F0, OverlayTexture.NO_OVERLAY, bodyColor);

        // Eyes dim as it looks down
        float eyeAlpha = alpha * (1.0f - lookProgress * 0.5f);
        renderEyes(entity, poseStack, bufferSource, 200, 40, 40, eyeAlpha, 320);

        poseStack.popPose();
    }

    /**
     * Mimicry phase: crossfade from shadow silhouette to player skin over 5 seconds.
     * Smoke particles are spawned client-side from MimicEntity.aiStep().
     */
    private void renderMimicry(MimicEntity entity, float entityYaw, float partialTick,
                                PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float progress = entity.getMimicryTicks() / (float) MimicEntity.MIMICRY_DURATION;
        progress = Math.min(1.0f, Math.max(0.0f, progress));

        poseStack.pushPose();

        // Transition rotation: lerp from billboard to 3D rotation
        float cameraYaw = this.entityRenderDispatcher.camera.getYRot();
        float billboardYaw = 180.0f - cameraYaw;
        float entityRotYaw = 180.0f - entityYaw;
        float blendedYaw = billboardYaw + (entityRotYaw - billboardYaw) * progress;

        poseStack.mulPose(Axis.YP.rotationDegrees(blendedYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        // Animate — early mimicry is stiff, late mimicry has full animation
        float limbSwing = entity.walkAnimation.position(partialTick);
        float limbSwingAmount = entity.walkAnimation.speed(partialTick) * progress;
        float headYaw = entity.getYHeadRot() - entityYaw;
        float headPitch = entity.getXRot();
        model.setupAnim(entity, limbSwing, limbSwingAmount,
                entity.tickCount + partialTick, headYaw * progress, headPitch * progress);

        // Single render call — switch from shadow to player skin at midpoint.
        // The smoke particles cover the transition.
        ResourceLocation skinTexture = getTargetSkin(entity);
        if (progress < 0.5f || skinTexture == null) {
            // First half: shadow silhouette, fading out
            float shadowAlpha = 0.7f * (1.0f - progress);
            VertexConsumer shadowVC = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(SHADOW_TEXTURE));
            int shadowColor = FastColor.ARGB32.color((int) (shadowAlpha * 180), 10, 10, 15);
            playerRoot.render(poseStack, shadowVC, 0xF000F0, OverlayTexture.NO_OVERLAY, shadowColor);
        } else {
            // Second half: player skin, fading in
            float skinAlpha = (progress - 0.5f) * 2.0f; // 0→1 over second half
            int alpha = (int) (skinAlpha * 255);
            VertexConsumer skinVC = bufferSource.getBuffer(RenderType.entityTranslucent(skinTexture));
            int skinColor = FastColor.ARGB32.color(alpha, 255, 255, 255);
            playerRoot.render(poseStack, skinVC, packedLight, OverlayTexture.NO_OVERLAY, skinColor);
        }

        // Eyes: red glow intensifies as morph progresses
        float eyeAlpha = 0.5f + progress * 0.5f;
        int er = 200 + (int) (55 * progress);
        int eg = (int) (40 * (1.0f - progress));
        int eb = (int) (40 * (1.0f - progress));
        renderEyes(entity, poseStack, bufferSource, er, eg, eb, eyeAlpha, 400);

        poseStack.popPose();
    }

    /**
     * Combat/Retreat: render using the target player's exact skin with red eye overlay.
     */
    private void renderCombat(MimicEntity entity, float entityYaw, float partialTick,
                               PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        // Animate
        float limbSwing = entity.walkAnimation.position(partialTick);
        float limbSwingAmount = entity.walkAnimation.speed(partialTick);
        float headYaw = entity.getYHeadRot() - entityYaw;
        float headPitch = entity.getXRot();
        model.setupAnim(entity, limbSwing, limbSwingAmount,
                entity.tickCount + partialTick, headYaw, headPitch);

        // Render with player's skin
        ResourceLocation skinTexture = getTargetSkin(entity);
        if (skinTexture != null) {
            VertexConsumer bodyVC = bufferSource.getBuffer(RenderType.entityTranslucent(skinTexture));
            playerRoot.render(poseStack, bodyVC, packedLight, OverlayTexture.NO_OVERLAY);
        } else {
            // Fallback: shadow silhouette
            VertexConsumer bodyVC = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(SHADOW_TEXTURE));
            int bodyColor = FastColor.ARGB32.color(198, 10, 10, 15);
            playerRoot.render(poseStack, bodyVC, 0xF000F0, OverlayTexture.NO_OVERLAY, bodyColor);
        }

        // Red eyes — the tell
        renderEyes(entity, poseStack, bufferSource, 255, 20, 20, 0.9f, 400);

        poseStack.popPose();
    }

    private ResourceLocation getTargetSkin(MimicEntity entity) {
        Optional<UUID> targetUUID = entity.getMimicTargetUUID();
        if (targetUUID.isEmpty()) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;

        PlayerInfo info = mc.getConnection().getPlayerInfo(targetUUID.get());
        if (info == null) return null;

        PlayerSkin skin = info.getSkin();
        return skin.texture();
    }

    private void renderEyes(MimicEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                             int er, int eg, int eb, float alpha, float alphaScale) {
        float eyeLocalY = 1.501f - 1.75f;
        float eyeLocalZ = -0.26f;
        float eyeHalfW = 0.0625f;
        float eyeHalfH = 0.03125f;
        float leftEyeX = 0.09375f;
        float rightEyeX = -0.09375f;

        int ea = Math.min(255, (int) (alpha * alphaScale));

        // Blink: every 3-5 seconds, 7-tick cycle
        // RenderType.eyes() ignores alpha, so we skip rendering entirely when closed
        int blinkPeriod = 60 + (entity.getId() % 40);
        int blinkPhase = entity.tickCount % blinkPeriod;
        int blinkStart = blinkPeriod - 7;
        boolean eyesClosed = false;
        boolean eyesHalf = false;
        if (blinkPhase >= blinkStart) {
            int blinkTick = blinkPhase - blinkStart; // 0-6
            if (blinkTick < 2 || blinkTick >= 5) {
                eyesHalf = true; // half-close / half-open: shrink eye height
            } else {
                eyesClosed = true; // fully closed: don't render
            }
        }

        if (eyesClosed || ea <= 0) return; // Eyes off — skip rendering entirely

        // Half-blink: squish eyes vertically (closing from top)
        float renderHalfH = eyesHalf ? eyeHalfH * 0.3f : eyeHalfH;
        float renderYOffset = eyesHalf ? -eyeHalfH * 0.35f : 0; // shift down when squished

        Matrix4f pose = poseStack.last().pose();
        PoseStack.Pose poseEntry = poseStack.last();
        VertexConsumer eyeVC = bufferSource.getBuffer(RenderType.eyes(SHADOW_TEXTURE));

        addQuad(eyeVC, pose, poseEntry,
                leftEyeX - eyeHalfW, eyeLocalY - renderHalfH + renderYOffset,
                leftEyeX + eyeHalfW, eyeLocalY + renderHalfH + renderYOffset,
                eyeLocalZ, er, eg, eb, ea);
        addQuad(eyeVC, pose, poseEntry,
                rightEyeX - eyeHalfW, eyeLocalY - renderHalfH + renderYOffset,
                rightEyeX + eyeHalfW, eyeLocalY + renderHalfH + renderYOffset,
                eyeLocalZ, er, eg, eb, ea);
    }

    @Override
    protected boolean shouldShowName(MimicEntity entity) {
        return false;
    }

    private static void resetModelPose(ModelPart root) {
        root.getAllParts().forEach(part -> {
            part.xRot = 0;
            part.yRot = 0;
            part.zRot = 0;
        });
    }

    private static void addQuad(VertexConsumer vc, Matrix4f pose, PoseStack.Pose poseEntry,
                                 float x0, float y0, float x1, float y1, float z,
                                 int r, int g, int b, int a) {
        vc.addVertex(pose, x0, y0, z).setColor(r, g, b, a)
                .setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(poseEntry, 0, 0, -1);
        vc.addVertex(pose, x1, y0, z).setColor(r, g, b, a)
                .setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(poseEntry, 0, 0, -1);
        vc.addVertex(pose, x1, y1, z).setColor(r, g, b, a)
                .setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(poseEntry, 0, 0, -1);
        vc.addVertex(pose, x0, y1, z).setColor(r, g, b, a)
                .setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(poseEntry, 0, 0, -1);
    }
}
