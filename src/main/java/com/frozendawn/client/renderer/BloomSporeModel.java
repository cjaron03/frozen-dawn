package com.frozendawn.client.renderer;

import com.frozendawn.entity.BloomSporeEntity;
import com.frozendawn.bloom.BloomSporePolicy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Constant, smooth locomotion without head tracking or idle gestures. */
public final class BloomSporeModel extends HumanoidModel<BloomSporeEntity> {
    public BloomSporeModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(BloomSporeEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, 0.0F, 0.0F);
        head.xRot = 0.04F;
        head.yRot = 0.0F;
        head.zRot = -0.035F;
        hat.copyFrom(head);
        body.xRot = 0.025F;
        rightArm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI)
                * 0.62F * limbSwingAmount - 0.10F;
        leftArm.xRot = Mth.cos(limbSwing * 0.6662F)
                * 0.62F * limbSwingAmount - 0.10F;
        rightArm.zRot = 0.04F;
        leftArm.zRot = -0.04F;
        if (entity.isSignalPaused()) {
            rightLeg.xRot = 0.18F;
            leftLeg.xRot = -0.18F;
            rightArm.xRot = -0.08F;
            leftArm.xRot = 0.08F;
        }
        if (entity.isRooting()) {
            float impact = Mth.clamp(entity.rootingProgress(0.0F)
                    * BloomSporePolicy.COLLAPSE_TICKS
                    / BloomSporePolicy.COLLAPSE_IMPACT_TICKS, 0.0F, 1.0F);
            float settle = impact * impact * (3.0F - 2.0F * impact);
            body.xRot = Mth.lerp(settle, body.xRot, 0.08F);
            head.xRot = Mth.lerp(settle, head.xRot, 0.30F);
            head.zRot = Mth.lerp(settle, head.zRot, -0.10F);
            rightArm.xRot = Mth.lerp(settle, rightArm.xRot, 0.65F);
            rightArm.zRot = Mth.lerp(settle, rightArm.zRot, 0.22F);
            leftArm.xRot = Mth.lerp(settle, leftArm.xRot, -0.20F);
            leftArm.zRot = Mth.lerp(settle, leftArm.zRot, -0.40F);
            rightLeg.xRot = Mth.lerp(settle, rightLeg.xRot, 0.30F);
            leftLeg.xRot = Mth.lerp(settle, leftLeg.xRot, -0.24F);
            hat.copyFrom(head);
        }
    }
}
