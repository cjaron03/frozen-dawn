package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Architect renderer — HumanoidMobRenderer with:
 * - 5 texture variants (reuses Returned textures)
 * - 30-tick death animation (lean + collapse)
 * - Held tools rendered automatically via ItemInHandLayer
 */
public class ArchitectRenderer extends HumanoidMobRenderer<ArchitectEntity, ArchitectModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/architect.png");
    private static final ResourceLocation BLINK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/architect_blink.png");

    public ArchitectRenderer(EntityRendererProvider.Context context) {
        super(context, new ArchitectModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(ArchitectEntity entity) {
        return isBlinking(entity) ? BLINK_TEXTURE : TEXTURE;
    }

    private boolean isBlinking(ArchitectEntity entity) {
        int cycle = Math.floorMod(entity.tickCount + entity.getId() * 13, 97);
        return cycle <= 1 || cycle == 41;
    }

    @Override
    public void render(ArchitectEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        int deathTicks = entity.getDeathTicks();
        if (deathTicks > 0) {
            float progress = (deathTicks + partialTick) / 30.0f;
            progress = Math.min(1.0f, progress);
            float eased = 1.0f - (float) Math.pow(1.0f - progress, 3.0f);
            float drift = Mth.sin((entity.tickCount + partialTick) * 0.15f) * 3.0f * eased;
            float spin = eased * 10.0f;
            float shrink = 1.0f - eased * 0.22f;
            poseStack.translate(0.0f, eased * 1.35f, 0.0f);
            poseStack.mulPose(Axis.YP.rotationDegrees(drift));
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
            poseStack.scale(shrink, 1.0f - eased * 0.10f, shrink);
        } else {
            int action = entity.getCurrentAction();
            if (action == ArchitectEntity.ACTION_OBSERVE || action == ArchitectEntity.ACTION_PEEK) {
                float sway = Mth.sin((entity.tickCount + partialTick) * 0.06f) * 2.5f;
                poseStack.mulPose(Axis.YP.rotationDegrees(sway));
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
