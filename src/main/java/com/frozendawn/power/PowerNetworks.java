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
        long coordinatedTick=Long.MIN_VALUE;
        final ArrayDeque<PowerLink> pending = new ArrayDeque<>();
        final Set<PowerLink> pendingSet = new HashSet<>();
        final Set<PowerLink> active = new TreeSet<>();
        Map<BlockPos, Set<BlockPos>> components = Map.of();
        boolean graphDirty = true;
        Map<BlockPos, Budget> budgets = Map.of();
        Map<BlockPos, PowerMonitorReading> monitorReadings = Map.of();
        Set<BlockPos> powered = Set.of();
        Set<BlockPos> lamps = Set.of();
        final Set<BlockPos> stores = new TreeSet<>();
        final Map<BlockPos,Long> storeEnergyTicks = new HashMap<>();
        final Set<BlockPos> machines = new TreeSet<>();
        final Set<BlockPos> heaters = new TreeSet<>();
        final Map<BlockPos,HeaterGrant> heaterGrants = new HashMap<>();
        final Set<BlockPos> lifeSupport = new TreeSet<>();
        Map<BlockPos,Integer> priorityRemaining = Map.of();
        Map<BlockPos, MachineGrant> machineGrants = Map.of();
        final Map<BlockPos, Long> machineTicks = new HashMap<>();
    }
    private static final class HeaterGrant {
        final int watts,generation;final long tick;final List<BlockPos> stores;double used;int batteryPaid;
        HeaterGrant(int watts,int generation,long tick,List<BlockPos> stores){this.watts=watts;this.generation=generation;this.tick=tick;this.stores=stores;}
    }
    private record MachineGrant(int generationWatts, List<BlockPos> stores, long expires) {}
    private record Budget(int supplyWatts, int demandWatts, long storedJoules) {}
    public record Reading(int nodes, int links, int inactiveLinks, double supplyKw, double demandKw, double storedKj) {}
    private PowerNetworks() {}
    private static Runtime runtime(ServerLevel level) { return LEVELS.computeIfAbsent(level, unused -> new Runtime()); }
    public static void heaterLoaded(ServerLevel l,BlockPos p){runtime(l).heaters.add(p.immutable());}
    public static void heaterRemoved(ServerLevel l,BlockPos p){var r=runtime(l);r.heaters.remove(p);r.heaterGrants.remove(p);}
    public static List<BlockPos> heaters(ServerLevel l){var r=runtime(l);r.heaters.removeIf(p->!l.isLoaded(p)||!(l.getBlockEntity(p) instanceof com.frozendawn.block.ResistiveHeaterBlockEntity));return List.copyOf(r.heaters);}
    public static int heaterGrant(ServerLevel l,BlockPos p){var g=runtime(l).heaterGrants.get(p);return g!=null&&g.tick==l.getGameTime()?g.watts:0;}
    /** Pay before delivery. Fractional joules round up cumulatively, at most one joule per heater-second. */
    public static double consumeHeaterEnergy(ServerLevel l,BlockPos p,double joules){
        var g=runtime(l).heaterGrants.get(p);
        if(g==null||g.tick!=l.getGameTime()||!Double.isFinite(joules)||joules<=0||!l.isLoaded(p)||!(l.getBlockEntity(p) instanceof com.frozendawn.block.ResistiveHeaterBlockEntity h))return 0;
        double q=Math.min(joules,Math.max(0,g.watts-g.used));
        int debt=(int)Math.ceil(Math.max(0,g.used+q-g.generation)-1e-9)-g.batteryPaid;
        var stores=g.stores.stream().filter(l::isLoaded).map(l::getBlockEntity).filter(com.frozendawn.block.PowerStorageBlockEntity.class::isInstance).map(com.frozendawn.block.PowerStorageBlockEntity.class::cast).toList();
        if(stores.stream().mapToLong(com.frozendawn.block.PowerStorageBlockEntity::storedJoules).sum()<debt)return 0;
        int needed=debt;for(var store:stores){int drawn=store.draw(needed);needed-=drawn;store.publishHeaterDraw(drawn);if(needed==0)break;}
        g.batteryPaid+=debt;g.used+=q;h.paid(q);return q;
    }
    public static void pixieLoaded(ServerLevel l,BlockPos p){runtime(l).lifeSupport.add(p.immutable());}
    public static void pixieRemoved(ServerLevel l,BlockPos p){runtime(l).lifeSupport.remove(p);}
    /** Cached active neighbors only. Oxygen plumbing is an explicit PIXIE-to-tank Binder link. */
    public static List<com.frozendawn.block.OxygenReservoirBlockEntity> linkedOxygenTanks(ServerLevel l,BlockPos p){
        return runtime(l).active.stream().filter(link->link.touches(p)).map(link->link.a().equals(p)?link.b():link.a())
                .sorted().filter(l::isLoaded).map(l::getBlockEntity).filter(com.frozendawn.block.OxygenReservoirBlockEntity.class::isInstance)
                .map(com.frozendawn.block.OxygenReservoirBlockEntity.class::cast).toList();
    }
    public static Set<BlockPos> component(ServerLevel l,BlockPos p){return runtime(l).components.getOrDefault(p,Set.of(p));}
    public static void accountHeating(ServerLevel l){var r=runtime(l);if(!heaters(l).isEmpty()&&r.coordinatedTick!=l.getGameTime()){update(l);account(l);r.coordinatedTick=l.getGameTime();}}
    public static void accountLifeSupport(ServerLevel l){account(l);runtime(l).coordinatedTick=l.getGameTime();}
    /** Physical loaded supply and stored energy for planning; no energy transfer or forced chunks. */
    public static long priorityBudget(ServerLevel l,BlockPos p){return component(l,p).stream().mapToLong(node->(long)supplyWatts(l,node)+storedJoules(l,node)).sum();}
    public static int priorityHeadroom(ServerLevel l,BlockPos p){return runtime(l).priorityRemaining.getOrDefault(p,0);}
    public static void storageLoaded(ServerLevel level, BlockPos pos) { runtime(level).stores.add(pos.immutable()); }
    public static void storageRemoved(ServerLevel level, BlockPos pos) { var rt=runtime(level);rt.stores.remove(pos);rt.storeEnergyTicks.remove(pos); }
    public static void machineLoaded(ServerLevel level, BlockPos pos) { runtime(level).machines.add(pos.immutable()); }
    public static void machineRemoved(ServerLevel level, BlockPos pos) {
        var rt=runtime(level);rt.machines.remove(pos);rt.machineTicks.remove(pos);
        var grants=new HashMap<>(rt.machineGrants);grants.remove(pos);rt.machineGrants=Map.copyOf(grants);
    }
    /** Allocation reserves capacity once per second; cells pay only for actual processing ticks.
     * A partial/blocked job never burns its unused battery reservation. No prepaid energy is saved. */
    public static boolean consumeMachineTick(ServerLevel level, BlockPos pos) {
        var rt=runtime(level);var grant=rt.machineGrants.get(pos);long tick=level.getGameTime();
        if(grant==null||tick>grant.expires()||!level.isLoaded(pos)
                ||rt.machineTicks.getOrDefault(pos,Long.MIN_VALUE)==tick)return false;
        if(!(level.getBlockEntity(pos) instanceof com.frozendawn.block.InductionFurnaceBlockEntity furnace)||!furnace.wantsPower())return false;
        int needed=(com.frozendawn.block.InductionFurnaceBlockEntity.DEMAND_WATTS-grant.generationWatts())/20;
        var stores=grant.stores().stream().filter(level::isLoaded).map(level::getBlockEntity)
                .filter(com.frozendawn.block.PowerStorageBlockEntity.class::isInstance)
                .map(com.frozendawn.block.PowerStorageBlockEntity.class::cast).toList();
        if(stores.stream().mapToLong(com.frozendawn.block.PowerStorageBlockEntity::storedJoules).sum()<needed)return false;
        for(var store:stores){int drawn=store.draw(needed);needed-=drawn;store.publishMachineDraw(drawn);if(needed==0)break;}
        rt.machineTicks.put(pos,tick);return true;
    }
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
    /** Physical node removal clears saved links. Ordinary solid material changes do not
     * change beam legality. Geometry edits suspend only affected connections. */
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        boolean node=before.getBlock()!=after.getBlock()&&(PowerLinkRules.range(before)>0||PowerLinkRules.range(after)>0);
        if(before.getBlock()!=after.getBlock()&&PowerLinkRules.range(before)>0)clear(level,pos);
        var rt=LEVELS.get(level);if(rt==null)return;
        boolean passage=com.frozendawn.world.RoomAtmosphere.isPassage(level,pos,before)!=com.frozendawn.world.RoomAtmosphere.isPassage(level,pos,after);
        boolean port=before.getBlock()!=after.getBlock()&&(isFeedthrough(before)||isFeedthrough(after));
        if(!node&&!passage&&!port)return;
        var rooms=com.frozendawn.world.RoomAtmosphere.cachedRoomViews(level).stream().map(com.frozendawn.world.RoomAtmosphere.RoomView::geometry).toList();
        var touched=rooms.stream().filter(g->g.cells().contains(pos)||g.walls().contains(pos)).toList();
        invalidate(level,rt,link->{var path=PowerLinkRules.path(link.a(),link.b());
            return path.contains(pos)||touched.stream().anyMatch(g->touches(g,path))
                    ||!protectedRoom(rooms,path)&&near(link,pos);
        });
    }
    private static boolean isFeedthrough(BlockState s){return s.getBlock() instanceof com.frozendawn.block.PowerRelayBlock relay&&relay.isFeedthrough();}
    private static boolean near(PowerLink link,BlockPos p){return near(link.a(),p)||near(link.b(),p);}
    private static boolean near(BlockPos a,BlockPos b){return Math.abs(a.getX()-b.getX())<=80&&Math.abs(a.getZ()-b.getZ())<=80&&Math.abs(a.getY()-b.getY())<=48;}
    private static boolean touches(com.frozendawn.world.RoomAtmosphere.Geometry g,List<BlockPos> path){return path.stream().anyMatch(p->g.cells().contains(p)||g.walls().contains(p));}
    private static boolean protectedRoom(List<com.frozendawn.world.RoomAtmosphere.Geometry> rooms,List<BlockPos> path){
        return rooms.stream().anyMatch(g->(g.cells().contains(path.getFirst())||g.walls().contains(path.getFirst()))
                &&(g.cells().contains(path.getLast())||g.walls().contains(path.getLast()))
                &&path.stream().allMatch(p->g.cells().contains(p)||g.walls().contains(p)));
    }
    private static void enqueue(Runtime rt,PowerLink link){if(rt.pendingSet.add(link))rt.pending.addLast(link);}
    private static void invalidate(ServerLevel l,Runtime rt,java.util.function.Predicate<PowerLink> affected){
        rt.geometryRevision++;
        for(var link:PowerNetworkSavedData.get(l).links())if(affected.test(link)){
            if(rt.active.remove(link))rt.graphDirty=true;enqueue(rt,link);
        }
    }
    private static void chunkChanged(ServerLevel l,net.minecraft.world.level.chunk.ChunkAccess chunk){
        var rt=LEVELS.get(l);if(rt==null)return;var cp=chunk.getPos();
        var rooms=com.frozendawn.world.RoomAtmosphere.cachedRoomViews(l).stream().map(com.frozendawn.world.RoomAtmosphere.RoomView::geometry).toList();
        var touched=rooms.stream().filter(g->java.util.stream.Stream.concat(g.cells().stream(),g.walls().stream()).anyMatch(p->new net.minecraft.world.level.ChunkPos(p).equals(cp))).toList();
        invalidate(l,rt,link->{var path=PowerLinkRules.path(link.a(),link.b());
            boolean close=List.of(link.a(),link.b()).stream().anyMatch(p->Math.abs((p.getX()>>4)-cp.x)<=5&&Math.abs((p.getZ()>>4)-cp.z)<=5);
            return path.stream().anyMatch(p->new net.minecraft.world.level.ChunkPos(p).equals(cp))
                    ||touched.stream().anyMatch(g->touches(g,path))||!protectedRoom(rooms,path)&&close;
        });
    }
    @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event){if(event.getLevel() instanceof ServerLevel l)chunkChanged(l,event.getChunk());}
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel l)chunkChanged(l,event.getChunk());}
    public static void update(ServerLevel level) {
        var data = PowerNetworkSavedData.get(level); var rt = runtime(level);
        if (rt.revision != data.revision()) {
            rt.pendingSet.retainAll(data.links());rt.pending.removeIf(link->!rt.pendingSet.contains(link));
            rt.active.retainAll(data.links());
            for(var link:data.links())if(!rt.active.contains(link))enqueue(rt,link);
            rt.revision = data.revision(); rt.graphDirty = true;
        }
        // Never force chunks, and never perform an unbounded flood-fill loop in a server tick.
        for (int budget = 0; budget < 8 && !rt.pending.isEmpty(); budget++) {
            var link = rt.pending.removeFirst();rt.pendingSet.remove(link);
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
        var budget = rt.budgets.getOrDefault(node, new Budget(supplyWatts(level, node), demandWatts(level, node), storedJoules(level, node)));
        return new Reading(cells.size(), links, inactive, budget.supplyWatts() / 1000.0, budget.demandWatts() / 1000.0, budget.storedJoules() / 1000.0);
    }
    public static PowerMonitorReading monitorReading(ServerLevel level, BlockPos pos) {
        var rt=runtime(level);
        if(!level.isLoaded(pos)||!rt.components.containsKey(pos))return PowerMonitorReading.EMPTY;
        return rt.monitorReadings.getOrDefault(pos,PowerMonitorReading.EMPTY);
    }
    private static int supplyWatts(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return 0;
        return level.getBlockEntity(pos) instanceof com.frozendawn.block.GeothermalCoreBlockEntity core
                ? core.getPowerShare() * 200 : 0;
    }
    private static int storedJoules(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof com.frozendawn.block.PowerStorageBlockEntity store ? store.storedJoules() : 0;
    }
    private static int demandWatts(ServerLevel level, BlockPos pos) {
        if(level.isLoaded(pos)&&level.getBlockEntity(pos) instanceof com.frozendawn.block.ResistiveHeaterBlockEntity heater)return heater.requestWatts();
        if(level.isLoaded(pos)&&level.getBlockEntity(pos) instanceof com.frozendawn.block.PixieBlockEntity pixie)return pixie.requestWatts();
        if(level.isLoaded(pos)&&level.getBlockEntity(pos) instanceof com.frozendawn.block.InductionFurnaceBlockEntity furnace)
            return furnace.wantsPower()?com.frozendawn.block.InductionFurnaceBlockEntity.DEMAND_WATTS:0;
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof com.frozendawn.block.PanelLightBlock
                ? com.frozendawn.block.PanelLightBlock.DEMAND_WATTS : 0;
    }
    /** One-second allocation, in integer watts. Each unique node contributes once, including in cycles. */
    public static void account(ServerLevel level) {
        var rt = runtime(level);
        com.frozendawn.world.RoomThermalManager.prepareElectricHeating(level);
        rt.heaterGrants.entrySet().removeIf(e->e.getValue().tick!=level.getGameTime()||!rt.heaters.contains(e.getKey()));
        var nodes = new TreeSet<BlockPos>();
        nodes.addAll(heaters(level));
        for (var link : PowerNetworkSavedData.get(level).links()) {
            if (level.isLoaded(link.a())) nodes.add(link.a());
            if (level.isLoaded(link.b())) nodes.add(link.b());
        }
        rt.stores.removeIf(pos -> !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof com.frozendawn.block.PowerStorageBlockEntity));
        nodes.addAll(rt.stores);
        rt.machines.removeIf(pos -> !level.isLoaded(pos)||!(level.getBlockEntity(pos) instanceof com.frozendawn.block.InductionFurnaceBlockEntity));
        nodes.addAll(rt.machines);
        rt.lifeSupport.removeIf(p->!level.isLoaded(p)||!(level.getBlockEntity(p) instanceof com.frozendawn.block.PixieBlockEntity));
        nodes.addAll(rt.lifeSupport);
        var priorityRemaining=new HashMap<BlockPos,Integer>();
        rt.machineTicks.keySet().retainAll(rt.machines);
        var machineGrants=new HashMap<BlockPos,MachineGrant>();
        // The duplicate guard belongs to physical stores, so an unrelated freshly linked network
        // receives its first allocation even after another component was queried this tick.
        rt.storeEnergyTicks.keySet().removeIf(pos -> !level.isLoaded(pos)||!(level.getBlockEntity(pos) instanceof com.frozendawn.block.PowerStorageBlockEntity));
        var visited = new HashSet<BlockPos>(); var budgets = new HashMap<BlockPos, Budget>();
        var monitorReadings=new HashMap<BlockPos,PowerMonitorReading>();
        var powered = new HashSet<BlockPos>(); var lamps = new HashSet<BlockPos>();
        for (var node : nodes) {
            if (visited.contains(node)) continue;
            var cells = rt.components.getOrDefault(node, Set.of(node)); visited.addAll(cells);
            int supply = 0, demand = 0;
            var loads = new TreeSet<BlockPos>(Comparator.comparingInt((BlockPos p)->level.getBlockEntity(p) instanceof com.frozendawn.block.PixieBlockEntity?0:level.getBlockEntity(p) instanceof com.frozendawn.block.ResistiveHeaterBlockEntity?1:2).thenComparing(Comparator.naturalOrder()));
            for (var cell : cells) {
                supply += supplyWatts(level, cell); int watts = demandWatts(level, cell); demand += watts;
                if (watts > 0) loads.add(cell);
                if(level.isLoaded(cell)&&level.getBlockState(cell).getBlock() instanceof com.frozendawn.block.PanelLightBlock) lamps.add(cell);
            }
            var stores = cells.stream().sorted().filter(p -> level.isLoaded(p))
                    .map(level::getBlockEntity).filter(com.frozendawn.block.PowerStorageBlockEntity.class::isInstance)
                    .map(com.frozendawn.block.PowerStorageBlockEntity.class::cast).toList();
            // Stored joules fund the next one-second interval. Saturate large networks safely.
            long stored = stores.stream().mapToLong(com.frozendawn.block.PowerStorageBlockEntity::storedJoules).sum();
            boolean energyStep=stores.stream().allMatch(store -> rt.storeEnergyTicks.getOrDefault(store.getBlockPos(),Long.MIN_VALUE)!=level.getGameTime());
            if (!energyStep && !stores.isEmpty()) {
                // Keep the already committed interval, including its served-light decision.
                for (var load : loads) if (rt.powered.contains(load)) {
                    powered.add(load);if(rt.machineGrants.containsKey(load))machineGrants.put(load,rt.machineGrants.get(load));
                }
                for (var cell : cells) {
                    budgets.put(cell,rt.budgets.getOrDefault(cell,new Budget(supply,demand,stored)));
                    monitorReadings.put(cell,rt.monitorReadings.getOrDefault(cell,PowerMonitorReading.EMPTY));
                    priorityRemaining.put(cell,rt.priorityRemaining.getOrDefault(cell,0));
                }
                continue;
            }
            for(var store:stores)rt.storeEnergyTicks.put(store.getBlockPos(),level.getGameTime());
            long remaining = supply + stored; int served = 0;
            var pixieGrants=new HashMap<BlockPos,Integer>();
            var heaterWatts=new HashMap<BlockPos,Integer>();
            for (var load : loads) {
                int watts = demandWatts(level, load);
                if(level.getBlockEntity(load) instanceof com.frozendawn.block.PixieBlockEntity pixie){
                    int grant=(int)Math.min(watts,remaining)/pixie.powerQuantum()*pixie.powerQuantum();pixieGrants.put(load,grant);remaining-=grant;served+=grant;
                    if(grant==watts)powered.add(load);
                } else if(level.getBlockEntity(load) instanceof com.frozendawn.block.ResistiveHeaterBlockEntity){
                    int grant=(int)Math.min(watts,remaining);heaterWatts.put(load,grant);remaining-=grant;served+=grant;if(grant==watts)powered.add(load);
                } else if (watts <= remaining) { powered.add(load); remaining -= watts; served += watts; }
            }
            int lifeWatts=pixieGrants.values().stream().mapToInt(Integer::intValue).sum();
            for(var cell:cells)priorityRemaining.put(cell,(int)Math.min(Integer.MAX_VALUE,supply+stored-lifeWatts));
            int generation=supply, lampShortage=0;
            var storePositions=stores.stream().map(com.frozendawn.block.PowerStorageBlockEntity::getBlockPos).toList();
            for(var load:loads) if(powered.contains(load)||pixieGrants.containsKey(load)||heaterWatts.containsKey(load)) {
                int watts=heaterWatts.getOrDefault(load,pixieGrants.getOrDefault(load,demandWatts(level,load))),fromCore=Math.min(watts,generation);generation-=fromCore;
                if(level.getBlockEntity(load) instanceof com.frozendawn.block.ResistiveHeaterBlockEntity)
                    rt.heaterGrants.putIfAbsent(load,new HeaterGrant(watts,fromCore,level.getGameTime(),storePositions));
                else if(level.getBlockEntity(load) instanceof com.frozendawn.block.InductionFurnaceBlockEntity)
                    machineGrants.put(load,new MachineGrant(fromCore,storePositions,level.getGameTime()+20));
                else lampShortage+=watts-fromCore;
            }
            int discharged=Math.max(0,served-supply),surplus=generation;
            for (var store : stores) {
                int drawn = store.draw(lampShortage); lampShortage -= drawn;
                int charged = store.isBank() ? store.charge(surplus) : 0; surplus -= charged;
                store.publish(charged, drawn);
            }
            for(var cell:cells)if(level.isLoaded(cell)&&level.getBlockEntity(cell) instanceof com.frozendawn.block.PixieBlockEntity pixie)
                pixie.produce(pixieGrants.getOrDefault(cell,0));
            long after = stores.stream().mapToLong(com.frozendawn.block.PowerStorageBlockEntity::storedJoules).sum();
            var budget = new Budget(supply + discharged, demand, after);
            long capacity=stores.stream().mapToLong(com.frozendawn.block.PowerStorageBlockEntity::capacityJoules).sum();
            var shed=loads.stream().filter(load->!powered.contains(load)).map(load->new PowerMonitorReading.ShedLoad(
                    level.getBlockEntity(load) instanceof com.frozendawn.block.ResistiveHeaterBlockEntity?4:level.getBlockEntity(load) instanceof com.frozendawn.block.PixieBlockEntity?3:level.getBlockEntity(load) instanceof com.frozendawn.block.InductionFurnaceBlockEntity?2:1,load)).toList();
            var monitor=new PowerMonitorReading(cells.size()>1,budget.supplyWatts(),supply,demand,after,capacity,shed);
            for (var cell : cells) {budgets.put(cell,budget);monitorReadings.put(cell,monitor);}
        }
        var affected = new HashSet<>(rt.lamps); affected.addAll(lamps);
        rt.budgets = Map.copyOf(budgets); rt.powered = Set.copyOf(powered); rt.lamps = Set.copyOf(lamps);
        rt.machineGrants=Map.copyOf(machineGrants);
        rt.monitorReadings=Map.copyOf(monitorReadings);
        rt.priorityRemaining=Map.copyOf(priorityRemaining);
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
            if(runtime(level).coordinatedTick!=level.getGameTime()) {
                update(level);
                if (level.getGameTime() % 20 == 0) account(level);
            }
            for (var player : level.players()) {
                if (level.getGameTime() % 20 == 0)
                    PacketDistributor.sendToPlayer(player, new PowerLinksPayload(level.dimension().location().toString(), nearby(level, player.blockPosition())));
                if (level.getGameTime() % 5 == 0) PowerBinderFeedback.send(player);
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LEVELS.clear(); PowerBinderFeedback.reset(); }
}
