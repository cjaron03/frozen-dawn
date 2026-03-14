package com.frozendawn.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Customizes the title screen:
 * - Cancels the vanilla panorama (MenuTheme handles background via ScreenEvent)
 * - Adds a frost glow under the logo area
 *
 * Logo tinting and the FROZEN DAWN subtitle are handled by LogoRendererMixin.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/TitleScreen;renderPanorama(Lnet/minecraft/client/gui/GuiGraphics;F)V"
            )
    )
    private void frozendawn$skipPanorama(TitleScreen screen, GuiGraphics graphics, float partialTick) {
    }

    /**
     * Add a subtle frost glow behind the title stack.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void frozendawn$renderOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int width = screen.width;

        // Subtle frost glow under the logo
        int glowWidth = 280;
        int glowX = (width - glowWidth) / 2;
        graphics.fillGradient(glowX, 26, glowX + glowWidth, 100,
                0x18A0D0F0, 0x00000000);
    }
}
