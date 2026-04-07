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
        this.hat.visible = false;

        // During active mining, preserve vanilla humanoid swing exactly instead of
        // layering a custom pose over it.
        if (action == ArchitectEntity.ACTION_APPROACH && entity.isMiningBlock()) {
            return;
        }

        this.body.xRot = 0.0f;
        this.body.yRot = 0.0f;
        this.body.zRot = 0.0f;
        this.head.zRot = 0.0f;
        this.rightArm.yRot = 0.0f;
        this.leftArm.yRot = 0.0f;
        this.rightArm.zRot = 0.0f;
        this.leftArm.zRot = 0.0f;
        this.rightLeg.yRot = 0.0f;
        this.leftLeg.yRot = 0.0f;
        this.rightLeg.zRot = 0.0f;
        this.leftLeg.zRot = 0.0f;

        if (action == ArchitectEntity.ACTION_OBSERVE || action == ArchitectEntity.ACTION_PEEK) {
            applyObservePose(ageInTicks, sway);
        } else if (action == ArchitectEntity.ACTION_APPROACH) {
            this.head.xRot += 0.02f;
            this.rightArm.xRot -= 0.15f;
            this.leftArm.xRot -= 0.15f;

            if (entity.hasQueuedScaffoldStep()) {
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

    private void applyObservePose(float ageInTicks, float sway) {
        float breath = Mth.sin(ageInTicks * 0.09f);
        float settle = Mth.sin(ageInTicks * 0.045f + 0.7f);
        float shoulderDrift = Mth.sin(ageInTicks * 0.06f + 1.1f) * 0.035f;
        float torsoLag = Mth.clamp(this.head.yRot * 0.35f, -0.35f, 0.35f);

        this.body.xRot = -0.03f + breath * 0.018f;
        this.body.yRot = torsoLag;
        this.body.zRot = settle * 0.028f;

        this.head.xRot += 0.025f + breath * 0.012f;
        this.head.zRot = sway * 0.45f + settle * 0.018f;

        this.rightArm.xRot = -0.22f + breath * 0.035f + shoulderDrift;
        this.leftArm.xRot = -0.34f - breath * 0.02f;
        this.rightArm.yRot = -0.10f + torsoLag * 0.65f;
        this.leftArm.yRot = 0.14f + torsoLag * 0.65f;
        this.rightArm.zRot = 0.10f + settle * 0.035f;
        this.leftArm.zRot = -0.16f - settle * 0.03f;

        this.rightLeg.xRot = -0.04f + breath * 0.01f;
        this.leftLeg.xRot = 0.05f - breath * 0.008f;
        this.rightLeg.yRot = torsoLag * 0.12f;
        this.leftLeg.yRot = torsoLag * 0.12f;
        this.rightLeg.zRot = -0.035f + settle * 0.015f;
        this.leftLeg.zRot = 0.035f - settle * 0.015f;
    }
}
