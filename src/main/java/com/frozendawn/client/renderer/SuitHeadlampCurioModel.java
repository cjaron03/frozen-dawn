package com.frozendawn.client.renderer;

import com.frozendawn.FrozenDawn;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/** Small native cuboids in head coordinates, outside the helmet and above the visor. */
public final class SuitHeadlampCurioModel {
    public static final ModelLayerLocation LAYER_LOCATION=new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"suit_headlamp_curio"),"main");
    private final ModelPart mount,housing,rim,lens,highlight,trim,hardware;

    public SuitHeadlampCurioModel(ModelPart root) {
        mount=root.getChild("mount");housing=root.getChild("housing");rim=root.getChild("rim");
        lens=root.getChild("lens");highlight=root.getChild("highlight");trim=root.getChild("trim");hardware=root.getChild("hardware");
    }
    public static LayerDefinition createBodyLayer() {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("mount",CubeListBuilder.create()
                .addBox(-2.2F,-8.85F,-5.25F,4.4F,2.8F,.45F)
                .addBox(-.65F,-9.12F,-5.9F,1.3F,.65F,1.15F),PartPose.ZERO);
        root.addOrReplaceChild("housing",CubeListBuilder.create()
                .addBox(-1.9F,-8.7F,-6.7F,3.8F,2.6F,1.85F),PartPose.ZERO);
        root.addOrReplaceChild("rim",CubeListBuilder.create()
                .addBox(-1.65F,-8.48F,-6.92F,3.3F,2.15F,.23F),PartPose.ZERO);
        // Three adjoining cuboids give the single lens stepped corners without coplanar overlaps.
        root.addOrReplaceChild("lens",CubeListBuilder.create()
                .addBox(-.65F,-8.23F,-7.06F,1.3F,1.65F,.14F)
                .addBox(-1.0F,-7.93F,-7.06F,.35F,1.05F,.14F)
                .addBox(.65F,-7.93F,-7.06F,.35F,1.05F,.14F),PartPose.ZERO);
        root.addOrReplaceChild("highlight",CubeListBuilder.create()
                .addBox(-.49F,-8.09F,-7.085F,.42F,.4F,.025F),PartPose.ZERO);
        root.addOrReplaceChild("trim",CubeListBuilder.create()
                .addBox(1.91F,-8.43F,-5.75F,.08F,2.05F,.45F)
                .addBox(-1.99F,-8.43F,-5.75F,.08F,2.05F,.45F),PartPose.ZERO);
        root.addOrReplaceChild("hardware",CubeListBuilder.create()
                .addBox(-1.4F,-8.82F,-6.15F,2.8F,.12F,.65F)
                .addBox(-1.48F,-7.62F,-6.96F,.25F,.35F,.04F)
                .addBox(1.23F,-7.62F,-6.96F,.25F,.35F,.04F),PartPose.ZERO);
        return LayerDefinition.create(mesh,32,32);
    }
    /** Use the actual parent head transform for standing, crouching, swimming and inventory poses. */
    public void renderAttached(PoseStack pose,ModelPart head,VertexConsumer vertices,int light,int overlay) {
        pose.pushPose();head.translateAndRotate(pose);
        mount.render(pose,vertices,light,overlay,0xFF555B65);
        housing.render(pose,vertices,light,overlay,0xFF30343C);
        rim.render(pose,vertices,light,overlay,0xFF737987);
        lens.render(pose,vertices,light,overlay,0xFFF6E7AE);
        highlight.render(pose,vertices,light,overlay,0xFFFFFBE5);
        trim.render(pose,vertices,light,overlay,0xFF32C7D1);
        hardware.render(pose,vertices,light,overlay,0xFF9298A4);
        pose.popPose();
    }
}
