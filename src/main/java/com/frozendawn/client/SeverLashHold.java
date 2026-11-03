package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.defense.LashHeat;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * How the Lash is held. Held, it sits in the fist; firing, both arms come up along the aim with the fists together on
 * the grip, so others see where it points; in first person it lifts a little and trembles without a kick. Through a lockout it dips
 * and tilts away to air out, and in air the vents smoke.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SeverLashHold implements IClientItemExtensions {
    /** Named in META-INF/enumextensions.json, which adds the arm pose. */
    public static final EnumProxy<HumanoidModel.ArmPose> FIRING_POSE =
            new EnumProxy<>(HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) SeverLashHold::raise);
    /** Where vanilla puts a held item in first person, before its own transforms. */
    private static final float HAND_X = .56F, HAND_Y = -.52F, HAND_Z = -.72F;
    /** How far each arm turns in while firing; the firing model turns back by GRIP_TURN to point along the aim. */
    private static final float GRIP_TURN = .3F, BRACE_TURN = .6F;

    @Override public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return entity.isUsingItem() && entity.getUsedItemHand() == hand ? FIRING_POSE.getValue() : null;
    }

    /** Both arms come up along the aim and turn in, as for a crossbow, so the fists meet side by side on the grip. */
    private static void raise(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        var main = right ? model.rightArm : model.leftArm;
        var off = right ? model.leftArm : model.rightArm;
        main.xRot = off.xRot = -Mth.HALF_PI + model.head.xRot;
        main.yRot = model.head.yRot + (right ? -GRIP_TURN : GRIP_TURN);
        off.yRot = model.head.yRot + (right ? BRACE_TURN : -BRACE_TURN);
        main.zRot = off.zRot = 0;
    }

    @Override public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                     float partialTick, float equipProcess, float swingProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        long now = player.level().getGameTime();
        float t = now + partialTick;
        var heat = stack.getOrDefault(ModDataComponents.LASH_HEAT.get(), LashHeat.COOL);
        if (player.isUsingItem() && player.getUseItem() == stack) {
            pose.translate(Mth.sin(t * 2.3F) * .004F, .03F + Mth.sin(t * 3.1F) * .004F, 0);
        } else if (heat.lockedOut(now)) {
            float k = Mth.clamp(Math.min(t - heat.lastFired(), heat.lockoutUntil() - t) / 4F, 0, 1);
            // Tilt about the item's own place in view, not the camera.
            pose.translate(side * HAND_X, HAND_Y, HAND_Z);
            pose.mulPose(Axis.ZP.rotationDegrees(-side * 10 * k));
            pose.translate(-side * HAND_X + side * .04F * k, -HAND_Y - .1F * k, -HAND_Z);
        }
        return false;
    }

    @SubscribeEvent public static void smoke(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        if (now % 3 != 0) return;
        for (var player : mc.level.players()) {
            for (var hand : InteractionHand.values()) {
                var stack = player.getItemInHand(hand);
                if (!stack.is(ModItems.SEVER_LASH.get())) continue;
                int stage = stack.getOrDefault(ModDataComponents.LASH_HEAT.get(), LashHeat.COOL).ventStage(now);
                if (stage == 0 || stage == LashHeat.VENT_SHUT || LashBeamClient.inVacuum(player.getId())) continue;
                var at = vents(mc, player, hand);
                var r = mc.level.random;
                mc.level.addParticle(ParticleTypes.SMOKE, at.x + (r.nextDouble() - .5) * .1, at.y, at.z + (r.nextDouble() - .5) * .1,
                        0, .03 + r.nextDouble() * .02, 0);
            }
        }
    }

    /** Roughly above the fins: low right of the view in first person, at the hip otherwise. */
    private static Vec3 vents(Minecraft mc, Player player, InteractionHand hand) {
        var arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            var camera = mc.gameRenderer.getMainCamera();
            return player.getEyePosition().add(new Vec3(camera.getLookVector()).scale(.8))
                    .add(new Vec3(camera.getLeftVector()).scale(-.45 * side)).add(new Vec3(camera.getUpVector()).scale(-.15));
        }
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        var forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        var right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return player.position().add(0, .95 * player.getScale(), 0).add(right.scale(.35 * side)).add(forward.scale(.45));
    }
}
