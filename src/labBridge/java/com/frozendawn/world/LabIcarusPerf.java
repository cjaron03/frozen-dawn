package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.power.IcarusBlast;
import com.frozendawn.power.IcarusPhysics;
import java.nio.file.*;
import java.util.Set;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One full-load (64 capsule) Icarus detonation at the origin of a disposable world, timed from the blast until no loaded
 * chunk is left to carve: ticks, the worst server tick and the peak heap. Excluded from release jars. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class LabIcarusPerf {
    static final String WORLD = "Icarus Perf";
    /** Ticks with nothing loaded left to carve before the run counts as finished. */
    private static final int SETTLE = 40;
    private static Run run;
    private static long tickStart, craterStart;

    private static final class Run {
        final ServerLevel level; final Path receipt; final long startTick; final int total;
        long worstNanos, worstTick, worstCrater, worstCraterTick, peakHeap; int ticks, idle;
        Run(ServerLevel level, Path receipt, int total) { this.level = level; this.receipt = receipt; this.total = total; startTick = level.getGameTime(); }
    }

    @SubscribeEvent public static void register(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("fdlab").requires(s->s.hasPermission(2)).then(Commands.literal("icarus_full_blast").executes(c->blast(c.getSource()))));}

    private static int blast(CommandSourceStack s) {
        if(FMLEnvironment.production||run!=null||!s.getServer().getWorldData().getLevelName().equals(WORLD)||!(s.getEntity() instanceof ServerPlayer p)||!p.isCreative())return 0;
        var receipt=s.getServer().getWorldPath(LevelResource.ROOT).resolve("icarus-perf.json");if(Files.exists(receipt))return 0;
        try {
            Files.writeString(receipt,"{\"state\":\"started\"}",StandardOpenOption.CREATE_NEW);
            var l=s.getLevel();var core=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,BlockPos.ZERO);
            double radius=IcarusBlast.radius(IcarusPhysics.MAX_CAPSULES*IcarusPhysics.CAPSULE);
            run=new Run(l,receipt,chunksInReach(radius));
            long before=System.nanoTime();
            IcarusDetonation.detonate(l,core,radius,Set.of());
            long detonateNanos=System.nanoTime()-before;
            FrozenDawn.LOGGER.info("ICARUS PERF: detonated r={} at {}; detonate() {} ms; {} chunks in reach",radius,core.toShortString(),detonateNanos/1e6,run.total);
            s.sendSuccess(()->Component.literal("ICARUS PERF: full-load blast at "+core.toShortString()+"; timing the crater"),false);return 1;
        } catch(Exception e){FrozenDawn.LOGGER.error("Icarus perf blast failed; preserve this attempt",e);run=null;return 0;}
    }

    private static int chunksInReach(double radius) {
        int reach=(int)Math.ceil(IcarusBlast.meltRadius(radius)/16)+1,n=0;
        for(int x=-reach;x<=reach;x++)for(int z=-reach;z<=reach;z++)if(Math.hypot(x*16+8,z*16+8)<=IcarusBlast.meltRadius(radius)+12)n++;
        return n;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void pre(ServerTickEvent.Pre e){tickStart=System.nanoTime();}

    /** The crater carves in the level's normal-priority post tick; bracketing that phase isolates it from terrain generation
     * and other mods' chunk work, which a fresh world's ticks also carry. Other normal-priority level post handlers fall
     * inside the bracket, so this is an upper bound on the crater's own cost. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void craterPre(LevelTickEvent.Post e){var r=run;if(r!=null&&e.getLevel()==r.level)craterStart=System.nanoTime();}

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void craterPost(LevelTickEvent.Post e) {
        var r=run;if(r==null||e.getLevel()!=r.level)return;
        long nanos=System.nanoTime()-craterStart;
        if(nanos>r.worstCrater){r.worstCrater=nanos;r.worstCraterTick=r.ticks+1;}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(ServerTickEvent.Post e) {
        var r=run;if(r==null)return;
        long nanos=System.nanoTime()-tickStart;r.ticks++;
        if(nanos>r.worstNanos){r.worstNanos=nanos;r.worstTick=r.ticks;}
        var rt=Runtime.getRuntime();r.peakHeap=Math.max(r.peakHeap,rt.totalMemory()-rt.freeMemory());
        int loaded=0,pending=0;
        for(var job:IcarusCraterData.get(r.level).jobs())for(long chunk:job.pending()){pending++;if(IcarusCrater.carvable(r.level,ChunkPos.getX(chunk),ChunkPos.getZ(chunk)))loaded++;}
        if(r.ticks%100==0)FrozenDawn.LOGGER.info("ICARUS PERF: tick {} pending {} loaded {} worst {} ms, crater phase {} ms",r.ticks,pending,loaded,r.worstNanos/1e6,r.worstCrater/1e6);
        r.idle=loaded==0?r.idle+1:0;
        if(r.idle<SETTLE)return;
        run=null;
        int ticks=r.ticks-SETTLE;
        var report="{\"state\":\"finished\",\"radius\":"+IcarusBlast.radius(IcarusPhysics.MAX_CAPSULES*IcarusPhysics.CAPSULE)+",\"ticksToCarveLoaded\":"+ticks
                +",\"chunksInReach\":"+r.total+",\"pendingUnloaded\":"+pending+",\"worstTickMs\":"+r.worstNanos/1e6+",\"worstTickAt\":"+r.worstTick+",\"worstCraterPhaseMs\":"+r.worstCrater/1e6+",\"worstCraterPhaseAt\":"+r.worstCraterTick
                +",\"peakHeapMb\":"+r.peakHeap/(1024*1024)+",\"maxHeapMb\":"+rt.maxMemory()/(1024*1024)+",\"budgetMs\":"+IcarusCrater.BUDGET_NANOS/1e6+"}";
        try{Files.writeString(r.receipt,report);}catch(Exception x){FrozenDawn.LOGGER.error("Icarus perf receipt failed",x);}
        FrozenDawn.LOGGER.info("ICARUS PERF RESULT: {}",report);
    }
}
