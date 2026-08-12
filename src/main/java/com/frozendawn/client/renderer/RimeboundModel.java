package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.RimeboundState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Thin humanoid base interrupted by a staged, irregular dorsal shell. */
public final class RimeboundModel extends HumanoidModel<RimeboundEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "rimebound"),
            "main");

    private final ModelPart shellLow;
    private final ModelPart shellMid;
    private final ModelPart shellHigh;

    public RimeboundModel(ModelPart root) {
        super(root);
        shellLow = body.getChild("shell_low");
        shellMid = body.getChild("shell_mid");
        shellHigh = body.getChild("shell_high");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(
                CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        PartDefinition head = root.getChild("head");
        PartDefinition rightArm = root.getChild("right_arm");
        PartDefinition leftArm = root.getChild("left_arm");

        body.addOrReplaceChild("shell_low",
                CubeListBuilder.create().texOffs(32, 32)
                        .addBox(-2.5F, 4.0F, 2.0F, 5.0F, 4.0F, 3.0F),
                PartPose.rotation(-0.18F, 0.0F, 0.0F));
        body.addOrReplaceChild("shell_mid",
                CubeListBuilder.create().texOffs(32, 39)
                        .addBox(-3.5F, -1.0F, 2.0F, 6.0F, 5.0F, 4.0F),
                PartPose.rotation(-0.24F, 0.11F, -0.09F));
        body.addOrReplaceChild("shell_high",
                CubeListBuilder.create().texOffs(32, 48)
                        .addBox(-2.0F, -5.0F, 1.5F, 5.0F, 5.0F, 4.0F),
                PartPose.rotation(-0.34F, -0.12F, 0.08F));
        head.addOrReplaceChild("socket_ridge",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-4.3F, -4.3F, -4.6F, 8.6F, 2.0F, 1.1F),
                PartPose.ZERO);
        rightArm.addOrReplaceChild("wedge",
                CubeListBuilder.create().texOffs(18, 32)
                        .addBox(-2.5F, 8.5F, -3.0F, 5.0F, 4.0F, 6.0F),
                PartPose.rotation(0.15F, -0.08F, 0.08F));
        leftArm.addOrReplaceChild("wedge",
                CubeListBuilder.create().texOffs(18, 42)
                        .addBox(-2.5F, 8.5F, -3.0F, 5.0F, 4.0F, 6.0F),
                PartPose.rotation(0.15F, 0.08F, -0.08F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(RimeboundEntity entity, float limbSwing,
                          float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        RimeboundState state = entity.activityState();
        float movement = state == RimeboundState.STALKING
                ? limbSwingAmount : 0.0F;
        super.setupAnim(entity, limbSwing, movement, ageInTicks,
                netHeadYaw * 0.55F, headPitch * 0.55F);

        body.xRot = 0.32F;
        head.xRot += 0.28F;
        head.zRot = -0.035F;
        hat.copyFrom(head);
        rightArm.xScale = 0.72F;
        rightArm.zScale = 0.72F;
        leftArm.xScale = 0.72F;
        leftArm.zScale = 0.72F;
        rightLeg.xScale = 0.78F;
        rightLeg.zScale = 0.78F;
        leftLeg.xScale = 0.78F;
        leftLeg.zScale = 0.78F;
        rightArm.zRot = 0.16F;
        leftArm.zRot = -0.12F;

        int shellStage = entity.shellVisualStage();
        shellLow.visible = shellStage >= 1;
        shellMid.visible = shellStage >= 2;
        shellHigh.visible = shellStage >= 3;

        if (state == RimeboundState.DORMANT) {
            body.xRot = 1.15F;
            head.xRot = 0.75F;
            rightArm.xRot = -0.2F;
            leftArm.xRot = -0.2F;
        } else if (state == RimeboundState.EMERGING) {
            float progress = Mth.clamp(entity.stateTicks() / 30.0F, 0.0F, 1.0F);
            body.xRot = Mth.lerp(progress, 1.15F, 0.32F);
            head.xRot = Mth.lerp(progress, 0.78F, 0.28F);
            float crack = Mth.sin(ageInTicks * 2.9F) * 0.045F;
            body.zRot = crack * (1.0F - progress);
        } else if (state == RimeboundState.RANGED_WINDUP) {
            float charge = Mth.clamp(entity.stateTicks() / 24.0F, 0.0F, 1.0F);
            rightArm.xRot = Mth.lerp(charge, -0.35F, -1.72F);
            rightArm.yRot = -0.28F;
            leftArm.xRot = Mth.lerp(charge, 0.1F, -0.62F);
        } else if (state == RimeboundState.LEAP_WINDUP) {
            body.xRot = 0.72F;
            rightArm.xRot = 0.45F;
            leftArm.xRot = 0.45F;
            rightLeg.xRot = -0.38F;
            leftLeg.xRot = -0.38F;
        } else if (state == RimeboundState.ARMORED) {
            float rebuild = Mth.clamp(entity.stateTicks() / 30.0F, 0.0F, 1.0F);
            body.xRot = 0.62F - rebuild * 0.3F;
            rightArm.xRot = -0.65F;
            leftArm.xRot = -0.65F;
            shellLow.visible = rebuild > 0.18F;
            shellMid.visible = rebuild > 0.48F;
            shellHigh.visible = rebuild > 0.76F;
        } else if (state == RimeboundState.DEAD) {
            float freeze = Mth.clamp(entity.stateTicks() / 20.0F, 0.0F, 1.0F);
            body.xRot = 0.25F;
            body.zRot = Mth.sin(ageInTicks * 1.8F) * (1.0F - freeze) * 0.05F;
            head.xRot = -0.18F;
            rightArm.xRot = -0.35F;
            leftArm.xRot = -0.18F;
            rightLeg.xRot = 0.0F;
            leftLeg.xRot = 0.0F;
        }
    }
}
