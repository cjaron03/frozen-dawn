package com.frozendawn.maeve;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.phase.PhaseManager;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

/**
 * The hive's coordination facade. Records and explains beliefs; nothing feeds the local AI.
 * Source of truth §§4, 9.1, 9.16a, 9.18, 9.19.
 */
public final class MaeveDirector {
    private static final Map<MinecraftServer, MaeveDirector> SERVERS = new IdentityHashMap<>();
    private final MaeveSavedData data;
    private long lastContactTick = Long.MIN_VALUE;

    private MaeveDirector(MinecraftServer server) {
        data = MaeveSavedData.get(server);
    }

    private static MaeveDirector current(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Maeve must run on the server thread");
        MaeveDirector director = SERVERS.computeIfAbsent(server, MaeveDirector::new);
        ApocalypseState apocalypse = ApocalypseState.get(server);
        director.data.synchronize(PostMaeveWorldState.isErased(server),
                PhaseManager.isVacuumActive(apocalypse.getPhase(), apocalypse.getProgress()));
        return director;
    }

    public static void tick(MinecraftServer server) {
        MaeveDirector director = current(server);
        long now = server.overworld().getGameTime();
        if (now % 20 != 0 || now == director.lastContactTick || director.data.store() == null) return;
        director.lastContactTick = now;
        if (ObservationCollector.refreshContacts(server, director.data.store(), now)) director.data.setDirty();
    }

    public static void observeDamage(ArchitectEntity observer, DamageSource source, float actualDamage) {
        MinecraftServer server = observer.getServer();
        if (server == null || observer.level().isClientSide()) return;
        MaeveDirector director = current(server);
        if (director.data.store() == null) return;
        ObservationCollector.damage(director.data.store(), observer, source, actualDamage,
                server.overworld().getGameTime());
        director.data.setDirty();
    }

    public static void observeRecovery(ServerPlayer player, ItemStack consumed) {
        MinecraftServer server = player.getServer();
        if (server == null || !ObservationCollector.restorative(consumed)) return;
        MaeveDirector director = current(server);
        if (director.data.store() == null) return;
        ObservationCollector.recovery(director.data.store(), player, consumed, server.overworld().getGameTime());
        director.data.setDirty();
    }

    /** Called in the same server-thread operation that sets the authoritative ERASED flag. */
    public static void erase(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Maeve erasure must run on the server thread");
        MaeveDirector director = SERVERS.computeIfAbsent(server, MaeveDirector::new);
        director.data.erase();
        director.lastContactTick = Long.MIN_VALUE;
    }

    public static void onServerStopped(MinecraftServer server) {
        SERVERS.remove(server);
    }

    /** Diagnostic snapshots are immutable and never consulted by gameplay. */
    public static Snapshot snapshot(MinecraftServer server, UUID player) {
        MaeveDirector director = current(server);
        BeliefStore store = director.data.store();
        return new Snapshot(director.data.lifecycle(), director.data.activated(),
                store == null ? 0 : store.size(), store == null ? 0 : store.beliefCount(),
                store == null || player == null ? List.of() : store.snapshot(player, server.overworld().getGameTime()));
    }

    public static List<String> diagnostics(MinecraftServer server, UUID player) {
        return DirectorDiagnostics.format(snapshot(server, player), player);
    }

    public static List<String> explain(MinecraftServer server, UUID player, String pattern) {
        return DirectorDiagnostics.explain(snapshot(server, player), player, pattern);
    }

    public static List<String> diagnosticPatterns(MinecraftServer server, UUID player) {
        return java.util.stream.Stream.concat(BeliefDescriptions.patterns().stream(),
                snapshot(server, player).beliefs().stream().map(BeliefSnapshot::pattern)).distinct().sorted().toList();
    }

    public static List<UUID> knownPlayers(MinecraftServer server) {
        BeliefStore store = current(server).data.store();
        return store == null ? List.of() : store.players();
    }

    public record Snapshot(String lifecycle, boolean activated, int profiles, int beliefCount,
                           List<BeliefSnapshot> beliefs) {
        public Snapshot { beliefs = List.copyOf(beliefs); }
    }

    public record BeliefSnapshot(String pattern, double confidence, int evidence, int contradictions,
                                 long lastConfirmed, long lastObserved, long ageTicks, boolean stale,
                                 double storedConfidence, long updatedAt, long evaluatedAt,
                                 List<EvidenceSnapshot> provenance) {
        public BeliefSnapshot { provenance = List.copyOf(provenance); }
    }

    public record EvidenceSnapshot(UUID observer, UUID encounter, String dimension, BlockPos position,
                                   long time, String action, boolean supporting) { }
}
