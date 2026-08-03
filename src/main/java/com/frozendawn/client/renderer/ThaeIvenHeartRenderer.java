package com.frozendawn.client.renderer;

import com.frozendawn.client.CognitiveLoadClientState;
import com.frozendawn.client.HeartEchoClient;
import com.frozendawn.client.HeartMemoryNodeClient;
import com.frozendawn.entity.ThaeIvenHeartEntity;
import com.frozendawn.homo.CognitiveLoadPolicy;
import com.frozendawn.homo.HeartCollapseStage;
import com.frozendawn.homo.HeartFormationStage;
import com.frozendawn.homo.HeartLattice;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached deterministic blue-black lattice renderer for the persistent Heart. */
public final class ThaeIvenHeartRenderer extends EntityRenderer<ThaeIvenHeartEntity> {
    private static final Map<Long, HeartLattice.Lattice> CACHE =
            new ConcurrentHashMap<>();
    private static final ResourceLocation END_CRYSTAL_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/end_crystal/end_crystal.png");
    private static final RenderType CRYSTAL_CORE_RENDER_TYPE =
            RenderType.entityCutoutNoCull(END_CRYSTAL_TEXTURE);
    private static final RenderType CRYSTAL_SHELL_RENDER_TYPE =
            RenderType.entityTranslucent(END_CRYSTAL_TEXTURE);
    private static final float SIN_45 = (float) Math.sin(Math.PI / 4.0D);
    private final ModelPart crystalCube;
    private final ModelPart crystalGlass;

