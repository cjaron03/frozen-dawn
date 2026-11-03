package com.frozendawn.client.renderer;
import com.frozendawn.block.*;
import com.frozendawn.oxygen.OxygenStatus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.network.chat.Component;
/** Live emissive text on an original native panel; warning pulse is client-side, with no menu or speech. */
public final class OxygenGaugeRenderer implements BlockEntityRenderer<OxygenGaugeBlockEntity> {
    private final Font font;
    public OxygenGaugeRenderer(BlockEntityRendererProvider.Context c){font=c.getFont();}
    @Override public void render(OxygenGaugeBlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(entity.getLevel()==null)return;var r=entity.reading();int color=OxygenStatus.color(r.mode(),entity.getLevel().getGameTime());
        pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(180-entity.getBlockState().getValue(OxygenGaugeBlock.FACING).toYRot()));
        pose.translate(0,0,13/16.0-.5-.004);pose.scale(-.006f,-.006f,.006f);
        draw(Component.literal("ORSA / O2"),-33,0xB2C7D0,pose,buffers);
        draw(Component.translatable("gauge.frozendawn.oxygen."+r.mode().name().toLowerCase(Locale.ROOT)),-20,color,pose,buffers);
        boolean measured=r.room()!=0&&r.mode()!=OxygenStatus.Mode.WAITING;
        draw(Component.literal(measured?"SUPPLY "+number(r.supply()):"SUPPLY --"),-6,color,pose,buffers);
        draw(Component.literal(measured?"DEMAND "+number(r.demand()):"DEMAND --"),6,color,pose,buffers);
        var reserve=entity.storage();
        draw(Component.literal(reserve.tanks()>0?"STORED "+Math.round(reserve.stored())+"s":measured?"LOCAL "+number(r.localDemand()):"LOCAL --"),18,0xB2C7D0,pose,buffers);
        draw(Component.literal(reserve.tanks()>0?(!reserve.connected()?"VALVE CLOSED":reserve.output()>0?"OUT "+number(reserve.output())+(reserve.secondsRemaining()>=0?" / "+Math.round(reserve.secondsRemaining())+"s":" / LIMITED"):reserve.stored()>=reserve.capacity()?"FULL":"CHARGING "+number(reserve.charging())):measured?r.rooms()+" ROOM"+(r.rooms()==1?"":"S")+" / "+r.sources()+" SOURCE"+(r.sources()==1?"":"S"):"RATE / PLAYER"),30,0x8299A5,pose,buffers);
        pose.popPose();
    }
    private String number(double v){return v>9999?"9999+":String.format(Locale.ROOT,"%.2f",v);}
    private void draw(Component text,int y,int color,PoseStack pose,MultiBufferSource buffers){float width=font.width(text);pose.pushPose();if(width>104)pose.scale(104/width,1,1);
        font.drawInBatch(text,-width/2,y,color,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,0xF000F0);pose.popPose();}
}
