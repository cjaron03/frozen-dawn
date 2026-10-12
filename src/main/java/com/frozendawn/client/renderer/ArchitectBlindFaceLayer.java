package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchitectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Opaque skin quads replace both eye patches, including blink frames, using the adjacent skin color. */
final class ArchitectBlindFaceLayer extends RenderLayer<ArchitectEntity, ArchitectModel> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    ArchitectBlindFaceLayer(RenderLayerParent<ArchitectEntity, ArchitectModel> parent) { super(parent); }
    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, ArchitectEntity actor,
                                 float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (!actor.isBlindArchitect() || actor.isInvisible() || !getParentModel().head.visible) return;
        pose.pushPose(); getParentModel().head.translateAndRotate(pose);
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        int overlay = LivingEntityRenderer.getOverlayCoords(actor, 0);
        for (int u : new int[]{10, 13}) {
            float x = (u - 12) / 16F, bottom = (13 - 16) / 16F, top = (10 - 16) / 16F;
            for (float[] point : new float[][]{{x,bottom},{x+2/16F,bottom},{x+2/16F,top},{x,top}})
                vertices.addVertex(pose.last().pose(), point[0], point[1], -.2502F)
                        .setColor(0xFF0C0C16).setUv(.5F,.5F).setOverlay(overlay).setLight(light).setNormal(pose.last(),0,0,-1);
        }
        pose.popPose();
    }
}
