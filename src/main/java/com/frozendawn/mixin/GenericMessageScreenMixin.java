package com.frozendawn.mixin;

import com.frozendawn.client.MenuTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericMessageScreen.class)
public class GenericMessageScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void frozendawn$renderFrozenBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        MenuTheme.renderMenuBackdrop(graphics);
        ci.cancel();
    }
}
