package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModItems;
import com.frozendawn.network.PowerLinksPayload;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Bounded loaded graph. Saved links survive chunk unload; inactive edges do not connect networks. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class PowerNetworks {
    private static final Map<ServerLevel, Runtime> LEVELS = new WeakHashMap<>();
    private static final class Runtime {
        long revision = -1;
        long geometryRevision;
        final ArrayDeque<PowerLink> pending = new ArrayDeque<>();
        final Set<PowerLink> active = new TreeSet<>();
        Map<BlockPos, Set<BlockPos>> components = Map.of();
        boolean graphDirty = true;
        Map<BlockPos, Budget> budgets = Map.of();
        Set<BlockPos> powered = Set.of();
        Set<BlockPos> lamps = Set.of();
    }
    private record Budget(int supplyWatts, int demandWatts) {}
    public record Reading(int nodes, int links, int inactiveLinks, double supplyKw, double demandKw, double storedKj) {}
    private PowerNetworks() {}
    private static Runtime runtime(ServerLevel level) { return LEVELS.computeIfAbsent(level, unused -> new Runtime()); }
    public static PowerLinkRules.Result link(ServerLevel level, BlockPos a, BlockPos b) {
        var result = PowerLinkRules.validate(level, a, b);
        if (result != PowerLinkRules.Result.OK) return result;
        var data = PowerNetworkSavedData.get(level); var link = new PowerLink(a, b);
        if (data.links().contains(link)) return PowerLinkRules.Result.DUPLICATE;
        if (!data.add(link)) return PowerLinkRules.Result.LIMIT;
        var rt = runtime(level); rt.active.add(link); rt.graphDirty = true;
        return PowerLinkRules.Result.OK;
    }
    public static int clear(ServerLevel level, BlockPos pos) {
        int removed = PowerNetworkSavedData.get(level).clear(pos);
        var rt = runtime(level); rt.active.removeIf(link -> link.touches(pos)); rt.graphDirty = true;
        return removed;
    }
    /** Invoked after all real block mutations, including commands, explosions and pistons. */
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (before.getBlock() != after.getBlock() && PowerLinkRules.range(before) > 0) clear(level, pos);
        var rt = LEVELS.get(level);
        if (rt != null && (before.getBlock() != after.getBlock()
                || com.frozendawn.world.RoomAtmosphere.isPassage(level, pos, before)
                != com.frozendawn.world.RoomAtmosphere.isPassage(level, pos, after))) invalidate(rt);
    }
    private static void invalidate(Runtime rt) { rt.geometryRevision++; rt.revision = -1; rt.active.clear(); rt.graphDirty = true; }
    @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && LEVELS.containsKey(level)) invalidate(LEVELS.get(level));
    }
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && LEVELS.containsKey(level)) invalidate(LEVELS.get(level));
    }
    public static void update(ServerLevel level) {
        var data = PowerNetworkSavedData.get(level); var rt = runtime(level);
        if (rt.revision != data.revision()) {
            rt.pending.clear(); rt.pending.addAll(data.links()); rt.active.retainAll(data.links());
            rt.revision = data.revision(); rt.graphDirty = true;
        }
        // Never force chunks, and never perform an unbounded flood-fill loop in a server tick.
        for (int budget = 0; budget < 8 && !rt.pending.isEmpty(); budget++) {
            var link = rt.pending.removeFirst();
            if (PowerLinkRules.validate(level, link.a(), link.b()) == PowerLinkRules.Result.OK) {
                if (rt.active.add(link)) rt.graphDirty = true;
            } else if (rt.active.remove(link)) rt.graphDirty = true;
        }
        if (rt.graphDirty) {
            var neighbors = new HashMap<BlockPos, Set<BlockPos>>();
            for (var link : rt.active) {
                neighbors.computeIfAbsent(link.a(), unused -> new HashSet<>()).add(link.b());
                neighbors.computeIfAbsent(link.b(), unused -> new HashSet<>()).add(link.a());
            }
            var components = new HashMap<BlockPos, Set<BlockPos>>();
            for (var node : neighbors.keySet()) if (!components.containsKey(node)) {
                var cells = new HashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>(); queue.add(node);
                while (!queue.isEmpty()) { var next = queue.removeFirst();
                    if (cells.add(next)) queue.addAll(neighbors.getOrDefault(next, Set.of())); }
                var frozen = Set.copyOf(cells); for (var cell : cells) components.put(cell, frozen);
            }
            rt.components = Map.copyOf(components); rt.graphDirty = false;
        }
    }
    public static long geometryRevision(ServerLevel level) { return runtime(level).geometryRevision; }
    public static Reading reading(ServerLevel level, BlockPos node) {
        var rt = runtime(level); var cells = rt.components.getOrDefault(node, Set.of(node));
        int links = (int) rt.active.stream().filter(link -> cells.contains(link.a())).count();
        int inactive = (int) PowerNetworkSavedData.get(level).links().stream()
                .filter(link -> (cells.contains(link.a()) || cells.contains(link.b())) && !rt.active.contains(link)).count();
        var budget = rt.budgets.getOrDefault(node, new Budget(supplyWatts(level, node), demandWatts(level, node)));
        return new Reading(cells.size(), links, inactive, budget.supplyWatts() / 1000.0, budget.demandWatts() / 1000.0, 0);
    }
    private static int supplyWatts(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return 0;
        return level.getBlockEntity(pos) instanceof com.frozendawn.block.GeothermalCoreBlockEntity core
                ? core.getPowerShare() * 200 : 0;
    }
    private static int demandWatts(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof com.frozendawn.block.PanelLightBlock
                ? com.frozendawn.block.PanelLightBlock.DEMAND_WATTS : 0;
    }
    /** One-second allocation, in integer watts. Each unique node contributes once, including in cycles. */
    public static void account(ServerLevel level) {
        var rt = runtime(level);
        var nodes = new TreeSet<BlockPos>();
        for (var link : PowerNetworkSavedData.get(level).links()) {
            if (level.isLoaded(link.a())) nodes.add(link.a());
            if (level.isLoaded(link.b())) nodes.add(link.b());
        }
        var visited = new HashSet<BlockPos>(); var budgets = new HashMap<BlockPos, Budget>();
        var powered = new HashSet<BlockPos>(); var lamps = new HashSet<BlockPos>();
        for (var node : nodes) {
            if (visited.contains(node)) continue;
            var cells = rt.components.getOrDefault(node, Set.of(node)); visited.addAll(cells);
            int supply = 0, demand = 0;
            var loads = new TreeSet<BlockPos>();
            for (var cell : cells) {
                supply += supplyWatts(level, cell); int watts = demandWatts(level, cell); demand += watts;
                if (watts > 0) { loads.add(cell); lamps.add(cell); }
            }
            // Lights are discretionary loads. Shed whole lights in stable position order, never dim
            // them or change a Core's heat/O2/power allocation. Life-support consumers are a later slice.
            int remaining = supply;
            for (var load : loads) {
                int watts = demandWatts(level, load);
                if (watts <= remaining) { powered.add(load); remaining -= watts; }
            }
            var budget = new Budget(supply, demand); for (var cell : cells) budgets.put(cell, budget);
        }
        var affected = new HashSet<>(rt.lamps); affected.addAll(lamps);
        rt.budgets = Map.copyOf(budgets); rt.powered = Set.copyOf(powered); rt.lamps = Set.copyOf(lamps);
        for (var lamp : affected) com.frozendawn.block.PanelLightBlock.setPowered(level, lamp, powered.contains(lamp));
    }
    public static boolean powered(ServerLevel level, BlockPos pos) {
        var rt = runtime(level);
        // Output changes at the fixed accounting cadence, not on unrelated chunk/geometry notifications.
        return level.isLoaded(pos) && rt.powered.contains(pos);
    }
    public static List<PowerLink> nearby(ServerLevel level, BlockPos pos) {
        return runtime(level).active.stream().filter(link -> link.a().distSqr(pos) <= 64 * 64
                || link.b().distSqr(pos) <= 64 * 64).limit(PowerLinksPayload.MAX_LINKS).toList();
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (var level : event.getServer().getAllLevels()) {
            if (!LEVELS.containsKey(level) && level.players().isEmpty()) continue;
            update(level);
            if (level.getGameTime() % 20 == 0) account(level);
            for (var player : level.players()) {
                if (level.getGameTime() % 20 == 0)
                    PacketDistributor.sendToPlayer(player, new PowerLinksPayload(level.dimension().location().toString(), nearby(level, player.blockPosition())));
                if (level.getGameTime() % 5 == 0) PowerBinderFeedback.send(player);
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LEVELS.clear(); PowerBinderFeedback.reset(); }
}
