package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchitectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Blind silhouette uses vanilla leather headgear as an adornment, without armor attributes. */
final class ArchitectBlindHelmetLayer extends RenderLayer<ArchitectEntity, ArchitectModel> {
    private static final ResourceLocation LEATHER = ResourceLocation.withDefaultNamespace("textures/models/armor/leather_layer_1.png");
    private static final ResourceLocation OVERLAY = ResourceLocation.withDefaultNamespace("textures/models/armor/leather_layer_1_overlay.png");
    private final HumanoidArmorModel<ArchitectEntity> helmet;

    ArchitectBlindHelmetLayer(RenderLayerParent<ArchitectEntity, ArchitectModel> parent, HumanoidArmorModel<ArchitectEntity> helmet) {
        super(parent);
        this.helmet = helmet;
    }

    @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, ArchitectEntity actor,
                                 float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (!actor.isBlindArchitect() || actor.isInvisible() || !getParentModel().head.visible
                || !actor.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        getParentModel().copyPropertiesTo(helmet);
        helmet.setAllVisible(false);
        helmet.head.visible = true;
        helmet.hat.visible = true;
        helmet.renderToBuffer(poses, buffers.getBuffer(RenderType.armorCutoutNoCull(LEATHER)), light, OverlayTexture.NO_OVERLAY, 0xFF5B4030);
        helmet.renderToBuffer(poses, buffers.getBuffer(RenderType.armorCutoutNoCull(OVERLAY)), light, OverlayTexture.NO_OVERLAY, -1);
    }
}
