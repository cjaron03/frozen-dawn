package com.frozendawn.mixin;

import com.frozendawn.compat.curios.CuriosCompat;
import com.frozendawn.event.SnowshoesHandler;
import com.frozendawn.event.SnowshoesTuning;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"), cancellable = true)
    private void frozendawn$boostSnowshoeTravel(Vec3 deltaMovement, float friction, CallbackInfoReturnable<Vec3> cir) {
        LivingEntity livingEntity = (LivingEntity) (Object) this;
        if (!(livingEntity instanceof Player player)
                || !CuriosCompat.hasSnowshoesEquipped(player)
                || !livingEntity.onGround()
                || livingEntity.isFallFlying()
                || livingEntity.isInWaterOrBubble()
                || livingEntity.isInLava()) {
            return;
        }

        double surfaceBonus = SnowshoesHandler.getSurfaceSpeedBonus(livingEntity.getBlockStateOn());
        if (surfaceBonus <= 0.0D) {
            return;
        }

        Vec3 horizontalInput = new Vec3(deltaMovement.x, 0.0D, deltaMovement.z);
        if (horizontalInput.lengthSqr() < 1.0E-4D) {
            return;
        }

        double impulse = SnowshoesTuning.getTravelImpulseForSpeedBonus(surfaceBonus);
        if (impulse <= 0.0D) {
            return;
        }

        Vec3 direction = horizontalInput.normalize();
        Vec3 baseMovement = cir.getReturnValue();
        cir.setReturnValue(baseMovement.add(direction.x * impulse, 0.0D, direction.z * impulse));
    }
}
