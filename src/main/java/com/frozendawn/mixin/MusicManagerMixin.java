package com.frozendawn.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frozen Dawn owns both menu music and in-world music scheduling.
 * Vanilla MusicManager is suppressed entirely so it cannot inject
 * default random tracks back into either flow.
 */
@Mixin(MusicManager.class)
public class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void frozendawn$suppressVanillaMusic(CallbackInfo ci) {
        ci.cancel();
    }
}
