package com.frozendawn.client.renderer;

import com.frozendawn.block.AirlockControllerBlock;
import com.frozendawn.block.AirlockControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Two native-size LED pixels animate on the approved panel face, entirely client-side. */
public final class AirlockControllerRenderer implements BlockEntityRenderer<AirlockControllerBlockEntity> {
    public AirlockControllerRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(AirlockControllerBlockEntity entity,float partialTick,PoseStack pose,
            MultiBufferSource buffers,int light,int overlay) {
        if(entity.getLevel()==null)return;
        int indicator=entity.getBlockState().getValue(AirlockControllerBlock.INDICATOR);
        long tick=entity.getLevel().getGameTime();
        boolean on=switch(indicator) {
            case 1 -> tick%10<5; // Fast pump blink.
            case 2 -> tick%40<34; // Mostly steady, brief ready heartbeat.
            default -> tick%40<20;
        };
        int color=on?switch(indicator) {case 1 -> 0xFFBF38;case 2 -> 0x65EA98;default -> 0xFF5656;}:0x20282E;
        pose.pushPose();pose.translate(.5,.5,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-entity.getBlockState().getValue(AirlockControllerBlock.FACING).toYRot()));
        pose.translate(-.5,-.5,-.5);
        var matrix=pose.last().pose();var vertices=buffers.getBuffer(RenderType.debugQuads());
        // Front image pixels (10..12, 4..6), matching the existing 16x16 lamp.
        float x0=4/16F,x1=6/16F,y0=10/16F,y1=12/16F,z=-.001F;
        int red=color>>16&255,green=color>>8&255,blue=color&255;
        vertices.addVertex(matrix,x0,y0,z).setColor(red,green,blue,255);
        vertices.addVertex(matrix,x0,y1,z).setColor(red,green,blue,255);
        vertices.addVertex(matrix,x1,y1,z).setColor(red,green,blue,255);
        vertices.addVertex(matrix,x1,y0,z).setColor(red,green,blue,255);
        pose.popPose();
    }
}
