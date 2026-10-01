package com.frozendawn.maeve;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.phase.PhaseManager;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/** Server-owned orchestration keeps the external facade small. */
final class DirectorRuntime {
    private static final Map<MinecraftServer, DirectorRuntime> SERVERS = new IdentityHashMap<>();
    final MinecraftServer server;
    final MaeveSavedData data;
    final AttentionCoordinator attention;
    final MissionPlanner missions;
    final LearningCoordinator learning;
    final ConvergenceCoordinator convergence;
    private long lastContactTick = Long.MIN_VALUE;
    private DirectorRuntime(MinecraftServer server) {
        this.server = server; data = MaeveSavedData.get(server);
        learning = new LearningCoordinator(server, data); attention = new AttentionCoordinator(server, data);
        missions = new MissionPlanner(server, data, attention); attention.bind(missions);
        convergence = new ConvergenceCoordinator(server, data, attention, missions);
    }
    static DirectorRuntime current(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Maeve must run on the server thread");
        var runtime = SERVERS.computeIfAbsent(server, DirectorRuntime::new);
        var apocalypse = ApocalypseState.get(server);
        boolean erased = PostMaeveWorldState.isErased(server);
        if (erased) runtime.clear("ERASED");
        runtime.data.synchronize(erased, PhaseManager.isVacuumActive(apocalypse.getPhase(), apocalypse.getProgress()));
        return runtime;
    }
    void tick() {
        long now = server.overworld().getGameTime(); learning.tick();
        if (now % 20 != 0 || now == lastContactTick || data.store() == null) return;
        lastContactTick = now; missions.tick(); attention.tick();
        if (ObservationCollector.refreshContacts(server, data.store(), now)) data.setDirty();
        convergence.tick();
    }
    private void clear(String reason) {
        convergence.clear(reason); CommitmentCoordinator.stopAll(server, data.store());
        missions.clear(); attention.clear(); learning.clear();
    }
    static void erase(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Maeve erasure must run on the server thread");
        var runtime = SERVERS.computeIfAbsent(server, DirectorRuntime::new);
        runtime.clear("ERASED"); runtime.data.erase(); runtime.lastContactTick = Long.MIN_VALUE;
    }
    static void stopped(MinecraftServer server) {
        var runtime = SERVERS.remove(server); if (runtime != null) runtime.clear("SERVER_STOPPED");
    }
}
