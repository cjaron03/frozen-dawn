package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.item.BatteryLanternItem;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Shared bounded Lantern/headlamp sources; preserves terrain without loading chunks or offline drain. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class HeldLanternLights {
    private record Frame(long tick,Set<BlockPos> positions){}
    private static final Map<ServerLevel,Frame> FRAMES=new WeakHashMap<>();
    private HeldLanternLights(){}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        for(var level:event.getServer().getAllLevels())tickLevel(level,level.players());
    }
    public static void tickLevel(ServerLevel level,List<ServerPlayer> players){
        var desired=new HashMap<BlockPos,Integer>();
        for(var player:players){
            if(player.level()!=level||!player.isAlive()||player.isSpectator()||!BatteryLanternItem.payHeldTick(player.getOffhandItem()))continue;
            for(var pos:List.of(BlockPos.containing(player.getEyePosition()),player.blockPosition())){
                if(!ReadyChunks.ready(level,pos))continue;
                var state=level.getBlockState(pos);
                if(state.is(ModBlocks.HELD_LANTERN_LIGHT)||state.isAir()||(state.is(Blocks.WATER)&&state.getFluidState().isSource())){
                    desired.merge(pos.immutable(),14,Math::max);break;
                }
                // Occupied blocks and vanilla/alarm lights remain untouched; try the feet instead.
            }
        }
        HeadlampPrototype.collect(level,players,desired);
        SuitHeadlamp.collect(level,players,desired);
        var previous=FRAMES.put(level,new Frame(level.getGameTime(),Set.copyOf(desired.keySet())));
        // Establish the new footprint before removing old sources to avoid a dark handover.
        for(var pos:desired.keySet()){
            var state=level.getBlockState(pos);
            if(state.is(ModBlocks.HELD_LANTERN_LIGHT)){
                if(state.getValue(LightBlock.LEVEL)!=desired.get(pos))level.setBlock(pos,state.setValue(LightBlock.LEVEL,desired.get(pos)),3);
                continue;
            }
            if(!state.isAir()&&!(state.is(Blocks.WATER)&&state.getFluidState().isSource()))continue;
            boolean water=state.getFluidState().getType()==Fluids.WATER;
            level.setBlock(pos,ModBlocks.HELD_LANTERN_LIGHT.get().defaultBlockState().setValue(LightBlock.WATERLOGGED,water).setValue(LightBlock.LEVEL,desired.get(pos)),3);
        }
        if(previous!=null)for(var pos:previous.positions())if(!desired.containsKey(pos))remove(level,pos);
    }
    public static boolean retains(ServerLevel level,BlockPos pos){
        var frame=FRAMES.get(level);
        return frame!=null&&level.getGameTime()-frame.tick()<=2&&frame.positions().contains(pos);
    }
    public static void remove(ServerLevel level,BlockPos pos){
        if(!ReadyChunks.ready(level,pos))return;
        var state=level.getBlockState(pos);
        if(state.is(ModBlocks.HELD_LANTERN_LIGHT))level.setBlock(pos,state.getFluidState().createLegacyBlock(),3);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){
        for(var level:event.getServer().getAllLevels()){
            HeadlampPrototype.clear(level);
            var frame=FRAMES.remove(level);
            if(frame!=null)for(var pos:frame.positions())remove(level,pos);
        }
    }
}
