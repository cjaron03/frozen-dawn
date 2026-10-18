package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.*;
import com.frozendawn.world.ReadyChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerChunkReadinessGameTest {
    @BeforeBatch(batch="power_ready_chunks") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}

    /**
     * Owner-observed deadlock (2026-10-17): a chunk-load event re-checked coolant links and read a radiator in a chunk
     * that was still loading, so the server thread waited forever inside that load. Link checks against a requested
     * but not-yet-ready chunk must return at once, treat it as unloaded, and leave it unfinished.
     */
    @GameTest(template=GameTestTemplates.EMPTY,batch="power_ready_chunks",timeoutTicks=200)
    public static void linkChecksNeverWaitOnALoadingChunk(GameTestHelper h){
        var l=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);
        var candidates=new ChunkPos[6];
        for(int i=0;i<candidates.length;i++){candidates[i]=new ChunkPos((origin.getX()>>4)-5000-i*89,(origin.getZ()>>4)+5000+i*53);
            l.getChunkSource().addRegionTicket(TicketType.FORCED,candidates[i],0,candidates[i]);}
        h.runAfterDelay(1,()->{
            try{
                ChunkPos loading=null;
                for(var c:candidates)if(l.isLoaded(c.getMiddleBlockPosition(64))&&!ReadyChunks.ready(l,c.x,c.z)){loading=c;break;}
                h.assertTrue(loading!=null,"A requested chunk is still loading one tick later (precondition)");
                var far=loading.getMiddleBlockPosition(70);var here=h.absolutePos(new BlockPos(1,2,1));
                h.assertTrue(!PowerNetworks.coolantLink(l,new PowerLink(here,far)),"Coolant check treats a loading endpoint as not a live coolant link");
                h.assertTrue(PowerLinkRules.validate(l,here,far)==PowerLinkRules.Result.UNLOADED,"Validation reports UNLOADED instead of waiting");
                h.assertTrue(!ReadyChunks.ready(l,loading.x,loading.z),"Neither check waited for or finished the chunk");
            }finally{for(var c:candidates)l.getChunkSource().removeRegionTicket(TicketType.FORCED,c,0,c);}
            h.succeed();
        });
    }
}
