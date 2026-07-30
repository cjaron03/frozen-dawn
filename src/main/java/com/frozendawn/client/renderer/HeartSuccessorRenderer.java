package com.frozendawn.client.renderer;

import com.frozendawn.entity.HeartSuccessorEntity;
import com.frozendawn.homo.HeartSuccessorPolicy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/** One-eyed humanoid focal point assembled from incomplete archive lattice. */
public final class HeartSuccessorRenderer extends EntityRenderer<HeartSuccessorEntity> {
    private static final List<Part> PARTS = List.of(
            new Part(0.0F, 1.48F, 0.0F, 0.34F, 0.46F, 0.18F, 3.8F, 1.5F, -2.8F),
            new Part(0.0F, 0.96F, 0.0F, 0.29F, 0.14F, 0.17F, -3.1F, 3.2F, 2.6F),
            new Part(0.0F, 2.28F, 0.0F, 0.30F, 0.30F, 0.29F, -4.0F, -0.8F, 3.2F),
            new Part(-0.48F, 1.63F, 0.0F, 0.13F, 0.30F, 0.13F, -2.8F, -3.3F, -2.4F),
            new Part(-0.50F, 1.02F, 0.02F, 0.12F, 0.29F, 0.12F, 4.2F, 2.1F, -3.0F),
            new Part(0.48F, 1.63F, 0.0F, 0.13F, 0.30F, 0.13F, 3.4F, -2.7F, 2.4F),
            new Part(0.51F, 1.02F, 0.02F, 0.12F, 0.29F, 0.12F, -2.6F, -3.8F, 2.1F),
            new Part(-0.18F, 0.46F, 0.0F, 0.14F, 0.45F, 0.14F, 2.8F, -4.2F, -1.9F),
            new Part(0.18F, 0.46F, 0.0F, 0.14F, 0.45F, 0.14F, 2.2F, -3.6F, 2.8F),
            new Part(0.39F, 1.95F, 0.01F, 0.18F, 0.10F, 0.20F, -3.3F, 2.7F, 3.4F));

    public HeartSuccessorRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(
            HeartSuccessorEntity entity, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float assembly = entity.assemblyProgress(partialTick);
        float ease = assembly * assembly * (3.0F - 2.0F * assembly);
        float scale = HeartSuccessorPolicy.scale(
                entity.generation(), entity.fieldStrength());
        float time = entity.tickCount + partialTick;
        float flightSpeed = Mth.clamp(
                (float) entity.getDeltaMovement().horizontalDistance() * 7.0F,
                0.0F, 1.0F);
        float stride = time * (0.16F + flightSpeed * 0.24F);
        float walk = Mth.sin(stride) * (0.07F + flightSpeed * 0.10F);

        poseStack.pushPose();
        poseStack.translate(
                0.0D,
                Mth.sin(time * 0.055F) * 0.11F
                        + Math.abs(Mth.sin(stride)) * flightSpeed * 0.035F,
                0.0D);
        if (entity.mode() == HeartSuccessorPolicy.Mode.STAGGERED) {
            poseStack.translate(
                    Mth.sin(time * 0.9F) * 0.055F,
                    Mth.cos(time * 0.7F) * 0.025F,
                    Mth.cos(time * 0.82F) * 0.055F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    8.0F + Mth.sin(time * 0.7F) * 3.5F));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-flightSpeed * 10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.sin(time * 0.035F) * (1.2F + flightSpeed * 1.8F)));
        poseStack.scale(scale, scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer shell = buffers.getBuffer(RenderType.debugQuads());
        float alpha = Mth.clamp(0.20F + ease * 0.80F, 0.0F, 1.0F);
        for (int index = 0; index < PARTS.size(); index++) {
            Part part = PARTS.get(index);
            float partProgress = Mth.clamp(ease * 1.45F - index * 0.055F, 0.0F, 1.0F);
            float x = Mth.lerp(partProgress, part.sourceX(), part.x());
            float y = Mth.lerp(partProgress, part.sourceY(), part.y());
            float z = Mth.lerp(partProgress, part.sourceZ(), part.z());
            if (index == 7 || index == 8) {
                z += (index == 7 ? walk : -walk);
                y += Math.abs(walk) * (0.10F + flightSpeed * 0.16F);
            } else if (index == 3 || index == 4 || index == 5 || index == 6) {
                z += (index <= 4 ? -walk : walk)
                        * (0.45F + flightSpeed * 0.35F);
            }
            if (entity.mode() == HeartSuccessorPolicy.Mode.CONDUCTING
                    && (index == 5 || index == 6)) {
                x += index == 5 ? 0.16F : 0.28F;
                y += index == 5 ? 0.30F : 0.66F;
                z -= index == 5 ? 0.10F : 0.22F;
            }
            box(matrix, shell, x, y, z, part.hx(), part.hy(), part.hz(),
                    index == 9 ? 0.16F : 0.025F,
                    index == 9 ? 0.27F : 0.055F,
                    index == 9 ? 0.34F : 0.12F,
                    alpha * (0.55F + partProgress * 0.45F));
        }

        // Half-mask and the single channel eye.
        box(matrix, shell, -0.14F, 2.28F, -0.315F,
                0.16F, 0.24F, 0.025F, 0.035F, 0.07F, 0.13F, alpha);
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float eyePulse = 0.84F + 0.16F * Mth.sin(time * 0.13F);
        box(matrix, glow, -0.14F, 2.31F, -0.35F,
                0.095F, 0.075F, 0.020F,
                0.18F, 0.90F, 1.0F, eyePulse * ease);

        // Last Wall seam through the unfinished torso.
        box(matrix, glow, 0.05F, 1.48F, -0.205F,
                0.030F, 0.34F, 0.021F,
                0.08F, 0.55F, 0.92F, alpha * 0.78F);

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void box(
            Matrix4f matrix, VertexConsumer consumer,
            float x, float y, float z, float hx, float hy, float hz,
            float red, float green, float blue, float alpha) {
        Vector3f[] p = {
                new Vector3f(x - hx, y - hy, z - hz),
                new Vector3f(x + hx, y - hy, z - hz),
                new Vector3f(x + hx, y + hy, z - hz),
                new Vector3f(x - hx, y + hy, z - hz),
                new Vector3f(x - hx, y - hy, z + hz),
                new Vector3f(x + hx, y - hy, z + hz),
                new Vector3f(x + hx, y + hy, z + hz),
                new Vector3f(x - hx, y + hy, z + hz)
        };
        quad(matrix, consumer, p[0], p[1], p[2], p[3], red, green, blue, alpha);
        quad(matrix, consumer, p[5], p[4], p[7], p[6], red, green, blue, alpha);
        quad(matrix, consumer, p[4], p[0], p[3], p[7], red, green, blue, alpha);
        quad(matrix, consumer, p[1], p[5], p[6], p[2], red, green, blue, alpha);
        quad(matrix, consumer, p[3], p[2], p[6], p[7], red, green, blue, alpha);
        quad(matrix, consumer, p[4], p[5], p[1], p[0], red, green, blue, alpha);
    }


    private static void quad(
            Matrix4f matrix, VertexConsumer consumer,
            Vector3f a, Vector3f b, Vector3f c, Vector3f d,
            float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, a.x, a.y, a.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, b.x, b.y, b.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, c.x, c.y, c.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, d.x, d.y, d.z).setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(HeartSuccessorEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private record Part(
            float x, float y, float z,
            float hx, float hy, float hz,
            float sourceX, float sourceY, float sourceZ) {
    }
}
