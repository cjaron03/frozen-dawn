package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

/**
 * The hive's coordination facade. Records beliefs and issues bounded commitments to local executors.
 * Source of truth §§4, 9.1, 9.16a, 9.18, 9.19.
 */
public final class MaeveDirector {
    private MaeveDirector() { }
    private static DirectorRuntime current(MinecraftServer server) { return DirectorRuntime.current(server); }
    public static void tick(MinecraftServer server) { current(server).tick(); }

    public static void observeDamage(ArchitectEntity observer, DamageSource source, float actualDamage) {
        MinecraftServer server = observer.getServer();
        if (server == null || observer.level().isClientSide() || observer.isMasterArchitectVisual()) return;
        var director = current(server);
        if (director.data.store() == null) return;
        CombatObservation.record(director.data, director.missions, director.learning, observer, source, actualDamage, false);
    }

    public static void observeShieldBlock(ArchitectEntity observer, DamageSource source, float blocked) {
        if (observer.getServer() == null || observer.level().isClientSide() || observer.isMasterArchitectVisual()) return;
        var director = current(observer.getServer());
        CombatObservation.record(director.data, director.missions, director.learning, observer, source, blocked, true);
    }

    public static void observeRecovery(ServerPlayer player, ItemStack consumed) {
        MinecraftServer server = player.getServer();
        if (server == null || !ObservationCollector.restorative(consumed)) return;
        var director = current(server);
        if (director.data.store() == null) return;
        ObservationCollector.recovery(director.data.store(), director.missions, player, consumed, server.overworld().getGameTime());
        director.data.setDirty();
    }

    /** Called in the authoritative ERASED transition. */
    public static void erase(MinecraftServer server) { DirectorRuntime.erase(server); }
    public static void onServerStopped(MinecraftServer server) { DirectorRuntime.stopped(server); }

    /** Immutable diagnostic snapshots; execution receives only bounded historical hints/directives. */
    public static Snapshot snapshot(MinecraftServer server, UUID player) {
        var director = current(server);
        BeliefStore store = director.data.store();
        return new Snapshot(director.data.lifecycle(), director.data.activated(),
                store == null ? 0 : store.size(), store == null ? 0 : store.beliefCount(),
                store == null || player == null ? List.of() : store.snapshot(player, server.overworld().getGameTime()));
    }

    public static List<String> diagnostics(MinecraftServer server, UUID player) {
        return withCommitmentDiagnostics(server, player, DirectorDiagnostics.format(snapshot(server, player), player));
    }

    public static List<String> explain(MinecraftServer server, UUID player, String pattern) {
        return withCommitmentDiagnostics(server, player, DirectorDiagnostics.explain(snapshot(server, player), player, pattern));
    }

    public static List<String> diagnosticPatterns(MinecraftServer server, UUID player) {
        return java.util.stream.Stream.concat(BeliefDescriptions.patterns().stream(),
                snapshot(server, player).beliefs().stream().map(BeliefSnapshot::pattern)).distinct().sorted().toList();
    }

    private static List<String> withCommitmentDiagnostics(MinecraftServer server, UUID player, List<String> beliefs) {
        var runtime = current(server);
        if (runtime.data.store() == null) return beliefs;
        return java.util.stream.Stream.concat(LearningDiagnostics.append(server, player, beliefs, runtime.data.store(), runtime.learning.diagnostics(player)).stream(), runtime.convergence.diagnostics().stream()).toList();
    }

    public static void observeWithdrawal(ArchitectEntity actor, ServerPlayer player) {
        if (actor.getServer() != null) current(actor.getServer()).learning.withdrawal(actor, player);
    }
    public static void observeCounterDamage(ArchitectEntity actor, ServerPlayer player, float damage, boolean outgoing) {
        if (actor.getServer() != null) current(actor.getServer()).learning.damage(actor, player, damage, outgoing);
    }

