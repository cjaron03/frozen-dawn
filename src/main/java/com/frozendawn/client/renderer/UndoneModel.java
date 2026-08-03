package com.frozendawn.client.renderer;

import com.frozendawn.entity.UndoneEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Returned proportions held in a posture the collective no longer corrects. */
public final class UndoneModel extends HumanoidModel<UndoneEntity> {
    public UndoneModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(UndoneEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        head.zRot = 0.16F;
        body.zRot = -0.055F;
        rightArm.xRot = rightArm.xRot * 0.58F - 0.18F;
        leftArm.xRot = leftArm.xRot * 0.42F + 0.10F;
        leftArm.zRot = -0.09F;
        rightArm.zRot = 0.04F;
        if (entity.isPaused()) {
            rightArm.xRot = 0.08F;
            leftArm.xRot = -0.04F;
        }
        if (entity.isGrasping()) {
            float tremor = (float) Math.sin(ageInTicks * 1.35F) * 0.045F;
            body.xRot = -0.08F;
            head.xRot += 0.10F;
            rightArm.xRot = -1.38F + tremor;
            leftArm.xRot = -1.38F - tremor;
            rightArm.yRot = -0.28F;
            leftArm.yRot = 0.28F;
            rightArm.zRot = 0.12F;
            leftArm.zRot = -0.12F;
        }
        if (entity.getStumbleTicks() > 0) {
            body.xRot = 0.30F;
            head.xRot -= 0.16F;
            rightArm.xRot += 0.48F;
        }
    }
}
