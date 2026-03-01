package com.frozendawn.client.renderer;

import com.frozendawn.entity.FrostbittenEntity;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

public class FrostbittenModel extends HumanoidModel<FrostbittenEntity> {

    public FrostbittenModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(FrostbittenEntity entity, float limbSwing, float limbSwingAmount,
                           float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, true,
                this.attackTime, ageInTicks);
    }
}
