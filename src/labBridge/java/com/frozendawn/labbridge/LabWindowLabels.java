package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Window identity and guest-only labels in local lab clients; excluded from release packaging. */
@EventBusSubscriber(modid="frozendawn",value=Dist.CLIENT)
public final class LabWindowLabels {
    private static long nextReport;
    private static boolean enabled(){return !FMLEnvironment.production&&(Boolean.getBoolean("frozendawn.labBridge")||Boolean.getBoolean("frozendawn.labLanGuest"));}
    private static boolean guest(){return Boolean.getBoolean("frozendawn.labLanGuest");}
    private static String title(){return guest()?"Frozen Dawn Lab - GUEST ArchitectGuest":"Frozen Dawn Lab - HOST Dev";}
    private static String banner(){return guest()?"GUEST: ArchitectGuest - PLAY HERE":"";}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){if(!enabled())return;var mc=Minecraft.getInstance();mc.getWindow().setTitle(title());long now=System.currentTimeMillis();if(now<nextReport)return;nextReport=now+2000;
        try{var o=new JsonObject();o.addProperty("titleApplied",title());o.addProperty("banner",banner());o.addProperty("pid",ProcessHandle.current().pid());o.addProperty("role",guest()?"GUEST":"HOST");o.addProperty("sampledAtMillis",now);o.addProperty("player",mc.player==null?"":mc.player.getUUID().toString());o.addProperty("focused",mc.isWindowActive());o.addProperty("screenOpen",mc.screen!=null);o.addProperty("checkout",System.getProperty("frozendawn.labBridge.checkout"));o.addProperty("note","Title applied on real client tick; only the guest draws a menu/HUD banner. This is not an OS pixel inspection.");LabBlindLan.atomic(mc.gameDirectory.toPath().resolve("lab-bridge/window-label.json"),o);}catch(Exception ex){System.err.println("[LabWindowLabels] Label acknowledgement failed: "+ex);}}
    private static void draw(GuiGraphics g){var mc=Minecraft.getInstance();String text=banner();int x=Math.max(4,(g.guiWidth()-mc.font.width(text))/2);g.fill(x-3,3,x+mc.font.width(text)+3,16,0xCC101018);g.drawString(mc.font,text,x,5,guest()?0xFF80FF90:0xFFFFB060,true);}
    @SubscribeEvent public static void menu(ScreenEvent.Render.Post e){if(enabled()&&guest())draw(e.getGuiGraphics());}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){if(enabled()&&guest()&&Minecraft.getInstance().screen==null)draw(e.getGuiGraphics());}
}
