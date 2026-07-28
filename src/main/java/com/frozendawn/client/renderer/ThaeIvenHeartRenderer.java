package com.frozendawn.client.renderer;

import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.homo.HeartFormationStage;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached deterministic blue-black lattice renderer for the persistent Heart. */
public final class ThaeIvenHeartRenderer extends EntityRenderer<ThaeIvenHeartEntity> {
    private static final Map<Long, Lattice> CACHE = new ConcurrentHashMap<>();

    public ThaeIvenHeartRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(
            ThaeIvenHeartEntity heart,
            float yaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {
        HeartFormationStage stage = heart.formationStage();
        if (stage == HeartFormationStage.NONE || stage == HeartFormationStage.DEAD_AIR
                || stage == HeartFormationStage.SHAKE) {
            renderGroundMark(heart, poseStack, buffers, partialTick);
            return;
        }

        float reveal = switch (stage) {
            case GATHER -> heart.stageProgress();
            case HOLD, LIVE -> 1.0F;
            default -> 0.0F;
        };
        Lattice lattice = CACHE.computeIfAbsent(
                heart.layoutSeed(), ThaeIvenHeartRenderer::createLattice);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer dark = buffers.getBuffer(RenderType.debugQuads());
        int visible = Math.max(1, Mth.ceil(lattice.segments.size() * reveal));
        for (int index = 0; index < visible; index++) {
            Segment segment = lattice.segments.get(index);
            float pulse = 0.86F + 0.14F * Mth.sin(
                    (heart.tickCount + partialTick) * 0.035F + segment.group * 0.8F);
            beam(pose.pose(), dark, segment, 0.015F, 0.035F, 0.075F,
                    (0.78F + 0.16F * heart.fieldStrength()) * pulse);
        }
        VertexConsumer light = buffers.getBuffer(RenderType.lightning());
        for (Node node : lattice.nodes) {
            if (reveal + 0.001F < node.revealAt) {
                continue;
            }
            float pulse = 0.72F + 0.28F * Mth.sin(
                    (heart.tickCount + partialTick) * 0.09F + node.phase);
            cube(pose.pose(), light, node.x, node.y, node.z, 0.8F,
                    0.10F, 0.78F, 1.0F, pulse);
        }
        super.render(heart, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void renderGroundMark(
            ThaeIvenHeartEntity heart,
            PoseStack poseStack,
            MultiBufferSource buffers,
            float partialTick) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        float y = -29.92F;
        float pulse = 0.18F + 0.05F * Mth.sin((heart.tickCount + partialTick) * 0.05F);
        for (int ring = 0; ring < 3; ring++) {
            float radius = 2.2F + ring * 1.35F;
            for (int index = 0; index < 24; index++) {
                double a0 = index * Math.PI * 2.0D / 24.0D;
                double a1 = (index + 1) * Math.PI * 2.0D / 24.0D;
                float x0 = (float) Math.cos(a0) * radius;
                float z0 = (float) Math.sin(a0) * radius;
                float x1 = (float) Math.cos(a1) * radius;
                float z1 = (float) Math.sin(a1) * radius;
                float width = 0.05F;
                quad(matrix, consumer, x0, y, z0, x1, y, z1, width,
                        0.03F, 0.22F, 0.34F, pulse);
            }
        }
    }

    private static Lattice createLattice(long seed) {
        RandomSource random = RandomSource.create(seed);
        List<Segment> segments = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        for (int trunk = 0; trunk < 12; trunk++) {
            float angle = (float) (trunk * Math.PI * 2.0D / 12.0D
                    + (random.nextFloat() - 0.5F) * 0.42F);
            float startRadius = 2.0F + random.nextFloat() * 3.0F;
            float x = Mth.cos(angle) * startRadius;
            float y = -13.0F + random.nextFloat() * 8.0F;
            float z = Mth.sin(angle) * startRadius * 0.78F;
            for (int step = 0; step < 5; step++) {
                float nextAngle = angle + (random.nextFloat() - 0.5F) * 0.7F;
                float radial = 4.0F + step * (2.8F + random.nextFloat() * 0.65F);
                float nx = Mth.cos(nextAngle) * radial
                        + (random.nextFloat() - 0.5F) * 3.0F;
                float ny = y + 3.0F + random.nextFloat();
                float nz = Mth.sin(nextAngle) * radial * 0.78F
                        + (random.nextFloat() - 0.5F) * 2.4F;
                float thickness = 1.4F - step * 0.14F + random.nextFloat() * 0.45F;
                segments.add(segmentBetween(x, y, z, nx, ny, nz, thickness, trunk));
                if (step >= 1 && random.nextFloat() < 0.78F) {
                    float bx = nx + (random.nextFloat() - 0.5F) * 8.0F;
                    float by = ny + 1.0F + random.nextFloat() * 5.0F;
                    float bz = nz + (random.nextFloat() - 0.5F) * 8.0F;
                    segments.add(segmentBetween(nx, ny, nz, bx, by, bz,
                            thickness * 0.55F, trunk));
                }
                x = nx;
                y = ny;
                z = nz;
            }
        }
        for (int index = 0; index < 5; index++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float radius = 3.0F + random.nextFloat() * 9.0F;
            nodes.add(new Node(
                    Mth.cos(angle) * radius,
                    -4.0F + random.nextFloat() * 18.0F,
                    Mth.sin(angle) * radius,
                    0.48F + index * 0.105F,
                    random.nextFloat() * Mth.TWO_PI));
        }
        return new Lattice(List.copyOf(segments), List.copyOf(nodes));
    }

    private static Segment segmentBetween(
            float x0, float y0, float z0, float x1, float y1, float z1,
            float thickness, int group) {
        return new Segment(x0, y0, z0, x1, y1, z1, thickness, group);
    }

    private static void beam(
            Matrix4f matrix, VertexConsumer consumer, Segment box,
            float red, float green, float blue, float alpha) {
        Vector3f start = new Vector3f(box.x0, box.y0, box.z0);
        Vector3f end = new Vector3f(box.x1, box.y1, box.z1);
        Vector3f direction = new Vector3f(end).sub(start).normalize();
        Vector3f reference = Math.abs(direction.y) > 0.92F
                ? new Vector3f(1.0F, 0.0F, 0.0F)
                : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f side = new Vector3f(direction).cross(reference).normalize().mul(box.thickness);
        Vector3f up = new Vector3f(side).cross(direction).normalize().mul(box.thickness);
        Vector3f[] a = corners(start, side, up);
        Vector3f[] b = corners(end, side, up);
        coloredQuad(matrix, consumer, a[0], a[1], a[2], a[3], red, green, blue, alpha);
        coloredQuad(matrix, consumer, b[3], b[2], b[1], b[0], red, green, blue, alpha);
        for (int index = 0; index < 4; index++) {
            int next = (index + 1) & 3;
            coloredQuad(matrix, consumer, a[index], b[index], b[next], a[next],
                    red, green, blue, alpha);
        }
    }

    private static Vector3f[] corners(Vector3f center, Vector3f side, Vector3f up) {
        return new Vector3f[] {
                new Vector3f(center).add(side).add(up),
                new Vector3f(center).sub(side).add(up),
                new Vector3f(center).sub(side).sub(up),
                new Vector3f(center).add(side).sub(up)
        };
    }

    private static void cube(
            Matrix4f matrix, VertexConsumer consumer,
            float x, float y, float z, float radius,
            float red, float green, float blue, float alpha) {
        Vector3f[] points = {
                new Vector3f(x - radius, y - radius, z - radius),
                new Vector3f(x + radius, y - radius, z - radius),
                new Vector3f(x + radius, y + radius, z - radius),
                new Vector3f(x - radius, y + radius, z - radius),
                new Vector3f(x - radius, y - radius, z + radius),
                new Vector3f(x + radius, y - radius, z + radius),
                new Vector3f(x + radius, y + radius, z + radius),
                new Vector3f(x - radius, y + radius, z + radius)
        };
        coloredQuad(matrix, consumer, points[0], points[1], points[2], points[3], red, green, blue, alpha);
        coloredQuad(matrix, consumer, points[5], points[4], points[7], points[6], red, green, blue, alpha);
        coloredQuad(matrix, consumer, points[4], points[0], points[3], points[7], red, green, blue, alpha);
        coloredQuad(matrix, consumer, points[1], points[5], points[6], points[2], red, green, blue, alpha);
        coloredQuad(matrix, consumer, points[3], points[2], points[6], points[7], red, green, blue, alpha);
        coloredQuad(matrix, consumer, points[4], points[5], points[1], points[0], red, green, blue, alpha);
    }

    private static void coloredQuad(
            Matrix4f matrix, VertexConsumer consumer,
            Vector3f a, Vector3f b, Vector3f c, Vector3f d,
            float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, a.x, a.y, a.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, b.x, b.y, b.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, c.x, c.y, c.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, d.x, d.y, d.z).setColor(red, green, blue, alpha);
    }

    private static void quad(
            Matrix4f matrix, VertexConsumer consumer,
            float x0, float y, float z0, float x1, float y1, float z1,
            float width, float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, x0 - width, y, z0 - width).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1 - width, y1, z1 - width).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x1 + width, y1, z1 + width).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x0 + width, y, z0 + width).setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(ThaeIvenHeartEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private record Segment(float x0, float y0, float z0,
                           float x1, float y1, float z1,
                           float thickness, int group) {
    }

    private record Node(float x, float y, float z, float revealAt, float phase) {
    }

    private record Lattice(List<Segment> segments, List<Node> nodes) {
    }
}
