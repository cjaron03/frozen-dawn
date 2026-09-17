package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Sequential physical stations in the wilderness, with one continuously simulated target. */
final class ArchitectConstructionCourse {
    final int base;
    final List<Vec3> route;
    private final ArchitectWildernessTerrain terrain;
    private final List<BlockPos> bridge=new ArrayList<>(),wall=new ArrayList<>(),roof=new ArrayList<>();
    private Vec3 goal;
    private int stage;
    private long stageHits, stagePlaces, stageBreaks;
    private boolean gapOpened,wallClosed,ceilingLowered,jukeIssued,jukeMoved;
    private Vec3 jukeStart;
    private long jukeUntil;
    private boolean ascentPassed,gapPassed,breachPassed,roofPassed;

    ArchitectConstructionCourse(ArchitectWildernessTerrain terrain){
        this.terrain=terrain;base=terrain.floor(64,64).getY()+1;
        for(int x=40;x<=94;x++)for(int z=59;z<=69;z++)for(int y=-1;y<=15;y++){
            boolean shell=y==-1||y==15||x==40||x==94||z==59||z==69;
            boolean platform=x>=52&&!(x>=61&&x<=64)&&y<6;
            terrain.put(new BlockPos(x,base+y,z),(shell||platform?Blocks.BEDROCK:Blocks.AIR).defaultBlockState());
        }
        for(int x=61;x<=64;x++)for(int z=60;z<=68;z++){
            BlockPos p=new BlockPos(x,base+5,z);bridge.add(p);terrain.put(p,Blocks.OAK_PLANKS.defaultBlockState());
        }
        for(int x=77;x<=92;x++)for(int z=60;z<=68;z++)terrain.put(new BlockPos(x,base+9,z),Blocks.BEDROCK.defaultBlockState());
        for(int z=60;z<=68;z++)for(int y=6;y<=8;y++)wall.add(new BlockPos(78,base+y,z));
        for(int x=84;x<=86;x++)for(int z=60;z<=68;z++){
            BlockPos p=new BlockPos(x,base+8,z);terrain.put(p,Blocks.STONE.defaultBlockState());roof.add(p.below());
        }
        route=List.of(new Vec3(44.5,base,64.5),new Vec3(56.5,base+6,64.5),
                new Vec3(58.5,base+6,64.5),new Vec3(70.5,base+6,64.5),new Vec3(90.5,base+6,64.5));
        goal=route.get(2);
    }

    Vec3 goal(){return goal;}
    int stage(){return stage;}
    private boolean safe(ArchitectWildernessRun run,List<BlockPos> blocks){
        return blocks.stream().noneMatch(p->new AABB(p).inflate(0.05).intersects(run.architect.getBoundingBox())
                ||new AABB(p).inflate(0.05).intersects(run.target.getBoundingBox()));
    }

    void tick(ArchitectWildernessRun run,long tick,long hits,long places,long breaks){
        var actor=run.architect;var target=run.villager;
        target.travelSpeed(stage==0?0.45:0.90);
        boolean working=actor.isMiningBlock()||actor.hasQueuedScaffoldStep();
        if(stage==0&&!jukeIssued&&working&&target.getX()>55.5){
            jukeIssued=true;jukeStart=target.position();jukeUntil=tick+60;
            goal=new Vec3(54.5,base+6,64.5);target.guideTo(goal);
            run.event("CONSTRUCTION_TARGET_JUKE","during="+(actor.isMiningBlock()?"mining":"scaffolding")+" goal="+goal);
        }
        if(jukeIssued&&!jukeMoved&&working&&jukeStart!=null&&target.getX()<jukeStart.x-0.5){
            jukeMoved=true;run.event("CONSTRUCTION_JUKE_VERIFIED","target moved while construction action remained active");
        }
        if(stage==0&&jukeIssued&&tick>=jukeUntil){goal=route.get(2);target.guideTo(goal);jukeUntil=Long.MAX_VALUE;}
        if(stage==0&&actor.getX()>=52&&actor.getY()>=base+5.8&&places>0){
            ascentPassed=true;stage=1;stagePlaces=places;stageHits=hits;
            goal=route.get(3);target.guideTo(goal);run.event("CONSTRUCTION_ASCENT_PASSED","placements="+places+" verifiedHits="+hits);
        }
        if(stage==1&&!gapOpened&&target.getX()>66&&actor.getX()<61&&safe(run,bridge)){
            for(BlockPos p:bridge)terrain.level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
            gapOpened=true;stagePlaces=places;run.event("CONSTRUCTION_GAP_OPENED","four-block gap after target crossed");
        }
        if(stage==1&&actor.getX()>=66&&actor.getY()>=base+5.8){
            if(!gapOpened||places<=stagePlaces){run.finish("SCENARIO_INCOMPLETE","Construction gap crossed without verified scaffolding");return;}
            gapPassed=true;stage=2;stageHits=hits;stageBreaks=breaks;
            goal=route.get(4);target.guideTo(goal);run.event("CONSTRUCTION_GAP_PASSED","newPlacements="+(places-stagePlaces));
        }
        if(stage==2&&!wallClosed&&target.getX()>80&&actor.getX()<78&&safe(run,wall)){
            for(BlockPos p:wall)terrain.level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());
            wallClosed=true;stageBreaks=breaks;run.event("CONSTRUCTION_WALL_CLOSED","target clear; mineable three-block wall");
        }
        if(stage==2&&!ceilingLowered&&target.getX()>88&&actor.getX()<84&&safe(run,roof)){
            for(BlockPos p:roof)terrain.level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
            ceilingLowered=true;run.event("CONSTRUCTION_CEILING_LOWERED","target clear; one-block headroom requires excavation");
        }
        if(stage==2&&wallClosed&&actor.getX()>79&&breaks>stageBreaks&&wall.stream().anyMatch(p->terrain.level.getBlockState(p).isAir()))breachPassed=true;
        if(stage==2&&ceilingLowered&&actor.getX()>87&&roof.stream().anyMatch(p->terrain.level.getBlockState(p).isAir())&&breaks>stageBreaks)roofPassed=true;
        if(stage==2&&actor.getX()>87&&hits>stageHits){
            if(!breachPassed||!roofPassed||!jukeMoved){run.finish("SCENARIO_INCOMPLETE","Construction requirements missing: "+summary());return;}
            stage=3;run.event("CONSTRUCTION_PASSED",summary().toString());run.finish("PASSED","Verified ascent, scaffold crossing, wall/ceiling destruction, target juke and a verified catch");
        }
    }

    Map<String,Object> summary(){return Map.of("stage",stage,"baseY",base,"ascent",ascentPassed,"scaffoldGap",gapPassed,
            "wallBreach",breachPassed,"lowCeiling",roofPassed,"targetJuke",jukeMoved,"gapOpened",gapOpened,"wallClosed",wallClosed,"ceilingLowered",ceilingLowered);}
}
