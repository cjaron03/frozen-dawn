package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.vault.VaultPlotBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** The ORSA System Plot: one quad on the wall's south face, 24 blocks by 8, glowing whatever the room's light. */
public final class VaultPlotRenderer implements BlockEntityRenderer<VaultPlotBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/vault_system_plot.png");
    private static final float WIDE = 24, TALL = 8, FACE = 1.01F;

    public VaultPlotRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(VaultPlotBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var c = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        vertex(c, pose, 0, TALL, 0, 0);
        vertex(c, pose, 0, 0, 0, 1);
        vertex(c, pose, WIDE, 0, 1, 1);
        vertex(c, pose, WIDE, TALL, 1, 0);
    }
    private static void vertex(VertexConsumer c, PoseStack pose, float x, float y, float u, float v) {
        c.addVertex(pose.last(), x, y, FACE).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 0, 1);
    }

    @Override public boolean shouldRenderOffScreen(VaultPlotBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(VaultPlotBlockEntity be) { return new AABB(be.getBlockPos()).expandTowards(24, 8, 1); }
}
