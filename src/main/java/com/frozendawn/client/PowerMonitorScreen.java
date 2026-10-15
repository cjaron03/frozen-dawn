package com.frozendawn.client;

import com.frozendawn.block.PowerMonitorMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** One view: supply, demand, charge and devices waiting for power. No inventory or settings. */
public final class PowerMonitorScreen extends AbstractContainerScreen<PowerMonitorMenu> {
    private Button previous,next;
    public PowerMonitorScreen(PowerMonitorMenu m,Inventory i,Component t){super(m,i,t);imageWidth=252;imageHeight=222;}
    @Override protected void init(){super.init();previous=addRenderableWidget(Button.builder(Component.literal("<"),b->change(0)).bounds(leftPos+8,topPos+192,20,20).build());next=addRenderableWidget(Button.builder(Component.literal(">"),b->change(1)).bounds(leftPos+224,topPos+192,20,20).build());}
    private void change(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private Component rowText(int row){return Component.translatable(menu.rowKind(row)==4?"block.frozendawn.resistive_heater":menu.rowKind(row)==3?"block.frozendawn.pixie":menu.rowKind(row)==2?"screen.frozendawn.power_monitor.furnace":"screen.frozendawn.power_monitor.light");}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x,y,x+252,y+222,0xFF28353D);g.fill(x+7,y+7,x+245,y+215,0xFF152128);
        int status=menu.data().get(12);String key=switch(status){case 1->"normal";case 2->"backup";case 3->"brownout";case 4->"no_power";default->"unlinked";};
        int color=switch(status){case 1->0xFF9CD5A5;case 2->0xFF64C9CC;case 3->0xFFE5A064;case 4->0xFFE58B83;default->0xFF9BAAB1;};
        g.drawString(font,Component.translatable("screen.frozendawn.power_monitor."+key),x+12,y+27,color,false);
        g.drawString(font,Component.translatable("screen.frozendawn.power_monitor.supply",String.format(Locale.ROOT,"%.2f",menu.value(0,2)/1000.0)),x+12,y+46,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.power_monitor.demand",String.format(Locale.ROOT,"%.2f",menu.value(2,2)/1000.0)),x+12,y+62,0xFFE0E6E8,false);
        long energy=menu.value(4,4),capacity=menu.value(8,4);
        g.drawString(font,Component.translatable("screen.frozendawn.power_monitor.battery",String.format(Locale.ROOT,"%.1f",energy/1000.0),String.format(Locale.ROOT,"%.0f",capacity/1000.0)),x+12,y+78,0xFFE0E6E8,false);
        g.fill(x+12,y+93,x+240,y+99,0xFF39464F);int filled=capacity<=0?0:(int)(228*Math.min(energy,capacity)/capacity);g.fill(x+12,y+93,x+12+filled,y+99,0xFF64C9CC);
        g.drawString(font,Component.translatable("screen.frozendawn.power_monitor.waiting",menu.data().get(13)),x+12,y+109,0xFFE5A064,false);
        if(menu.data().get(13)==0)g.drawString(font,Component.translatable(status==0?"screen.frozendawn.power_monitor.link_hint":"screen.frozendawn.power_monitor.none"),x+12,y+125,0xFF9BAAB1,false);
        for(int row=0;row<menu.data().get(16);row++)g.drawString(font,font.plainSubstrByWidth(rowText(row).getString(),228),x+12,y+125+row*10,0xFFE0E6E8,false);
        if(menu.data().get(15)>1){String pages=(menu.data().get(14)+1)+" / "+menu.data().get(15);g.drawString(font,pages,x+(252-font.width(pages))/2,y+199,0xFF9BAAB1,false);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,12,10,0xFFE0E6E8,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){previous.visible=next.visible=menu.data().get(15)>1;previous.active=menu.data().get(14)>0;next.active=menu.data().get(14)+1<menu.data().get(15);super.render(g,mx,my,partial);for(int row=0;row<menu.data().get(16);row++)if(mx>=leftPos+12&&mx<leftPos+240&&my>=topPos+125+row*10&&my<topPos+135+row*10)g.renderTooltip(font,rowText(row),mx,my);}
}
