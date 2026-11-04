package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.vault.VaultTechnicianBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** The technician, seated against the north wall facing south, a body in a suit. The head is turned to the door, west,
 * until the one change, when it faces the cradle, east. */
public final class VaultTechnicianRenderer implements BlockEntityRenderer<VaultTechnicianBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/vault_technician.png");
    private static final float HEAD_YAW = .9F;
    private final HumanoidModel<LivingEntity> model;

    public VaultTechnicianRenderer(BlockEntityRendererProvider.Context context) { model = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)); }

    @Override public void render(VaultTechnicianBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        model.young = false;
        model.head.yRot = be.turned() ? -HEAD_YAW : HEAD_YAW;
        model.head.xRot = .28F;
        model.hat.copyFrom(model.head);
        model.rightLeg.xRot = model.leftLeg.xRot = -Mth.HALF_PI;
        model.rightLeg.yRot = .12F;
        model.leftLeg.yRot = -.12F;
        model.rightArm.xRot = model.leftArm.xRot = -.2F;
        model.rightArm.zRot = .08F;
        model.leftArm.zRot = -.08F;
        pose.pushPose();
        pose.translate(.5, 0, .3);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(-1, -1, 1);
        pose.translate(0, -.76, 0);
        int lit = be.getLevel() == null ? light : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().south());
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), lit, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
