package com.frozendawn.client.renderer;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** While a Scribe writes, its slate turns face-up toward its eyes. Every other held item renders as vanilla. */
final class ScribeSlateItemLayer extends ItemInHandLayer<ArchitectEntity, ArchitectModel> {
    static final float ROLL = 90.0F, TILT = -35.0F;
    private final ItemInHandRenderer items;
    private float partialTick;

    ScribeSlateItemLayer(RenderLayerParent<ArchitectEntity, ArchitectModel> parent, ItemInHandRenderer items) {
        super(parent, items);
        this.items = items;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, ArchitectEntity entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        this.partialTick = partialTick;
        super.render(pose, buffers, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    protected void renderArmWithItem(LivingEntity living, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                     PoseStack pose, MultiBufferSource buffers, int light) {
        float writing = living instanceof ArchitectEntity architect && arm == HumanoidArm.RIGHT
                && stack.is(ModItems.SCRIBE_RECORD.get()) ? architect.getScribeWriting(partialTick) : 0.0F;
        if (writing <= 0.001F) {
            super.renderArmWithItem(living, stack, context, arm, pose, buffers, light);
            return;
        }
        // Vanilla's hand transform, then a roll and tilt so the slate's face meets the writing hand.
        pose.pushPose();
        getParentModel().translateToHand(arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(1.0F / 16.0F, 0.125F, -0.625F);
        pose.mulPose(Axis.ZP.rotationDegrees(ROLL * writing));
        pose.mulPose(Axis.XP.rotationDegrees(TILT * writing));
        items.renderItem(living, stack, context, false, pose, buffers, light);
        pose.popPose();
    }
}
