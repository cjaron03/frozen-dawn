package com.frozendawn.client.renderer;
import com.frozendawn.block.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
/** Emissive segmented contents indicator on the native ribbed model, with a readable numeric reserve. */
public final class OxygenReservoirRenderer implements BlockEntityRenderer<OxygenReservoirBlockEntity> {
    private final Font font;
    public OxygenReservoirRenderer(BlockEntityRendererProvider.Context c){font=c.getFont();}
    @Override public void render(OxygenReservoirBlockEntity tank,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(tank.getLevel()==null)return;
        pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(180-tank.getBlockState().getValue(OxygenReservoirBlock.FACING).toYRot()));pose.translate(-.5,-.5,-.5);
        var matrix=pose.last().pose();var vertices=buffers.getBuffer(RenderType.debugQuads());
        double fraction=(double)tank.storedMicro()/OxygenReservoirBlockEntity.capacityMicro();
        int active=(int)Math.ceil(fraction*6);boolean blink=tank.getLevel().getGameTime()%20<12;
        int color=!tank.connected()?(blink?0xFFBF38:0x343C40):tank.output()>0?0xFFCF4A:0x55E4DB;
        for(int i=0;i<6;i++){
            int c=i<active?color:0x26343A;float x0=6/16F,x1=10/16F,y0=(4.8f+i*.78f)/16F,y1=y0+.48f/16F,z=1.5f/16F-.002F;
            vertices.addVertex(matrix,x0,y0,z).setColor(c>>16&255,c>>8&255,c&255,255);vertices.addVertex(matrix,x0,y1,z).setColor(c>>16&255,c>>8&255,c&255,255);
            vertices.addVertex(matrix,x1,y1,z).setColor(c>>16&255,c>>8&255,c&255,255);vertices.addVertex(matrix,x1,y0,z).setColor(c>>16&255,c>>8&255,c&255,255);
        }
        pose.translate(.5,11.5/16.0,1.5/16.0-.004);pose.scale(-.004f,-.004f,.004f);
        String text="O2 "+Math.round(tank.storedSeconds())+"s";float width=font.width(text);if(width>88)pose.scale(88/width,1,1);
        font.drawInBatch(text,-width/2,0,color,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,0xF000F0);pose.popPose();
    }
}