    public static CommitmentSnapshot commitmentSnapshot(MinecraftServer server, UUID player) {
        var store = current(server).data.store();
        var state = store == null ? null : store.commitment(player);
        long now = server.overworld().getGameTime();
        return state == null ? new CommitmentSnapshot("NONE", null, false, List.of(), List.of(), null, List.of(), List.of())
                : new CommitmentSnapshot(state.outcome(now), state.encounter(), state.issued(),
                state.blocked().stream().sorted().toList(), state.blockNext().stream().sorted().toList(),
                state.selected(), state.hints(now), state.alternatives());
    }

    public static List<UUID> knownPlayers(MinecraftServer server) {
        BeliefStore store = current(server).data.store();
        return store == null ? List.of() : store.players();
    }

    /** Called at a coarse local decision boundary, and only after an Architect sees a player. */
    public static List<CommitmentHint> commitmentHints(ArchitectEntity observer, ServerPlayer player) {
        var director = current(player.serverLevel().getServer());
        return CommitmentCoordinator.hints(director.data, observer, player);
    }

    public static boolean chooseCommitment(ArchitectEntity observer, ServerPlayer player, List<PositionCandidate> candidates) {
        var director = current(player.serverLevel().getServer());
        boolean selected = CommitmentCoordinator.choose(director.data, director.attention, observer, player, candidates);
        if (selected && BeliefStore.PURSUIT.equals(director.data.store().commitment(player.getUUID()).selected().pattern())) director.learning.withdrawal(observer, player);
        return selected;
    }

    public static PositionDirective positionDirective(ArchitectEntity observer) {
        if (observer.getServer() == null) return null;
        return CommitmentCoordinator.directive(current(observer.getServer()).data, observer);
    }

    public static void commitmentArrived(ArchitectEntity observer) {
        if (observer.getServer() != null) CommitmentCoordinator.arrived(current(observer.getServer()).data, observer);
    }

    public static void releaseCommitment(ArchitectEntity observer, String reason) {
        if (observer.getServer() != null) {
            var director = current(observer.getServer());
            CommitmentCoordinator.release(director.data, observer, reason); director.attention.releaseCommitment(observer);
        }
    }

    public static UtilityBias utilityBias(ArchitectEntity observer, ServerPlayer player) {
        var data = current(player.serverLevel().getServer()).data;
        if (data.store() == null || !CommitmentCoordinator.eligible(observer, player)) return UtilityBias.NONE;
        var state = data.store().commitment(player.getUUID());
        return LearningUtility.bias(state, observer, player.serverLevel().getServer().overworld().getGameTime());
    }

    /** Coarse local presence samples: both sides of a crossing require the same observer's sight. */
    public static void observePresence(ArchitectEntity observer) {
        if (observer.getTarget() instanceof ServerPlayer player) observePresence(observer, player);
    }
    public static void observePresence(ArchitectEntity observer, ServerPlayer player) {
        if (observer.getServer() == null || observer.level().isClientSide() || observer.isMasterArchitectVisual()) return;
        var director = current(observer.getServer());
        if (director.data.store() != null) {
            director.convergence.localContact(observer, player);
            SpatialObservations.presence(director.data.store(), director.missions, observer, player, observer.getServer().overworld().getGameTime());
            if (observer.getServer().overworld().getGameTime() % 20 == 0) director.attention.observe(observer, player);
            director.data.setDirty();
        }
    }

    public static List<PositionCandidate> spatialCandidates(ArchitectEntity observer, ServerPlayer player, List<CommitmentHint> hints) {
        return SpatialCommitments.candidates(current(player.getServer()).data.store(), observer, player, hints);
    }

    public static boolean discoverAccess(ArchitectEntity observer, PositionDirective directive) {
        var data = current(observer.getServer()).data;
        boolean changed = SpatialObservations.discover(data.store(), observer, directive, observer.getServer().overworld().getGameTime());
        if (changed) data.setDirty();
        return changed;
    }

    public static List<BlockPos> knownDangers(ArchitectEntity observer, UUID player) {
        if (observer.isMasterArchitectVisual()) return List.of();
        var runtime = current(observer.getServer()); var store = runtime.data.store();
        var world = store == null ? null : store.world(player);
        List<BlockPos> personal = world == null ? List.of() : world.dangers(observer.level().dimension().location().toString(),
                observer.blockPosition(), observer.getServer().overworld().getGameTime());
        return java.util.stream.Stream.concat(personal.stream(), runtime.convergence.avoided(observer).stream()).distinct().limit(64).toList();
    }

