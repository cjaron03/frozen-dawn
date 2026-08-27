package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchivistRelicEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Fixed item rendering: no bob, spin, merge, or despawn language. */
public final class ArchivistRelicRenderer extends EntityRenderer<ArchivistRelicEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

    public ArchivistRelicRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0.12F;
    }

    @Override
    public void render(ArchivistRelicEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.getItem().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(78.0F));
        poseStack.scale(0.72F, 0.72F, 0.72F);
        itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.FIXED,
                packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ArchivistRelicEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
