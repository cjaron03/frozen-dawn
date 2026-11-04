package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.frozendawn.vault.CustodianEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Custodian as drawn in {@code watch-floor-mockup.png}: 32 by 48 texels, a block to a 16, arms too long, no face. */
public final class CustodianModel extends HierarchicalModel<CustodianEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "custodian"), "main");
    private final ModelPart root, leftLeg, rightLeg, leftArm, rightArm;

    public CustodianModel(ModelPart root) {
        this.root = root;
        leftLeg = root.getChild("left_leg");
        rightLeg = root.getChild("right_leg");
        leftArm = root.getChild("left_arm");
        rightArm = root.getChild("right_arm");
    }

    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -24, -5, 12, 10, 10), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 20).addBox(-10, -14, -5, 20, 19, 10), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(64, 0).addBox(-3, 0, -3, 6, 25, 6), PartPose.offset(-13, -14, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(64, 0).mirror().addBox(-3, 0, -3, 6, 25, 6), PartPose.offset(13, -14, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(64, 32).addBox(-3, 0, -4, 6, 19, 8), PartPose.offset(-5, 5, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(64, 32).mirror().addBox(-3, 0, -4, 6, 19, 8), PartPose.offset(5, 5, 0));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override public ModelPart root() { return root; }
    @Override public void setupAnim(CustodianEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        float swing = Mth.cos(limbSwing * .55F) * .8F * limbSwingAmount;
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;
        rightArm.xRot = -swing * .4F;
        leftArm.xRot = swing * .4F;
    }
}
