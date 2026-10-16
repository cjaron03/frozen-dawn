package com.frozendawn.airlock;

import com.frozendawn.airlock.AirlockSavedState.Chamber;
import com.frozendawn.block.*;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModSounds;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.network.AirlockStatusPayload;
import com.frozendawn.network.AirlockStatusPayload.Status;
import com.frozendawn.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.network.PacketDistributor;

/** Loaded geometry and finite chamber gas, including verified internal room connections. */
public final class AirlockManager {
    private enum Layout { VALID, INVALID, UNLOADED }
    private record Candidate(Set<BlockPos> cells,Set<BlockPos> doors,BlockPos base,BlockPos outside) {}
    private AirlockManager() {}

    public static BlockPos lower(BlockPos pos,BlockState state) {
        return state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
    }
    private static boolean completeDoor(ServerLevel level,BlockPos pos) {
        if(!level.isLoaded(pos)||!level.isLoaded(pos.above()))return false;
        var a=level.getBlockState(pos);var b=level.getBlockState(pos.above());
        return a.getBlock() instanceof AirlockDoorBlock&&b.is(a.getBlock())
                &&a.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&b.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER
                &&a.getValue(DoorBlock.FACING)==b.getValue(DoorBlock.FACING);
    }
    private static Set<BlockPos> doors(ServerLevel level,RoomAtmosphere.Geometry g) {
        var found=new HashSet<BlockPos>();
        for(var wall:g.walls())if(level.getBlockState(wall).getBlock() instanceof AirlockDoorBlock) {
            var pos=lower(wall,level.getBlockState(wall));if(!completeDoor(level,pos))return Set.of();found.add(pos.immutable());
        }
        return found;
    }
    private static BlockPos otherSide(ServerLevel level,BlockPos door,Set<BlockPos> cells) {
        var facing=level.getBlockState(door).getValue(DoorBlock.FACING);
        for(int dy=0;dy<2;dy++) {
            var a=door.above(dy).relative(facing);var b=door.above(dy).relative(facing.getOpposite());
            if(cells.contains(a))return b;if(cells.contains(b))return a;
        }
        return null;
    }
    private static Candidate candidate(ServerLevel level,BlockPos start) {
        if(!level.isLoaded(start)||level.getBlockState(start).getBlock() instanceof AirlockDoorBlock)return null;
        var g=RoomAtmosphere.inspectAirlockPartition(level,start,33);
        if(g.seal()!=RoomAtmosphere.Seal.SEALED||g.cells().size()>32)return null;
        var doors=doors(level,g);if(doors.size()<2)return null;
        BlockPos base=null,outside=null;
        for(var door:doors) {
            var side=otherSide(level,door,g.cells());if(side==null||!level.isLoaded(side))return null;
            var adjacent=RoomAtmosphere.inspectAirlockPartition(level,side,12000);
            if(adjacent.seal()==RoomAtmosphere.Seal.UNKNOWN&&(!adjacent.exceededBounds()||!RoomAtmosphere.knownExteriorAt(level,side)))return null;
            if(adjacent.seal()==RoomAtmosphere.Seal.OPEN&&CombustionAtmosphere.isVacuum(level))RoomAirState.get(level).evacuate(Set.of(side));
            if(adjacent.seal()==RoomAtmosphere.Seal.SEALED&&RoomAtmosphere.hasAir(level,side)) {
                if(base!=null&&!adjacent.cells().contains(base))return null;
                base=side;
            } else {
                if(outside!=null&&adjacent.seal()==RoomAtmosphere.Seal.SEALED&&!adjacent.cells().contains(outside))return null;
                outside=side;
            }
        }
        return base!=null&&outside!=null?new Candidate(g.cells(),doors,base,outside):null;
    }
    public static Chamber discover(ServerLevel level,BlockPos panel,Direction facing) {
        if(!CombustionAtmosphere.isVacuum(level))return null;
        var known=nearbyKnown(level,panel,facing);if(known!=null)return known;
        var state=AirlockSavedState.get(level);var candidates=new LinkedHashMap<Long,Candidate>();
        var start=panel.relative(facing);var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();
        queue.add(start);seen.add(start);
        while(!queue.isEmpty()&&seen.size()<=256) {
            var pos=queue.removeFirst();if(!level.isLoaded(pos))return null;if(pos.distManhattan(panel)>4)continue;
            var block=level.getBlockState(pos);
            if(block.getBlock() instanceof AirlockDoorBlock) {
                var door=lower(pos,block);if(!completeDoor(level,door))continue;
                var normal=block.getValue(DoorBlock.FACING);
                for(var dir:new Direction[]{normal,normal.getOpposite()}) {
                    var c=candidate(level,door.relative(dir));
                    if(c!=null)candidates.put(c.cells.stream().mapToLong(BlockPos::asLong).min().orElseThrow(),c);
                }
                continue;
            }
            if(!RoomAtmosphere.isPassage(level,pos,block))continue;
            if(pos.equals(start)) {
                var c=candidate(level,pos);if(c!=null)candidates.put(c.cells.stream().mapToLong(BlockPos::asLong).min().orElseThrow(),c);
            }
            for(var dir:Direction.values())if(pos.relative(dir).distManhattan(panel)<=4&&seen.add(pos.relative(dir)))queue.addLast(pos.relative(dir));
        }
        if(candidates.size()!=1)return null;
        var c=candidates.values().iterator().next();
        // Sample ordinary trapped air before registering the controlled override.
        boolean air=RoomAtmosphere.hasAir(level,c.cells.iterator().next());
        return state.create(level,c.cells,c.doors,c.base,c.outside,air);
    }
    private static Chamber nearbyKnown(ServerLevel level,BlockPos panel,Direction facing) {
        var saved=AirlockSavedState.get(level);var matches=new HashSet<Chamber>();
        var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();
        queue.add(panel.relative(facing));seen.add(panel.relative(facing));
        while(!queue.isEmpty()) {
            var pos=queue.removeFirst();if(!level.isLoaded(pos))return null;
            var state=level.getBlockState(pos);var c=saved.at(pos);
            if(c!=null&&layout(level,c)==Layout.VALID)matches.add(c);
            if(state.getBlock() instanceof AirlockDoorBlock) {
                for(var owner:saved.atDoor(lower(pos,state)))if(layout(level,owner)==Layout.VALID)matches.add(owner);
                continue;
            }
            if(!RoomAtmosphere.isPassage(level,pos,state))continue;
            for(var dir:Direction.values())if(pos.relative(dir).distManhattan(panel)<=4&&seen.add(pos.relative(dir)))queue.add(pos.relative(dir));
        }
        return matches.size()==1?matches.iterator().next():null;
    }
    public static Chamber resolve(ServerLevel level,AirlockControllerBlockEntity panel) {
        var state=AirlockSavedState.get(level);var c=panel.assigned()?state.byId(panel.chamberId()):null;
        if(c!=null&&c.panels.contains(panel.getBlockPos())) {
            c=refresh(level,c);if(layout(level,c)==Layout.VALID)return c;
        }
        c=discover(level,panel.getBlockPos(),panel.getBlockState().getValue(AirlockControllerBlock.FACING));
        if(c!=null) {c.panels.add(panel.getBlockPos().immutable());panel.assign(c.id);state.setDirty();}
        return c;
    }
    /** A separated pocket is no longer the chamber: begin beside its owned doors. */
    private static BlockPos chamberAnchor(ServerLevel level,Chamber c) {
        for(var door:c.doors.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList()) {
            var state=level.getBlockState(door);
            if(!(state.getBlock() instanceof AirlockDoorBlock))continue;
            var normal=state.getValue(DoorBlock.FACING);
            for(int dy=0;dy<2;dy++)for(var side:new Direction[]{normal,normal.getOpposite()}) {
                var pos=door.above(dy).relative(side);
                if(c.cells.contains(pos)&&RoomAtmosphere.isPassage(level,pos,level.getBlockState(pos)))return pos;
            }
        }
        return c.cells.stream().filter(pos->RoomAtmosphere.isPassage(level,pos,level.getBlockState(pos)))
                .min(Comparator.comparingLong(BlockPos::asLong)).orElse(null);
    }
    private static Layout layout(ServerLevel level,Chamber c) {
        for(var pos:c.cells) {
            if(!level.isLoaded(pos))return Layout.UNLOADED;
            for(var dir:Direction.values())if(!level.isLoaded(pos.relative(dir)))return Layout.UNLOADED;
        }
        for(var door:c.doors)if(!level.isLoaded(door)||!level.isLoaded(door.above()))return Layout.UNLOADED;
        if(!level.isLoaded(c.base)||!level.isLoaded(c.outside))return Layout.UNLOADED;
        var anchor=chamberAnchor(level,c);
        if(anchor==null)return Layout.INVALID;
        var g=RoomAtmosphere.inspectAirlockPartition(level,anchor,33);
        if(g.seal()==RoomAtmosphere.Seal.UNKNOWN&&!g.exceededBounds())return Layout.UNLOADED;
        return g.seal()==RoomAtmosphere.Seal.SEALED&&g.cells().equals(c.cells)&&doors(level,g).equals(c.doors)?Layout.VALID:Layout.INVALID;
    }
    private static Chamber refresh(ServerLevel level,Chamber c) {
        var current=layout(level,c);
        if(current!=Layout.INVALID) {
            // A repaired partition retains its measured, already paid chamber gas.
            if(current==Layout.VALID&&c.gas.breathable()&&RoomAirState.get(level).isDepleted(c.cells))
                RoomAirState.get(level).refill(c.cells);
            return c;
        }
        var anchor=chamberAnchor(level,c);
        if(anchor==null)return c;
        var g=RoomAtmosphere.inspectAirlockPartition(level,anchor,33);
        if(g.seal()==RoomAtmosphere.Seal.SEALED&&g.cells().size()<=32&&doors(level,g).equals(c.doors)) {
            if(c.gas.cycling())machinery(level,c,ModSounds.AIRLOCK_INTERRUPTED.get());
            return AirlockSavedState.get(level).reshape(level,c,g.cells());
        }
        var actual=RoomAtmosphere.inspect(level,anchor);
        if(actual.seal()==RoomAtmosphere.Seal.OPEN)invalidate(level,c,null);
        else if(actual.seal()==RoomAtmosphere.Seal.SEALED&&sharesBaseRoom(level,c,actual.cells())&&c.gas.cycling()) {
            c.gas.interrupt();c.waitingForPower=false;AirlockSavedState.get(level).setDirty();
            machinery(level,c,ModSounds.AIRLOCK_INTERRUPTED.get());
        }
        return c;
    }
    /** The caller supplies a verified physical room; a pipe or power link cannot join it. */
    public static boolean sharesBaseRoom(ServerLevel level,Chamber c,Set<BlockPos> cells) {
        if(!level.isLoaded(c.base)||!cells.contains(c.base))return false;
        boolean chamber=false;
        for(var pos:c.cells) {
            if(!level.isLoaded(pos))return false;
            if(!RoomAtmosphere.isPassage(level,pos,level.getBlockState(pos)))continue;
            if(!cells.contains(pos))return false;
            chamber=true;
        }
        return chamber;
    }
    public static int roomAirRequest(ServerLevel level,Chamber c,Set<BlockPos> cells) {
        return !c.gas.cycling()&&sharesBaseRoom(level,c,cells)?AirlockSavedState.get(level).roomAirRequest(level,cells):0;
    }
    public static boolean roomAirPending(ServerLevel level,Set<BlockPos> cells) {
        var saved=AirlockSavedState.get(level);
        return saved.hasRoomGas(cells)&&saved.roomAirRequest(level,cells)>0;
    }
    public static boolean blocksAutomaticAir(ServerLevel level,Set<BlockPos> cells) {
        var saved=AirlockSavedState.get(level);var seen=new HashSet<Chamber>();
        for(var pos:cells) {
            var c=saved.at(pos);if(c==null||!seen.add(c))continue;
            if(sharesBaseRoom(level,c,cells))continue;
            if(layout(level,c)!=Layout.VALID||c.gas.cycling()||c.gas.air()==0)return true;
        }
        return false;
    }
    private static double pressure(ServerLevel level,BlockPos pos) {
        if(!level.isLoaded(pos))return -1;
        var c=AirlockSavedState.get(level).at(pos);
        if(c!=null) {
            c=refresh(level,c);
            if(layout(level,c)==Layout.UNLOADED)return -1;
            if(layout(level,c)==Layout.VALID&&c.cells.contains(pos)) {
                if(c.gas.cycling())return -1;
                return c.gas.density();
            }
            // A genuinely breached chamber uses its physical surroundings, not a stale lock.
        }
        var g=RoomAtmosphere.inspect(level,pos);
        // A scan limit preserves confirmed pressure; unloaded probes remain unknown.
        if(g.seal()==RoomAtmosphere.Seal.UNKNOWN) {
            if(!g.exceededBounds())return -1;
            if(RoomAtmosphere.knownExteriorAt(level,pos))return CombustionAtmosphere.isVacuum(level)?0:1;
            var known=com.frozendawn.data.RoomIdentityState.get(level).at(pos);
            return known==null?-1:(double)AirlockSavedState.get(level).roomGas(level,known.cells())/(known.cells().size()*AirlockCycle.UNITS_PER_CELL);
        }
        if(g.seal()==RoomAtmosphere.Seal.OPEN)return CombustionAtmosphere.isVacuum(level)?0:1;
        RoomAtmosphere.view(level,pos); // Bind the actual base membership before a doorway joins it.
        return (double)AirlockSavedState.get(level).roomGas(level,g.cells())/(g.cells().size()*AirlockCycle.UNITS_PER_CELL);
    }
    public static boolean canOpen(ServerLevel level,BlockPos pos) {
        var block=level.getBlockState(pos);if(!(block.getBlock() instanceof AirlockDoorBlock))return false;
        var door=lower(pos,block);
        for(var c:List.copyOf(AirlockSavedState.get(level).atDoor(door)))if(refresh(level,c).gas.cycling())return false;
        if(!CombustionAtmosphere.isVacuum(level))return true;
        if(!completeDoor(level,door))return false;
        var normal=level.getBlockState(door).getValue(DoorBlock.FACING);
        double a=pressure(level,door.relative(normal)),b=pressure(level,door.relative(normal.getOpposite()));
        return matchingPressure(a,b);
    }
    private static boolean matchingPressure(double a,double b) {
        return a>=0&&b>=0&&Math.abs(a-b)<=.05*Math.max(1,Math.max(a,b));
    }
    public static BlockState normalizeDoor(ServerLevel level,BlockPos pos,BlockState requested) {
        if(requested.getBlock() instanceof AirlockDoorBlock&&requested.getValue(DoorBlock.OPEN)) {
            var old=level.getBlockState(pos);
            if(!old.is(requested.getBlock())||!old.getValue(DoorBlock.OPEN)&&!canOpen(level,pos))
                return requested.setValue(DoorBlock.OPEN,false).setValue(DoorBlock.POWERED,false);
        }
        return requested;
    }
    public static void notify(Player player,Status status,Chamber chamber,int amount) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        var gas=chamber==null?null:chamber.gas;
        PacketDistributor.sendToPlayer(serverPlayer,new AirlockStatusPayload(status,
                gas==null?0:gas.reserve(),gas==null?0:gas.air(),gas==null?0:gas.capacity(),
                gas==null?0:gas.elapsed()*100/AirlockCycle.DURATION,amount));
    }
    private static void broadcast(ServerLevel level,Chamber chamber,Status status,int amount) {
        for(var player:level.players()) {
            boolean nearby=AtmosphericBreach.contains(chamber.cells,player);
            for(var panel:chamber.panels) nearby|=player.distanceToSqr(panel.getCenter())<=16;
            if(nearby)notify(player,status,chamber,amount);
        }
    }
    public static void refuse(ServerLevel level,BlockPos pos,Player player) {
        notify(player,Status.DIFFERENTIAL,null,0);
        level.playSound(null,pos,ModSounds.AIRLOCK_REFUSE.get(),SoundSource.BLOCKS,.65f,1f);
    }
    private static boolean closed(ServerLevel level,Chamber c) {
        for(var door:c.doors)if(!completeDoor(level,door)||level.getBlockState(door).getValue(DoorBlock.OPEN)
                ||level.getBlockState(door.above()).getValue(DoorBlock.OPEN))return false;
        return true;
    }
    public static boolean hasCoreFeed(ServerLevel level,Chamber c) {
        if(!RoomAtmosphere.hasAir(level,c.base))return false;
        var base=RoomAtmosphere.inspectAirlockPartition(level,c.base,12000);
        if(base.seal()!=RoomAtmosphere.Seal.SEALED)return false;
        for(var pos:GeothermalCoreRegistry.getCores(level)) {
            if(!level.isLoaded(pos)||!(level.getBlockEntity(pos) instanceof GeothermalCoreBlockEntity core)
                    ||core.getOxygenSupply()<=0||c.base.distSqr(pos)>(long)core.getEffectiveO2Range()*core.getEffectiveO2Range())continue;
            if(base.cells().contains(pos))return true;
            for(var dir:Direction.values())if(base.cells().contains(pos.relative(dir)))return true;
        }
        return false;
    }
    public static boolean canReceiveSharedFeed(ServerLevel level,Chamber c){return layout(level,c)==Layout.VALID&&hasCoreFeed(level,c);}
    public static int charge(ServerLevel level,Chamber c,ItemStack tank) {
        if(!(tank.getItem() instanceof O2TankItem item))return 0;
        int offered=Math.clamp(tank.getOrDefault(ModDataComponents.O2_LEVEL.get(),item.getMaxO2()),0,item.getMaxO2());
        int accepted=c.gas.fill(offered);
        if(accepted>0) {tank.set(ModDataComponents.O2_LEVEL.get(),offered-accepted);AirlockSavedState.get(level).setDirty();}
        return accepted;
    }
    private static Status waitingStatus(Chamber c) {
        boolean filling=c.gas.cycling()?c.gas.mode()==AirlockCycle.Mode.PRESSURIZING:!c.gas.breathable();
        return filling?Status.WAITING_TO_FILL:Status.WAITING_TO_VENT;
    }
    private static String statusName(Status status){return status.name().toLowerCase(Locale.ROOT);}
    public static String status(Chamber c){return c.gas.cycling()?(c.waitingForPower?statusName(waitingStatus(c)):c.gas.mode()==AirlockCycle.Mode.PRESSURIZING?"pressurizing":"depressurizing"):"status";}
    public static String start(ServerLevel level,Chamber c) {
        if(layout(level,c)!=Layout.VALID)return "invalid";
        if(c.gas.cycling())return status(c);
        if(!closed(level,c))return "close_doors";
        // Reading actual base pressure also resolves a repaired split before pump gas changes.
        double basePressure=pressure(level,c.base);
        if(basePressure<0)return "invalid";
        double destination=matchingPressure(c.gas.density(),basePressure)?pressure(level,c.outside):basePressure;
        if(destination<0||destination*c.gas.capacity()>Integer.MAX_VALUE)return "invalid";
        int target=(int)Math.round(destination*c.gas.capacity());
        if(target==c.gas.air())return "status";
        boolean pressurize=target>c.gas.air();
        // A powerless request keeps the existing air and matching return door available.
        if(!com.frozendawn.power.PowerNetworks.airlockCanStart(level,c))
            return statusName(pressurize?Status.WAITING_TO_FILL:Status.WAITING_TO_VENT);
        if(!c.gas.startTo(target))return "insufficient";
        AirlockSavedState.get(level).setDirty();CombustionAtmosphere.reset();
        if(!pressurize) {
            RoomAirState.get(level).evacuate(c.cells);snuff(level,c.cells);

        }
        // A reachable budget admits the cycle, but running is reported only after
        // a grant actually delivers its first paid tick.
        c.waitingForPower=true;
        return status(c);
    }
    public static void tick(ServerLevel level,Chamber c) {
        c=refresh(level,c);
        if(c.lastTick==level.getGameTime())return;c.lastTick=level.getGameTime();
        var layout=layout(level,c);if(layout==Layout.UNLOADED)return;
        if(layout==Layout.INVALID)return;
        var actual=level.getGameTime()%20==0?RoomAtmosphere.inspect(level,c.cells.iterator().next()):null;
        if(actual!=null&&actual.seal()==RoomAtmosphere.Seal.OPEN&&c.gas.air()>0) {invalidate(level,c,null);return;}
        // Core feed is allocated from the same room production as every other charger.
        if(c.gas.cycling()) {
            if(!closed(level,c)) {c.gas.interrupt();AirlockSavedState.get(level).setDirty();broadcast(level,c,Status.INTERRUPTED,0);machinery(level,c,ModSounds.AIRLOCK_INTERRUPTED.get());return;}
            if(!com.frozendawn.power.PowerNetworks.consumeAirlockTick(level,c)) {
                if(!c.waitingForPower||level.getGameTime()%20==0)broadcast(level,c,waitingStatus(c),0);
                c.waitingForPower=true;indicate(level,c);return;
            }
            boolean resumed=c.waitingForPower;
            c.waitingForPower=false;
            if(c.gas.elapsed()%20==0)machinery(level,c,c.gas.mode()==AirlockCycle.Mode.PRESSURIZING?ModSounds.AIRLOCK_PUMP_FILL.get():ModSounds.AIRLOCK_PUMP_RECOVER.get());
            boolean done=c.gas.tick();AirlockSavedState.get(level).setDirty();
            if(!done&&(resumed||c.gas.elapsed()%20==0))broadcast(level,c,c.gas.mode()==AirlockCycle.Mode.PRESSURIZING?Status.PRESSURIZING:Status.DEPRESSURIZING,0);
            if(done) {
                // A successful match can contain less or more than nominal air.
                boolean airPresent=c.gas.air()>0;
                if(airPresent) {
                    RoomAirState.get(level).refill(c.cells);
                    broadcast(level,c,Status.READY,0);
                } else broadcast(level,c,Status.EVACUATED,c.gas.lost());
                CombustionAtmosphere.reset();
                machinery(level,c,airPresent?ModSounds.AIRLOCK_READY.get():ModSounds.AIRLOCK_EVACUATED.get());
            }
        }
        indicate(level,c);
    }
    private static void indicate(ServerLevel level,Chamber c) {
        int indicator=c.gas.cycling()?1:c.gas.air()>0&&!RoomAirState.get(level).isDepleted(c.cells)?2:0;
        for(var pos:List.copyOf(c.panels)) {
            if(!level.isLoaded(pos))continue;
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof AirlockControllerBlock)) {c.panels.remove(pos);AirlockSavedState.get(level).setDirty();continue;}
            if(state.getValue(AirlockControllerBlock.INDICATOR)!=indicator)level.setBlock(pos,state.setValue(AirlockControllerBlock.INDICATOR,indicator),3);
        }
    }
    private static void machinery(ServerLevel level,Chamber c,SoundEvent sound) {
        // One anchor and one event per chamber, regardless of optional panel count.
        var anchor=c.panels.stream().filter(level::isLoaded).sorted(Comparator.comparingLong(BlockPos::asLong)).findFirst()
                .orElseGet(()->c.cells.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow());
        level.playSound(null,anchor,sound,SoundSource.BLOCKS,.7f,1f);
    }
    private static void snuff(ServerLevel level,Set<BlockPos> cells) {
        var walls=new HashSet<BlockPos>();for(var pos:cells)for(var dir:Direction.values())if(!cells.contains(pos.relative(dir)))walls.add(pos.relative(dir));
        VacuumFlames.extinguishRoom(level,cells,walls);
    }
    private static void invalidate(ServerLevel level,Chamber c,BlockPos hole) {
        if(c.gas.cycling())machinery(level,c,ModSounds.AIRLOCK_INTERRUPTED.get());
        boolean hadAir=c.gas.air()>0;
        if(CombustionAtmosphere.isVacuum(level)){c.gas.vent();RoomAirState.get(level).evacuate(c.cells);snuff(level,c.cells);}
        else {c.gas=AirlockCycle.reconfigured(c.cells.size(),c.gas.capacity(),c.gas.reserve());RoomAirState.get(level).refill(c.cells);}
        AirlockSavedState.get(level).setDirty();CombustionAtmosphere.reset();
        if(hadAir&&hole!=null&&RoomAtmosphere.isPassage(level,hole,level.getBlockState(hole)))AtmosphericBreach.start(level,c.cells,hole);
    }
    public static void blockChanged(ServerLevel level,BlockPos pos,BlockState before,BlockState after) {
        if(before==after||RoomAtmosphere.isPassage(level,pos,before)==RoomAtmosphere.isPassage(level,pos,after)
                &&!(before.getBlock() instanceof AirlockDoorBlock)&&!(after.getBlock() instanceof AirlockDoorBlock))return;
        var saved=AirlockSavedState.get(level);
        var affected=new HashSet<Chamber>(saved.atDoor(pos));affected.addAll(saved.atDoor(pos.below()));
        var owner=saved.at(pos);if(owner!=null)affected.add(owner);
        for(var dir:Direction.values()) {owner=saved.at(pos.relative(dir));if(owner!=null)affected.add(owner);}
        for(var c:affected)if(layout(level,c)==Layout.INVALID) {
            int beforeAir=c.gas.air();var current=refresh(level,c);
            if(beforeAir>0&&current.gas.air()==0&&RoomAtmosphere.isPassage(level,pos,after))AtmosphericBreach.start(level,c.cells,pos);
        }
    }
    public static void removePanel(ServerLevel level,BlockPos pos,long id) {
        var saved=AirlockSavedState.get(level);var c=saved.byId(id);if(c==null)return;
        c.panels.remove(pos);if(c.panels.isEmpty())c.gas.discardReserve();saved.setDirty();
    }
    public static boolean emergencyVent(ServerLevel level,BlockPos valve,Direction facing) {
        var c=discover(level,valve,facing);if(c==null)return false;
        var actual=RoomAtmosphere.inspect(level,c.cells.iterator().next());
        var cells=new HashSet<>(c.cells);cells.addAll(actual.cells());
        boolean hadAir=c.gas.air()>0;c.gas.vent();RoomAirState.get(level).evacuate(cells);snuff(level,cells);
        AirlockSavedState.get(level).setDirty();CombustionAtmosphere.reset();
        if(hadAir)AtmosphericBreach.start(level,cells,valve);
        // AtmosphericBreach owns the escaping-air whoosh; this is only the valve mechanism.
        level.playSound(null,valve,ModSounds.AIRLOCK_VALVE.get(),SoundSource.BLOCKS,.8f,1f);
        return true;
    }
}