    public static List<WorldPointSnapshot> worldSnapshot(MinecraftServer server, UUID player) {
        var store = current(server).data.store(); var world = store == null ? null : store.world(player);
        return world == null ? List.of() : world.snapshot(server.overworld().getGameTime());
    }

    public static AttentionSnapshot attentionSnapshot(MinecraftServer server) { return current(server).attention.snapshot(); }
    public static boolean requestReconnaissance(ArchitectEntity actor, ServerPlayer player) { return current(player.getServer()).missions.request(actor, player); }
    public static void beginLocalCombat(ArchitectEntity actor, ServerPlayer player) { current(player.getServer()).missions.engage(actor, player); }
    public static MissionPacket missionPacket(ArchitectEntity actor) { return actor.getServer() == null ? null : current(actor.getServer()).missions.packet(actor); }
    public static String inspectMission(ArchitectEntity actor) { return current(actor.getServer()).missions.sample(actor); }
    public static void finishMission(ArchitectEntity actor, String reason, boolean withdraw) {
        if (actor.getServer() != null) current(actor.getServer()).missions.finish(actor, reason, withdraw);
    }
    public static List<MissionSnapshot> missionSnapshots(MinecraftServer server, UUID player) { return current(server).missions.snapshots(player); }
    public static void observePawn(ArchitectEntity actor) { if (actor.getServer() != null) current(actor.getServer()).convergence.observe(actor); }
    public static void pawnDestroyed(ArchitectEntity actor) { if (actor.getServer() != null) current(actor.getServer()).convergence.destroyed(actor); }
    public static void observePawnDeath(ArchitectEntity actor) { if (actor.getServer() != null) current(actor.getServer()).convergence.death(actor); }
    public static void pawnReachedPlayer(ArchitectEntity actor, ServerPlayer player) { if (actor.getServer() != null) current(actor.getServer()).convergence.contact(actor, player); }
    public static PawnOrder pawnOrder(ArchitectEntity actor) { return actor.getServer() == null ? null : current(actor.getServer()).convergence.order(actor); }
    public static void pawnRouteUnavailable(ArchitectEntity actor) { if (actor.getServer() != null) current(actor.getServer()).convergence.routeUnavailable(actor); }
    public static boolean allowNaturalPawn(net.minecraft.server.level.ServerLevel level, BlockPos position) { return current(level.getServer()).convergence.allowNaturalSpawn(level, position); }
    public record PawnOrder(UUID dispatch, BlockPos destination, long notBefore, long expiresAt) {
        public PawnOrder { destination = destination.immutable(); }
    }
    public record AccessHint(BlockPos outside, BlockPos inside, String state, double confidence, EvidenceSnapshot source) {
        public AccessHint { outside = outside.immutable(); inside = inside.immutable(); }
    }
    public record MissionPacket(UUID id, UUID player, UUID observer, UUID encounter, String dimension, String objective,
                                AccessHint access, List<BlockPos> dangers, List<String> orders, List<String> alternatives, long issuedAt, long expiresAt) {
        public MissionPacket { dangers = dangers.stream().map(BlockPos::immutable).toList(); orders = List.copyOf(orders); alternatives = List.copyOf(alternatives); }
    }
    public record MissionSnapshot(MissionPacket packet, String outcome, String report, long time) { }
    public static void observeAttention(ArchitectEntity observer, ServerPlayer player) {
        if (observer.getServer() != null && !observer.isMasterArchitectVisual()) current(observer.getServer()).attention.observe(observer, player);
    }
    public record FocusSnapshot(String kind, UUID subject, long admittedAt, long dwellRemaining, List<UUID> executors, List<String> reports) {
        public FocusSnapshot { executors = List.copyOf(executors); reports = List.copyOf(reports); }
    }
    public record AttentionSnapshot(int capacity, List<FocusSnapshot> slots, List<String> events) {
        public AttentionSnapshot { slots = List.copyOf(slots); events = List.copyOf(events); }
    }
    public record UtilityBias(float fortify, float peek) {
        public static final UtilityBias NONE = new UtilityBias(0, 0);
    }
    public record CommitmentHint(String pattern, double confidence, EvidenceSnapshot evidence) { }
    public record PositionCandidate(String pattern, BlockPos position, BlockPos cover, double recoveryCost, SpatialTarget spatial, boolean advancingCover, boolean keepAwayArcher) {
        public PositionCandidate(String pattern, BlockPos position, BlockPos cover, double recoveryCost) { this(pattern, position, cover, recoveryCost, null); }
        public PositionCandidate(String pattern, BlockPos position, BlockPos cover, double recoveryCost, SpatialTarget spatial) { this(pattern, position, cover, recoveryCost, spatial, false); }
        public PositionCandidate(String pattern, BlockPos position, BlockPos cover, double recoveryCost, SpatialTarget spatial, boolean advancingCover) { this(pattern, position, cover, recoveryCost, spatial, advancingCover, false); }
        public PositionCandidate { position = position.immutable(); cover = cover == null ? null : cover.immutable(); }
    }
    public record PositionDirective(UUID player, UUID observer, UUID encounter, String pattern, double confidence,
                                    EvidenceSnapshot evidence, BlockPos position, BlockPos cover, double recoveryCost,
                                    long startedAt, long arrivedAt, long holdUntil, long contradictedAt,
                                    SpatialTarget spatial, BlockPos obstruction, boolean advancingCover, boolean keepAwayArcher) {
        public PositionDirective(UUID player, UUID observer, UUID encounter, String pattern, double confidence, EvidenceSnapshot evidence, BlockPos position, BlockPos cover, double recoveryCost, long startedAt, long arrivedAt, long holdUntil, long contradictedAt, SpatialTarget spatial, BlockPos obstruction) { this(player, observer, encounter, pattern, confidence, evidence, position, cover, recoveryCost, startedAt, arrivedAt, holdUntil, contradictedAt, spatial, obstruction, false); }
        public PositionDirective(UUID player, UUID observer, UUID encounter, String pattern, double confidence, EvidenceSnapshot evidence, BlockPos position, BlockPos cover, double recoveryCost, long startedAt, long arrivedAt, long holdUntil, long contradictedAt, SpatialTarget spatial, BlockPos obstruction, boolean advancingCover) { this(player, observer, encounter, pattern, confidence, evidence, position, cover, recoveryCost, startedAt, arrivedAt, holdUntil, contradictedAt, spatial, obstruction, advancingCover, false); }
        public PositionDirective { position = position.immutable(); cover = cover == null ? null : cover.immutable(); }
    }
    public record SpatialTarget(BlockPos inside, BlockPos outside) {
        public SpatialTarget { inside = inside.immutable(); outside = outside.immutable(); }
    }
    public record WorldPointSnapshot(String label, String dimension, BlockPos position, BlockPos inside,
                                     String state, double confidence, double previousConfidence, int evidence,
                                     int contradictions, long observedAt, List<EvidenceSnapshot> provenance) {
        public WorldPointSnapshot { provenance = List.copyOf(provenance); }
    }
    public record CommitmentSnapshot(String outcome, UUID encounter, boolean issued,
                                     List<String> blocked, List<String> blockNext, PositionDirective selected,
                                     List<CommitmentHint> hints, List<String> alternatives) {
        public CommitmentSnapshot {
            blocked = List.copyOf(blocked); blockNext = List.copyOf(blockNext);
            hints = List.copyOf(hints); alternatives = List.copyOf(alternatives);
        }
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
                                   long time, String action, boolean supporting, double confidenceWeight) {
        public EvidenceSnapshot(UUID observer, UUID encounter, String dimension, BlockPos position, long time, String action, boolean supporting) {
            this(observer, encounter, dimension, position, time, action, supporting, Double.NaN);
        }
    }
}
