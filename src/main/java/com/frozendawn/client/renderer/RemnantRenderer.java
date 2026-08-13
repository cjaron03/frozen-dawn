package com.frozendawn.client.renderer;

import com.frozendawn.entity.RemnantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.UUID;

/** Renders the committed Remnant as an exact visual snapshot of its victim. */
public final class RemnantRenderer extends
        HumanoidMobRenderer<RemnantEntity, RemnantReflectionPlayerModel> {
    private final RemnantReflectionPlayerModel wideModel;
    private final RemnantReflectionPlayerModel slimModel;

    public RemnantRenderer(EntityRendererProvider.Context context) {
        super(context,
                new RemnantReflectionPlayerModel(
                        context.bakeLayer(ModelLayers.PLAYER), false),
                0.5F);
        wideModel = model;
        slimModel = new RemnantReflectionPlayerModel(
                context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(RemnantEntity entity) {
        return skin(entity).texture();
    }

    @Override
    public void render(RemnantEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.isInvisible()) return;
        model = skin(entity).model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
        entity.beginVisualEquipmentRender();
        poseStack.pushPose();
        if (entity.deathTime > 0) {
            float elapsed = entity.deathTime + partialTick;
            float rupture = 1.0F - Mth.clamp((elapsed - 29.0F) / 18.0F, 0.0F, 1.0F);
            poseStack.translate(
                    Mth.sin(elapsed * 5.7F + entity.getId()) * 0.055F * rupture,
                    Math.abs(Mth.sin(elapsed * 4.3F)) * 0.025F * rupture,
                    Mth.cos(elapsed * 6.2F) * 0.032F * rupture);
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    Mth.sin(elapsed * 5.1F) * 4.8F * rupture));
        }
        try {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
            entity.endVisualEquipmentRender();
        }
    }

    @Override
    protected void setupRotations(RemnantEntity entity, PoseStack poseStack,
                                  float bob, float bodyYaw, float partialTick, float scale) {
        if (entity.deathTime > 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            return;
        }
        super.setupRotations(entity, poseStack, bob, bodyYaw, partialTick, scale);
    }

    private static PlayerSkin skin(RemnantEntity entity) {
        UUID playerId = entity.facePlayer().orElse(entity.getUUID());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerId);
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(playerId);
    }
}
