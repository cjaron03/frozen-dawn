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

public class BlizzardGogglesCurioModel<T extends LivingEntity> extends HumanoidModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "blizzard_goggles_curio"),
            "main"
    );

    public BlizzardGogglesCurioModel(ModelPart root) {
        super(root);
        this.setAllVisible(false);
        this.head.visible = true;
        this.hat.visible = true;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.4F, -5.4F, -4.8F, 8.8F, 3.0F, 1.0F)
                        .texOffs(0, 4)
                        .addBox(-4.6F, -4.8F, -4.5F, 1.0F, 2.0F, 9.0F)
                        .texOffs(0, 15)
                        .addBox(3.6F, -4.8F, -4.5F, 1.0F, 2.0F, 9.0F)
                        .texOffs(20, 0)
                        .addBox(-4.0F, -4.7F, -4.95F, 3.0F, 2.0F, 1.0F)
                        .texOffs(20, 3)
                        .addBox(1.0F, -4.7F, -4.95F, 3.0F, 2.0F, 1.0F)
                        .texOffs(20, 6)
                        .addBox(-1.0F, -4.4F, -4.95F, 2.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void syncFrom(HumanoidModel<?> parentModel) {
        ((HumanoidModel) parentModel).copyPropertiesTo((HumanoidModel) this);
        this.setAllVisible(false);
        this.head.visible = true;
        this.hat.visible = true;
    }
}
