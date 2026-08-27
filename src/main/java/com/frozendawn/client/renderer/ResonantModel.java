package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.ResonantState;
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

/** A three-block-tall absence arranged around a resonating rib cage. */
public final class ResonantModel extends EntityModel<ResonantEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "resonant"), "main");

    private final ModelPart root;
    private final ModelPart trunk;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart cavity;
    private final ModelPart upperLeftRib;
    private final ModelPart upperRightRib;
    private final ModelPart lowerLeftRib;
    private final ModelPart lowerRightRib;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightForearm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ResonantModel(ModelPart root) {
        this.root = root;
        trunk = root.getChild("trunk");
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        cavity = trunk.getChild("cavity");
        upperLeftRib = trunk.getChild("upper_left_rib");
        upperRightRib = trunk.getChild("upper_right_rib");
        lowerLeftRib = trunk.getChild("lower_left_rib");
        lowerRightRib = trunk.getChild("lower_right_rib");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightForearm = rightArm.getChild("forearm");
        leftForearm = leftArm.getChild("forearm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition trunk = root.addOrReplaceChild("trunk",
                CubeListBuilder.create()
                        .texOffs(0, 24)
                        .addBox(-1.5F, 0.0F, -1.3F, 3.0F, 20.0F, 3.0F)
                        .texOffs(16, 24)
                        .addBox(-6.0F, 0.0F, -1.5F, 12.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F,
                        0.12F, 0.0F, 0.0F));
        trunk.addOrReplaceChild("cavity",
                CubeListBuilder.create().texOffs(34, 24)
                        .addBox(-2.7F, 3.0F, -2.45F, 5.4F, 12.0F, 1.0F),
                PartPose.ZERO);
        trunk.addOrReplaceChild("sternum",
                CubeListBuilder.create().texOffs(48, 24)
                        .addBox(-0.7F, 3.0F, -3.0F, 1.4F, 13.5F, 1.3F),
                PartPose.ZERO);
        trunk.addOrReplaceChild("upper_left_rib",
                CubeListBuilder.create().texOffs(0, 50)
                        .addBox(0.0F, -1.0F, -2.7F, 6.3F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.2F, 5.2F, 0.0F,
                        0.0F, 0.0F, 0.23F));
        trunk.addOrReplaceChild("upper_right_rib",
                CubeListBuilder.create().texOffs(0, 55)
                        .addBox(-6.3F, -1.0F, -2.7F, 6.3F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-0.2F, 5.2F, 0.0F,
                        0.0F, 0.0F, -0.23F));
        trunk.addOrReplaceChild("lower_left_rib",
                CubeListBuilder.create().texOffs(18, 50)
                        .addBox(0.0F, -1.0F, -2.65F, 5.8F, 1.8F, 1.8F),
                PartPose.offsetAndRotation(0.2F, 11.1F, 0.0F,
                        0.0F, 0.0F, 0.34F));
        trunk.addOrReplaceChild("lower_right_rib",
                CubeListBuilder.create().texOffs(18, 55)
                        .addBox(-5.8F, -1.0F, -2.65F, 5.8F, 1.8F, 1.8F),
                PartPose.offsetAndRotation(-0.2F, 11.1F, 0.0F,
                        0.0F, 0.0F, -0.34F));

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.5F, -8.0F, -3.5F, 7.0F, 8.0F, 7.0F)
                        .texOffs(30, 0)
                        .addBox(-4.1F, -5.0F, -3.8F, 8.2F, 2.0F, 7.6F),
                PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F,
                        0.23F, 0.0F, -0.035F));
        head.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(64, 0)
                        .addBox(-3.0F, 0.0F, -1.5F, 6.0F, 6.0F, 2.5F),
                PartPose.offset(0.0F, -1.0F, -3.0F));

        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 64)
                        .addBox(-2.4F, -1.0F, -1.35F, 2.8F, 13.0F, 2.8F),
                PartPose.offsetAndRotation(-6.0F, -2.0F, 0.0F,
                        0.05F, 0.0F, 0.08F));
        rightArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(12, 64)
                        .addBox(-1.2F, 0.0F, -1.2F, 2.4F, 15.0F, 2.4F),
                PartPose.offsetAndRotation(-1.0F, 11.5F, 0.0F,
                        -0.06F, 0.0F, 0.05F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(24, 64)
                        .addBox(-0.4F, -1.0F, -1.35F, 2.8F, 13.0F, 2.8F),
                PartPose.offsetAndRotation(6.0F, -2.0F, 0.0F,
                        -0.03F, 0.0F, -0.08F));
        leftArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(36, 64)
                        .addBox(-1.2F, 0.0F, -1.2F, 2.4F, 15.0F, 2.4F),
                PartPose.offsetAndRotation(1.0F, 11.5F, 0.0F,
                        0.08F, 0.0F, -0.05F));

        PartDefinition rightLeg = root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(50, 64)
                        .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
                PartPose.offset(-2.0F, 16.0F, 0.0F));
        rightLeg.addOrReplaceChild("lower",
                CubeListBuilder.create().texOffs(64, 64)
                        .addBox(-1.1F, 0.0F, -1.1F, 2.2F, 10.0F, 2.2F),
                PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F,
                        0.03F, 0.0F, 0.02F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(76, 64)
                        .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
                PartPose.offset(2.0F, 16.0F, 0.0F));
        leftLeg.addOrReplaceChild("lower",
                CubeListBuilder.create().texOffs(90, 64)
                        .addBox(-1.1F, 0.0F, -1.1F, 2.2F, 10.0F, 2.2F),
                PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F,
                        -0.02F, 0.0F, -0.02F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(ResonantEntity entity, float limbSwing,
                          float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        ResonantState state = entity.activityState();
        float suspended = Mth.sin(ageInTicks * 0.055F + entity.getId()) * 0.028F;
        float pulse = Mth.sin(ageInTicks * 0.22F) * 0.10F;

        trunk.xRot = 0.12F + suspended;
        head.xRot = 0.23F - suspended * 1.4F;
        head.zRot = -0.035F;
        rightArm.xRot = 0.05F + suspended * 1.8F;
        leftArm.xRot = -0.03F - suspended * 1.5F;
        rightForearm.xRot = -0.06F + pulse * 0.12F;
        leftForearm.xRot = 0.08F - pulse * 0.12F;
        rightLeg.xRot = suspended * 0.45F;
        leftLeg.xRot = -suspended * 0.45F;

        float confidenceOpen = Mth.clamp((entity.confidence() - 45.0F) / 55.0F,
                0.0F, 1.0F);
        cavity.xScale = 1.0F + pulse * (0.15F + confidenceOpen * 0.35F);
        cavity.yScale = 1.0F - pulse * 0.08F;
        upperLeftRib.zRot = 0.23F + confidenceOpen * 0.15F + pulse * 0.04F;
        upperRightRib.zRot = -upperLeftRib.zRot;
        lowerLeftRib.zRot = 0.34F + confidenceOpen * 0.22F - pulse * 0.035F;
        lowerRightRib.zRot = -lowerLeftRib.zRot;

        jaw.visible = entity.confidence() >= 80.0F
                || state == ResonantState.BREACHING
                || state == ResonantState.GRABBING;
        if (jaw.visible) {
            jaw.xRot = 0.18F + confidenceOpen * 0.52F
                    + Mth.sin(ageInTicks * 0.36F) * 0.045F;
        }

        if (entity.deathTime > 0) {
            applyDeathPose(entity.deathTime, ageInTicks);
            return;
        }

        if (state == ResonantState.TRIANGULATING) {
            head.yRot = Mth.sin(ageInTicks * 0.09F) * 0.11F;
            trunk.zRot = Mth.sin(ageInTicks * 0.14F) * 0.025F;
        } else if (state == ResonantState.PHASING || state == ResonantState.STALKING) {
            trunk.xRot = -0.02F;
            rightArm.xRot = 0.24F;
            leftArm.xRot = 0.19F;
            rightForearm.xRot = 0.28F;
            leftForearm.xRot = 0.24F;
        } else if (state == ResonantState.BREACHING) {
            float press = Mth.clamp(entity.stateTicks() / 20.0F, 0.0F, 1.0F);
            trunk.xRot = Mth.lerp(press, 0.12F, -0.18F);
            rightArm.xRot = Mth.lerp(press, 0.05F, -1.48F);
            leftArm.xRot = Mth.lerp(press, -0.03F, -1.48F);
            rightForearm.xRot = -0.35F;
            leftForearm.xRot = -0.35F;
        } else if (state == ResonantState.GRABBING) {
            trunk.xRot = -0.2F;
            rightArm.xRot = -1.62F;
            leftArm.xRot = -1.62F;
            rightArm.yRot = -0.32F;
            leftArm.yRot = 0.32F;
            rightForearm.xRot = -0.78F;
            leftForearm.xRot = -0.78F;
        } else if (state == ResonantState.DISORIENTED) {
            float collapse = Mth.sin(ageInTicks * 0.92F);
            trunk.xRot = 0.38F;
            trunk.zRot = collapse * 0.13F;
            head.zRot = -collapse * 0.22F;
            rightArm.zRot = 0.25F + collapse * 0.08F;
            leftArm.zRot = -0.25F - collapse * 0.08F;
        }
    }

    private void applyDeathPose(int deathTicks, float ageInTicks) {
        float failure = Mth.clamp(deathTicks / 12.0F, 0.0F, 1.0F);
        float fold = Mth.clamp((deathTicks - 10.0F) / 18.0F, 0.0F, 1.0F);
        float collapse = Mth.clamp((deathTicks - 24.0F) / 18.0F, 0.0F, 1.0F);
        float tremor = Mth.sin(ageInTicks * 2.7F) * (1.0F - collapse) * 0.055F;

        jaw.visible = true;
        jaw.xRot = Mth.lerp(failure, 0.45F, -0.12F);
        cavity.xScale = Mth.lerp(failure, 1.0F, 0.12F);
        cavity.yScale = Mth.lerp(failure, 1.0F, 1.45F);
        upperLeftRib.zRot = Mth.lerp(failure, 0.23F, -0.18F);
        upperRightRib.zRot = -upperLeftRib.zRot;
        lowerLeftRib.zRot = Mth.lerp(failure, 0.34F, -0.12F);
        lowerRightRib.zRot = -lowerLeftRib.zRot;

        trunk.xRot = Mth.lerp(fold, 0.12F, 1.08F);
        trunk.zRot = tremor;
        head.xRot = Mth.lerp(fold, 0.23F, -0.72F);
        head.zRot = -tremor * 1.8F;
        rightArm.xRot = Mth.lerp(fold, 0.05F, -1.72F);
        leftArm.xRot = Mth.lerp(fold, -0.03F, -1.72F);
        rightArm.zRot = Mth.lerp(fold, 0.08F, 0.78F);
        leftArm.zRot = Mth.lerp(fold, -0.08F, -0.78F);
        rightForearm.xRot = Mth.lerp(fold, -0.06F, -1.18F);
        leftForearm.xRot = Mth.lerp(fold, 0.08F, -1.18F);
        rightLeg.xRot = collapse * 1.18F;
        leftLeg.xRot = collapse * 1.18F;
        rightLeg.zRot = collapse * 0.24F;
        leftLeg.zRot = -collapse * 0.24F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay, int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
