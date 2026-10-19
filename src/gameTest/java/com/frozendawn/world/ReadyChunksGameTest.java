package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReadyChunksGameTest {
    @BeforeBatch(batch="ready_chunks") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}

    /**
     * Epoch catch-up stalled the server because "loaded only" temperature reads trusted Level.isLoaded, which is true
     * for chunks that are still generating, and then waited for them. Request far, never-generated chunks; for one
     * that is requested but not yet ready, a loaded-only temperature read inside it must return without finishing it.
     */
    @GameTest(template=GameTestTemplates.EMPTY,batch="ready_chunks",timeoutTicks=200)
    public static void loadedOnlyTemperatureNeverWaitsForAGeneratingChunk(GameTestHelper h){
        var l=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);
        var candidates=new ChunkPos[6];
        for(int i=0;i<candidates.length;i++){candidates[i]=new ChunkPos((origin.getX()>>4)+4000+i*97,(origin.getZ()>>4)-4000-i*61);
            h.assertTrue(!l.isLoaded(candidates[i].getMiddleBlockPosition(64)),"Candidate starts unrequested");
            l.getChunkSource().addRegionTicket(TicketType.FORCED,candidates[i],0,candidates[i]);}
        h.runAfterDelay(1,()->{
            try{
                ChunkPos generating=null;
                for(var c:candidates)if(l.isLoaded(c.getMiddleBlockPosition(64))&&!ReadyChunks.ready(l,c.x,c.z)){generating=c;break;}
                h.assertTrue(generating!=null,"A requested chunk is still generating one tick later (precondition)");
                var pos=generating.getMiddleBlockPosition(70);
                h.assertTrue(l.isLoaded(pos)&&!ReadyChunks.ready(l,pos),"isLoaded trusts a generating chunk; ReadyChunks does not");
                var apocalypse=com.frozendawn.data.ApocalypseState.get(l.getServer());
                TemperatureManager.getLoadedTemperatureAt(l,pos,apocalypse.getCurrentDay(),apocalypse.getTotalDays());
                h.assertTrue(!ReadyChunks.ready(l,generating.x,generating.z),"The loaded-only read returned without waiting for or generating the chunk");
            }finally{for(var c:candidates)l.getChunkSource().removeRegionTicket(TicketType.FORCED,c,0,c);}
            h.succeed();
        });
    }
}
