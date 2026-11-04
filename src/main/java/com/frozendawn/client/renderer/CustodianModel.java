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

/** The Custodian as drawn in {@code watch-floor-mockup.png}: 32 by 48 texels, a block to a 16, arms too long, no face. It opens
 * and dies as drawn in {@code watch-floor-tally-mockup.png}: the chest's two halves swing out, the core pushes through. */
public final class CustodianModel extends HierarchicalModel<CustodianEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "custodian"), "main");
    private static final float SWUNG = 1.75F, KNEEL = 11, LEGS = 19;
    private final ModelPart root, head, leftLeg, rightLeg, leftArm, rightArm, leftPlate, rightPlate, core;
    private float partial;

    public CustodianModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        leftPlate = root.getChild("left_plate");
        rightPlate = root.getChild("right_plate");
        core = root.getChild("core");
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
        root.addOrReplaceChild("right_plate", CubeListBuilder.create().texOffs(88, 0).addBox(0, 0, -1, 8, 16, 1), PartPose.offset(-8, -12, -5));
        root.addOrReplaceChild("left_plate", CubeListBuilder.create().texOffs(88, 20).addBox(-8, 0, -1, 8, 16, 1), PartPose.offset(8, -12, -5));
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(88, 40).addBox(-3, -4, 0, 6, 8, 3), PartPose.offset(0, -4, -4));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override public ModelPart root() { return root; }
    @Override public void prepareMobModel(CustodianEntity entity, float limbSwing, float limbSwingAmount, float partial) { this.partial = partial; }
    @Override public void setupAnim(CustodianEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        float dying = entity.dying(partial), kneel = ease(dying, .5F, .8F);
        float swing = Mth.cos(limbSwing * .55F) * .8F * limbSwingAmount * (dying > 0 ? 0 : 1);
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;
        rightArm.xRot = -swing * .4F;
        leftArm.xRot = swing * .4F;

        float open = entity.openness(partial), swung = ease(open, .15F, .7F) * SWUNG;
        rightPlate.yRot = swung;
        leftPlate.yRot = -swung;
        core.z = -4 - 3 * ease(open, .5F, 1);
        float flare = dying > 0 && dying < .25F ? 1.25F : 1;
        core.xScale = core.yScale = flare;

        root.y = KNEEL * kneel;
        rightLeg.yScale = leftLeg.yScale = 1 - KNEEL / LEGS * kneel;
        head.xRot = .6F * kneel;
    }
    /** Nought before {@code from}, one after {@code to}, smoothly between. */
    private static float ease(float value, float from, float to) {
        float t = Mth.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
