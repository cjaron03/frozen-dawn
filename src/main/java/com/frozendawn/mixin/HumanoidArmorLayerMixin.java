package com.frozendawn.mixin;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.frozendawn.init.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes enclosed visor helmets render with translucency so the tinted
 * visor pixels (alpha < 255) show through to the player's face underneath.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>>
        extends RenderLayer<T, M> {

    private static final ResourceLocation THERMAL_VISOR_FACE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/thermal_visor_face.png");

    protected HumanoidArmorLayerMixin(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD")
    )
    private void frozendawn$renderThermalVisorFace(PoseStack poseStack, MultiBufferSource bufferSource,
                                                   int light, T entity, float limbSwing, float limbSwingAmount,
                                                   float partialTick, float ageInTicks, float netHeadYaw,
                                                   float headPitch, CallbackInfo ci) {
        if (!entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ORSA_THERMAL_VISOR.get())) {
            return;
        }

        M parentModel = this.getParentModel();
        boolean headVisible = parentModel.head.visible;
        boolean hatVisible = parentModel.hat.visible;
        boolean bodyVisible = parentModel.body.visible;
        boolean rightArmVisible = parentModel.rightArm.visible;
        boolean leftArmVisible = parentModel.leftArm.visible;
        boolean rightLegVisible = parentModel.rightLeg.visible;
        boolean leftLegVisible = parentModel.leftLeg.visible;

        parentModel.setAllVisible(false);
        parentModel.head.visible = true;
        parentModel.hat.visible = false;

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(THERMAL_VISOR_FACE));
        parentModel.renderToBuffer(
                poseStack,
                vc,
                light,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                -1
        );

        parentModel.head.visible = headVisible;
        parentModel.hat.visible = hatVisible;
        parentModel.body.visible = bodyVisible;
        parentModel.rightArm.visible = rightArmVisible;
        parentModel.leftArm.visible = leftArmVisible;
        parentModel.rightLeg.visible = rightLegVisible;
        parentModel.leftLeg.visible = leftLegVisible;
    }

    @Inject(
            method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void frozendawn$translucentVisor(PoseStack poseStack, MultiBufferSource bufferSource,
                                              int light, Model model, int color,
                                              ResourceLocation texture, CallbackInfo ci) {
        if (FrozenDawn.MOD_ID.equals(texture.getNamespace())
                && (texture.getPath().contains("eva_layer_1")
                || texture.getPath().contains("thermal_visor_layer_1"))) {
            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
            model.renderToBuffer(poseStack, vc, light, OverlayTexture.NO_OVERLAY, color);
            ci.cancel();
        }
    }
}
