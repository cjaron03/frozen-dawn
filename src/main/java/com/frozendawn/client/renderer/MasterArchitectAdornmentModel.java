package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/** Master-only Continuity Crown geometry layered over the ordinary Architect model. */
public final class MasterArchitectAdornmentModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "master_architect_adornments"),
            "main");

    private final ModelPart darkHead;
    private final ModelPart frostHead;
    private final ModelPart darkRightArm;
    private final ModelPart darkLeftArm;
    private final ModelPart glowBody;
    private final ModelPart glowRightArm;

    public MasterArchitectAdornmentModel(ModelPart root) {
        darkHead = root.getChild("dark_head");
        frostHead = root.getChild("frost_head");
        darkRightArm = root.getChild("dark_right_arm");
        darkLeftArm = root.getChild("dark_left_arm");
        glowBody = root.getChild("glow_body");
        glowRightArm = root.getChild("glow_right_arm");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition darkHead = root.addOrReplaceChild(
                "dark_head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.8F, -9.1F, -4.4F, 9.6F, 1.1F, 8.8F),
                PartPose.ZERO);
        darkHead.addOrReplaceChild(
                "left_prong",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-0.6F, -5.2F, -0.7F, 1.2F, 5.2F, 1.4F),
                PartPose.offsetAndRotation(-3.6F, -8.8F, 0.0F, 0.0F, 0.0F, -0.15F));
        darkHead.addOrReplaceChild(
                "center_prong",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-0.6F, -7.2F, -0.7F, 1.2F, 7.2F, 1.4F),
                PartPose.offset(0.0F, -8.8F, 0.0F));
        darkHead.addOrReplaceChild(
                "right_prong",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-0.6F, -4.4F, -0.7F, 1.2F, 4.4F, 1.4F),
                PartPose.offsetAndRotation(3.6F, -8.8F, 0.0F, 0.0F, 0.0F, 0.15F));

        root.addOrReplaceChild(
                "frost_head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.6F, -9.0F, -4.65F, 9.2F, 0.75F, 0.8F),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "dark_right_arm",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -2.8F, -3.0F, 6.0F, 2.0F, 6.0F)
                        .texOffs(0, 0)
                        .addBox(-3.4F, 7.2F, -2.4F, 4.8F, 3.0F, 4.8F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild(
                "dark_left_arm",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-2.0F, -2.8F, -3.0F, 6.0F, 2.0F, 6.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild(
                "glow_body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.2F, 0.2F, -2.65F, 8.4F, 0.8F, 0.8F),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "glow_right_arm",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.55F, 8.25F, -2.9F, 1.1F, 0.9F, 0.7F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
    }

    public void syncFrom(ArchitectModel parent) {
        darkHead.copyFrom(parent.head);
        frostHead.copyFrom(parent.head);
        darkRightArm.copyFrom(parent.rightArm);
        darkLeftArm.copyFrom(parent.leftArm);
        glowBody.copyFrom(parent.body);
        glowRightArm.copyFrom(parent.rightArm);
    }

    public void renderDark(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color) {
        darkHead.render(poseStack, consumer, packedLight, packedOverlay, color);
        darkRightArm.render(poseStack, consumer, packedLight, packedOverlay, color);
        darkLeftArm.render(poseStack, consumer, packedLight, packedOverlay, color);
    }

    public void renderFrost(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color) {
        frostHead.render(poseStack, consumer, packedLight, packedOverlay, color);
    }

    public void renderGlow(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color) {
        glowBody.render(poseStack, consumer, packedLight, packedOverlay, color);
        glowRightArm.render(poseStack, consumer, packedLight, packedOverlay, color);
    }

    public void renderAll(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color) {
        renderDark(poseStack, consumer, packedLight, packedOverlay, color);
        renderFrost(poseStack, consumer, packedLight, packedOverlay, color);
        renderGlow(poseStack, consumer, packedLight, packedOverlay, color);
    }
}
