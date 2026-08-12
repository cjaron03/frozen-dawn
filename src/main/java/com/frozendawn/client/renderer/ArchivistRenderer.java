package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchivistEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArchivistRenderer
        extends HumanoidMobRenderer<ArchivistEntity, ArchivistModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/entity/archivist.png");

    public ArchivistRenderer(EntityRendererProvider.Context context) {
        super(context, new ArchivistModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.55F);
        addLayer(new ArchivistLoadLayer(this, context.getItemRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(ArchivistEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ArchivistEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        int deathTicks = entity.getMarkedDeathTicks();
        if (deathTicks > 0) {
            float strength = deathTicks >= 24 ? 0.065F : 0.022F;
            float shake = Mth.sin((entity.tickCount + partialTick) * 4.7F) * strength;
            float shudder = Mth.sin((entity.tickCount + partialTick) * 6.1F)
                    * strength * 0.45F;
            poseStack.translate(shake, shudder, -shake * 0.35F);
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(4.5F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-2.5F));
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
