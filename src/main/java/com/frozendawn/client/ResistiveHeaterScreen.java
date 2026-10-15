package com.frozendawn.client;

import com.frozendawn.block.ResistiveHeaterMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ResistiveHeaterScreen extends AbstractContainerScreen<ResistiveHeaterMenu> {
    public ResistiveHeaterScreen(ResistiveHeaterMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=228;imageHeight=126;}
    private String number(int n){return String.format(Locale.ROOT,"%.1f",n/10.0);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,0xFF28353D);g.fill(x+7,y+23,x+221,y+119,0xFF152128);
        int s=menu.data().get(0);String key=switch(s){case 1->"no_power";case 2->"heating";case 3->"structure";case 4->"target";default->"waiting";};
        g.drawString(font,Component.translatable("screen.frozendawn.rh5."+key),x+13,y+32,s==1?0xFFE5A064:0xFF9CD5A5,false);
        g.drawString(font,Component.translatable(menu.data().get(4)==1?"screen.frozendawn.rh5.air":"screen.frozendawn.rh5.walls",menu.data().get(1)==-32768?"--":number(menu.data().get(1))),x+13,y+51,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.rh5.setpoint",number(menu.data().get(2))),x+13,y+67,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.rh5.draw",String.format(Locale.ROOT,"%.2f",menu.data().get(3)/1000.0)),x+13,y+83,0xFF64C9CC,false);
        g.drawString(font,Component.translatable("screen.frozendawn.rh5.hint"),x+13,y+104,0xFF9BAAB1,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,8,7,0xFFE0E6E8,false);}
}
