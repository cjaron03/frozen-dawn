package com.frozendawn.client;

import com.frozendawn.block.ResistiveHeaterBlockEntity;
import com.frozendawn.block.ResistiveHeaterMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public final class ResistiveHeaterScreen extends AbstractContainerScreen<ResistiveHeaterMenu> {
    private PowerLimitSlider limit;
    public ResistiveHeaterScreen(ResistiveHeaterMenu m, Inventory inv, Component title) {
        super(m, inv, title); imageWidth=228; imageHeight=154;
    }
    @Override protected void init() {
        super.init();
        limit=addRenderableWidget(new PowerLimitSlider(leftPos+13, topPos+102));
    }
    @Override protected void containerTick() { super.containerTick(); limit.synchronize(); }
    private String number(int n) { return String.format(Locale.ROOT, "%.1f", n/10.0); }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x=leftPos, y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF28353D);
        g.fill(x+7,y+23,x+221,y+147,0xFF152128);
        int s=menu.data().get(0);
        String key=switch(s) {
            case 1->"no_power"; case 2->"heating"; case 3->"structure";
            case 4->"target"; case 5->"limited"; case 6->"off"; default->"waiting";
        };
        g.drawString(font,Component.translatable("screen.frozendawn.rh5."+key),x+13,y+32,
                s==1||s==5?0xFFE5A064:0xFF9CD5A5,false);
        g.drawString(font,Component.translatable(menu.data().get(4)==1?"screen.frozendawn.rh5.air":"screen.frozendawn.rh5.walls",
                menu.data().get(1)==-32768?"--":number(menu.data().get(1))),x+13,y+51,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.rh5.setpoint",number(menu.data().get(2))),x+13,y+67,0xFFE0E6E8,false);
        g.drawString(font,Component.translatable("screen.frozendawn.rh5.draw",
                String.format(Locale.ROOT,"%.2f",menu.drawWatts()/1000.0),menu.powerLimitKw()),x+13,y+83,0xFF64C9CC,false);
        g.drawString(font,Component.translatable("screen.frozendawn.rh5.hint"),x+13,y+133,0xFF9BAAB1,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) { g.drawString(font,title,8,7,0xFFE0E6E8,false); }

    private final class PowerLimitSlider extends AbstractSliderButton {
        private static final int MAX_KW=ResistiveHeaterBlockEntity.MAX_WATTS/1000;
        private boolean dragging;
        private int submitted=-1;
        private long submittedAt;
        PowerLimitSlider(int x,int y) {
            super(x,y,202,20,Component.empty(),menu.powerLimitKw()/(double)MAX_KW);
            updateMessage();
        }
        private int kilowatts() { return (int)Math.round(value*MAX_KW); }
        @Override protected void updateMessage() {
            setMessage(Component.translatable("screen.frozendawn.rh5.limit",kilowatts()));
        }
        @Override protected void applyValue() {
            value=Math.round(value*MAX_KW)/(double)MAX_KW;
            updateMessage();
            int kw=kilowatts();
            if(kw!=submitted&&minecraft!=null&&minecraft.gameMode!=null) {
                submitted=kw; submittedAt=net.minecraft.Util.getMillis();
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,ResistiveHeaterMenu.LIMIT_BUTTON_BASE+kw);
            }
        }
        @Override public void onClick(double x,double y) { dragging=true; super.onClick(x,y); }
        @Override public boolean mouseReleased(double x,double y,int button) {
            boolean result=super.mouseReleased(x,y,button); dragging=false; return result;
        }
        @Override public boolean keyPressed(int key,int scanCode,int modifiers) {
            if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT) {
                value=Math.clamp(value+(key==GLFW.GLFW_KEY_LEFT?-1.0:1.0)/MAX_KW,0,1);
                applyValue(); return true;
            }
            return super.keyPressed(key,scanCode,modifiers);
        }
        void synchronize() {
            if(dragging)return;
            if(submitted>=0&&menu.powerLimitKw()!=submitted&&net.minecraft.Util.getMillis()-submittedAt<1000)return;
            value=menu.powerLimitKw()/(double)MAX_KW; submitted=-1; updateMessage();
        }
    }
}
