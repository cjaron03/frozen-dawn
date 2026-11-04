package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.shepherd.ShepherdEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;

/** The Lantern, box for box from make_body_directions.lantern(). Texture offsets match make_textures.BOXES. */
public final class ShepherdModel extends HierarchicalModel<ShepherdEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "cs1_unit"), "main");
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/cs1_unit.png");
    /** Voxel to model space: she is centered on x and z, and voxel y 0 is the entity's feet. */
    public static final int CENTER_X = 6, CENTER_Z = 5, FEET_Y = 24;

    private final ModelPart root;

    public ShepherdModel(ModelPart root) {
        this.root = root;
    }

    private static CubeListBuilder box(CubeListBuilder cubes, int u, int v, int x0, int y0, int z0, int x1, int y1, int z1) {
        return cubes.texOffs(u, v).addBox(x0 - CENTER_X, FEET_Y - y1, CENTER_Z - z1, x1 - x0, y1 - y0, z1 - z0, CubeDeformation.NONE);
    }

    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var cubes = CubeListBuilder.create();
        box(cubes, 0, 0, 0, 2, 0, 12, 12, 10);
        box(cubes, 0, 20, 3, 0, 2, 9, 2, 8);
        box(cubes, 24, 20, -3, 5, 2, 0, 9, 8);
        box(cubes, 42, 20, 12, 5, 2, 15, 9, 8);
        box(cubes, 0, 30, -3, 3, 5, 0, 4, 6);
        box(cubes, 8, 30, 12, 3, 5, 15, 4, 6);
        box(cubes, 16, 30, 2, 12, 4, 3, 15, 5);
        box(cubes, 20, 30, 9, 12, 4, 10, 15, 5);
        box(cubes, 24, 30, 2, 15, 4, 10, 16, 5);
        mesh.getRoot().addOrReplaceChild("lantern", cubes, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override public ModelPart root() {
        return root;
    }

    @Override public void setupAnim(ShepherdEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }
}
