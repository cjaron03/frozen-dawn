package com.frozendawn.labbridge;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Loaded only in development; only runClientLab opts in. The release jar excludes this entire source set. */
@EventBusSubscriber(modid = "frozendawn", value = Dist.CLIENT)
public final class LabBridgeClient {
    private static LabInbox inbox;
    private static volatile MinecraftLabRuntime runtime;
    private static boolean attempted;
    private LabBridgeClient() { }
    private static synchronized void initialize() {
        if (attempted || FMLEnvironment.production || !Boolean.getBoolean("frozendawn.labBridge")) return;
        attempted = true;
        try {
            inbox = new LabInbox(Minecraft.getInstance().gameDirectory.toPath().resolve("lab-bridge"));
            inbox.start();
            System.out.println("[LabBridge] Local development command interface enabled");
        } catch (Exception error) { System.err.println("[LabBridge] Disabled: " + error); }
    }
    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        initialize();
        var current = runtime; var mc = Minecraft.getInstance();
        if (current == null || mc.player == null || mc.level == null || mc.isPaused() || !current.inputActive(mc.getSingleplayerServer())) return;
        var row = inputHeader(current, mc);
        row.addProperty("pose", mc.player.getPose().name()); row.addProperty("sneaking", mc.player.isShiftKeyDown());
        row.addProperty("x", mc.player.getX()); row.addProperty("y", mc.player.getY()); row.addProperty("z", mc.player.getZ());
        row.addProperty("yaw",mc.player.getYRot());row.addProperty("pitch",mc.player.getXRot());row.addProperty("selectedSlot",mc.player.getInventory().selected);
        row.addProperty("useDown",mc.options.keyUse.isDown());
        row.addProperty("flying", mc.player.getAbilities().flying); row.addProperty("onGround", mc.player.onGround());
        var mapped = new com.google.gson.JsonObject();
        mapped.addProperty("crouch", mc.options.keyShift.isDown()); mapped.addProperty("left", mc.options.keyLeft.isDown());
        mapped.addProperty("right", mc.options.keyRight.isDown()); mapped.addProperty("forward", mc.options.keyUp.isDown());
        mapped.addProperty("back", mc.options.keyDown.isDown());
        row.add("mapped", mapped);
        row.addProperty("crouchBinding", mc.options.keyShift.getKey().getName());
        row.addProperty("leftBinding", mc.options.keyLeft.getKey().getName());
        row.addProperty("rightBinding", mc.options.keyRight.getKey().getName());
        row.addProperty("toggleCrouch", mc.options.toggleCrouch().get());
        var input = new com.google.gson.JsonObject();
        input.addProperty("crouch", mc.player.input.shiftKeyDown); input.addProperty("left", mc.player.input.left);
        input.addProperty("right", mc.player.input.right); input.addProperty("forward", mc.player.input.up);
        input.addProperty("back", mc.player.input.down); input.addProperty("leftImpulse", mc.player.input.leftImpulse);
        input.addProperty("forwardImpulse", mc.player.input.forwardImpulse); row.add("input", input);
        if (mc.isWindowActive() && mc.screen == null) {
            var raw = new com.google.gson.JsonObject();
            for (int key : new int[] {org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_KEY_D, org.lwjgl.glfw.GLFW.GLFW_KEY_W, org.lwjgl.glfw.GLFW.GLFW_KEY_S})
                raw.addProperty(Integer.toString(key), org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(), key) == org.lwjgl.glfw.GLFW.GLFW_PRESS);
            row.add("windowKeys", raw);
        }
        current.inputClient(row, false);
    }
    private static com.google.gson.JsonObject inputHeader(MinecraftLabRuntime current, Minecraft mc) {
        var row = new com.google.gson.JsonObject(); row.addProperty("session", current.session());
        row.addProperty("player", mc.player.getUUID().toString()); row.addProperty("sampledAtMillis", System.currentTimeMillis());
        current.inputClock(row, mc.level.getGameTime()); row.addProperty("playerTick", mc.player.tickCount);
        row.addProperty("focused", mc.isWindowActive()); row.addProperty("paused", mc.isPaused());
        row.addProperty("screenOpen", mc.screen != null); return row;
    }
    @SubscribeEvent
    public static void keyInput(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        var current = runtime; var mc = Minecraft.getInstance(); int key = event.getKey();
        if (current == null || mc.player == null || mc.level == null || mc.screen != null || !mc.isWindowActive()
                || !current.inputActive(mc.getSingleplayerServer()) || !(key == 340 || key == 344 || key == 65 || key == 68 || key == 87 || key == 83)) return;
        var row = inputHeader(current, mc); row.addProperty("key", key); row.addProperty("action", event.getAction());
        current.inputClient(row, true);
    }
    @SubscribeEvent
    public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var current = runtime;
        if (current != null) current.serverTick(event.getServer());
    }
    @SubscribeEvent
    public static synchronized void started(ServerStartedEvent event) {
        initialize();
        if (inbox == null || event.getServer().isDedicatedServer()) return;
        runtime = new MinecraftLabRuntime(event.getServer(), Path.of(System.getProperty("frozendawn.labBridge.policy")));
        inbox.bind(runtime);
    }
    @SubscribeEvent
    public static synchronized void stopping(ServerStoppingEvent event) {
        if (runtime != null) { LabBlindLan.stopping(event.getServer()); LabBlindAttention.stopping(event.getServer()); LabBlindReconnect.stopping(event.getServer()); runtime.close(); runtime = null; }
        if (inbox != null) inbox.bind(null);
    }
}
