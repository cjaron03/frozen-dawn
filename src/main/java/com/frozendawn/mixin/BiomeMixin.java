package com.frozendawn.mixin;

import com.frozendawn.phase.FrozenDawnPhaseTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces all biomes to produce snow instead of rain when the apocalypse
 * reaches phase 3 or later. Affects both client rendering and server
 * snow-layer placement.
 */
@Mixin(Biome.class)
public class BiomeMixin {

    @Inject(method = "warmEnoughToRain", at = @At("HEAD"), cancellable = true)
    private void frozendawn$forceSnow(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (FrozenDawnPhaseTracker.getPhase() >= 3) {
            cir.setReturnValue(false);
        }
    }
}
