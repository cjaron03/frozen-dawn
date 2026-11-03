package com.frozendawn.entity.blind;

import com.frozendawn.entity.ArchitectEntity;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/** Loaded executors only. Erasure clears pending returns even before the first attention admission. */
public final class BlindArchitectRegistry {
    private static final Map<MinecraftServer, Set<ArchitectEntity>> LOADED = new IdentityHashMap<>();
    public static void loaded(ArchitectEntity actor) {
        if (actor.getServer() == null || actor.level().isClientSide()) return;
        var actors = LOADED.computeIfAbsent(actor.getServer(), unused -> new LinkedHashSet<>());
        if (actor.isBlindArchitect()) actors.add(actor); else actors.remove(actor);
    }
    public static void removed(ArchitectEntity actor) {
        var actors = LOADED.get(actor.getServer()); if (actors != null) actors.remove(actor);
    }
    public static void erase(MinecraftServer server) {
        for (var actor : Set.copyOf(LOADED.getOrDefault(server, Set.of()))) if (!actor.isRemoved()) actor.blindController().clearLocal();
    }
    public static void stopped(MinecraftServer server) { LOADED.remove(server); }
    private BlindArchitectRegistry() { }
}
