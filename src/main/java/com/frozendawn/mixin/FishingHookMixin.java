package com.frozendawn.mixin;

import com.frozendawn.data.ApocalypseState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stretches the wait before a fish is lured once the cold sets in: half again
 * as long in phase 4, twice as long from phase 5. Catches stay vanilla until
 * phase 6, where {@code IceFishingLootModifier} takes over.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @Shadow private int nibble;
    @Shadow private int timeUntilLured;
    @Shadow private int timeUntilHooked;
    @Unique private boolean frozendawn$rollingLureWait;

    @Inject(method = "catchingFish", at = @At("HEAD"))
    private void frozendawn$noteLureRoll(BlockPos pos, CallbackInfo ci) {
        // Vanilla rolls a fresh lure wait only when all three timers have run out.
        frozendawn$rollingLureWait = nibble <= 0 && timeUntilHooked <= 0 && timeUntilLured <= 0;
    }

    @Inject(method = "catchingFish", at = @At("RETURN"))
    private void frozendawn$stretchLureWait(BlockPos pos, CallbackInfo ci) {
        if (!frozendawn$rollingLureWait || timeUntilLured <= 0) return;
        Level level = ((FishingHook) (Object) this).level();
        if (level.dimension() != Level.OVERWORLD || level.getServer() == null) return;
        int phase = ApocalypseState.get(level.getServer()).getPhase();
        if (phase >= 5) {
            timeUntilLured *= 2;
        } else if (phase == 4) {
            timeUntilLured += timeUntilLured / 2;
        }
    }
}
