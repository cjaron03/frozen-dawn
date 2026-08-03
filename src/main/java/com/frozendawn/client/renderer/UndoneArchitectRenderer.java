package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.UndoneArchitectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public final class UndoneArchitectRenderer
        extends HumanoidMobRenderer<UndoneArchitectEntity, UndoneArchitectModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID,
                    "textures/entity/undone_architect.png");

    public UndoneArchitectRenderer(EntityRendererProvider.Context context) {
        super(context,
                new UndoneArchitectModel(context.bakeLayer(ModelLayers.ZOMBIE)),
                0.5F);
        addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel<>(
                        context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(
                        context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new UndoneArchitectAccretionLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(UndoneArchitectEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            UndoneArchitectEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        int deathTicks = entity.getDeathTicks();
        if (deathTicks > 0) {
            renderDeathDissolve(entity, entityYaw, partialTick,
                    poseStack, bufferSource, packedLight, deathTicks);
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(2.8F));
        int accretionStage = entity.getAccretionVisualStage();
        float widthScale = 1.0F + accretionStage * 0.045F;
        float heightScale = 1.0F + accretionStage * 0.025F;
        poseStack.scale(widthScale, heightScale, widthScale);
        super.render(entity, entityYaw, partialTick,
                poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    protected float getShadowRadius(UndoneArchitectEntity entity) {
        return entity.getDeathTicks() > 0 ? 0.0F : super.getShadowRadius(entity);
    }

    private void renderDeathDissolve(
            UndoneArchitectEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int deathTicks) {
        float progress = Math.min(1.0F, (deathTicks + partialTick) / 30.0F);
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 3.0F);
        float shudder = Mth.sin((entity.tickCount + partialTick) * 2.7F)
                * (1.0F - progress) * 5.5F;
        float alpha = Math.max(0.0F, 1.0F - eased * 1.08F);
        float width = 1.0F - eased * 0.62F;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        poseStack.translate(0.0F, -eased * 0.78F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                -2.8F - eased * 38.0F + shudder));
        poseStack.mulPose(Axis.XP.rotationDegrees(eased * 17.0F));
        poseStack.scale(width, 1.0F - eased * 0.38F, width);

        float limbSwing = entity.walkAnimation.position(partialTick);
        float limbSwingAmount = entity.walkAnimation.speed(partialTick);
        float headYaw = entity.getYHeadRot() - entityYaw;
        float headPitch = entity.getXRot();
        model.attackTime = getAttackAnim(entity, partialTick);
        model.riding = entity.isPassenger();
        model.young = entity.isBaby();
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(entity, limbSwing, limbSwingAmount,
                entity.tickCount + partialTick, headYaw, headPitch);

        VertexConsumer body = bufferSource.getBuffer(
                RenderType.entityTranslucent(getTextureLocation(entity)));
        model.renderToBuffer(
                poseStack,
                body,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        entity, getWhiteOverlayProgress(entity, partialTick)),
                FastColor.ARGB32.color(
                        Mth.floor(alpha * 255.0F), 255, 255, 255));
        poseStack.popPose();
    }
}
