package com.frozendawn.client.renderer;

import com.frozendawn.block.BloomMassBlock;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.hearthrot.HearthrotPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModEffects;
import com.frozendawn.init.ModItems;
import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Small 3D Bloom fragments on EVA visor, intake, and joints. */
public final class HearthrotSuitLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation[] INTERNAL_GROWTH_TEXTURES = {
            null,
            id("textures/entity/player/hearthrot_growth_1.png"),
            id("textures/entity/player/hearthrot_growth_2.png"),
            id("textures/entity/player/hearthrot_growth_3.png"),
            id("textures/entity/player/hearthrot_growth_4.png")
    };

    public HearthrotSuitLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        renderInternalGrowth(poseStack, bufferSource, packedLight, player);
        int stage = visualStage(player);
        if (stage <= 0) {
            return;
        }
        BlockState pale = ModBlocks.BLOOM_MASS.get().defaultBlockState()
                .setValue(BloomMassBlock.BAND, BloomBand.FRONTIER);
        BlockState fresh = ModBlocks.BLOOM_TIP.get().defaultBlockState();

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        fragment(poseStack, bufferSource, fresh,
                0.29F, -0.30F, -0.31F, 0.105F, -18.0F, packedLight);
        if (stage >= 2) {
            fragment(poseStack, bufferSource, pale,
                    -0.30F, -0.22F, -0.29F, 0.09F, 26.0F, packedLight);
        }
        poseStack.popPose();

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        fragment(poseStack, bufferSource, pale,
                0.29F, 0.04F, -0.19F, 0.09F, 36.0F, packedLight);
        if (stage >= 3) {
            fragment(poseStack, bufferSource, fresh,
                    -0.27F, -0.15F, -0.20F, 0.085F, -24.0F, packedLight);
        }
        poseStack.popPose();

        if (stage >= 2) {
            poseStack.pushPose();
            getParentModel().rightArm.translateAndRotate(poseStack);
            fragment(poseStack, bufferSource, pale,
                    0.02F, 0.39F, -0.03F, 0.085F, 18.0F, packedLight);
            poseStack.popPose();
        }
        if (stage >= 3) {
            poseStack.pushPose();
            getParentModel().leftLeg.translateAndRotate(poseStack);
            fragment(poseStack, bufferSource, pale,
                    0.0F, 0.43F, -0.03F, 0.085F, -31.0F, packedLight);
            poseStack.popPose();
        }
        if (stage >= 4) {
            poseStack.pushPose();
            getParentModel().leftArm.translateAndRotate(poseStack);
            fragment(poseStack, bufferSource, pale,
                    -0.03F, 0.22F, -0.05F, 0.10F, -14.0F, packedLight);
            poseStack.popPose();
            poseStack.pushPose();
            getParentModel().rightLeg.translateAndRotate(poseStack);
            fragment(poseStack, bufferSource, fresh,
                    0.01F, 0.28F, -0.04F, 0.085F, 22.0F, packedLight);
            poseStack.popPose();
        }
    }

    private void renderInternalGrowth(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            AbstractClientPlayer player) {
        MobEffectInstance effect = player.getEffect(ModEffects.HEARTHROT);
        int diseaseStage = effect == null ? 0 : effect.getAmplifier() + 1;
        if (diseaseStage < 3) {
            return;
        }
        int visualStage = Math.min(4, diseaseStage - 2);
        poseStack.pushPose();
        poseStack.scale(1.0015F, 1.0015F, 1.0015F);
        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.entityTranslucent(INTERNAL_GROWTH_TEXTURES[visualStage]));
        getParentModel().renderToBuffer(
                poseStack,
                consumer,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                FastColor.ARGB32.color(255, 255, 255, 255));
        poseStack.popPose();
    }

    private static int visualStage(AbstractClientPlayer player) {
        int maximum = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = player.getItemBySlot(slot);
            if (!isColonizable(stack)) {
                continue;
            }
            maximum = Math.max(maximum, stack.getOrDefault(
                    ModDataComponents.HEARTHROT_COLONIZATION.get(), 0));
        }
        return HearthrotPolicy.visualStage(maximum);
    }

    private static boolean isColonizable(ItemStack stack) {
        return stack.is(ModItems.EVA_HELMET.get())
                || stack.is(ModItems.EVA_CHESTPLATE.get())
                || stack.is(ModItems.EVA_LEGGINGS.get())
                || stack.is(ModItems.EVA_BOOTS.get())
                || stack.is(ModItems.LINED_EVA_CHESTPLATE.get())
                || stack.is(ModItems.ORSA_THERMAL_VISOR.get());
    }

    private static void fragment(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            BlockState state,
            float x,
            float y,
            float z,
            float scale,
            float rotation,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation * 0.35F));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation * 0.55F));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, poseStack, bufferSource, packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, path);
    }
}
