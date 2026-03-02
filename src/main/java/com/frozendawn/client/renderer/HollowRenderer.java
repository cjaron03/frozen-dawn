package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.HollowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public class HollowRenderer extends EntityRenderer<HollowEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/hollow.png");

    private final ModelPart playerRoot;

    public HollowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
        this.playerRoot = context.bakeLayer(ModelLayers.PLAYER);
    }

    @Override
    public ResourceLocation getTextureLocation(HollowEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(HollowEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        float tickCount = entity.tickCount + partialTick;

        // Alpha: 30-40% base with subtle breathing pulse
        float baseAlpha = 0.35f + 0.05f * Mth.sin(tickCount * 0.1f);
        int alpha = (int) (baseAlpha * 255);

        // Scale wobble for vapor instability
        float scaleJitter = 1.0f + 0.025f * Mth.sin(tickCount * 0.3f + entity.getId() * 17);
        float yRotWobble = Mth.sin(tickCount * 0.05f + entity.getId() * 7) * 3.0f;

        float cameraYaw = this.entityRenderDispatcher.camera.getYRot();

        poseStack.pushPose();

        // If passenger (grabbing player), render at offset from rider
        if (entity.isPassenger() && entity.getVehicle() != null) {
            poseStack.translate(0.0, 0.3, -0.3);
        }

        // Billboard facing camera + wobble
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - cameraYaw + yRotWobble));
        poseStack.scale(-scaleJitter, -scaleJitter, scaleJitter);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        // Reset model to standing pose
        playerRoot.getAllParts().forEach(part -> {
            part.xRot = 0;
            part.yRot = 0;
            part.zRot = 0;
        });

        // Render with translucent emissive — glows faintly, ignores world lighting
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        int color = FastColor.ARGB32.color(alpha, 180, 200, 240);
        playerRoot.render(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, color);

        poseStack.popPose();
    }
}
