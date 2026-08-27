package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchivistEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** A burdened silhouette: small head, long arms, and no player-facing attention. */
public final class ArchivistModel extends HumanoidModel<ArchivistEntity> {
    public ArchivistModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(ArchivistEntity entity, float limbSwing,
                          float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        int deathTicks = entity.getMarkedDeathTicks();
        float activeSwing = deathTicks > 0 ? 0.0F : limbSwing;
        float activeAmount = deathTicks > 0 ? 0.0F : limbSwingAmount;
        super.setupAnim(entity, activeSwing, activeAmount, ageInTicks, 0.0F, 0.0F);
        head.xScale = 0.90F;
        head.yScale = 0.90F;
        head.zScale = 0.90F;
        hat.xScale = 0.90F;
        hat.yScale = 0.90F;
        hat.zScale = 0.90F;
        rightArm.yScale = 1.70F;
        leftArm.yScale = 1.70F;

        body.xRot = 0.28F;
        head.xRot = 0.56F;
        head.yRot = 0.0F;
        head.zRot = -0.05F;
        hat.copyFrom(head);
        rightArm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI)
                * 0.38F * limbSwingAmount + 0.16F;
        leftArm.xRot = Mth.cos(limbSwing * 0.6662F)
                * 0.38F * limbSwingAmount + 0.22F;
        rightArm.zRot = 0.12F;
        leftArm.zRot = -0.18F;
        rightLeg.xRot *= 0.72F;
        leftLeg.xRot *= 0.72F;

        if (deathTicks > 0) {
            applyMarkedDeathPose(deathTicks, ageInTicks);
        }
    }

    private void applyMarkedDeathPose(int deathTicks, float ageInTicks) {
        float scream = Mth.clamp((deathTicks - 20.0F) / 7.0F, 0.0F, 1.0F);
        float tremor = Mth.sin(ageInTicks * (scream > 0.0F ? 3.8F : 2.5F));
        float intensity = Mth.lerp(scream, 0.025F, 0.080F);

        body.xRot = Mth.lerp(scream, 0.20F, -0.08F);
        body.zRot = tremor * intensity;
        head.xRot = Mth.lerp(scream, 0.18F, -0.52F);
        head.yRot = tremor * intensity * 0.7F;
        head.zRot = -tremor * intensity;
        hat.copyFrom(head);

        // The arms fall into an open, emptied posture at the scream.
        rightArm.xRot = Mth.lerp(scream, 0.18F, 0.48F);
        leftArm.xRot = Mth.lerp(scream, 0.22F, 0.48F);
        rightArm.yRot = Mth.lerp(scream, 0.0F, -0.42F);
        leftArm.yRot = Mth.lerp(scream, 0.0F, 0.42F);
        rightArm.zRot = Mth.lerp(scream, 0.12F, 0.58F) + tremor * intensity;
        leftArm.zRot = Mth.lerp(scream, -0.18F, -0.58F) - tremor * intensity;
        rightLeg.xRot = 0.0F;
        leftLeg.xRot = 0.0F;
    }
}
