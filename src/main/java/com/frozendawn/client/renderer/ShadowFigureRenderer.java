package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ShadowFigureEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;

public class ShadowFigureRenderer extends EntityRenderer<ShadowFigureEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/shadow_blank.png");

    private final ModelPart playerRoot;

    public ShadowFigureRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.playerRoot = context.bakeLayer(ModelLayers.PLAYER);
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowFigureEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ShadowFigureEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float alpha = entity.computeAlpha(partialTick);
        if (alpha <= 0) return;

        boolean isWatcher = entity.isWatcher();
        float cameraYaw = this.entityRenderDispatcher.camera.getYRot();

        // ── Single pass: body + eyes + hat all in one coordinate space ──
        poseStack.pushPose();

        // Billboard: face toward camera — standard LivingEntityRenderer convention
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - cameraYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        // Reset model to standing pose
        resetModelPose(playerRoot);

        // Watcher: rotate head pitch to track player
        if (isWatcher) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Vec3 playerEye = mc.player.getEyePosition(partialTick);
                Vec3 entityPos = entity.position().add(0, 1.62, 0);
                Vec3 toPlayer = playerEye.subtract(entityPos);
                double horizDist = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
                float headPitch = (float) Math.atan2(toPlayer.y, horizDist);

                ModelPart head = playerRoot.getChild("head");
                head.xRot = Mth.clamp(headPitch, -0.5f, 0.5f);
                ModelPart hat = playerRoot.getChild("hat");
                hat.xRot = head.xRot;
            }
        }

        // Render player model body
        VertexConsumer bodyVC = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        int bodyColor = FastColor.ARGB32.color((int) (alpha * 180), 10, 10, 15);
        playerRoot.render(poseStack, bodyVC, 0xF000F0, OverlayTexture.NO_OVERLAY, bodyColor);

        // ── Eyes: render in model coordinate space so they align with the head ──
        // In this space (after scale -1,-1,1 and translate 0,-1.501,0):
        //   world_Y = -local_Y + 1.501  →  local_Y = 1.501 - world_Y
        //   world_X = -local_X  (X is flipped)
        //   world_Z = local_Z
        //
        // Model head face center: world Y ≈ 1.75, front at Z = -0.25
        // Eyes at ~4px below head top = world Y ≈ 1.75

        float eyeLocalY = 1.501f - 1.75f; // ≈ -0.249
        float eyeLocalZ = -0.26f; // slightly in front of face

        // Steve-proportioned: 2px wide × 1px tall each, 1.5px from center
        float eyeHalfW = 0.0625f;   // 1px = 0.0625 blocks (half of 2px eye)
        float eyeHalfH = 0.03125f;  // half of 1px

        // X is flipped: camera-left → positive local X
        float leftEyeX  =  0.09375f; // 1.5px from center (camera-left eye)
        float rightEyeX = -0.09375f; // camera-right eye

        int er, eg, eb;
        if (isWatcher) {
            er = 255; eg = 0; eb = 0;
        } else {
            er = 200; eg = 40; eb = 40;
        }
        int ea = Math.min(255, (int) (alpha * (isWatcher ? 450 : 320)));

        // Blink: every ~3-5 seconds, close for 3 ticks
        int blinkPeriod = 60 + (entity.getId() % 40);
        int blinkPhase = entity.getTicksAlive() % blinkPeriod;
        if (blinkPhase >= blinkPeriod - 3) ea = 0;

        Matrix4f pose = poseStack.last().pose();
        PoseStack.Pose poseEntry = poseStack.last();

        VertexConsumer eyeVC = bufferSource.getBuffer(RenderType.eyes(TEXTURE));

        // Left eye (from camera perspective)
        addQuad(eyeVC, pose, poseEntry,
                leftEyeX - eyeHalfW, eyeLocalY - eyeHalfH,
                leftEyeX + eyeHalfW, eyeLocalY + eyeHalfH,
                eyeLocalZ, er, eg, eb, ea);

        // Right eye
        addQuad(eyeVC, pose, poseEntry,
                rightEyeX - eyeHalfW, eyeLocalY - eyeHalfH,
                rightEyeX + eyeHalfW, eyeLocalY + eyeHalfH,
                eyeLocalZ, er, eg, eb, ea);

        poseStack.popPose();

        // ── Watcher hat: separate non-flipped pass ──
        if (isWatcher) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - cameraYaw));

            Matrix4f hatPose = poseStack.last().pose();
            PoseStack.Pose hatEntry = poseStack.last();

            VertexConsumer hatVC = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
            int hatColor = FastColor.ARGB32.color((int) (alpha * 220), 0, 0, 0);

            // Wide brim on top of head
            addColorQuad(hatVC, hatPose, hatEntry, -0.40f, 1.87f, 0.40f, 1.93f, -0.01f, hatColor);
            // Crown above brim
            addColorQuad(hatVC, hatPose, hatEntry, -0.17f, 1.93f, 0.17f, 2.18f, -0.01f, hatColor);

            poseStack.popPose();
        }
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

    private static void addColorQuad(VertexConsumer vc, Matrix4f pose, PoseStack.Pose poseEntry,
                                     float x0, float y0, float x1, float y1, float z, int color) {
        addQuad(vc, pose, poseEntry, x0, y0, x1, y1, z,
                FastColor.ARGB32.red(color), FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color), FastColor.ARGB32.alpha(color));
    }
}
