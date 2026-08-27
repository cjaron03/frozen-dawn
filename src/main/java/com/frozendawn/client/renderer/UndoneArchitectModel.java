package com.frozendawn.client.renderer;

import com.frozendawn.entity.UndoneArchitectEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Architect proportions continuing their motions without collective correction. */
public final class UndoneArchitectModel extends HumanoidModel<UndoneArchitectEntity> {
    public UndoneArchitectModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(
            UndoneArchitectEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount,
                ageInTicks, netHeadYaw, headPitch);
        hat.visible = false;
        head.zRot = -0.13F;
        body.zRot = 0.055F;
        rightArm.xRot = rightArm.xRot * 0.62F - 0.15F;
        leftArm.xRot = leftArm.xRot * 0.44F + 0.18F;
        leftArm.zRot = -0.11F;
        rightArm.zRot = 0.05F;
        if (entity.getAccretionTicks() > 0) {
            float settle = (float) Math.sin(ageInTicks * 2.9F) * 0.075F;
            body.xRot = 0.24F;
            body.zRot = settle;
            head.xRot = 0.16F;
            rightArm.xRot = -0.72F + settle;
            rightArm.zRot = 0.28F;
            leftArm.xRot = -0.68F - settle;
            leftArm.zRot = -0.31F;
            return;
        }
        if (entity.getBuildTicks() > 0) {
            float shake = (float) Math.sin(ageInTicks * 1.7F) * 0.06F;
            body.xRot = -0.06F;
            rightArm.xRot = -1.48F + shake;
            rightArm.yRot = -0.28F;
            leftArm.xRot = -0.52F - shake;
            leftArm.yRot = 0.18F;
        }
    }
}
