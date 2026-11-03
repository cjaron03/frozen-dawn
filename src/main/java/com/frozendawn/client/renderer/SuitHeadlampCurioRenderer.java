package com.frozendawn.client.renderer;

import com.frozendawn.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Equipment appearance is independent of switch/charge: an unpowered lamp remains mounted. */
public final class SuitHeadlampCurioRenderer implements ICurioRenderer {
    private static final ResourceLocation WHITE=ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private final SuitHeadlampCurioModel model;
    public SuitHeadlampCurioRenderer(EntityModelSet models) {
        model=new SuitHeadlampCurioModel(models.bakeLayer(SuitHeadlampCurioModel.LAYER_LOCATION));
    }
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void render(
            ItemStack stack,SlotContext context,PoseStack pose,RenderLayerParent<T,M> parent,
            MultiBufferSource buffers,int light,float limbSwing,float limbSwingAmount,float partialTicks,
            float ageInTicks,float netHeadYaw,float headPitch) {
        if(!context.identifier().equals("helmet_mount")||context.index()!=0||context.cosmetic()
                ||context.entity().isInvisible()||!(parent.getModel() instanceof HumanoidModel<?> humanoid))return;
        var helmet=context.entity().getItemBySlot(EquipmentSlot.HEAD);
        if(!helmet.is(ModItems.EVA_HELMET)&&!helmet.is(ModItems.ORSA_THERMAL_VISOR))return;
        model.renderAttached(pose,humanoid.head,buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)),
                light,LivingEntityRenderer.getOverlayCoords(context.entity(),0));
    }
}
