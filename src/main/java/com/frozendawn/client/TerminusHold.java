package com.frozendawn.client;

import com.frozendawn.item.TerminusItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * How the Terminus is held: in the fist, pointing forward; winding up, one arm straight out along the look, nothing
 * bracing it. In first person it lifts toward the crosshair and trembles more as the wind-up nears its end.
 */
public final class TerminusHold implements IClientItemExtensions {
    /** Named in META-INF/enumextensions.json, which adds the arm pose. */
    public static final EnumProxy<HumanoidModel.ArmPose> WINDING_POSE =
            new EnumProxy<>(HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) TerminusHold::raise);
    /** The arm turns in this far, as the Lash's grip arm does, so the wand shares the Lash's solved display transform. */
    private static final float TURN = .3F;

    @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return TerminusItem.winding(entity, stack) ? WINDING_POSE.getValue() : null;
    }

    private static void raise(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        var main = right ? model.rightArm : model.leftArm;
        main.xRot = -Mth.HALF_PI + model.head.xRot;
        main.yRot = model.head.yRot + (right ? -TURN : TURN);
        main.zRot = 0;
    }

    @Override public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                     float partialTick, float equipProcess, float swingProcess) {
        if (!TerminusItem.winding(player, stack)) return false;
        float t = player.level().getGameTime() + partialTick;
        float k = Mth.clamp((player.getTicksUsingItem() + partialTick) / com.frozendawn.defense.Terminus.WINDUP_TICKS, 0, 1);
        float shake = .002F + .006F * k * k;
        pose.translate(Mth.sin(t * 2.9F) * shake, .05F * k + Mth.sin(t * 3.7F) * shake, -.06F * k);
        return false;
    }
}
