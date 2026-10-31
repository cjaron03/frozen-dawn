package com.frozendawn.mixin;

import com.frozendawn.client.IcarusBlastCloud;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An Icarus flash lights the world itself: just before the lightmap uploads, every light level is washed toward the
 * flash's colour by its strength, so dark blocks and caves flare as bright as open ground. Under a crater's soot pall
 * the sky's share of each level is dimmed first. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Shadow @Final private NativeImage lightPixels;

    @Inject(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void frozendawn$icarusFlashLight(float partialTicks, CallbackInfo ci) {
        float dim = com.frozendawn.client.IcarusCraterAmbience.skyDim();
        if (dim > 0.003f) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            // Under the soot pall daylight dims: the more a level owes to the sky (y) and the less to blocks (x), the more.
            float scale = 1 - dim * (y / 15f) * (1 - x / 15f);
            int p = lightPixels.getPixelRGBA(x, y);
            int pr = (int) ((p & 0xFF) * scale), pg = (int) ((p >> 8 & 0xFF) * scale), pb = (int) ((p >> 16 & 0xFF) * scale);
            lightPixels.setPixelRGBA(x, y, 0xFF000000 | pb << 16 | pg << 8 | pr);
        }
        float k = IcarusBlastCloud.worldLight(partialTicks);
        if (k <= 0.003f) return;
        int rgb = IcarusBlastCloud.lightColor(), r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            // NativeImage pixels are ABGR.
            int p = lightPixels.getPixelRGBA(x, y);
            int pr = p & 0xFF, pg = p >> 8 & 0xFF, pb = p >> 16 & 0xFF;
            pr += (int) ((r - pr) * k); pg += (int) ((g - pg) * k); pb += (int) ((b - pb) * k);
            lightPixels.setPixelRGBA(x, y, 0xFF000000 | pb << 16 | pg << 8 | pr);
        }
    }
}
