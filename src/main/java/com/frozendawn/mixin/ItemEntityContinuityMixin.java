package com.frozendawn.mixin;

import com.frozendawn.event.ContinuityRecoveryHandler;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla merges keep the younger age: prevent a later drop from refreshing this recovery window. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityContinuityMixin {
    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void frozendawn$preserveRecoveryLifetime(ItemEntity other, CallbackInfo ci) {
        if (ContinuityRecoveryHandler.isRecoveryDrop((ItemEntity) (Object) this)
                || ContinuityRecoveryHandler.isRecoveryDrop(other)) ci.cancel();
    }
}
