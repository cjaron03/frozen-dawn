package com.frozendawn.mixin;

import com.frozendawn.client.StartupFreezeVisuals;
import net.minecraft.util.FastColor;
import net.neoforged.fml.earlydisplay.ColourScheme;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.ElementShader;
import net.neoforged.fml.earlydisplay.QuadHelper;
import net.neoforged.fml.earlydisplay.RenderElement;
import net.neoforged.fml.earlydisplay.SimpleBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extends the FML early window so the freeze transition is visible before
 * NeoForge hands off to the in-game loading overlay.
 */
@Mixin(DisplayWindow.class)
public class DisplayWindowMixin {

    @Shadow private RenderElement.DisplayContext context;

    @Unique
    private static final int frozendawn$barBaseY = 250;

    @Unique
    private static final int frozendawn$barFontOffset = 34;

    @Unique
    private static final int frozendawn$barWidth = 400;

    @Unique
    private static final int frozendawn$barHeight = 20;

    @Unique
    private final SimpleBufferBuilder frozendawn$builder = new SimpleBufferBuilder(512);

    @Redirect(
            method = "renderThreadFunc",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/fml/earlydisplay/ColourScheme;background()Lnet/neoforged/fml/earlydisplay/ColourScheme$Colour;"
            )
    )
    private ColourScheme.Colour frozendawn$backgroundColour(ColourScheme scheme) {
        return StartupFreezeVisuals.loadingBackgroundColour(0.0f);
    }

    @Inject(
            method = "renderThreadFunc",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/fml/earlydisplay/ElementShader;clear()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void frozendawn$renderFreezeOverlay(CallbackInfo ci) {
        float tint = StartupFreezeVisuals.tint(0.0f);
        if (tint <= 0.01f) {
            return;
        }

        ElementShader shader = this.context.elementShader();
        shader.updateTextureUniform(0);
        shader.updateRenderTypeUniform(ElementShader.RenderType.BAR);

        int overlayColor = FastColor.ARGB32.color((int) (tint * 92.0f), 0x18, 0x30, 0x50);
        int screenWidth = this.context.scaledWidth();
        int screenHeight = this.context.scaledHeight();

        this.frozendawn$builder.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        frozendawn$rect(0, screenWidth, 0, screenHeight, overlayColor);

        float icicleProgress = StartupFreezeVisuals.icicleProgress(tint);
        if (icicleProgress > 0.0f) {
            int barLeft = (screenWidth - frozendawn$barWidth * this.context.scale()) / 2;
            int barTop = (frozendawn$barBaseY * this.context.scale()) + frozendawn$barFontOffset;
            int barWidth = frozendawn$barWidth * this.context.scale();
            int barHeight = frozendawn$barHeight;
            int barBottom = barTop + barHeight;

            int frostColor = FastColor.ARGB32.color((int) (145 * icicleProgress), 0xC8, 0xED, 0xFF);
            frozendawn$rect(barLeft - 1, barLeft + barWidth + 1, barTop - 1, barTop, frostColor);
            frozendawn$rect(barLeft - 1, barLeft + barWidth + 1, barBottom, barBottom + 1, frostColor);

            int iceColor = FastColor.ARGB32.color((int) (195 * icicleProgress), 0xA0, 0xD0, 0xF0);
            int iceColorDark = FastColor.ARGB32.color((int) (175 * icicleProgress), 0x60, 0x90, 0xC0);
            int icicleTop = barBottom + 2;
            int icicleCount = 16;

            for (int i = 0; i < icicleCount; i++) {
                float frac = (float) i / (icicleCount - 1);
                int x = barLeft + Math.round(barWidth * frac);
                float profile = 1.0f - Math.abs(frac - 0.5f) * 1.35f;
                int maxLen = Math.max(8, Math.round(18.0f + 12.0f * profile));
                int len = Math.round(maxLen * icicleProgress);
                if (len < 3) {
                    continue;
                }

                int baseWidth = i % 3 == 0 ? 3 : 2;
                int bodyLen = len * 2 / 3;
                frozendawn$rect(x, x + baseWidth, icicleTop, icicleTop + bodyLen, iceColor);
                frozendawn$rect(x, x + 1, icicleTop + bodyLen, icicleTop + len, iceColorDark);
            }
        }

        this.frozendawn$builder.draw();
    }

    @Unique
    private void frozendawn$rect(int minX, int maxX, int minY, int maxY, int color) {
        QuadHelper.loadQuad(this.frozendawn$builder, minX, maxX, minY, maxY, 0.0f, 0.0f, 0.0f, 0.0f, color);
    }
}
