package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.RocketLaunchEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public class RocketLaunchModel extends EntityModel<RocketLaunchEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "rocket_launch"), "main");

    private final ModelPart root;

    public RocketLaunchModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("engine_bell",
                CubeListBuilder.create().texOffs(0, 48).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 6.0F, 12.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("lower_body",
                CubeListBuilder.create().texOffs(0, 20).addBox(-4.0F, -26.0F, -4.0F, 8.0F, 20.0F, 8.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("mid_body",
                CubeListBuilder.create().texOffs(32, 16).addBox(-4.0F, -50.0F, -4.0F, 8.0F, 24.0F, 8.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("upper_body",
                CubeListBuilder.create().texOffs(64, 16).addBox(-4.0F, -74.0F, -4.0F, 8.0F, 24.0F, 8.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("logo_plate",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -63.0F, -4.55F, 7.0F, 7.0F, 1.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("nose_base",
                CubeListBuilder.create().texOffs(32, 48).addBox(-3.0F, -86.0F, -3.0F, 6.0F, 12.0F, 6.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("nose_tip",
                CubeListBuilder.create().texOffs(56, 48).addBox(-2.0F, -94.0F, -2.0F, 4.0F, 8.0F, 4.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("fin_north",
                CubeListBuilder.create().texOffs(88, 0).addBox(-5.0F, -16.0F, -1.0F, 10.0F, 14.0F, 2.0F),
                PartPose.offset(0.0F, 24.0F, -5.0F));
        root.addOrReplaceChild("fin_south",
                CubeListBuilder.create().texOffs(88, 16).addBox(-5.0F, -16.0F, -1.0F, 10.0F, 14.0F, 2.0F),
                PartPose.offset(0.0F, 24.0F, 5.0F));
        root.addOrReplaceChild("fin_west",
                CubeListBuilder.create().texOffs(88, 32).addBox(-1.0F, -16.0F, -5.0F, 2.0F, 14.0F, 10.0F),
                PartPose.offset(-5.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("fin_east",
                CubeListBuilder.create().texOffs(88, 56).addBox(-1.0F, -16.0F, -5.0F, 2.0F, 14.0F, 10.0F),
                PartPose.offset(5.0F, 24.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(RocketLaunchEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
                               com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               int color) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
