package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.ResonantState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;

/** World-lit translucency whose alpha is the Resonant's confidence readout. */
public final class ResonantRenderer extends EntityRenderer<ResonantEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/entity/resonant.png");
    private final ResonantModel model;

    public ResonantRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new ResonantModel(context.bakeLayer(ResonantModel.LAYER_LOCATION));
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ResonantEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ResonantEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        if (entity.deathTime > 0) {
            float collapse = net.minecraft.util.Mth.clamp(
                    (entity.deathTime + partialTick - 24.0F) / 18.0F,
                    0.0F, 1.0F);
            poseStack.translate(0.0D, -collapse * 1.45D, 0.0D);
            poseStack.scale(1.0F - collapse * 0.28F,
                    1.0F - collapse * 0.34F,
                    1.0F - collapse * 0.28F);
        }
        if (entity.activityState() == ResonantState.BREACHING) {
            Vec3 projected = entity.breachOutside().getCenter().add(0.0D, -0.5D, 0.0D)
                    .subtract(entity.position());
            poseStack.translate(projected.x, projected.y, projected.z);
            poseStack.scale(1.0F, 1.0F, 0.34F);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -2.251F, 0.0F);

        float age = entity.tickCount + partialTick;
        model.prepareMobModel(entity, 0.0F, 0.0F, partialTick);
        model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        int alpha = (int) (entity.renderAlpha() * 255.0F);
        int color = FastColor.ARGB32.color(alpha, 205, 211, 208);
        int overlay = entity.hurtTime > 0
                ? LivingEntityRenderer.getOverlayCoords(entity, 0.0F)
                : OverlayTexture.NO_OVERLAY;
        model.renderToBuffer(poseStack, consumer, packedLight, overlay, color);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
