package com.frozendawn.mixin;

import com.frozendawn.client.FrozenLoadingRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Replaces the NeoForge loading overlay render so the cold branding persists
 * instead of falling back to the early-window texture.
 */
@Mixin(NeoForgeLoadingOverlay.class)
public class NeoForgeLoadingOverlayMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow @Final private DisplayWindow displayWindow;
    @Shadow @Final private ProgressMeter progressMeter;
    @Shadow private long fadeOutStart;
    @Shadow private float currentProgress;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void frozendawn$renderFrozenLoading(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();

        long millis = Util.getMillis();
        float fadeOut = this.fadeOutStart > -1L ? (float) (millis - this.fadeOutStart) / 1000.0F : -1.0F;
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + this.reload.getActualProgress() * 0.05F, 0.0F, 1.0F);
        this.progressMeter.setAbsolute(Mth.ceil(this.currentProgress * 1000.0F));

        float overlayAlpha = 1.0F - Mth.clamp(fadeOut - 1.0F, 0.0F, 1.0F);
        if (fadeOut >= 1.0F && this.minecraft.screen != null) {
            this.minecraft.screen.render(graphics, 0, 0, partialTick);
        }

        float progressBarAlpha = fadeOut < 1.0F ? 1.0F - Mth.clamp(fadeOut, 0.0F, 1.0F) : 0.0F;
        FrozenLoadingRenderer.render(graphics, graphics.guiWidth(), graphics.guiHeight(), this.currentProgress, overlayAlpha, progressBarAlpha);

        if (fadeOut >= 2.0F) {
            this.progressMeter.complete();
            this.minecraft.setOverlay(null);
            this.displayWindow.close();
        }

        if (this.fadeOutStart == -1L && this.reload.isDone()) {
            this.fadeOutStart = Util.getMillis();
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                this.onFinish.accept(Optional.of(throwable));
            }

            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(this.minecraft, this.minecraft.getWindow().getGuiScaledWidth(), this.minecraft.getWindow().getGuiScaledHeight());
            }
        }
    }
}
