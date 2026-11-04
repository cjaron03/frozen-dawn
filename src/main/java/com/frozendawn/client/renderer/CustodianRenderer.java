package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.vault.CustodianEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** The steel takes the room's light; the lens, and the core as it comes through the shell, are what you track in the dark. */
public final class CustodianRenderer extends MobRenderer<CustodianEntity, CustodianModel> {
    private static final ResourceLocation STEEL = texture("custodian"), LENS = texture("custodian_lens"), CORE = texture("custodian_core");

    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/" + name + ".png"); }

    public CustodianRenderer(EntityRendererProvider.Context context) {
        super(context, new CustodianModel(context.bakeLayer(CustodianModel.LAYER_LOCATION)), 1);
        addLayer(new RenderLayer<>(this) {
            @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, CustodianEntity entity, float limb, float amount, float partial, float age, float yaw, float pitch) {
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(LENS)), 0xF00000, OverlayTexture.NO_OVERLAY);
                int shade = (int) (entity.coreLight(partial) * 255);
                if (shade > 0) getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(CORE)), 0xF00000, OverlayTexture.NO_OVERLAY, 0xFF000000 | shade * 0x010101);
            }
        });
    }
    @Override public ResourceLocation getTextureLocation(CustodianEntity entity) { return STEEL; }
}
