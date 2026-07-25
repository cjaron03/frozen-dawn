package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectCombatAction;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.homo.MasterArchitectFloodPolicy;
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
        this.setAllVisible(true);

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

        if (entity.isMasterArchitectVisual() && entity.getDeathTicks() > 0) {
            applyMasterDeathPose(entity.getDeathTicks(), ageInTicks);
            return;
        }

        int masterAction = entity.getMasterCombatAction();
        if (masterAction != MasterArchitectCombatAction.IDLE) {
            applyMasterCombatPose(
                    masterAction, entity.getMasterCombatActionTicks(), ageInTicks);
            if (entity.isMasterMindCopy()
                    && masterAction == MasterArchitectCombatAction.MIND_RETURN_STAGGER) {
                applyMindDisintegrationVisibility(
                        entity.getMasterCombatActionTicks(), ageInTicks);
            }
            return;
        }

        if (entity.isMasterArchitectVisual()) {
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
                float charge = MasterArchitectCombatPolicy.thermalCastCharge(actionTicks);
                this.body.xRot = 0.0F;
                this.body.yRot = 0.0F;
                this.body.zRot = 0.0F;
                this.head.xRot += 0.02F;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
                this.rightArm.xRot = Mth.lerp(charge, MASTER_WAND_HOLD_X, -1.38F);
                this.rightArm.yRot = Mth.lerp(charge, MASTER_WAND_HOLD_Y, -0.12F);
                this.rightArm.zRot = MASTER_WAND_HOLD_Z;
                this.leftArm.xRot = Mth.lerp(charge, 0.10F, -1.28F);
                this.leftArm.yRot = Mth.lerp(charge, 0.0F, 0.24F);
                this.leftArm.zRot = Mth.lerp(charge, 0.0F, -0.10F);
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
            case MasterArchitectCombatAction.CONSTRUCTION_WALL_CAST -> {
                float build = Mth.clamp(actionTicks / 10.0F, 0.0F, 1.0F);
                this.body.xRot = -0.03F;
                this.body.yRot = 0.0F;
                this.head.xRot += 0.10F;
                applyMasterWandGrip();
                this.leftArm.xRot = Mth.lerp(build, -0.32F, -1.62F);
                this.leftArm.yRot = 0.48F * build;
                this.leftArm.zRot = -0.20F * build;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            case MasterArchitectCombatAction.CONSTRUCTION_STAGGER -> {
                float recoil = 0.10F + pulse * 0.75F;
                this.body.xRot = 0.22F;
                this.body.zRot = recoil;
                this.head.xRot += 0.18F;
                this.head.zRot = -recoil;
                applyMasterWandGrip();
                this.leftArm.xRot = -0.42F;
                this.leftArm.yRot = 0.34F;
                this.rightLeg.xRot = -0.18F;
                this.leftLeg.xRot = 0.18F;
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
            case MasterArchitectCombatAction.OBSTRUCTION_SMASH -> {
                float strike = Mth.clamp(actionTicks / 8.0F, 0.0F, 1.0F);
                this.body.yRot = -0.30F * strike;
                this.body.xRot = 0.08F * strike;
                this.head.xRot += 0.12F;
                this.rightArm.xRot = Mth.lerp(strike, MASTER_WAND_HOLD_X, -2.35F);
                this.rightArm.yRot = Mth.lerp(strike, MASTER_WAND_HOLD_Y, -0.52F);
                this.rightArm.zRot = MASTER_WAND_HOLD_Z;
                this.leftArm.xRot = -0.72F;
                this.leftArm.yRot = 0.26F;
            }
            case MasterArchitectCombatAction.FLOOD_CHANNEL -> {
                this.body.xRot = -0.04F;
                this.body.yRot = 0.0F;
                this.body.zRot = 0.0F;
                this.head.xRot = -0.10F;
                this.head.yRot = 0.0F;
                this.head.zRot = 0.0F;
                this.rightArm.xRot = -1.42F;
                this.rightArm.yRot = -0.72F;
                this.rightArm.zRot = 0.18F;
                this.leftArm.xRot = -1.42F;
                this.leftArm.yRot = 0.72F;
                this.leftArm.zRot = -0.18F;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            case MasterArchitectCombatAction.MIND_CORE_REVEAL -> {
                float elapsed = Mth.clamp(
                        (MasterArchitectFloodPolicy.CORE_REVEAL_TICKS - actionTicks)
                                / (float) MasterArchitectFloodPolicy.CORE_REVEAL_TICKS,
                        0.0F,
                        1.0F);
                float recoilProgress = Mth.clamp(elapsed / 0.34F, 0.0F, 1.0F);
                float recoil = Mth.sin(recoilProgress * Mth.PI);
                float revealInput = Mth.clamp(
                        (elapsed - 0.20F) / 0.80F, 0.0F, 1.0F);
                float reveal = revealInput * revealInput * (3.0F - 2.0F * revealInput);
                this.body.xRot = 0.24F * recoil;
                this.body.zRot = pulse * recoil * 1.7F;
                this.head.xRot = 0.18F * recoil - 0.08F * reveal;
                this.head.zRot = -pulse * recoil;
                this.rightArm.xRot = Mth.lerp(reveal, -1.42F, -0.58F);
                this.rightArm.yRot = Mth.lerp(reveal, -0.72F, -1.22F);
                this.rightArm.zRot = Mth.lerp(reveal, 0.18F, 0.58F);
                this.leftArm.xRot = Mth.lerp(reveal, -1.42F, -0.68F);
                this.leftArm.yRot = Mth.lerp(reveal, 0.72F, 0.28F);
                this.leftArm.zRot = Mth.lerp(reveal, -0.18F, -0.10F);
                this.rightLeg.xRot = -0.12F * recoil;
                this.leftLeg.xRot = 0.16F * recoil;
            }
            case MasterArchitectCombatAction.MIND_CORE_READY -> {
                this.body.xRot = -0.02F;
                this.head.xRot = -0.08F;
                this.head.yRot = 0.0F;
                this.rightArm.xRot = -0.58F;
                this.rightArm.yRot = -1.22F;
                this.rightArm.zRot = 0.58F;
                this.leftArm.xRot = -0.68F;
                this.leftArm.yRot = 0.28F;
                this.leftArm.zRot = -0.10F;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            case MasterArchitectCombatAction.FLOOD_FOLD_CAST -> {
                float panic = Mth.sin(ageInTicks * 3.8F) * 0.075F;
                this.body.xRot = -0.08F;
                this.body.zRot = panic;
                this.head.xRot = -0.20F;
                this.head.zRot = -panic * 0.75F;
                this.rightArm.xRot = -1.62F;
                this.rightArm.yRot = -0.78F;
                this.rightArm.zRot = 0.20F + panic;
                this.leftArm.xRot = -1.62F;
                this.leftArm.yRot = 0.78F;
                this.leftArm.zRot = -0.20F - panic;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            case MasterArchitectCombatAction.MIND_HIT_STAGGER -> {
                float recoil = Mth.clamp(actionTicks / 7.0F, 0.0F, 1.0F);
                this.body.xRot = 0.22F + pulse;
                this.body.zRot = 0.10F * recoil;
                this.head.xRot = 0.20F;
                this.head.zRot = -0.14F * recoil;
                this.rightArm.xRot = -0.82F;
                this.rightArm.yRot = -0.28F;
                this.leftArm.xRot = -0.64F;
                this.leftArm.yRot = 0.34F;
                this.rightLeg.xRot = -0.12F;
                this.leftLeg.xRot = 0.12F;
            }
            case MasterArchitectCombatAction.MIND_CORE_EXPOSED -> {
                float fracture = Mth.sin(ageInTicks * 1.9F) * 0.12F;
                this.body.xRot = 0.28F;
                this.body.zRot = fracture;
                this.head.xRot = 0.18F;
                this.head.zRot = -fracture * 0.8F;
                this.rightArm.xRot = -0.58F;
                this.rightArm.yRot = -1.22F;
                this.rightArm.zRot = 0.58F;
                this.leftArm.xRot = -0.72F;
                this.leftArm.yRot = 0.30F;
                this.leftArm.zRot = -0.12F;
                this.rightLeg.xRot = -0.08F;
                this.leftLeg.xRot = 0.08F;
            }
            case MasterArchitectCombatAction.MIND_RETURN_STAGGER -> {
                this.body.xRot = 0.44F;
                this.body.zRot = pulse * 0.35F;
                this.head.xRot = 0.34F;
                this.head.zRot = -pulse * 0.45F;
                this.rightArm.xRot = -0.18F;
                this.rightArm.yRot = -0.18F;
                this.leftArm.xRot = -1.10F;
                this.leftArm.yRot = 0.46F;
                this.rightLeg.xRot = -0.20F;
                this.leftLeg.xRot = 0.34F;
            }
            case MasterArchitectCombatAction.MIND_RETURN_CHARGE -> {
                float shake = Mth.sin(ageInTicks * 3.1F) * 0.08F;
                this.body.xRot = -0.05F;
                this.body.zRot = shake;
                this.head.xRot = -0.16F;
                this.head.zRot = -shake;
                this.rightArm.xRot = -1.34F;
                this.rightArm.yRot = -0.64F;
                this.leftArm.xRot = -1.34F;
                this.leftArm.yRot = 0.64F;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            default -> {
            }
        }
    }

    private void applyMindDisintegrationVisibility(int actionTicks, float ageInTicks) {
        float progress = Mth.clamp(
                actionTicks
                        / (float) MasterArchitectFloodPolicy.MIND_DEATH_DISINTEGRATION_TICKS,
                0.0F,
                1.0F);
        this.leftLeg.visible = dissolvePartVisible(progress, 0.18F, ageInTicks, 1);
        this.rightLeg.visible = dissolvePartVisible(progress, 0.32F, ageInTicks, 2);
        this.leftArm.visible = dissolvePartVisible(progress, 0.48F, ageInTicks, 3);
        this.rightArm.visible = dissolvePartVisible(progress, 0.62F, ageInTicks, 4);
        this.body.visible = dissolvePartVisible(progress, 0.78F, ageInTicks, 5);
        this.head.visible = dissolvePartVisible(progress, 0.93F, ageInTicks, 6);
    }

    private static boolean dissolvePartVisible(
            float progress, float threshold, float ageInTicks, int salt) {
        if (progress < threshold) {
            return true;
        }
        if (progress >= threshold + 0.12F) {
            return false;
        }
        return (Mth.floor(ageInTicks * 2.0F) + salt) % 4 != 0;
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
