package com.frozendawn.miller;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/** Server-owned, so the facade stays small. */
final class PatternRuntime {
    private static final Map<MinecraftServer, PatternRuntime> SERVERS = new IdentityHashMap<>();
    private final MinecraftServer server;
    final PatternSavedData data;

    private PatternRuntime(MinecraftServer server) {
        this.server = server;
        data = PatternSavedData.get(server);
    }

    static PatternRuntime current(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("CS-1's state must run on the server thread");
        return SERVERS.computeIfAbsent(server, PatternRuntime::new);
    }

    static void stopped(MinecraftServer server) {
        SERVERS.remove(server);
    }

    PatternState state() {
        return data.state();
    }

    long now() {
        return server.overworld().getGameTime();
    }
}
