package com.frozendawn.mixin;

import com.frozendawn.client.MenuTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla MINECRAFT logo with an icy blue tint and swaps
 * the edition ribbon for a scaled FROZEN DAWN subtitle in the standard font.
 */
@Mixin(LogoRenderer.class)
public class LogoRendererMixin {

    @Shadow @Final private boolean showEasterEgg;
    @Shadow @Final private boolean keepLogoThroughFade;

    @Inject(
            method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void frozendawn$renderCustomLogo(GuiGraphics graphics, int screenWidth, float alpha, int y, CallbackInfo ci) {
        ci.cancel();

        float effectiveAlpha = this.keepLogoThroughFade ? 1.0F : alpha;
        if (effectiveAlpha < 0.01F) {
            return;
        }

        // Render the vanilla title texture with a colder tint.
        graphics.setColor(0.52f, 0.76f, 0.96f, effectiveAlpha);
        RenderSystem.enableBlend();

        int logoX = screenWidth / 2 - 128;
        ResourceLocation logo = this.showEasterEgg
                ? LogoRenderer.EASTER_EGG_LOGO
                : LogoRenderer.MINECRAFT_LOGO;
        graphics.blit(logo, logoX, y, 0.0F, 0.0F, 256, 44, 256, 64);

        MenuTheme.renderLogoSnowCollision(graphics, logoX, y, effectiveAlpha);

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Replace the edition ribbon with a mod subtitle.
        var font = Minecraft.getInstance().font;
        String text = "FROZEN DAWN";
        float scale = 1.85f;
        int unscaledWidth = font.width(text);
        float textScreenX = screenWidth / 2.0f - unscaledWidth * scale / 2.0f;
        float textScreenY = y + 46.0f;

        int textAlpha = Math.max(4, (int) (effectiveAlpha * 255));
        int textColor = (textAlpha << 24) | 0xB7E7FF;
        int shadowColor = (Math.max(4, (int) (effectiveAlpha * 140)) << 24) | 0x17324D;

        graphics.pose().pushPose();
        graphics.pose().translate(textScreenX, textScreenY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 1.0f, 1.0f, shadowColor, false);
        graphics.drawString(font, text, 0.0f, 0.0f, textColor, false);
        graphics.pose().popPose();

        RenderSystem.disableBlend();
    }
}
