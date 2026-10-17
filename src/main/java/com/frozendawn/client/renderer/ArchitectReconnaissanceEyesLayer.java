package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchitectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Recolors only the existing two 2x3-pixel eyes: purple for a scout, white for the §9.4b Scribe. Skin and blink textures stay untouched. */
final class ArchitectReconnaissanceEyesLayer extends RenderLayer<ArchitectEntity, ArchitectModel> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    ArchitectReconnaissanceEyesLayer(RenderLayerParent<ArchitectEntity, ArchitectModel> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, ArchitectEntity actor,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        boolean scribe = actor.isScribe();
        if (!scribe && !actor.hasReconnaissanceEyes() || actor.isInvisible() || !getParentModel().head.visible) return;
        // Reuse the same texture decision, including the 97-tick phase and three blink frames.
        boolean blink = !ArchitectRenderer.textureForBlinkCycle(actor.tickCount, actor.getId()).equals(ArchitectRenderer.baseTexture());
        pose.pushPose(); getParentModel().head.translateAndRotate(pose);
        int opacity = 255 - Math.abs(actor.getReconnaissanceDissolve()) * 255 / 20;
        var vertices = buffers.getBuffer(opacity < 255 ? RenderType.entityTranslucent(WHITE) : RenderType.entityCutoutNoCull(WHITE));
        int overlay = LivingEntityRenderer.getOverlayCoords(actor, 0);
        int[] palette = scribe ? new int[]{0xB4BDC2, 0xD8DEE2, 0xFFFFFF, 0x8E989E} : new int[]{0x692696, 0x7E2DB4, 0xB340FF, 0x461964};
        for (int x : new int[]{10, 13}) {
            if (blink) row(pose, vertices, x, 11, opacity << 24 | palette[0], light, overlay);
            else {
                row(pose, vertices, x, 10, opacity << 24 | palette[1], light, overlay);
                row(pose, vertices, x, 11, opacity << 24 | palette[2], light, overlay);
                row(pose, vertices, x, 12, opacity << 24 | palette[3], light, overlay);
            }
        }
        pose.popPose();
    }

    private static void row(PoseStack pose, VertexConsumer vertices, int u, int v, int color, int light, int overlay) {
        // Standard 64x64 head front UVs: (8,8)..(16,16), on z=-4 pixels.
        float x = (u - 12) / 16f, y = (v - 16) / 16f, z = -.2501f;
        vertex(pose, vertices, x, y + 1/16f, z, color, light, overlay);
        vertex(pose, vertices, x + 2/16f, y + 1/16f, z, color, light, overlay);
        vertex(pose, vertices, x + 2/16f, y, z, color, light, overlay);
        vertex(pose, vertices, x, y, z, color, light, overlay);
    }
    private static void vertex(PoseStack pose, VertexConsumer vertices, float x, float y, float z, int color, int light, int overlay) {
        vertices.addVertex(pose.last().pose(), x, y, z).setColor(color).setUv(.5f, .5f)
                .setOverlay(overlay).setLight(light).setNormal(pose.last(), 0, 0, -1);
    }
}
