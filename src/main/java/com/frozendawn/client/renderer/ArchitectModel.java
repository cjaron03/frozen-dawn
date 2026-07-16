package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectCombatAction;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.client.MasterArchitectFourthWallMoment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Architect model — extends HumanoidModel.
 * Uses vanilla player-like arm swing for walking/combat.
 * Stiff arms only when observing (stalking).
 */
public class ArchitectModel extends HumanoidModel<ArchitectEntity> {
    private static final float MASTER_WAND_HOLD_X = -0.42F;
    private static final float MASTER_WAND_HOLD_Y = -0.08F;
    private static final float MASTER_WAND_HOLD_Z = 0.07F;

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

        if (entity.isHearthMasterArchitect() && entity.getDeathTicks() > 0) {
            applyMasterDeathPose(entity.getDeathTicks(), ageInTicks);
            return;
        }

        int masterAction = entity.getMasterCombatAction();
        if (masterAction != MasterArchitectCombatAction.IDLE) {
            applyMasterCombatPose(
                    masterAction, entity.getMasterCombatActionTicks(), ageInTicks);
            return;
        }

        if (entity.isHearthMasterArchitect()) {
            applyMasterIdlePose(limbSwingAmount);
            MasterArchitectFourthWallMoment.CameraHeadAngles cameraHead =
                    MasterArchitectFourthWallMoment.cameraHeadAngles(
                            entity, ageInTicks - entity.tickCount);
            if (cameraHead != null) {
                this.head.yRot = cameraHead.yawRadians();
                this.head.xRot = cameraHead.pitchRadians();
            }
            return;
        }

