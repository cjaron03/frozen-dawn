package com.frozendawn.mixin;

import com.frozendawn.client.MenuTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public class ReceivingLevelScreenMixin {

    @Shadow @Final private ReceivingLevelScreen.Reason reason;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void frozendawn$renderFrozenBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.reason != ReceivingLevelScreen.Reason.OTHER) {
            return;
        }

        MenuTheme.renderMenuBackdrop(graphics);
        ci.cancel();
    }
}
