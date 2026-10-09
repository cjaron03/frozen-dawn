package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Disposable bottle, regulator, harness and field patches on the vanilla EVA silhouette. */
public final class EmergencyEvaSuitLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation MATERIAL = ResourceLocation.fromNamespaceAndPath(
            FrozenDawn.MOD_ID, "textures/entity/player/emergency_eva_material.png");
    private final ModelPart bottle = boxes(new float[][] {
            {-2.5F, 2, 3.3F, 5, 7, 4}, {-1.8F, 1.3F, 4, 3.6F, 0.7F, 2.6F},
            {-1.8F, 9, 4, 3.6F, 0.7F, 2.6F}});
    private final ModelPart hardware = boxes(new float[][] {
            {-1, 0, 4.4F, 2, 1.4F, 1.8F}, {1.4F, 1, 3.4F, 2, 2, 2},
            {2.6F, 2.5F, 2.9F, 0.7F, 4, 0.7F}, {2.6F, 6, -3.2F, 0.7F, 0.7F, 6.8F}});
    private final ModelPart harness = boxes(new float[][] {
            {-2.9F, 0.5F, 3.05F, 0.65F, 10, 0.5F}, {2.25F, 0.5F, 3.05F, 0.65F, 10, 0.5F},
            {-3.8F, 3.4F, -3.1F, 7.6F, 0.8F, 0.35F}, {-3.2F, 8.7F, -3.1F, 6.4F, 0.7F, 0.35F}});
    private final ModelPart pullTab = boxes(new float[][] {{2.8F, 2.4F, 5.5F, 0.9F, 1.8F, 0.3F}});
    private final ModelPart label = boxes(new float[][] {{-1.7F, 4.5F, 7.31F, 3.4F, 1.5F, 0.12F}});
    private final ModelPart labelStripe = boxes(new float[][] {{-1.7F, 4.5F, 7.45F, 0.55F, 1.5F, 0.05F}});
    private final ModelPart chestPatch = boxes(new float[][] {{-2.8F, 5, -3.12F, 2.5F, 2, 0.3F}});
    private final ModelPart jointPatch = boxes(new float[][] {{-1.2F, 3, -2.75F, 2.4F, 2.8F, 0.3F}});
    private final ModelPart armPatch = boxes(new float[][] {{-1.2F, 3, -3.15F, 2.4F, 2.8F, 0.3F}});
    private final ModelPart bootSeam = boxes(new float[][] {{-2.4F, 9, -3.15F, 4.8F, 0.65F, 0.3F}});
    private final ModelPart collar = boxes(new float[][] {
            {-5.1F, -0.8F, -5.15F, 10.2F, 0.7F, 0.3F}, {-5.1F, -0.8F, 4.85F, 10.2F, 0.7F, 0.3F}});
    private final ModelPart helmetTape = boxes(new float[][] {
            {0.8F, -5.2F, -5.03F, 3.4F, 0.65F, 0.15F}, {1.5F, -5.85F, -5.03F, 2.5F, 0.65F, 0.15F}});

    public EmergencyEvaSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
        // Stack identity is synced for other players; owner-only attachment state is not a render gate.
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.EMERGENCY_EVA_CHESTPLATE.get())) {
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            draw(bottle, pose, consumer, light, 0xFFABB5B2);
            draw(hardware, pose, consumer, light, 0xFF535E61);
            draw(harness, pose, consumer, light, 0xFF555C59);
            draw(pullTab, pose, consumer, light, 0xFFC6A460);
            draw(label, pose, consumer, light, 0xFFD0D5CA);
            draw(labelStripe, pose, consumer, light, 0xFF4C92B8);
            draw(chestPatch, pose, consumer, light, 0xFF899590);
            pose.popPose();
            attached(armPatch, getParentModel().rightArm, pose, consumer, light, 0xFF929C93);
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.EMERGENCY_EVA_LEGGINGS.get())) {
            attached(jointPatch, getParentModel().leftLeg, pose, consumer, light, 0xFF737F78);
        }
        if (player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.EMERGENCY_EVA_BOOTS.get())) {
            attached(bootSeam, getParentModel().leftLeg, pose, consumer, light, 0xFF8B938D);
            attached(bootSeam, getParentModel().rightLeg, pose, consumer, light, 0xFF8B938D);
        }
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.EMERGENCY_EVA_HELMET.get())) {
            attached(collar, getParentModel().head, pose, consumer, light, 0xFF737C79);
            attached(helmetTape, getParentModel().head, pose, consumer, light, 0xFFABB5AF);
        }
    }

    private static void attached(ModelPart detail, ModelPart anchor, PoseStack pose,
            VertexConsumer consumer, int light, int color) {
        pose.pushPose();
        anchor.translateAndRotate(pose);
        draw(detail, pose, consumer, light, color);
        pose.popPose();
    }

    private static void draw(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int color) {
        part.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, color);
    }

    private static ModelPart boxes(float[][] boxes) {
        var mesh = new MeshDefinition();
        var builder = CubeListBuilder.create().texOffs(0, 0);
        for (var box : boxes) builder.addBox(box[0], box[1], box[2], box[3], box[4], box[5]);
        mesh.getRoot().addOrReplaceChild("detail", builder, PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("detail");
    }
}
