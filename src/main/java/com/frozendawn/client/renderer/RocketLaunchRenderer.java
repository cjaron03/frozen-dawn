package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.client.RocketLaunchClientController;
import com.frozendawn.entity.RocketLaunchEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public class RocketLaunchRenderer extends EntityRenderer<RocketLaunchEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/rocket_launch.png");

    private final RocketLaunchModel model;

    public RocketLaunchRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new RocketLaunchModel(context.bakeLayer(RocketLaunchModel.LAYER_LOCATION));
        this.shadowRadius = 1.35F;
    }

    @Override
    public ResourceLocation getTextureLocation(RocketLaunchEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(RocketLaunchEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!RocketLaunchClientController.shouldRenderExteriorRocket(entity)) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.78F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        float shake = entity.getShakeAmount(partialTick);
        float time = entity.tickCount + partialTick;
        poseStack.translate(
                Mth.sin(time * 0.37F) * shake,
                Mth.cos(time * 0.51F) * shake * 0.45F,
                Mth.cos(time * 0.29F) * shake
        );
        if (entity.isLaunching()) {
            float ascentTicks = Math.max(0.0F, entity.getSequenceTicks() + partialTick - RocketLaunchEntity.COUNTDOWN_TICKS);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.2F) * 0.6F));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(ascentTicks * 0.08F) * 0.4F));
        }

        int color = FastColor.ARGB32.color(255, 255, 255, 255);
        model.renderToBuffer(poseStack,
                bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                color);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