    public ThaeIvenHeartRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
        ModelPart crystal = context.bakeLayer(ModelLayers.END_CRYSTAL);
        crystalCube = crystal.getChild("cube");
        crystalGlass = crystal.getChild("glass");
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
            applyMaeveErasurePose(heart, poseStack, partialTick);
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
                    heart.collapseProgress())
                    || !survivesMaeveErasure(index,
                    heart.maeveErasureProgress())) {
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
            boolean hittable = active && HeartLattice.isNodeHittable(
                    node.index(), CognitiveLoadClientState.loadPercent(), echoExposed);
            float damagePulse = active
                    ? 1.0F + heart.activeNodeDamage() * 0.10F
                    * Mth.sin((heart.tickCount + partialTick) * 0.72F)
                    : 1.0F;
            float radius = (echoExposed && hittable
                    ? 1.48F : hittable ? 0.94F : 0.60F)
                    * damagePulse;
            float alpha = echoExposed && hittable ? 1.0F : hittable
                    ? pulse : 0.16F + pulse * 0.10F;
            cube(pose.pose(), light, node.x(), node.y(), node.z(), radius,
                    echoExposed && hittable ? 0.42F : hittable ? 0.16F : 0.04F,
                    echoExposed && hittable ? 1.0F : hittable ? 0.88F : 0.28F,
                    1.0F, alpha);
        }
        if (heart.maeveFormationProgress() > 0.0F) {
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

    private void renderDormantMaeve(
            ThaeIvenHeartEntity heart,
            PoseStack poseStack,
            MultiBufferSource buffers,
            float partialTick) {
        float overall = heart.maeveErasureProgress();
        float formation = heart.maeveFormationProgress();
        float formationScale = Mth.lerp(formation, 0.08F, 1.0F);
        float breakPoint = 120.0F / 220.0F;
        float erasure = Mth.clamp(overall / breakPoint, 0.0F, 1.0F);
        float forge = Mth.clamp((overall - breakPoint) / (1.0F - breakPoint),
                0.0F, 1.0F);
        float channel = HeartMemoryNodeClient.maeveChannelProgress(heart);
        float pulse = 0.78F + 0.22F * Mth.sin(
                (heart.tickCount + partialTick) * 0.045F);
        float shellFade = 1.0F - erasure;
        shellFade = shellFade * shellFade * (3.0F - 2.0F * shellFade);
        float shellScale = Mth.lerp(erasure, 1.0F, 0.38F) * formationScale;
        float coreScale = (forge > 0.0F
                ? Mth.lerp(forge, 0.52F, 0.68F)
                : Mth.lerp(erasure, 1.0F + channel * 0.80F, 0.52F))
                * Mth.lerp(formation, 0.18F, 1.0F);
        float age = heart.tickCount + partialTick;
        float shellRotation = age * (1.25F + channel * 1.8F);
        float coreRotation = age * (2.2F + erasure * 1.8F + forge * 42.0F);

        float assemblyDrift = 1.0F - formation;
        int overlay = OverlayTexture.NO_OVERLAY;
        if (shellFade > 0.01F && shellScale > 0.01F) {
            VertexConsumer shell = buffers.getBuffer(CRYSTAL_SHELL_RENDER_TYPE);
            int outerAlpha = Mth.clamp(Math.round(shellFade * 255.0F), 0, 255);
            int innerAlpha = Mth.clamp(Math.round(shellFade * 230.0F), 0, 255);
            poseStack.pushPose();
            poseStack.translate(
                    -0.7F + Mth.sin(age * 0.11F) * assemblyDrift * 1.6F,
                    -3.4F + assemblyDrift * 2.8F,
                    0.2F + Mth.cos(age * 0.09F) * assemblyDrift * 1.3F);
            poseStack.scale(5.4F * shellScale, 5.4F * shellScale,
                    5.4F * shellScale);
            poseStack.mulPose(Axis.YP.rotationDegrees(shellRotation));
            poseStack.mulPose(new Quaternionf().setAngleAxis(
                    (float) (Math.PI / 3.0D), SIN_45, 0.0F, SIN_45));
            crystalGlass.render(poseStack, shell, LightTexture.FULL_BRIGHT,
                    overlay, outerAlpha << 24 | 0x082238);
            poseStack.scale(0.82F, 0.82F, 0.82F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-shellRotation * 0.72F));
            poseStack.mulPose(new Quaternionf().setAngleAxis(
                    (float) (Math.PI / 3.0D), SIN_45, 0.0F, SIN_45));
            crystalGlass.render(poseStack, shell, LightTexture.FULL_BRIGHT,
                    overlay, innerAlpha << 24 | 0x0A5475);
            poseStack.popPose();
        }

        VertexConsumer crystal = buffers.getBuffer(CRYSTAL_CORE_RENDER_TYPE);
        poseStack.pushPose();
        poseStack.translate(-0.7F, -3.4F, 0.2F);
        float visibleCoreScale = 3.45F * coreScale;
        poseStack.scale(visibleCoreScale, visibleCoreScale, visibleCoreScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(coreRotation));
        poseStack.mulPose(Axis.XP.rotationDegrees(coreRotation * 0.61F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-coreRotation * 0.37F));
        crystalCube.render(poseStack, crystal, LightTexture.FULL_BRIGHT,
                overlay, 0xFF14BEE6);
        poseStack.popPose();

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer core = buffers.getBuffer(RenderType.lightning());
        cube(matrix, core, -0.7F, -3.4F, 0.2F,
                Math.max(0.12F, 0.92F * coreScale),
                0.08F + erasure * 0.72F,
                0.78F + erasure * 0.22F,
                1.0F, (0.76F + channel * 0.22F) * pulse * formation);
        for (int branch = 0; branch < 9; branch++) {
            double angle = branch * Math.PI * 2.0D / 9.0D
                    + age * 0.006D + 0.35D;
            float x = -0.7F + (float) Math.cos(angle) * 4.2F;
            float y = -3.4F + (branch % 3 - 1) * 1.25F;
            float z = 0.2F + (float) Math.sin(angle) * 3.3F;
            beam(matrix, core, new HeartLattice.Segment(
                    -0.7F, -3.4F, 0.2F, x, y, z, 0.11F, 0),
                    0.04F + erasure * 0.28F,
                    0.36F + channel * 0.30F,
                    0.72F + erasure * 0.28F,
                    0.48F * pulse * formation * (1.0F - erasure * 0.82F));
        }
    }

    private static void applyMaeveErasurePose(
            ThaeIvenHeartEntity heart, PoseStack poseStack, float partialTick) {
        float progress = heart.maeveErasureProgress();
        if (progress <= 0.0F) {
            return;
        }
        float age = heart.tickCount + partialTick;
        float amplitude = 0.035F + progress * 0.14F;
        poseStack.translate(
                Mth.sin(age * 1.73F) * amplitude,
                Mth.sin(age * 2.11F) * amplitude * 0.45F,
                Mth.cos(age * 1.41F) * amplitude);
    }

    private static boolean survivesMaeveErasure(int index, float progress) {
        if (progress <= 0.50F) {
            return true;
        }
        float shatterProgress = Mth.clamp((progress - 0.50F) / 0.22F,
                0.0F, 1.0F);
        float threshold = Math.floorMod(index * 53 + 19, 101) / 100.0F;
        return threshold >= shatterProgress * 0.98F;
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
