package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.FrostwritheState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * One connected colony body. The server still owns the real Frostmites that
 * appear on breakup; this model only gives the assembled state one silhouette.
 */
public final class FrostwritheModel extends EntityModel<FrostwritheEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frostwrithe"),
            "main");
    private static final int SEGMENT_COUNT = 10;

    private final ModelPart root;
    private final ModelPart[] segments = new ModelPart[SEGMENT_COUNT];
    private final ModelPart[] leftLegs = new ModelPart[SEGMENT_COUNT];
    private final ModelPart[] rightLegs = new ModelPart[SEGMENT_COUNT];
    private final ModelPart leftMandible;
    private final ModelPart rightMandible;

    public FrostwritheModel(ModelPart root) {
        this.root = root;
        ModelPart segment = root.getChild("segment_0");
        for (int index = 0; index < SEGMENT_COUNT; index++) {
            segments[index] = segment;
            leftLegs[index] = segment.getChild("left_leg");
            rightLegs[index] = segment.getChild("right_leg");
            if (index + 1 < SEGMENT_COUNT) {
                segment = segment.getChild("segment_" + (index + 1));
            }
        }
        leftMandible = segments[0].getChild("left_mandible");
        rightMandible = segments[0].getChild("right_mandible");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition segment = root.addOrReplaceChild("segment_0",
                headGeometry(), PartPose.offset(0.0F, 20.0F, -20.0F));
        addLegs(segment, 6.0F, 0);
        segment.addOrReplaceChild("left_mandible",
                CubeListBuilder.create().texOffs(0, 36)
                        .addBox(-6.5F, -0.8F, -1.1F, 7.0F, 1.6F, 2.2F),
                PartPose.offsetAndRotation(-3.8F, 0.1F, -4.8F,
                        0.08F, 0.52F, -0.18F));
        segment.addOrReplaceChild("right_mandible",
                CubeListBuilder.create().texOffs(0, 41)
                        .mirror()
                        .addBox(-0.5F, -0.8F, -1.1F, 7.0F, 1.6F, 2.2F),
                PartPose.offsetAndRotation(3.8F, 0.1F, -4.8F,
                        0.08F, -0.52F, 0.18F));

        for (int index = 1; index < SEGMENT_COUNT; index++) {
            float width = index == SEGMENT_COUNT - 1
                    ? 5.2F : 12.6F - index * 0.72F;
            float height = Math.max(4.5F, 7.8F - index * 0.34F);
            float depth = index == SEGMENT_COUNT - 1 ? 9.0F : 8.4F;
            // Deep overlap is what makes the chain read as one body while it bends.
            float step = index < 3 ? 4.4F : index < 7 ? 4.1F : 3.8F;
            segment = segment.addOrReplaceChild("segment_" + index,
                    segmentGeometry(index, width, height, depth),
                    PartPose.offset(0.0F, -0.05F, step));
            addLegs(segment, width * 0.48F, index);
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static CubeListBuilder headGeometry() {
        return CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-6.5F, -4.8F, -5.5F, 13.0F, 8.0F, 10.5F)
                .texOffs(52, 0)
                .addBox(-5.5F, -6.0F, -3.9F, 11.0F, 2.4F, 7.6F)
                .texOffs(52, 12)
                .addBox(-6.1F, -5.25F, -1.5F, 2.2F, 2.2F, 5.4F)
                .texOffs(68, 12)
                .addBox(3.9F, -5.25F, -1.5F, 2.2F, 2.2F, 5.4F);
    }

    private static CubeListBuilder segmentGeometry(int index, float width,
                                                    float height, float depth) {
        float bottom = 3.15F;
        float plateWidth = Math.max(3.0F, width - 2.1F - (index % 3) * 0.65F);
        float plateDepth = Math.max(3.5F, depth - 2.3F);
        float plateOffset = (index % 2 == 0 ? -0.35F : 0.35F)
                + (index % 3 - 1) * 0.16F;
        CubeListBuilder builder = CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-width * 0.5F, bottom - height, -depth * 0.5F,
                        width, height, depth)
                .texOffs(52, 0)
                .addBox(-plateWidth * 0.5F + plateOffset,
                        bottom - height - 1.45F,
                        -plateDepth * 0.5F + (index % 2 == 0 ? 0.2F : -0.2F),
                        plateWidth, 2.0F, plateDepth);
        if (index == SEGMENT_COUNT - 1) {
            builder.texOffs(88, 0)
                    .addBox(-3.2F, -1.8F, 2.0F, 2.0F, 2.0F, 8.0F)
                    .texOffs(88, 12)
                    .addBox(1.2F, -1.8F, 2.0F, 2.0F, 2.0F, 8.0F);
        }
        return builder;
    }

    private static void addLegs(PartDefinition segment, float side, int index) {
        float back = (index % 2 == 0 ? 0.65F : -0.65F);
        segment.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 36)
                        .addBox(-4.2F, -0.65F, -1.0F, 4.5F, 1.3F, 2.0F)
                        .texOffs(20, 36)
                        .addBox(-6.8F, -0.5F, -0.75F, 3.2F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(-side, 1.5F, back,
                        0.0F, 0.08F, 0.44F));
        segment.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 41)
                        .mirror()
                        .addBox(-0.3F, -0.65F, -1.0F, 4.5F, 1.3F, 2.0F)
                        .texOffs(20, 41)
                        .addBox(3.6F, -0.5F, -0.75F, 3.2F, 1.0F, 1.5F),
                PartPose.offsetAndRotation(side, 1.5F, -back,
                        0.0F, -0.08F, -0.44F));
    }

    @Override
    public void setupAnim(FrostwritheEntity entity, float limbSwing,
                          float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        int visible = Mth.clamp(entity.visibleBodies(), 2, SEGMENT_COUNT);
        float movement = Mth.clamp(limbSwingAmount * 2.4F, 0.0F, 1.0F);
        float travel = limbSwing * 0.78F;
        FrostwritheState state = entity.activityState();

        for (int index = 0; index < SEGMENT_COUNT; index++) {
            ModelPart segment = segments[index];
            segment.visible = index < visible;
            float phase = index * 0.66F;
            float scuttle = Mth.sin(travel - phase);
            float idle = Mth.sin(ageInTicks * 0.085F - phase) * 0.012F;
            segment.yRot = scuttle * movement * 0.052F + idle;
            segment.xRot = Mth.cos(travel - phase) * movement * 0.018F;

            float legStride = Mth.sin(travel * 1.65F - phase * 1.35F);
            leftLegs[index].yRot = 0.08F + legStride * movement * 0.42F;
            rightLegs[index].yRot = -0.08F - legStride * movement * 0.42F;
            leftLegs[index].zRot = 0.44F + Math.abs(legStride) * movement * 0.16F;
            rightLegs[index].zRot = -leftLegs[index].zRot;
        }

        float feel = Mth.sin(ageInTicks * 0.17F) * 0.08F;
        leftMandible.yRot = 0.52F + feel;
        rightMandible.yRot = -0.52F - feel;

        if (state == FrostwritheState.ASSEMBLING) {
            float gather = Mth.clamp(entity.stateTicks() / 60.0F, 0.0F, 1.0F);
            for (int index = 0; index < visible; index++) {
                float side = (index & 1) == 0 ? -1.0F : 1.0F;
                segments[index].x += side * (1.0F - gather) * (7.0F + index * 0.5F);
                segments[index].y -= (1.0F - gather) * (3.0F + index * 0.25F);
                segments[index].yRot += side * (1.0F - gather) * 0.32F;
            }
        } else if (state == FrostwritheState.SHELL) {
            for (int index = 0; index < visible; index++) {
                segments[index].yRot *= 0.2F;
                segments[index].xRot *= 0.25F;
                leftLegs[index].zRot = 0.18F;
                rightLegs[index].zRot = -0.18F;
            }
        } else if (state == FrostwritheState.CLIMBER) {
            root.xRot = -0.36F;
        } else if (state == FrostwritheState.BRIDGING) {
            for (int index = 0; index < visible; index++) {
                segments[index].xRot = -0.035F * index / visible;
                segments[index].yRot *= 0.25F;
            }
        } else if (state == FrostwritheState.OVERRUN) {
            for (int index = 0; index < visible; index++) {
                segments[index].yRot *= 1.55F;
            }
        } else if (state == FrostwritheState.DISASSEMBLING) {
            float split = Mth.clamp(entity.stateTicks() / 18.0F, 0.0F, 1.0F);
            for (int index = 0; index < visible; index++) {
                float side = (index & 1) == 0 ? -1.0F : 1.0F;
                segments[index].x += side * split * (1.2F + index * 0.22F);
                segments[index].y -= split * (index % 3) * 0.55F;
                segments[index].yRot += side * split * (0.10F + index * 0.015F);
                segments[index].zRot = side * split * 0.08F;
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay, int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