        if (action == ArchitectEntity.ACTION_OBSERVE || action == ArchitectEntity.ACTION_PEEK) {
            applyObservePose(ageInTicks, sway, limbSwingAmount);
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

    private void applyMasterCombatPose(int action, int actionTicks, float ageInTicks) {
        float pulse = Mth.sin(ageInTicks * 0.35F) * 0.06F;
        switch (action) {
            case MasterArchitectCombatAction.STAFF_STRIKE -> {
                float windup = Mth.clamp(actionTicks / 6.0F, 0.0F, 1.0F);
                this.body.yRot = -0.20F * windup;
                this.rightArm.xRot = Mth.lerp(windup, -0.35F, -2.05F);
                this.rightArm.yRot = -0.35F;
                this.leftArm.xRot = -0.55F;
                this.leftArm.yRot = 0.22F;
                this.head.xRot += 0.08F;
            }
            case MasterArchitectCombatAction.CONTINUITY_FRACTURE -> {
                this.body.xRot = -0.04F;
                this.head.xRot += 0.04F;
                this.head.zRot = 0.16F + pulse * 0.35F;
                this.rightArm.xRot = -1.15F + pulse;
                this.leftArm.xRot = -1.15F - pulse;
                this.rightArm.yRot = -0.65F;
                this.leftArm.yRot = 0.65F;
                this.rightArm.zRot = 0.22F;
                this.leftArm.zRot = -0.22F;
            }
            case MasterArchitectCombatAction.THERMAL_SEVER -> {
                this.body.xRot = 0.0F;
                this.head.xRot += 0.04F;
                this.rightArm.xRot = -1.52F;
                this.rightArm.yRot = -0.10F;
                this.rightArm.zRot = 0.02F;
                this.leftArm.xRot = -0.82F;
                this.leftArm.yRot = 0.45F;
                this.leftArm.zRot = -0.28F;
            }
            case MasterArchitectCombatAction.LAST_WALL_CAST -> {
                this.body.xRot = 0.0F;
                this.head.xRot += 0.08F;
                applyMasterWandGrip();
                this.leftArm.xRot = -1.48F;
                this.leftArm.yRot = 0.32F;
                this.leftArm.zRot = -0.18F;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            case MasterArchitectCombatAction.LAST_WALL_HEAL -> {
                this.body.xRot = 0.0F;
                this.head.xRot += 0.06F;
                applyMasterWandGrip();
                this.leftArm.xRot = -1.18F;
                this.leftArm.yRot = 0.48F;
                this.leftArm.zRot = -0.20F;
            }
            case MasterArchitectCombatAction.STORM_MAINTENANCE -> {
                float raise = Mth.clamp(actionTicks / 12.0F, 0.0F, 1.0F);
                float lower = Mth.clamp(
                        (MasterArchitectCombatPolicy.STORM_MAINTENANCE_ACTION_TICKS
                                - actionTicks) / 10.0F,
                        0.0F,
                        1.0F);
                float hold = Math.min(raise, lower);
                this.body.xRot = 0.0F;
                this.head.xRot = 0.0F;
                this.head.zRot = 0.0F;
                applyMasterWandGrip();
                this.leftArm.xRot = Mth.lerp(hold, -0.20F, -2.48F);
                this.leftArm.yRot = 0.68F * hold;
                this.leftArm.zRot = -0.24F * hold;
            }
            default -> {
            }
        }
    }

    private void applyMasterDeathPose(int deathTicks, float ageInTicks) {
        float charge = MasterArchitectCombatPolicy.deathChargeProgress(deathTicks);
        this.body.xRot = Mth.lerp(charge, 0.05F, -0.03F);
        this.body.zRot = 0.0F;
        this.head.xRot = Mth.lerp(charge, 0.12F, -0.12F);
        this.head.zRot = 0.0F;
        applyMasterWandGrip();
        this.leftArm.xRot = Mth.lerp(charge, -0.22F, -0.92F);
        this.leftArm.yRot = 0.28F * charge;
        this.leftArm.zRot = -0.18F;
        this.rightLeg.xRot = -0.05F;
        this.leftLeg.xRot = 0.05F;
    }

    private void applyMasterIdlePose(float limbSwingAmount) {
        float gaitBlend = Mth.clamp(limbSwingAmount * 2.0F, 0.0F, 1.0F);
        float leftArmWalkX = this.leftArm.xRot;

        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;
        this.head.zRot = 0.0F;
        applyMasterWandGrip();
        this.leftArm.xRot = Mth.lerp(gaitBlend * 0.55F, -0.12F, leftArmWalkX);
        this.leftArm.yRot = 0.06F;
        this.leftArm.zRot = -0.04F;
    }

    private void applyMasterWandGrip() {
        this.rightArm.xRot = MASTER_WAND_HOLD_X;
        this.rightArm.yRot = MASTER_WAND_HOLD_Y;
        this.rightArm.zRot = MASTER_WAND_HOLD_Z;
    }

    private void applyObservePose(float ageInTicks, float sway, float limbSwingAmount) {
        // Preserve a blended amount of vanilla gait so OBSERVE movement still reads
        // as locomotion instead of a frozen pose.
        float rightArmWalkX = this.rightArm.xRot;
        float leftArmWalkX = this.leftArm.xRot;
        float rightLegWalkX = this.rightLeg.xRot;
        float leftLegWalkX = this.leftLeg.xRot;

        float breath = Mth.sin(ageInTicks * 0.09f);
        float settle = Mth.sin(ageInTicks * 0.045f + 0.7f);
        float shoulderDrift = Mth.sin(ageInTicks * 0.06f + 1.1f) * 0.035f;
        float torsoLag = Mth.clamp(this.head.yRot * 0.35f, -0.35f, 0.35f);
        float gaitBlend = Mth.clamp(limbSwingAmount * 2.0f, 0.0f, 1.0f);
        float armGaitBlend = gaitBlend * 0.75f;

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
        this.rightArm.xRot = Mth.lerp(armGaitBlend, this.rightArm.xRot, rightArmWalkX);
        this.leftArm.xRot = Mth.lerp(armGaitBlend, this.leftArm.xRot, leftArmWalkX);

        this.rightLeg.xRot = -0.04f + breath * 0.01f;
        this.leftLeg.xRot = 0.05f - breath * 0.008f;
        this.rightLeg.yRot = torsoLag * 0.12f;
        this.leftLeg.yRot = torsoLag * 0.12f;
        this.rightLeg.zRot = -0.035f + settle * 0.015f;
        this.leftLeg.zRot = 0.035f - settle * 0.015f;
        this.rightLeg.xRot = Mth.lerp(gaitBlend, this.rightLeg.xRot, rightLegWalkX);
        this.leftLeg.xRot = Mth.lerp(gaitBlend, this.leftLeg.xRot, leftLegWalkX);
    }
}
