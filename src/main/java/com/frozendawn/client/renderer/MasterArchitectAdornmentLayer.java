package com.frozendawn.client.renderer;

import com.frozendawn.client.MasterArchitectWeather;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectCombatAction;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Renders the Continuity Crown silhouette only for the Hearth Master Architect. */
public final class MasterArchitectAdornmentLayer
        extends RenderLayer<ArchitectEntity, ArchitectModel> {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int DARK_COLOR = FastColor.ARGB32.color(255, 16, 27, 36);
    private static final int FROST_COLOR = FastColor.ARGB32.color(255, 159, 203, 208);

    private final MasterArchitectAdornmentModel adornments;

    public MasterArchitectAdornmentLayer(
            RenderLayerParent<ArchitectEntity, ArchitectModel> parent,
            MasterArchitectAdornmentModel adornments) {
        super(parent);
        this.adornments = adornments;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            ArchitectEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!entity.isHearthMasterArchitect() || entity.isInvisible()) {
            return;
        }

        adornments.syncFrom(getParentModel());
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        VertexConsumer cutout = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(WHITE_TEXTURE));
        adornments.renderDark(poseStack, cutout, packedLight, overlay, DARK_COLOR);
        adornments.renderFrost(poseStack, cutout, packedLight, overlay, FROST_COLOR);

        VertexConsumer glow = bufferSource.getBuffer(
                RenderType.entityTranslucentEmissive(WHITE_TEXTURE));
        boolean hostile = MasterArchitectWeather.getStrength() > 0.05F
                || entity.getMasterCombatAction() != MasterArchitectCombatAction.IDLE;
        float pulse = 0.5F + 0.5F * Mth.sin(ageInTicks * (hostile ? 0.32F : 0.15F));
        int glowAlpha = Mth.floor((hostile
                ? 0.80F + pulse * 0.20F
                : 0.58F + pulse * 0.14F) * 255.0F);
        int crownAlpha = Mth.floor((hostile
                ? 0.30F + pulse * 0.38F
                : 0.10F + pulse * 0.10F) * 255.0F);
        adornments.renderGlow(
                poseStack,
                glow,
                LightTexture.FULL_BRIGHT,
                overlay,
                FastColor.ARGB32.color(glowAlpha, 85, 219, 233));
        adornments.renderFrost(
                poseStack,
                glow,
                LightTexture.FULL_BRIGHT,
                overlay,
                FastColor.ARGB32.color(crownAlpha, 111, 235, 244));

        float thermalCharge = entity.getMasterCombatAction()
                == MasterArchitectCombatAction.THERMAL_SEVER
                ? MasterArchitectCombatPolicy.thermalCastCharge(
                        entity.getMasterCombatActionTicks())
                : entity.getMasterThermalCharge();
        adornments.renderThermalCharge(
                poseStack,
                glow,
                LightTexture.FULL_BRIGHT,
                overlay,
                thermalCharge);

        if (entity.getDeathTicks() > 0) {
            float charge = MasterArchitectCombatPolicy.deathChargeProgress(
                    entity.getDeathTicks() + partialTick);
            int alpha = Mth.floor(Mth.clamp(charge * 0.78F, 0.0F, 0.78F) * 255.0F);
            if (alpha > 0) {
                adornments.renderAll(
                        poseStack,
                        glow,
                        LightTexture.FULL_BRIGHT,
                        overlay,
                        FastColor.ARGB32.color(alpha, 255, 255, 255));
            }
        }
    }
}
