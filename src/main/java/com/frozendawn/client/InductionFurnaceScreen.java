package com.frozendawn.client;

import com.frozendawn.block.InductionFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Familiar two-slot smelting layout: input, arrow, output and one status line. */
public final class InductionFurnaceScreen extends AbstractContainerScreen<InductionFurnaceMenu> {
    public InductionFurnaceScreen(InductionFurnaceMenu m,Inventory inv,Component title){super(m,inv,title);imageWidth=176;imageHeight=166;inventoryLabelY=73;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;g.fill(x,y,x+176,y+166,0xFF28353D);
        for(var s:menu.slots)g.fill(x+s.x-1,y+s.y-1,x+s.x+17,y+s.y+17,0xFF152128);
        g.fill(x+77,y+41,x+96,y+47,0xFF4C5961);for(int n=0;n<4;n++)g.fill(x+96+n,y+38+n,x+97+n,y+50-n,0xFF4C5961);
        int fill=24*Math.clamp(menu.data().get(0),0,Math.max(1,menu.data().get(1)))/Math.max(1,menu.data().get(1));
        g.enableScissor(x+77,y+38,x+77+fill,y+50);g.fill(x+77,y+41,x+96,y+47,0xFF64C9CC);for(int n=0;n<4;n++)g.fill(x+96+n,y+38+n,x+97+n,y+50-n,0xFF64C9CC);g.disableScissor();
        int status=menu.data().get(2);String key=switch(status){case 1->"no_power";case 2->"smelting";case 3->"no_recipe";case 4->"output_full";default->"idle";};
        int watts=menu.data().get(3)*100;boolean hot=watts>com.frozendawn.block.InductionFurnaceBlockEntity.DEMAND_WATTS&&(status==1||status==2);
        // Refining runs hotter than smelting; say why the draw rose instead of showing a bare number.
        var text=hot?Component.translatable("screen.frozendawn.induction_furnace."+key+"_hot",watts/1000):Component.translatable("screen.frozendawn.induction_furnace."+key);
        g.drawString(font,text,x+(176-font.width(text))/2,y+(hot?53:60),status==2?0xFF9CD5A5:status==1?0xFFE5A064:0xFF9BAAB1,false);
        if(hot){var why=Component.translatable("screen.frozendawn.induction_furnace.refining_reason",com.frozendawn.recipe.InductionRecipes.REFINING_C);g.drawString(font,why,x+(176-font.width(why))/2,y+62,0xFF9BAAB1,false);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,title,8,6,0xFFE0E6E8,false);g.drawString(font,playerInventoryTitle,8,73,0xFF9BAAB1,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
}
