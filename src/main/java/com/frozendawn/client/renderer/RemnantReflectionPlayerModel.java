package com.frozendawn.client.renderer;

import com.frozendawn.entity.RemnantEntity;
import com.frozendawn.entity.RemnantState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/** Vanilla player geometry driven by the Remnant's delayed player recording. */
final class RemnantReflectionPlayerModel extends PlayerModel<RemnantEntity> {
    RemnantReflectionPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(RemnantEntity entity, float limbSwing, float limbAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        setAllVisible(true);
        crouching = entity.reflectionCrouching();
        rightArmPose = poseFor(entity, InteractionHand.MAIN_HAND,
                entity.visualItem(EquipmentSlot.MAINHAND));
        leftArmPose = poseFor(entity, InteractionHand.OFF_HAND,
                entity.visualItem(EquipmentSlot.OFFHAND));

        float reflectedSpeed = Mth.clamp((float) Math.sqrt(
                entity.reflectionForward() * entity.reflectionForward()
                        + entity.reflectionStrafe() * entity.reflectionStrafe()), 0.0F, 1.0F);
        if (reflectedSpeed > 0.04F) {
            limbSwing = ageInTicks * (entity.reflectionSprinting() ? 0.95F : 0.67F);
            limbAmount = reflectedSpeed;
        }
        super.setupAnim(entity, limbSwing, limbAmount, ageInTicks,
                entity.reflectionHeadYaw(), entity.reflectionHeadPitch());

        if (entity.isWallLatched()) {
            body.xRot = 0.13F;
            head.xRot = 0.32F;
            rightArm.xRot = -1.08F;
            rightArm.yRot = -0.34F;
            rightArm.zRot = 0.22F;
            leftArm.xRot = -1.08F;
            leftArm.yRot = 0.34F;
            leftArm.zRot = -0.22F;
        }
        if (entity.state() == RemnantState.DYING || entity.deathTime > 0) {
            float elapsed = entity.deathTime;
            float recoil = Mth.clamp(elapsed / 6.0F, 0.0F, 1.0F);
            float collapse = Mth.clamp((elapsed - 18.0F) / 24.0F, 0.0F, 1.0F);
            float shake = Mth.sin(ageInTicks * 5.6F) * (1.0F - collapse);
            body.xRot = -0.28F * recoil + collapse * 0.82F;
            body.zRot = shake * 0.06F;
            head.xRot = -0.5F * recoil + collapse * 0.9F;
            head.yRot = shake * 0.11F;
            rightArm.xRot = -1.45F * recoil + collapse * 0.8F;
            leftArm.xRot = -1.45F * recoil + collapse * 0.8F;
            rightArm.zRot = 0.34F + shake * 0.08F;
            leftArm.zRot = -0.34F - shake * 0.08F;
        }
    }

    private static HumanoidModel.ArmPose poseFor(RemnantEntity entity,
                                                  InteractionHand hand,
                                                  ItemStack stack) {
        if (!entity.reflectionUsingItem() || entity.reflectionHand() != hand) {
            return stack.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        UseAnim animation = entity.reflectionUseAnimation();
        return switch (animation) {
            case BLOCK -> HumanoidModel.ArmPose.BLOCK;
            case BOW -> HumanoidModel.ArmPose.BOW_AND_ARROW;
            case SPEAR -> HumanoidModel.ArmPose.THROW_SPEAR;
            case CROSSBOW -> HumanoidModel.ArmPose.CROSSBOW_HOLD;
            case SPYGLASS -> HumanoidModel.ArmPose.SPYGLASS;
            case TOOT_HORN -> HumanoidModel.ArmPose.TOOT_HORN;
            case BRUSH -> HumanoidModel.ArmPose.BRUSH;
            default -> stack.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        };
    }
}
