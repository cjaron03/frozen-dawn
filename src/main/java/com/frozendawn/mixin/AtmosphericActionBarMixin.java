package com.frozendawn.mixin;

import com.frozendawn.client.AtmosphericBreachClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Oxygen feedback owns the action bar until its notice or danger has ended. */
@Mixin(Gui.class)
public abstract class AtmosphericActionBarMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void frozendawn$prioritizeOxygen(Component message, boolean animateColor, CallbackInfo ci) {
        if (AtmosphericBreachClient.suppressCompetingActionBar()) ci.cancel();
    }
}
