package com.frozendawn.maeve;

import net.minecraft.server.MinecraftServer;

final class ConvergenceLifecycle {
    static boolean enabled(MinecraftServer server, MaeveSavedData data) {
        return data.convergence() != null && data.lifecycle().equals("ACTIVE") && !isArchitectExistencePermanentlyEnded(server);
    }
    /** E11 is not built. Its authoritative no-Architects flag plugs into this single check. */
    private static boolean isArchitectExistencePermanentlyEnded(MinecraftServer server) { return false; }
    private ConvergenceLifecycle() { }
}
