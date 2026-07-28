package com.frozendawn.client.renderer;

import com.frozendawn.entity.MasterArchitectLightningEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/** Thick blue-black lightning used only by the Master Architect aura. */
public final class MasterArchitectLightningRenderer
        extends EntityRenderer<MasterArchitectLightningEntity> {
    private static final int SEGMENTS = 10;

    public MasterArchitectLightningRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            MasterArchitectLightningEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        RandomSource random = RandomSource.create(entity.getBoltSeed());
        float[] x = new float[SEGMENTS + 1];
        float[] z = new float[SEGMENTS + 1];
        for (int index = 1; index <= SEGMENTS; index++) {
            float taper = 1.0F - index / (float) SEGMENTS;
            x[index] = x[index - 1] + (random.nextFloat() - 0.5F) * 3.8F * taper;
            z[index] = z[index - 1] + (random.nextFloat() - 0.5F) * 3.8F * taper;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        float alpha = entity.getLifeAlpha(partialTick);
        float segmentHeight = entity.getBoltHeight() / SEGMENTS;
        float intensity = entity.getBoltIntensity();
        for (int layer = 0; layer < 4; layer++) {
            float width = (0.08F + layer * 0.085F) * intensity;
            float red = Mth.lerp(layer / 3.0F, 0.22F, 0.015F);
            float green = Mth.lerp(layer / 3.0F, 0.95F, 0.22F);
            float blue = Mth.lerp(layer / 3.0F, 1.0F, 0.32F);
            float layerAlpha = alpha * Mth.lerp(layer / 3.0F, 0.88F, 0.24F);
            for (int index = 0; index < SEGMENTS; index++) {
                quad(matrix, consumer,
                        x[index], z[index], index * segmentHeight,
                        x[index + 1], z[index + 1], (index + 1) * segmentHeight,
                        width, red, green, blue, layerAlpha);
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void quad(
            Matrix4f matrix, VertexConsumer consumer,
            float x1, float z1, float y1,
            float x2, float z2, float y2,
            float width, float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, x1 - width, y1, z1 - width)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2 - width, y2, z2 - width)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2 + width, y2, z2 + width)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1 + width, y1, z1 + width)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1 - width, y1, z1 + width)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2 - width, y2, z2 + width)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x2 + width, y2, z2 - width)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1 + width, y1, z1 - width)
                .setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(MasterArchitectLightningEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
