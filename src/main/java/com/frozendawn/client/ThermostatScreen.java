package com.frozendawn.client;
import com.frozendawn.block.ThermostatMenu;
import com.frozendawn.world.ThermostatManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class ThermostatScreen extends AbstractContainerScreen<ThermostatMenu> {
    public ThermostatScreen(ThermostatMenu menu,Inventory inv,Component name){super(menu,inv,name);imageWidth=214;imageHeight=154;inventoryLabelY=999;}
    @Override protected void init(){super.init();for(int id=0;id<2;id++){final int button=id;addRenderableWidget(Button.builder(Component.literal(id==0?"-5 C":"+5 C"),b->{if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);}).bounds(leftPos+22+id*100,topPos+111,70,20).build());}}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,(imageWidth-font.width(title))/2,8,0x77DDEE,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,0xFF607882);g.fill(x+2,y+2,x+imageWidth-2,y+imageHeight-2,0xFF101820);var d=menu.data();int mode=d.get(5);String status=switch(mode){case ThermostatManager.SEALED->d.get(1)>d.get(0)*10+5?"Above target":d.get(1)>=d.get(0)*10-5?"Holding":"Heating";case ThermostatManager.OPEN->"Open camp";case ThermostatManager.OVERRIDDEN->"Overridden";case ThermostatManager.NO_SEAL->"No seal";default->"Waiting for room";};
        if((mode==ThermostatManager.SEALED||mode==ThermostatManager.OPEN)&&d.get(1)>d.get(0)*10+5)status="Above target";
        g.drawString(font,"Target: "+d.get(0)+" C",x+12,y+29,0xE0E8EC,false);
        g.drawString(font,"Sensed: "+temperature(d.get(1)),x+12,y+44,0x99DDEE,false);
        g.drawString(font,(mode==ThermostatManager.OPEN?"Background: ":"Room base: ")+temperature(d.get(2)),x+12,y+58,0x9BAAB7,false);
        g.drawString(font,"Heaters: "+d.get(3)+" | Burn: "+d.get(4)+"%",x+12,y+74,0xE0C090,false);
        g.drawString(font,status,x+12,y+91,mode==ThermostatManager.OVERRIDDEN||mode==ThermostatManager.NO_SEAL?0xFFAA66:0x88DDAA,false);
        g.drawString(font,"Core/local warmth included",x+12,y+137,0x7B909F,false);
    }
    private static String temperature(int t){return t==-32768?"--":String.format(java.util.Locale.ROOT,"%.1f C",t/10.0);}
}
