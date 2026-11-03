package com.frozendawn.client;

import com.frozendawn.block.*;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PixieScreen extends AbstractContainerScreen<PixieMenu> {
    public PixieScreen(PixieMenu m,Inventory i,Component t){super(m,i,t);imageWidth=210;imageHeight=246;inventoryLabelY=153;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x,y,x+210,y+246,0xFF28353D);g.fill(x+7,y+23,x+203,y+146,0xFF152128);
        for(var s:menu.slots)g.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xFF111C23);
        String state=switch(menu.data().get(0)){case 0->"producing";case 1->"idle";case 2->"no_power";case 3->"no_water";case 4->"frozen";case 5->"unsealed";default->"waiting";};
        g.drawString(font,Component.translatable("screen.frozendawn.pixie."+state),x+12,y+29,menu.data().get(1)>0?0xFF9CD5A5:menu.data().get(0)==PixieBlockEntity.IDLE?0xFF9BAAB1:0xFFE5A064,false);
        if(menu.data().get(1)>0){String activity=switch(menu.data().get(6)){case PixieBlockEntity.STORAGE->"storage";case PixieBlockEntity.ROOM_AND_STORAGE->"room_storage";default->"room";};g.drawString(font,Component.translatable("screen.frozendawn.pixie."+activity),x+40,y+43,0xFF9BAAB1,false);}
        g.drawString(font,Component.translatable("screen.frozendawn.pixie.ice"),x+12,y+43,0xFF9BAAB1,false);
        g.drawString(font,Component.translatable("screen.frozendawn.pixie.output",String.format(Locale.ROOT,"%.1f",menu.data().get(1)/1000.0)),x+40,y+55,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.pixie.power",String.format(Locale.ROOT,"%.2f",menu.data().get(1)/1000.0)),x+40,y+71,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.pixie.water",String.format(Locale.ROOT,"%.1f",menu.water()/12000000.0)),x+12,y+87,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.pixie.machine_temperature",temperature(menu.data().get(7))),x+12,y+116,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.pixie.room_feel",temperature(menu.data().get(8))),x+12,y+132,0xFF9BAAB1,false);
        g.fill(x+12,y+100,x+198,y+106,0xFF39464F);g.fill(x+12,y+100,x+12+(int)(186*Math.min(menu.water(),PixieBlockEntity.WATER_CAPACITY)/PixieBlockEntity.WATER_CAPACITY),y+106,0xFF64C9CC);
    }
    private static String temperature(int value){return value==Short.MIN_VALUE?"--":String.format(Locale.ROOT,"%.1f°C",value/10.0);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,12,9,0xFFE0E6E8,false);g.drawString(font,playerInventoryTitle,24,153,0xFF9BAAB1,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);if(mx>=leftPos+12&&mx<leftPos+198&&my>=topPos+84&&my<topPos+107)g.renderTooltip(font,Component.translatable("screen.frozendawn.pixie.water_hint"),mx,my);if(mx>=leftPos+12&&mx<leftPos+198&&my>=topPos+112&&my<topPos+127)g.renderTooltip(font,Component.translatable("screen.frozendawn.pixie.machine_temperature_hint"),mx,my);if(mx>=leftPos+12&&mx<leftPos+198&&my>=topPos+128&&my<topPos+144)g.renderTooltip(font,Component.translatable("screen.frozendawn.pixie.room_feel_hint"),mx,my);}
}
