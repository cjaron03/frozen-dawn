package com.frozendawn.client.renderer;

import com.frozendawn.entity.BloomSporeCorpseEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Fixed collapsed body used by a satellite node's persistent corpse. */
public final class BloomSporeCorpseModel extends HumanoidModel<BloomSporeCorpseEntity> {
    public BloomSporeCorpseModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(BloomSporeCorpseEntity entity, float limbSwing,
                          float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, 0.0F, 0.0F, ageInTicks, 0.0F, 0.0F);
        head.xRot = 0.30F;
        head.zRot = -0.10F;
        hat.copyFrom(head);
        rightArm.xRot = 0.65F;
        rightArm.zRot = 0.22F;
        leftArm.xRot = -0.20F;
        leftArm.zRot = -0.40F;
        rightLeg.xRot = 0.30F;
        leftLeg.xRot = -0.24F;
    }
}
