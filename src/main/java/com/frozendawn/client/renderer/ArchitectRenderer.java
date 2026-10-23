package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectCombatAction;
import com.frozendawn.entity.architect.ArchitectFelledDeath;
import com.frozendawn.entity.architect.ArchitectWoundDownDeath;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.homo.MasterArchitectFloodPolicy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.util.Mth;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

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
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private final MasterArchitectAdornmentLayer masterAdornmentLayer;
    private final ArchitectReconnaissanceEyesLayer reconnaissanceEyes;
    private final ArchitectBlindFaceLayer blindFace;
    private final ArchitectBlindHelmetLayer blindHelmet;

    public ArchitectRenderer(EntityRendererProvider.Context context) {
        super(context, new ArchitectModel(context.bakeLayer(ArchitectModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
        this.masterAdornmentLayer = new MasterArchitectAdornmentLayer(
                this,
                new MasterArchitectAdornmentModel(
                        context.bakeLayer(MasterArchitectAdornmentModel.LAYER_LOCATION)));
        this.addLayer(this.masterAdornmentLayer);
        this.reconnaissanceEyes = new ArchitectReconnaissanceEyesLayer(this);
        this.addLayer(reconnaissanceEyes);
        this.blindFace = new ArchitectBlindFaceLayer(this);
        this.addLayer(blindFace);
        this.blindHelmet = new ArchitectBlindHelmetLayer(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)));
        this.addLayer(blindHelmet);
        this.layers.replaceAll(layer -> layer.getClass() == net.minecraft.client.renderer.entity.layers.ItemInHandLayer.class
                ? new ScribeSlateItemLayer(this, context.getItemInHandRenderer()) : layer);
    }

    /** Shared by the distant sky face so it samples the exact live entity texture. */
    public static ResourceLocation baseTexture() {
        return TEXTURE;
    }

    @Override
    protected boolean shouldShowName(ArchitectEntity entity) { return !entity.isBlindArchitect() && super.shouldShowName(entity); }

    @Override
    public ResourceLocation getTextureLocation(ArchitectEntity entity) {
        return textureForBlinkCycle(entity.tickCount, entity.getId());
    }

    /** Shared so the distant sky head blinks with the physical Master's exact cadence. */
    public static ResourceLocation textureForBlinkCycle(int tickCount, int seed) {
        int cycle = Math.floorMod(tickCount + seed * 13, 97);
        return cycle <= 1 || cycle == 41 ? BLINK_TEXTURE : TEXTURE;
    }

    @Override
    public void render(ArchitectEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        int deathTicks = entity.getDeathTicks();
        if (entity.isWoundDownDying()) {
            renderWoundDownDeath(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight, deathTicks);
            return;
        }
        if (entity.getReconnaissanceDissolve() != 0) {
            renderScoutDissolve(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        if (deathTicks > 0) {
            if (entity.isMasterArchitectVisual()) {
                renderMasterDeathCharge(
                        entity,
                        entityYaw,
                        partialTick,
                        poseStack,
                        bufferSource,
                        packedLight,
                        deathTicks);
            } else {
                renderFelledDeath(
                        entity,
                        entityYaw,
                        partialTick,
                        poseStack,
                        bufferSource,
                        packedLight,
                        deathTicks);
            }
            return;
        }

        poseStack.pushPose();
        int action = entity.getCurrentAction();
        if (entity.isMasterArchitectVisual()
                && entity.getMasterCombatAction()
                        == MasterArchitectCombatAction.STORM_MAINTENANCE) {
            float time = entity.tickCount + partialTick;
            poseStack.translate(
                    Mth.sin(time * 2.2F) * 0.009F,
                    Mth.cos(time * 2.7F) * 0.006F,
                    Mth.sin(time * 2.45F) * 0.009F);
        } else if (entity.isMasterArchitectVisual()
                && entity.getMasterCombatAction()
                        == MasterArchitectCombatAction.FLOOD_FOLD_CAST) {
            float time = entity.tickCount + partialTick;
            poseStack.translate(
                    Mth.sin(time * 5.7F) * 0.026F,
                    Mth.cos(time * 6.4F) * 0.014F,
                    Mth.sin(time * 5.1F) * 0.026F);
        } else if (entity.isMasterMindCopy()
                && entity.getMasterCombatAction()
                        == MasterArchitectCombatAction.MIND_CORE_REVEAL) {
            float time = entity.tickCount + partialTick;
            float elapsed = Mth.clamp(
                    (MasterArchitectFloodPolicy.CORE_REVEAL_TICKS
                            - entity.getMasterCombatActionTicks())
                            / (float) MasterArchitectFloodPolicy.CORE_REVEAL_TICKS,
                    0.0F,
                    1.0F);
            float recoil = Mth.sin(Mth.clamp(elapsed / 0.34F, 0.0F, 1.0F) * Mth.PI);
            poseStack.translate(
                    Mth.sin(time * 8.0F) * 0.028F * recoil,
                    -0.025F * recoil,
                    0.075F * recoil);
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    Mth.sin(time * 7.2F) * 2.4F * recoil));
        } else if (entity.isMasterMindCopy()
                && entity.getMasterCombatAction()
                        == MasterArchitectCombatAction.MIND_RETURN_STAGGER) {
            float time = entity.tickCount + partialTick;
            poseStack.translate(
                    Mth.sin(time * 8.8F) * 0.075F,
                    Mth.cos(time * 10.2F) * 0.045F,
                    Mth.sin(time * 9.5F) * 0.075F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    Mth.sin(time * 7.6F) * 4.2F));
        } else if (entity.isMasterMindCopy()
                && entity.getMasterCombatAction()
                        == MasterArchitectCombatAction.MIND_CORE_EXPOSED) {
            float time = entity.tickCount + partialTick;
            poseStack.translate(
                    Mth.sin(time * 9.2F) * 0.048F,
                    Mth.cos(time * 10.6F) * 0.026F,
                    Mth.sin(time * 8.7F) * 0.048F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    Mth.sin(time * 6.8F) * 2.8F));
        } else if (!entity.isMasterArchitectVisual()
                && !entity.isHoldingMaevePosition()
                && !entity.isShowingReconnaissancePose()
                && (action == ArchitectEntity.ACTION_OBSERVE
                || action == ArchitectEntity.ACTION_PEEK)) {
            float sway = Mth.sin((entity.tickCount + partialTick) * 0.035f) * 0.9f;
            poseStack.mulPose(Axis.YP.rotationDegrees(sway));
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    protected float getShadowRadius(ArchitectEntity entity) {
        return entity.getDeathTicks() > 0 || entity.getReconnaissanceDissolve() != 0
                ? 0.0f : super.getShadowRadius(entity);
    }

    private void renderScoutDissolve(ArchitectEntity entity, float yaw, float partialTick,
                                     PoseStack poses, MultiBufferSource buffers, int light) {
        float alpha = 1.0F - Math.abs(entity.getReconnaissanceDissolve()) / 20.0F;
        if (alpha <= 0) return;
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        poses.scale(-1, -1, 1); poses.translate(0, -1.501F, 0);
        model.attackTime = 0; model.riding = entity.isPassenger(); model.young = entity.isBaby();
        model.prepareMobModel(entity, 0, 0, partialTick);
        model.setupAnim(entity, 0, 0, entity.tickCount + partialTick, entity.getYHeadRot() - yaw, entity.getXRot());
        model.renderToBuffer(poses, buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                light, LivingEntityRenderer.getOverlayCoords(entity, 0),
                FastColor.ARGB32.color((int) (alpha * 255), 255, 255, 255));
        if (entity.getReconnaissanceDissolve() > 0)
            reconnaissanceEyes.render(poses, buffers, light, entity, 0, 0, partialTick, entity.tickCount + partialTick, 0, 0);
        poses.popPose();
    }

    /** Rigid, buckling forward fall timed to the beam collapse death sound. */
    private void renderFelledDeath(ArchitectEntity entity, float entityYaw, float partialTick,
                                     PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                     int deathTicks) {
        float elapsed = deathTicks + partialTick;
        float topple = ArchitectFelledDeath.topple(elapsed);
        float fall = ArchitectFelledDeath.fallDegrees(elapsed);
        float sink = 0.22f * ArchitectFelledDeath.buckle(elapsed) * (1.0f - topple);
        float tremor = ArchitectFelledDeath.tremor(elapsed) * Mth.sin(elapsed * 2.9f) * 1.2f;
        float alpha = ArchitectFelledDeath.alpha(elapsed);
        if (alpha <= 0.0f) return;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        // Pivot at the feet; lift by half the body depth as it lies down so it lands on the ground, not in it.
        poseStack.translate(0.0f, 0.18f * Mth.sin(fall * Mth.DEG_TO_RAD) - sink, 0.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.fallsBackward() ? fall : -fall));
        poseStack.mulPose(Axis.ZP.rotationDegrees(7.0f * topple + tremor));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        this.model.attackTime = 0.0f;
        this.model.riding = entity.isPassenger();
        this.model.young = entity.isBaby();
        this.model.prepareMobModel(entity, 0.0f, 0.0f, partialTick);
        this.model.setupAnim(entity, 0.0f, 0.0f, entity.tickCount + partialTick, 0.0f, 0.0f);

        VertexConsumer body = bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
        int color = FastColor.ARGB32.color((int) (alpha * 255), 255, 255, 255);
        this.model.renderToBuffer(
                poseStack,
                body,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(entity, this.getWhiteOverlayProgress(entity, partialTick)),
                color
        );
        poseStack.popPose();
    }

    /** Blind crumples into a sit, its hood slips off, and the body darkens and sheds away. */
    private void renderWoundDownDeath(ArchitectEntity entity, float entityYaw, float partialTick,
                                      PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                      int deathTicks) {
        float elapsed = deathTicks + partialTick;
        float crumple = ArchitectWoundDownDeath.crumple(elapsed);
        float sag = ArchitectWoundDownDeath.wind(elapsed) * (1.0f - crumple);
        float alpha = ArchitectWoundDownDeath.alpha(elapsed);
        float hoodAlpha = ArchitectWoundDownDeath.hoodAlpha(elapsed);
        if (hoodAlpha <= 0.0f) return;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        // Hips drop from leg height to the ground; the body leans forward about them.
        float hip = 0.75f - 0.05f * sag - 0.62f * crumple;
        poseStack.translate(0.0f, hip, 0.0f);
        poseStack.mulPose(Axis.XP.rotation(-ArchitectWoundDownDeath.lean(elapsed)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(1.5f * ArchitectWoundDownDeath.twitch(elapsed)));
        poseStack.translate(0.0f, -0.75f, 0.0f);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        this.model.attackTime = 0.0f;
        this.model.riding = false;
        this.model.young = entity.isBaby();
        this.model.prepareMobModel(entity, 0.0f, 0.0f, partialTick);
        this.model.setupAnim(entity, 0.0f, 0.0f, entity.tickCount + partialTick, 0.0f, 0.0f);

        if (alpha > 0.0f) {
            int shade = (int) (255 * (1.0f - 0.85f * ArchitectWoundDownDeath.ash(elapsed)));
            this.model.renderToBuffer(
                    poseStack,
                    bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                    packedLight,
                    LivingEntityRenderer.getOverlayCoords(entity, 0.0f),
                    FastColor.ARGB32.color((int) (alpha * 255), shade, shade, shade));
            // The eye mask goes with the body once it is mostly dust.
            if (alpha > 0.45f) {
                blindFace.render(poseStack, bufferSource, packedLight, entity, 0, 0, partialTick, entity.tickCount + partialTick, 0, 0);
            }
        }
        blindHelmet.renderSlipping(poseStack, bufferSource, packedLight, entity,
                ArchitectWoundDownDeath.hoodSlip(elapsed), hoodAlpha);
        poseStack.popPose();
    }

    private void renderMasterDeathCharge(
            ArchitectEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int deathTicks) {
        float elapsed = deathTicks + partialTick;
        float charge = MasterArchitectCombatPolicy.deathChargeProgress(elapsed);
        float shake = MasterArchitectCombatPolicy.deathShakeStrength(elapsed);
        float time = entity.tickCount + partialTick;
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.72F);
        float glowAlpha = Mth.clamp(charge * (0.72F + pulse * 0.26F), 0.0F, 0.98F);

        poseStack.pushPose();
        poseStack.translate(
                Mth.sin(time * 3.85F) * 0.052F * shake,
                Mth.sin(time * 4.65F) * 0.034F * shake,
                Mth.cos(time * 4.2F) * 0.052F * shake);
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.sin(time * 3.35F) * 4.4F * shake));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        float limbSwing = entity.walkAnimation.position(partialTick);
        float limbSwingAmount = entity.walkAnimation.speed(partialTick);
        float headYaw = entity.getYHeadRot() - entityYaw;
        float headPitch = entity.getXRot();
        this.model.attackTime = this.getAttackAnim(entity, partialTick);
        this.model.riding = entity.isPassenger();
        this.model.young = entity.isBaby();
        this.model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        this.model.setupAnim(
                entity,
                limbSwing,
                limbSwingAmount,
                time,
                headYaw,
                headPitch);

        VertexConsumer body = bufferSource.getBuffer(
                RenderType.entityTranslucent(getTextureLocation(entity)));
        this.model.renderToBuffer(
                poseStack,
                body,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.color(255, 255, 255, 255));

        if (glowAlpha > 0.0F) {
            VertexConsumer glow = bufferSource.getBuffer(
                    RenderType.entityTranslucentEmissive(WHITE_TEXTURE));
            this.model.renderToBuffer(
                    poseStack,
                    glow,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(
                            Mth.floor(glowAlpha * 255.0F), 255, 255, 255));
        }
        this.masterAdornmentLayer.render(
                poseStack,
                bufferSource,
                packedLight,
                entity,
                limbSwing,
                limbSwingAmount,
                partialTick,
                time,
                headYaw,
                headPitch);
        poseStack.popPose();
    }
}
