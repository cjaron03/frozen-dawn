package com.frozendawn.mixin;

import com.frozendawn.client.FrozenLoadingRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Replaces the vanilla brand render so the startup overlay stays in the
 * Frozen Dawn visual style for the whole reload sequence.
 */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void frozendawn$renderFrozenLoading(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();

        long now = Util.getMillis();
        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = now;
        }

        float fadeOut = this.fadeOutStart > -1L ? (float) (now - this.fadeOutStart) / 1000.0F : -1.0F;
        float fadeInProgress = this.fadeInStart > -1L ? (float) (now - this.fadeInStart) / 500.0F : -1.0F;
        float overlayAlpha;
        if (fadeOut >= 1.0F) {
            if (this.minecraft.screen != null) {
                this.minecraft.screen.render(graphics, 0, 0, partialTick);
            }
            overlayAlpha = 1.0F - Mth.clamp(fadeOut - 1.0F, 0.0F, 1.0F);
        } else if (this.fadeIn) {
            if (this.minecraft.screen != null && fadeInProgress < 1.0F) {
                this.minecraft.screen.render(graphics, mouseX, mouseY, partialTick);
            }
            overlayAlpha = Mth.clamp(fadeInProgress, 0.0F, 1.0F);
        } else {
            overlayAlpha = 1.0F;
        }

        float actualProgress = this.reload.getActualProgress();
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + actualProgress * 0.050000012F, 0.0F, 1.0F);
        float progressBarAlpha = fadeOut < 1.0F ? 1.0F - Mth.clamp(fadeOut, 0.0F, 1.0F) : 0.0F;
        FrozenLoadingRenderer.render(graphics, graphics.guiWidth(), graphics.guiHeight(), this.currentProgress, overlayAlpha, progressBarAlpha);

        if (fadeOut >= 2.0F) {
            this.minecraft.setOverlay(null);
        }

        if (this.fadeOutStart == -1L && this.reload.isDone() && (!this.fadeIn || fadeInProgress >= 2.0F)) {
            this.fadeOutStart = Util.getMillis();
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                this.onFinish.accept(Optional.of(throwable));
            }

            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(this.minecraft, graphics.guiWidth(), graphics.guiHeight());
            }
        }
    }
}
