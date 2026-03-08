package com.frozendawn.client.renderer;

import com.frozendawn.entity.MimicEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

public class MimicModel extends HumanoidModel<MimicEntity> {

    public MimicModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(MimicEntity entity, float limbSwing, float limbSwingAmount,
                           float ageInTicks, float netHeadYaw, float headPitch) {
        int phase = entity.getMimicPhase();

        if (phase == MimicEntity.PHASE_OBSERVATION) {
            // Standing pose: all parts at default, arms at sides, no swing
            resetPose();
        } else {
            // Combat phases: normal humanoid animation (walking, arm swing)
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }

    private void resetPose() {
        this.head.xRot = 0;
        this.head.yRot = 0;
        this.head.zRot = 0;
        this.hat.xRot = 0;
        this.hat.yRot = 0;
        this.hat.zRot = 0;
        this.body.xRot = 0;
        this.body.yRot = 0;
        this.body.zRot = 0;
        this.rightArm.xRot = 0;
        this.rightArm.yRot = 0;
        this.rightArm.zRot = 0;
        this.leftArm.xRot = 0;
        this.leftArm.yRot = 0;
        this.leftArm.zRot = 0;
        this.rightLeg.xRot = 0;
        this.rightLeg.yRot = 0;
        this.rightLeg.zRot = 0;
        this.leftLeg.xRot = 0;
        this.leftLeg.yRot = 0;
        this.leftLeg.zRot = 0;
    }
}
