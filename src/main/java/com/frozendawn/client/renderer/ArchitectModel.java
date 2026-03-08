package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Architect model — extends HumanoidModel.
 * Uses vanilla player-like arm swing for walking/combat.
 * Stiff arms only when observing (stalking).
 */
public class ArchitectModel extends HumanoidModel<ArchitectEntity> {

    public ArchitectModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(ArchitectEntity entity, float limbSwing, float limbSwingAmount,
                           float ageInTicks, float netHeadYaw, float headPitch) {
        // super.setupAnim handles player-like walk animation (arm + leg swing)
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        int action = entity.getCurrentAction();
        float sway = Mth.sin(ageInTicks * 0.08f) * 0.06f;
        this.body.xRot = 0.0f;
        this.body.yRot = 0.0f;
        this.body.zRot = 0.0f;
        this.head.zRot = 0.0f;
        this.rightArm.yRot = 0.0f;
        this.leftArm.yRot = 0.0f;
        this.rightArm.zRot = 0.0f;
        this.leftArm.zRot = 0.0f;
        this.hat.visible = false;

        if (action == ArchitectEntity.ACTION_OBSERVE || action == ArchitectEntity.ACTION_PEEK) {
            this.head.zRot = sway * 0.75f;
            this.rightArm.xRot = -0.40f + sway * 0.7f;
            this.leftArm.xRot = -0.40f - sway * 0.7f;
            this.rightArm.zRot = 0.08f;
            this.leftArm.zRot = -0.08f;
        } else if (action == ArchitectEntity.ACTION_APPROACH) {
            this.head.xRot += 0.02f;
            this.rightArm.xRot -= 0.15f;
            this.leftArm.xRot -= 0.15f;

            if (entity.isMiningBlock()) {
                float miningSwing = Mth.sin(ageInTicks * 0.9f)
                        * 0.35f
                        * Mth.clamp(entity.getMiningProgress() + 0.35f, 0.35f, 1.0f);
                this.head.xRot += 0.18f;
                this.rightArm.xRot = -1.35f + miningSwing;
                this.rightArm.yRot = -0.22f;
                this.leftArm.xRot = -0.35f - miningSwing * 0.25f;
                this.leftArm.yRot = 0.18f;
            } else if (entity.hasQueuedScaffoldStep()) {
                this.rightArm.xRot = -0.95f;
                this.leftArm.xRot = -0.55f;
                this.rightArm.yRot = -0.08f;
                this.leftArm.yRot = 0.08f;
            }
        } else if (action == ArchitectEntity.ACTION_ATTACK_MELEE) {
            this.head.xRot += 0.04f;
            this.rightArm.xRot -= 0.35f;
            this.leftArm.xRot -= 0.20f;
            this.rightArm.yRot = -0.12f;
            this.leftArm.yRot = 0.12f;
        } else if (action == ArchitectEntity.ACTION_RETREAT) {
            this.head.xRot -= 0.02f;
            this.rightArm.xRot = -0.60f;
            this.leftArm.xRot = -0.60f;
            this.rightArm.yRot = -0.35f;
            this.leftArm.yRot = 0.35f;

            if (entity.isRetreatRecovering()) {
                this.head.xRot = -0.06f;
                this.rightArm.xRot = -1.20f;
                this.rightArm.yRot = -0.12f;
                this.leftArm.xRot = -0.35f;
                this.leftArm.yRot = 0.20f;
            }
        }
    }
}
