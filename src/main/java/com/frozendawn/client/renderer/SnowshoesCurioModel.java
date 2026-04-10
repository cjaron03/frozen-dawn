package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class SnowshoesCurioModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "snowshoes_curio"),
            "main"
    );

    public SnowshoesCurioModel(ModelPart root) {
        super(root);
        this.setAllVisible(false);
        this.rightLeg.visible = true;
        this.leftLeg.visible = true;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-2.8F, 10.4F, -4.4F, 5.0F, 1.0F, 8.0F)
                        .texOffs(0, 9)
                        .addBox(-1.8F, 8.6F, -1.8F, 3.0F, 2.0F, 4.0F)
                        .texOffs(15, 9)
                        .addBox(-2.1F, 11.0F, -3.1F, 4.0F, 1.0F, 1.0F)
                        .texOffs(15, 11)
                        .addBox(-2.1F, 11.0F, 2.1F, 4.0F, 1.0F, 1.0F),
                PartPose.offset(-1.9F, 12.0F, 0.0F)
        );
        root.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .mirror()
                        .addBox(-2.2F, 10.4F, -4.4F, 5.0F, 1.0F, 8.0F)
                        .texOffs(0, 9)
                        .mirror()
                        .addBox(-1.2F, 8.6F, -1.8F, 3.0F, 2.0F, 4.0F)
                        .texOffs(15, 9)
                        .mirror()
                        .addBox(-1.9F, 11.0F, -3.1F, 4.0F, 1.0F, 1.0F)
                        .texOffs(15, 11)
                        .mirror()
                        .addBox(-1.9F, 11.0F, 2.1F, 4.0F, 1.0F, 1.0F),
                PartPose.offset(1.9F, 12.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, 32, 32);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void syncFrom(HumanoidModel<?> parentModel) {
        ((HumanoidModel) parentModel).copyPropertiesTo((HumanoidModel) this);
        this.setAllVisible(false);
        this.rightLeg.visible = true;
        this.leftLeg.visible = true;
    }
}
