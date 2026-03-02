package com.frozendawn.client.renderer;

import com.frozendawn.entity.ReturnedEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

public class ReturnedModel extends HumanoidModel<ReturnedEntity> {

    public ReturnedModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(ReturnedEntity entity, float limbSwing, float limbSwingAmount,
                           float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // Stiffen arms slightly forward — like zombie but less exaggerated
        this.rightArm.xRot = this.rightArm.xRot * 0.5f - 0.3f;
        this.leftArm.xRot = this.leftArm.xRot * 0.5f - 0.3f;
    }
}
