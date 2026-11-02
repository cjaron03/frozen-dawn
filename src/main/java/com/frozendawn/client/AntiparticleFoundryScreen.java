package com.frozendawn.client;

import com.frozendawn.block.AntiparticleFoundryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Two slots, a fill bar and a status line with the time left on the current capsule. */
public final class AntiparticleFoundryScreen extends AbstractContainerScreen<AntiparticleFoundryMenu> {
    public AntiparticleFoundryScreen(AntiparticleFoundryMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=176;imageHeight=166;inventoryLabelY=73;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x,y,x+176,y+166,0xFF2B2838);
        for(var s:menu.slots)g.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xFF16131F);
        int done=menu.data().get(0),total=Math.max(1,menu.data().get(1));
        g.fill(x+72,y+41,x+104,y+47,0xFF4A4559);g.fill(x+72,y+41,x+72+32*Math.clamp(done,0,total)/total,y+47,0xFFB07CF0);
        int status=menu.data().get(2),left=Math.max(0,total-done);
        var text=switch(status){
            case 1->Component.translatable("screen.frozendawn.antiparticle_foundry.no_power",menu.data().get(3));
            case 2->Component.translatable("screen.frozendawn.antiparticle_foundry.filling",left/60,String.format(java.util.Locale.ROOT,"%02d",left%60));
            case 4->Component.translatable("screen.frozendawn.antiparticle_foundry.output_full");
            default->Component.translatable("screen.frozendawn.antiparticle_foundry.idle");};
        g.drawString(font,text,x+(176-font.width(text))/2,y+60,status==2?0xFFC9A5F5:status==1?0xFFE5A064:0xFF9BAAB1,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,8,6,0xFFE0E6E8,false);g.drawString(font,playerInventoryTitle,8,73,0xFF9BAAB1,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
}
