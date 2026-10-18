package com.frozendawn.client;

import com.frozendawn.block.PowerStorageMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Just the cells, charge bar and current flow. No configuration or extra tabs. */
public final class PowerStorageScreen extends AbstractContainerScreen<PowerStorageMenu> {
    public PowerStorageScreen(PowerStorageMenu m, Inventory i, Component t) { super(m,i,t);imageWidth=176;imageHeight=182;inventoryLabelY=89; }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;g.fill(x,y,x+176,y+182,0xFF28353D);g.fill(x+6,y+19,x+170,y+48,0xFF18242B);
        int capacity=menu.cellSlots()*5000,stored=menu.data().get(0);
        g.drawString(font,String.format(Locale.ROOT,"%.1f / %d kJ",stored/10.0,menu.cellSlots()*500),x+12,y+23,0xFFE0E6E8,false);
        g.fill(x+12,y+38,x+164,y+42,0xFF40515A);g.fill(x+12,y+38,x+12+(int)(152L*stored/capacity),y+42,0xFF64C9CC);
        for(var slot:menu.slots)g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xFF152128);
        int charge=menu.chargingWatts(),draw=menu.feedingWatts();
        String flow=charge>0?String.format(Locale.ROOT,"Charging %.2f kW",charge/1000.0):draw>0?String.format(Locale.ROOT,"Feeding %.2f kW",draw/1000.0):"Idle";
        g.drawString(font,flow,x+8,y+77,0xFF9CD5A5,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) { g.drawString(font,title,8,6,0xFFE0E6E8,false);g.drawString(font,playerInventoryTitle,8,89,0xFF9BAAB1,false); }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) { super.render(g,mx,my,partial);renderTooltip(g,mx,my); }
}
