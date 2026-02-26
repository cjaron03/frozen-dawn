package com.frozendawn.mixin;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes EVA helmet visor render with translucency so the gold-tinted
 * visor pixels (alpha < 255) show through to the player's face underneath.
 */
@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Inject(
            method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void frozendawn$translucentVisor(PoseStack poseStack, MultiBufferSource bufferSource,
                                              int light, Model model, int color,
                                              ResourceLocation texture, CallbackInfo ci) {
        if (FrozenDawn.MOD_ID.equals(texture.getNamespace())
                && texture.getPath().contains("eva_layer_1")) {
            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
            model.renderToBuffer(poseStack, vc, light, OverlayTexture.NO_OVERLAY, color);
            ci.cancel();
        }
    }
}
