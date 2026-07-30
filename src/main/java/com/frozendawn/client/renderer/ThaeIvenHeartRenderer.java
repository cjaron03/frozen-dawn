package com.frozendawn.client.renderer;

import com.frozendawn.client.CognitiveLoadClientState;
import com.frozendawn.client.HeartEchoClient;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.homo.CognitiveLoadPolicy;
import com.frozendawn.homo.HeartCollapseStage;
import com.frozendawn.homo.HeartFormationStage;
import com.frozendawn.homo.HeartLattice;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached deterministic blue-black lattice renderer for the persistent Heart. */
public final class ThaeIvenHeartRenderer extends EntityRenderer<ThaeIvenHeartEntity> {
    private static final Map<Long, HeartLattice.Lattice> CACHE =
            new ConcurrentHashMap<>();

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
        HeartCollapseStage collapseStage = heart.collapseStage();
        renderFrostBlooms(heart, poseStack, buffers);
        boolean transformed = stage == HeartFormationStage.LIVE;
        if (transformed) {
            poseStack.pushPose();
            float descent = collapseStage == HeartCollapseStage.NONE
                    ? CognitiveLoadClientState.heartDescentBlocks()
                    : CognitiveLoadPolicy.heartDescentBlocks(
                    HeartLattice.requiredLoad(HeartLattice.NODE_COUNT - 1));
            poseStack.translate(0.0D, -descent, 0.0D);
            applyCollapsePose(heart, poseStack, partialTick);
        }
        int destroyedMask = heart.destroyedNodeMask();
        HeartLattice.Lattice lattice = CACHE.computeIfAbsent(
                heart.layoutSeed(), HeartLattice::create);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer dark = buffers.getBuffer(RenderType.debugQuads());
        int visible = Math.max(1, Mth.ceil(lattice.segments().size() * reveal));
        for (int index = 0; index < visible; index++) {
            HeartLattice.Segment segment = lattice.segments().get(index);
            if (removedByDestroyedNode(segment.group(), destroyedMask)) {
                continue;
            }
            if (!survivesCollapse(index, collapseStage,
                    heart.collapseProgress())) {
                continue;
            }
            float pulse = 0.86F + 0.14F * Mth.sin(
                    (heart.tickCount + partialTick) * 0.035F
                            + segment.group() * 0.8F);
            beam(pose.pose(), dark, segment, 0.015F, 0.035F, 0.075F,
                    (0.78F + 0.16F * heart.fieldStrength()) * pulse);
        }
        VertexConsumer light = buffers.getBuffer(RenderType.lightning());
        int activeNode = HeartLattice.nextNode(destroyedMask);
        for (HeartLattice.Node node : lattice.nodes()) {
            if (reveal + 0.001F < node.revealAt()
                    || HeartLattice.isDestroyed(destroyedMask, node.index())) {
                continue;
            }
            float pulse = 0.72F + 0.28F * Mth.sin(
                    (heart.tickCount + partialTick) * 0.09F + node.phase());
            boolean active = node.index() == activeNode;
            boolean echoExposed = active && HeartEchoClient.isNodeExposed(node.index());
            float damagePulse = active
                    ? 1.0F + heart.activeNodeDamage() * 0.10F
                    * Mth.sin((heart.tickCount + partialTick) * 0.72F)
                    : 1.0F;
            float radius = (echoExposed ? 1.48F : active ? 0.94F : 0.60F)
                    * damagePulse;
            float alpha = echoExposed ? 1.0F : active
                    ? pulse : 0.16F + pulse * 0.10F;
            cube(pose.pose(), light, node.x(), node.y(), node.z(), radius,
                    echoExposed ? 0.42F : active ? 0.16F : 0.04F,
                    echoExposed ? 1.0F : active ? 0.88F : 0.28F,
                    1.0F, alpha);
        }
        if (heart.maeveExposed()) {
            renderDormantMaeve(heart, poseStack, buffers, partialTick);
        }
        super.render(heart, yaw, partialTick, poseStack, buffers, packedLight);
        if (transformed) {
            poseStack.popPose();
        }
    }

    private static void applyCollapsePose(
            ThaeIvenHeartEntity heart, PoseStack poseStack, float partialTick) {
        HeartCollapseStage stage = heart.collapseStage();
        float progress = heart.collapseProgress();
        if (stage == HeartCollapseStage.NONE) {
            return;
        }
        if (stage == HeartCollapseStage.RUPTURE) {
            float shake = (0.03F + progress * 0.11F)
                    * Mth.sin((heart.tickCount + partialTick) * 1.7F);
            poseStack.translate(shake, Math.abs(shake) * 0.45F, -shake * 0.6F);
            return;
        }
        float fall = stage == HeartCollapseStage.FALL ? progress : 1.0F;
        poseStack.translate(0.0D, -2.4D * fall, 0.0D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(14.0F * fall));
        poseStack.mulPose(Axis.XP.rotationDegrees(-5.0F * fall));
        if (stage == HeartCollapseStage.SETTLE) {
            float settle = (1.0F - progress) * 0.05F
                    * Mth.sin((heart.tickCount + partialTick) * 0.9F);
            poseStack.translate(settle, 0.0D, -settle);
        }
    }

    private static boolean survivesCollapse(
            int segmentIndex, HeartCollapseStage stage, float progress) {
        if (stage == HeartCollapseStage.NONE || stage == HeartCollapseStage.RUPTURE) {
            return true;
        }
        float removal = stage == HeartCollapseStage.FALL
                ? progress * 0.72F : 0.72F;
        float deterministic = Math.floorMod(segmentIndex * 37 + 11, 101) / 100.0F;
        return deterministic >= removal;
    }

    private static void renderDormantMaeve(
            ThaeIvenHeartEntity heart,
            PoseStack poseStack,
            MultiBufferSource buffers,
            float partialTick) {
        Matrix4f matrix = poseStack.last().pose();
        float pulse = 0.78F + 0.22F * Mth.sin(
                (heart.tickCount + partialTick) * 0.045F);
        VertexConsumer shell = buffers.getBuffer(RenderType.debugQuads());
        cube(matrix, shell, -0.7F, -3.4F, 0.2F, 2.8F,
                0.005F, 0.012F, 0.026F, 0.96F);
        cube(matrix, shell, -0.7F, -3.4F, 0.2F, 2.15F,
                0.018F, 0.045F, 0.080F, 0.88F);
        VertexConsumer core = buffers.getBuffer(RenderType.lightning());
        cube(matrix, core, -0.7F, -3.4F, 0.2F, 0.72F,
                0.10F, 0.72F, 1.0F, 0.76F * pulse);
        for (int branch = 0; branch < 7; branch++) {
            double angle = branch * Math.PI * 2.0D / 7.0D + 0.35D;
            float x = -0.7F + (float) Math.cos(angle) * 3.7F;
            float y = -3.4F + (branch % 3 - 1) * 1.25F;
            float z = 0.2F + (float) Math.sin(angle) * 2.8F;
            beam(matrix, core, new HeartLattice.Segment(
                    -0.7F, -3.4F, 0.2F, x, y, z, 0.11F, 0),
                    0.04F, 0.36F, 0.72F, 0.48F * pulse);
        }
    }

    private static boolean removedByDestroyedNode(int group, int destroyedMask) {
        return (HeartLattice.isDestroyed(destroyedMask, 0) && (group == 0 || group == 1))
                || (HeartLattice.isDestroyed(destroyedMask, 1)
                && (group == 3 || group == 4))
                || (HeartLattice.isDestroyed(destroyedMask, 2)
                && (group == 6 || group == 7))
                || (HeartLattice.isDestroyed(destroyedMask, 3) && group == 9)
                || (HeartLattice.isDestroyed(destroyedMask, 4) && group == 11);
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

    private static void renderFrostBlooms(
            ThaeIvenHeartEntity heart,
            PoseStack poseStack,
            MultiBufferSource buffers) {
        int rings = Math.min(4, HeartLattice.destroyedCount(
                heart.destroyedNodeMask()));
        if (rings <= 0) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        float y = -29.90F;
        int segments = 72;
        for (int ring = 0; ring < rings; ring++) {
            float baseRadius = 7.0F + ring * 4.2F;
            float alpha = 0.29F - ring * 0.025F;
            for (int index = 0; index < segments; index++) {
                double a0 = index * Math.PI * 2.0D / segments;
                double a1 = (index + 1) * Math.PI * 2.0D / segments;
                float wobble0 = 0.34F * Mth.sin(
                        index * 1.91F + ring * 2.37F
                                + (heart.layoutSeed() & 31L));
                float wobble1 = 0.34F * Mth.sin(
                        (index + 1) * 1.91F + ring * 2.37F
                                + (heart.layoutSeed() & 31L));
                float radius0 = baseRadius + wobble0;
                float radius1 = baseRadius + wobble1;
                quad(
                        matrix,
                        consumer,
                        (float) Math.cos(a0) * radius0,
                        y,
                        (float) Math.sin(a0) * radius0,
                        (float) Math.cos(a1) * radius1,
                        y,
                        (float) Math.sin(a1) * radius1,
                        0.10F,
                        0.16F,
                        0.72F,
                        0.92F,
                        alpha);
            }
        }
    }

    private static void beam(
            Matrix4f matrix, VertexConsumer consumer, HeartLattice.Segment box,
            float red, float green, float blue, float alpha) {
        Vector3f start = new Vector3f(box.x0(), box.y0(), box.z0());
        Vector3f end = new Vector3f(box.x1(), box.y1(), box.z1());
        Vector3f direction = new Vector3f(end).sub(start).normalize();
        Vector3f reference = Math.abs(direction.y) > 0.92F
                ? new Vector3f(1.0F, 0.0F, 0.0F)
                : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f side = new Vector3f(direction).cross(reference).normalize()
                .mul(box.thickness());
        Vector3f up = new Vector3f(side).cross(direction).normalize()
                .mul(box.thickness());
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

}
