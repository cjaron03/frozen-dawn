package com.frozendawn.maeve;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;

final class LearningDiagnostics {
    private LearningDiagnostics() { }
    static List<String> append(MinecraftServer server, UUID player, List<String> beliefs, BeliefStore store, List<String> learning) {
        if (store == null) return beliefs;
        var attention = AttentionCoordinator.format(MaeveDirector.attentionSnapshot(server));
        var missions = MissionPlanner.format(MaeveDirector.missionSnapshots(server, player));
        if (player == null) return Stream.of(beliefs, attention, missions, learning).flatMap(List::stream).toList();
        var policy = store.commitment(player);
        long now = server.overworld().getGameTime();
        return Stream.of(beliefs, CommitmentDiagnostics.format(MaeveDirector.commitmentSnapshot(server, player)),
                WorldDiagnostics.format(store.world(player), now), attention, missions, learning,
                policy == null ? List.<String>of() : List.of("RECON ADMISSION: " + policy.surveyAdmission()),
                policy == null || policy.exitWatch() == null ? List.<String>of() : List.of("EXIT WATCH: " + policy.exitWatch()
                        + " active=" + (policy.active(now) != null)),
                policy == null ? List.<String>of() : policy.performance().diagnostics(now)).flatMap(List::stream).toList();
    }
}
