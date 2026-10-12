package com.frozendawn.airlock;

import com.frozendawn.airlock.AirlockSavedState.Chamber;
import com.frozendawn.block.*;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.network.AirlockStatusPayload;
import com.frozendawn.network.AirlockStatusPayload.Status;
import com.frozendawn.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.network.PacketDistributor;

/** Loaded geometry only. Device gas is never refilled by ordinary room recovery. */
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
            if(adjacent.seal()==RoomAtmosphere.Seal.UNKNOWN)return null;
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
        if(c!=null&&c.panels.contains(panel.getBlockPos())&&layout(level,c)==Layout.VALID)return c;
        c=discover(level,panel.getBlockPos(),panel.getBlockState().getValue(AirlockControllerBlock.FACING));
        if(c!=null) {c.panels.add(panel.getBlockPos().immutable());panel.assign(c.id);state.setDirty();}
        return c;
    }
    private static Layout layout(ServerLevel level,Chamber c) {
        for(var pos:c.cells) {
            if(!level.isLoaded(pos))return Layout.UNLOADED;
            for(var dir:Direction.values())if(!level.isLoaded(pos.relative(dir)))return Layout.UNLOADED;
        }
        for(var door:c.doors)if(!level.isLoaded(door)||!level.isLoaded(door.above()))return Layout.UNLOADED;
        if(!level.isLoaded(c.base)||!level.isLoaded(c.outside))return Layout.UNLOADED;
        var g=RoomAtmosphere.inspectAirlockPartition(level,c.cells.iterator().next(),33);
        return g.seal()==RoomAtmosphere.Seal.SEALED&&g.cells().equals(c.cells)&&doors(level,g).equals(c.doors)?Layout.VALID:Layout.INVALID;
    }
    public static boolean blocksAutomaticAir(ServerLevel level,Set<BlockPos> cells) {
        var saved=AirlockSavedState.get(level);var seen=new HashSet<Chamber>();
        for(var pos:cells) {
            var c=saved.at(pos);if(c==null||!seen.add(c))continue;
            if(layout(level,c)!=Layout.VALID||!c.gas.breathable())return true;
        }
        return false;
    }
    private static int pressure(ServerLevel level,BlockPos pos) {
        if(!level.isLoaded(pos))return -1;
        var c=AirlockSavedState.get(level).at(pos);
        if(c!=null) {
            if(layout(level,c)!=Layout.VALID||c.gas.cycling()||c.gas.air()>0&&c.gas.air()<c.gas.capacity())return -1;
            return c.gas.breathable()?1:0;
        }
        var g=RoomAtmosphere.inspect(level,pos);
        return g.seal()==RoomAtmosphere.Seal.UNKNOWN?-1:RoomAtmosphere.hasAir(level,pos)?1:0;
    }
    public static boolean canOpen(ServerLevel level,BlockPos pos) {
        var block=level.getBlockState(pos);if(!(block.getBlock() instanceof AirlockDoorBlock))return false;
        var door=lower(pos,block);
        for(var c:AirlockSavedState.get(level).atDoor(door))if(c.gas.cycling())return false;
        if(!CombustionAtmosphere.isVacuum(level))return true;
        if(!completeDoor(level,door))return false;
        var normal=level.getBlockState(door).getValue(DoorBlock.FACING);
        int a=pressure(level,door.relative(normal)),b=pressure(level,door.relative(normal.getOpposite()));
        return a>=0&&a==b;
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
        level.playSound(null,pos,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,0.25F,1.5F);
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
                    ||c.base.distSqr(pos)>(long)core.getEffectiveO2Range()*core.getEffectiveO2Range())continue;
            if(base.cells().contains(pos))return true;
            for(var dir:Direction.values())if(base.cells().contains(pos.relative(dir)))return true;
        }
        return false;
    }
    public static int charge(ServerLevel level,Chamber c,ItemStack tank) {
        if(!(tank.getItem() instanceof O2TankItem item))return 0;
        int offered=Math.clamp(tank.getOrDefault(ModDataComponents.O2_LEVEL.get(),item.getMaxO2()),0,item.getMaxO2());
        int accepted=c.gas.fill(offered);
        if(accepted>0) {tank.set(ModDataComponents.O2_LEVEL.get(),offered-accepted);AirlockSavedState.get(level).setDirty();}
        return accepted;
    }
    public static String start(ServerLevel level,Chamber c) {
        if(layout(level,c)!=Layout.VALID)return "invalid";
        if(c.gas.cycling())return "cycling";
        if(!closed(level,c))return "close_doors";
        boolean pressurize=!c.gas.breathable();
        if(!c.gas.start(pressurize))return "insufficient";
        AirlockSavedState.get(level).setDirty();CombustionAtmosphere.reset();
        if(!pressurize) {
            RoomAirState.get(level).evacuate(c.cells);snuff(level,c.cells);
            level.playSound(null,c.cells.iterator().next(),SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,0.45F,0.8F);
        }
        return pressurize?"pressurizing":"depressurizing";
    }
    public static void tick(ServerLevel level,Chamber c) {
        if(c.lastTick==level.getGameTime())return;c.lastTick=level.getGameTime();
        var layout=layout(level,c);if(layout==Layout.UNLOADED)return;
        if(layout==Layout.INVALID) {invalidate(level,c,null);return;}
        var actual=level.getGameTime()%20==0?RoomAtmosphere.inspect(level,c.cells.iterator().next()):null;
        if(actual!=null&&actual.seal()==RoomAtmosphere.Seal.OPEN&&c.gas.air()>0) {invalidate(level,c,null);return;}
        if(level.getGameTime()%20==0&&hasCoreFeed(level,c)&&c.gas.fill(40)>0)AirlockSavedState.get(level).setDirty();
        if(c.gas.cycling()) {
            if(!closed(level,c)) {c.gas.interrupt();AirlockSavedState.get(level).setDirty();broadcast(level,c,Status.INTERRUPTED,0);return;}
            if(c.gas.elapsed()%20==0)level.playSound(null,c.cells.iterator().next(),SoundEvents.PISTON_EXTEND,SoundSource.BLOCKS,0.22F,0.7F);
            boolean done=c.gas.tick();AirlockSavedState.get(level).setDirty();
            if(!done&&c.gas.elapsed()%20==0)broadcast(level,c,c.gas.mode()==AirlockCycle.Mode.PRESSURIZING?Status.PRESSURIZING:Status.DEPRESSURIZING,0);
            if(done) {
                if(c.gas.breathable()) {
                    RoomAirState.get(level).refill(c.cells);
                    broadcast(level,c,Status.READY,0);
                }
                if(!c.gas.breathable())broadcast(level,c,Status.EVACUATED,c.gas.lost());
                CombustionAtmosphere.reset();
                level.playSound(null,c.cells.iterator().next(),SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,0.3F,1.3F);
            }
        }
        int indicator=c.gas.cycling()?1:c.gas.breathable()?2:0;
        for(var pos:List.copyOf(c.panels)) {
            if(!level.isLoaded(pos))continue;
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof AirlockControllerBlock)) {c.panels.remove(pos);AirlockSavedState.get(level).setDirty();continue;}
            if(state.getValue(AirlockControllerBlock.INDICATOR)!=indicator)level.setBlock(pos,state.setValue(AirlockControllerBlock.INDICATOR,indicator),3);
        }
    }
    private static void snuff(ServerLevel level,Set<BlockPos> cells) {
        var walls=new HashSet<BlockPos>();for(var pos:cells)for(var dir:Direction.values())if(!cells.contains(pos.relative(dir)))walls.add(pos.relative(dir));
        VacuumFlames.extinguishRoom(level,cells,walls);
    }
    private static void invalidate(ServerLevel level,Chamber c,BlockPos hole) {
        boolean hadAir=c.gas.air()>0;c.gas.vent();RoomAirState.get(level).evacuate(c.cells);snuff(level,c.cells);
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
        for(var c:affected)if(layout(level,c)==Layout.INVALID)invalidate(level,c,pos);
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
        level.playSound(null,valve,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,0.8F,0.6F);
        return true;
    }
}
