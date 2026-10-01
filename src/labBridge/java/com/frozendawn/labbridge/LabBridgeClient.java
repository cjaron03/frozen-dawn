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
    private static MinecraftLabRuntime runtime;
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
    public static void clientTick(ClientTickEvent.Post event) { initialize(); }
    @SubscribeEvent
    public static synchronized void started(ServerStartedEvent event) {
        initialize();
        if (inbox == null || event.getServer().isDedicatedServer()) return;
        runtime = new MinecraftLabRuntime(event.getServer(), Path.of(System.getProperty("frozendawn.labBridge.policy")));
        inbox.bind(runtime);
    }
    @SubscribeEvent
    public static synchronized void stopping(ServerStoppingEvent event) {
        if (runtime != null) { runtime.close(); runtime = null; }
        if (inbox != null) inbox.bind(null);
    }
}
