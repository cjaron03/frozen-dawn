package com.frozendawn.labbridge;

import com.google.gson.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in remote guest recorder; no integrated server or production networking changes. */
@EventBusSubscriber(modid="frozendawn",value=Dist.CLIENT)
public final class LabLanGuestCapture {
    private static final String PROCESS=UUID.randomUUID().toString();
    private static final Set<String> ended=new HashSet<>();
    private static Path directory;
    private static String capture;
    private static int frames,keys,waitingFrames,waitingKeys,dropped;
    private static JsonObject gate(){try{
        if(FMLEnvironment.production||!Boolean.getBoolean("frozendawn.labLanGuest"))return null;
        Path checkout=Path.of(System.getProperty("frozendawn.labBridge.checkout")).toRealPath();
        Path path=Path.of(System.getProperty("frozendawn.labLanGate")).toAbsolutePath().normalize();
        if(!path.equals(checkout.resolve("run-lab/lab-bridge/lan-input-gate.json")))return null;
        var o=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        Path dir=Path.of(o.get("directory").getAsString()).toAbsolutePath().normalize();
        if(!o.get("checkout").getAsString().equals(checkout.toString())||!o.get("world").getAsString().equals("Blind Architect Reusable Lab")||!validDirectory(checkout,dir,o))return null;
        return o;
    }catch(Exception e){return null;}}
    public static boolean validDirectory(Path checkout,Path dir,JsonObject gate){try{
        String kind=gate.has("kind")?gate.get("kind").getAsString():"LAN";
        Path save=checkout.resolve("run-lab/saves/Blind Architect 09 Disconnected Supports");
        if(kind.equals("RECONNECT"))return Set.of("first","returned").contains(dir.getFileName().toString())&&dir.getParent().getFileName().toString().matches("attempt-[0-9a-f-]{36}")&&dir.getParent().getParent().equals(save.resolve("blind-lab-reconnect"));
        return dir.getFileName().toString().matches("attempt-[0-9a-f-]{36}")&&((kind.equals("LAN")&&!gate.has("kind")&&dir.getParent().equals(save.resolve("blind-lab-lan")))||(kind.equals("ATTENTION")&&dir.getParent().equals(save.resolve("blind-lab-attention"))));
    }catch(Exception e){return false;}}
    private static boolean connection(Minecraft mc,JsonObject g){try{
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()!=null||mc.getConnection()==null||!mc.player.getUUID().toString().equals(g.get("guest").getAsString()))return false;
        var remote=mc.getConnection().getConnection().getRemoteAddress();if(!(remote instanceof InetSocketAddress address)||address.getPort()!=g.get("port").getAsInt())return false;
        return address.getAddress()!=null&&(address.getAddress().isLoopbackAddress()||NetworkInterface.getByInetAddress(address.getAddress())!=null);
    }catch(Exception e){return false;}}
    private static JsonObject header(Minecraft mc,JsonObject g){var o=new JsonObject();o.addProperty("process",PROCESS);o.addProperty("captureSession",g.get("captureSession").getAsString());o.addProperty("player",mc.player.getUUID().toString());o.addProperty("playerName",mc.player.getGameProfile().getName());o.addProperty("sampledAtMillis",System.currentTimeMillis());o.addProperty("gameTime",g.get("gameTime").getAsLong());o.addProperty("hostSampledAtMillis",g.get("sampledAtMillis").getAsLong());o.addProperty("remoteGameTime",mc.level.getGameTime());o.addProperty("clockSource","HOST_FILE_POST_TICK");o.addProperty("remoteConnection",true);o.addProperty("port",g.get("port").getAsInt());o.addProperty("playerTick",mc.player.tickCount);o.addProperty("focused",mc.isWindowActive());o.addProperty("paused",mc.isPaused());o.addProperty("screenOpen",mc.screen!=null);o.addProperty("capture",g.get("capture").getAsBoolean());o.addProperty("gatePhase",g.get("phase").getAsString());return o;}
    private static JsonObject sample(Minecraft mc,JsonObject g){var o=header(mc,g);var p=mc.player;o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());o.addProperty("yaw",p.getYRot());o.addProperty("pitch",p.getXRot());o.addProperty("health",p.getHealth());o.addProperty("pose",p.getPose().name());o.addProperty("sneaking",p.isShiftKeyDown());o.addProperty("flying",p.getAbilities().flying);o.addProperty("onGround",p.onGround());o.addProperty("selectedSlot",p.getInventory().selected);o.addProperty("useDown",mc.options.keyUse.isDown());o.addProperty("attackDown",mc.options.keyAttack.isDown());
        var mapped=new JsonObject();mapped.addProperty("crouch",mc.options.keyShift.isDown());mapped.addProperty("left",mc.options.keyLeft.isDown());mapped.addProperty("right",mc.options.keyRight.isDown());mapped.addProperty("forward",mc.options.keyUp.isDown());mapped.addProperty("back",mc.options.keyDown.isDown());mapped.addProperty("jump",mc.options.keyJump.isDown());mapped.addProperty("sprint",mc.options.keySprint.isDown());o.add("mapped",mapped);
        var input=new JsonObject();input.addProperty("crouch",p.input.shiftKeyDown);input.addProperty("left",p.input.left);input.addProperty("right",p.input.right);input.addProperty("forward",p.input.up);input.addProperty("back",p.input.down);input.addProperty("leftImpulse",p.input.leftImpulse);input.addProperty("forwardImpulse",p.input.forwardImpulse);o.add("input",input);
        if(mc.isWindowActive()&&mc.screen==null){var raw=new JsonObject();for(int k:new int[]{340,344,65,68,87,83,32,341,345})raw.addProperty(Integer.toString(k),GLFW.glfwGetKey(mc.getWindow().getWindow(),k)==GLFW.GLFW_PRESS);for(int b:new int[]{0,1,2})raw.addProperty("mouse"+b,GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),b)==GLFW.GLFW_PRESS);o.add("windowKeys",raw);}return o;}
    private static void begin(JsonObject g)throws Exception{String id=g.get("captureSession").getAsString();if(Objects.equals(capture,id))return;if(capture!=null||ended.contains(id))throw new IllegalStateException("Guest capture already ended or belongs to another attempt");directory=Path.of(g.get("directory").getAsString());capture=id;for(String n:List.of("guest-client.jsonl","guest-keys.jsonl","guest-readiness.jsonl","guest-readiness-keys.jsonl"))Files.writeString(directory.resolve(n),"",StandardOpenOption.CREATE_NEW);}
    private static void append(JsonObject o,boolean key)throws Exception{boolean live=o.get("capture").getAsBoolean();int count=live?(key?keys:frames):(key?waitingKeys:waitingFrames);if(count>=8192){dropped++;return;}String n=live?(key?"guest-keys.jsonl":"guest-client.jsonl"):(key?"guest-readiness-keys.jsonl":"guest-readiness.jsonl");Files.writeString(directory.resolve(n),o+"\n",StandardOpenOption.APPEND);if(live){if(key)keys++;else frames++;}else{if(key)waitingKeys++;else waitingFrames++;}if(!key)LabBlindLan.atomic(directory.resolve("guest-latest.json"),o);}
    private static void finish(String reason){if(capture==null)return;try{var o=new JsonObject();o.addProperty("captureSession",capture);o.addProperty("process",PROCESS);o.addProperty("frames",frames);o.addProperty("keyEvents",keys);o.addProperty("readinessFrames",waitingFrames);o.addProperty("readinessKeys",waitingKeys);o.addProperty("dropped",dropped);o.addProperty("reason",reason);o.addProperty("writersStopped",true);o.addProperty("sampledAtMillis",System.currentTimeMillis());Files.writeString(directory.resolve("guest-summary.json"),o+"\n",StandardOpenOption.CREATE_NEW);}catch(Exception e){System.err.println("[BlindLanGuest] Summary failed: "+e);}ended.add(capture);capture=null;directory=null;frames=keys=waitingFrames=waitingKeys=dropped=0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var g=gate();if(g==null){if(capture!=null)finish("GATE_MISSING");return;}if(!g.get("active").getAsBoolean()){if(capture!=null)finish("HOST_GATE_STOPPED");return;}if(ended.contains(g.get("captureSession").getAsString()))return;var mc=Minecraft.getInstance();if(!connection(mc,g)){if(capture!=null)finish("REMOTE_CONNECTION_CHANGED");return;}
        try{begin(g);append(sample(mc,g),false);}catch(Exception e){System.err.println("[BlindLanGuest] Input archive failed: "+e);finish("ARCHIVE_FAILED");}}
    @SubscribeEvent public static void key(net.neoforged.neoforge.client.event.InputEvent.Key event){var g=gate();var mc=Minecraft.getInstance();if(capture==null||g==null||!g.get("active").getAsBoolean()||!connection(mc,g))return;try{var o=header(mc,g);o.addProperty("key",event.getKey());o.addProperty("action",event.getAction());append(o,true);}catch(Exception e){finish("KEY_ARCHIVE_FAILED");}}
}
