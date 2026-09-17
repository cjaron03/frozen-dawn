package com.frozendawn.debug.architect;

import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Continuous encounters; neither actor is teleported or AI-reset after begin(). */
final class ArchitectWildernessRun {
    enum Scenario {
        SURFACE(3600), CAVES(6000), SHELTER_OPEN(6000), SHELTER_BREACH(6000),
        CONSTRUCTION(18000), WEATHER(18000), CHANGING(9600), ENDURANCE(36000), ROAM(12000), PLAYER(36000);
        final int duration;
        Scenario(int duration){this.duration=duration;}
        boolean exploratory(){return this==ROAM || this==PLAYER;}
        boolean dynamic(){return this==CHANGING || this==ENDURANCE;}
        String id(){return name().toLowerCase(Locale.ROOT);}
    }
    final UUID id=UUID.randomUUID();
    final ArchitectWildernessTerrain terrain;
    final ServerLevel level;
    final Scenario scenario;
    private final ArchitectWildernessWeather weather;
    final ArchitectEntity architect;
    final ArchitectWildernessTarget villager;
    LivingEntity target;
    private final List<Vec3> route=new ArrayList<>();
    private final List<Map<String,Object>> checkpoints=new ArrayList<>();
    private final List<Map<String,Object>> events=new ArrayList<>();
    private final Set<String> mutations=new LinkedHashSet<>();
    private final Set<BlockPos> actorCells=new HashSet<>();
    private final Set<BlockPos> progressCells=new HashSet<>();
    private final StringBuilder frames=new StringBuilder("tick\tactorX\tactorY\tactorZ\tyaw\theadYaw\tbodyYaw\taction\ttargetX\ttargetY\ttargetZ\ttargetHealth\ttargetPathReady\twaypoint\tcatchCount\tbreaks\tplaces\tserverMeanMs\n");
    private String status="PREPARED",reason="Ready; start with wilderness run";
    private long start,lastTick=-1,end,hitCount,lastMelee,lastBreaks,lastPlaces,lastProgress;
    private final ArchitectWildernessProgress targetProgress = new ArchitectWildernessProgress();
    private long actorStill,targetStill,maxActorStill,maxTargetStill,lastFrameTick,boostUntil;
    private long checkpointHits,checkpointTick;
    private double actorDistance,targetDistance,checkpointTargetDistance,checkpointActorDistance;
    private double turnWindow,actorWindowDistance,peakMeanMs,stageBestDistance=Double.POSITIVE_INFINITY;
    private float previousHealth,previousYaw,largestFall;
    private Vec3 previousActor,previousTarget;
    private int waypoint,lap,spinWarnings,closeLoops,completedCheckpoints;
    private boolean hitThisLeg,holdShelter,breachCompleted,actorEnteredCave,actorExitedCave;
    private Vec3 jukeGoal;
    private long jukeUntil,crystalStageTick;
    private int crystalStage=-1;
    private int shelterPatrol;
    private long shelterBreakBaseline;
    private final List<Double> tickCosts=new ArrayList<>();

    ArchitectWildernessRun(ArchitectWildernessTerrain terrain,Scenario scenario){
        this.terrain=terrain;this.level=terrain.level;this.scenario=scenario;
        weather=scenario==Scenario.WEATHER?new ArchitectWildernessWeather(level,terrain.seed):null;
        architect=ModEntities.ARCHITECT.get().create(level);
        if(architect==null)throw new IllegalStateException("Could not create wilderness Architect");
        villager=new ArchitectWildernessTarget(level);
        target=villager;
        if(!scenario.exploratory())villager.enableRecovery(this::event);
        architect.addTag("fd_lab");architect.addTag("fd_wilderness");architect.setPersistenceRequired();
        architect.decisionJournal().stop();
        architect.setCustomName(net.minecraft.network.chat.Component.literal("The Architect — wilderness lab"));
        architect.setCustomNameVisible(true);
        buildRoute();
        validateRoute();
        architect.setPos(route.get(0));
        villager.setPos(route.get(1));
        architect.setNoAi(true);villager.setNoAi(true);
        architect.setNoGravity(true);villager.setNoGravity(true);
        level.addFreshEntity(architect);level.addFreshEntity(villager);
    }

    private void buildRoute(){
        if(scenario==Scenario.CONSTRUCTION){route.addAll(Objects.requireNonNull(terrain.course).route);return;}
        route.addAll(terrain.surface);
        if(scenario==Scenario.CAVES || scenario==Scenario.ENDURANCE){
            int first=-1,last=-1;
            for(int i=0;i<route.size();i++){
                Vec3 p=route.get(i);
                if(p.x<24 && p.z<=87 && p.z>=53){if(first<0)first=i;last=i;}
            }
            if(first>=0){route.subList(first,last+1).clear();route.addAll(first,terrain.cave);}
        }
        if(scenario==Scenario.SHELTER_OPEN || scenario==Scenario.SHELTER_BREACH){
            route.clear();
            for(int z=38;z<=70;z+=4)route.add(Vec3.atBottomCenterOf(terrain.floor(108,z).above()));
            for(int z=66;z>=42;z-=4)route.add(Vec3.atBottomCenterOf(terrain.floor(108,z).above()));
        }
        if(route.size()<3)throw new IllegalStateException("Wilderness route has fewer than three destinations");
    }

    private void validateRoute(){
        // Preparation only: verify the target can physically travel every adjacent leg.
        // Failed target fixtures must never be presented as Architect failures.
        if(scenario.exploratory())return;
        for(int i=scenario==Scenario.CONSTRUCTION?1:0;i<(scenario==Scenario.CONSTRUCTION?route.size()-1:route.size());i++){
            villager.setPos(route.get(i));villager.setOnGround(true);villager.getNavigation().stop();
            var path=villager.getNavigation().createPath(BlockPos.containing(route.get((i+1)%route.size())),0);
            if(path==null||!path.canReach())throw new IllegalStateException("Target route is disconnected at waypoint "+i+": "+route.get(i)+" -> "+route.get((i+1)%route.size())+". Native terrain needs a trail adjustment; no Architect run has started.");
        }
    }

    void begin(ServerPlayer operator){
        if(!status.equals("PREPARED"))throw new IllegalStateException("Reset the wilderness before another run");
        if(level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)throw new IllegalStateException("Use /difficulty easy before starting; Peaceful removes the Architect");
        if(scenario==Scenario.PLAYER){
            if(operator==null || operator.isSpectator() || operator.isCreative())throw new IllegalStateException("Player pursuit requires Survival or Adventure mode in the wilderness dimension");
            target=operator;villager.discard();
        }
        architect.tickCount=villager.tickCount=0;
        architect.debugForceApproach(target);
        architect.getRandom().setSeed(terrain.seed);
        villager.getRandom().setSeed(terrain.seed);
        architect.setNoAi(false);architect.setNoGravity(false);
        villager.setNoAi(false);villager.setNoGravity(false);villager.setInvulnerable(false);
        villager.setAutonomous(scenario==Scenario.ROAM);
        architect.startDecisionRecording(id,terrain.seed,net.minecraft.world.level.block.Rotation.NONE);
        architect.decisionJournal().useEnduranceLabBuffer();
        start=level.getGameTime();lastProgress=0;
        waypoint=2;boostUntil=40;
        if(!scenario.exploratory())villager.guideTo(route.get(waypoint));
        previousActor=architect.position();previousTarget=target.position();targetProgress.reset(previousTarget);previousHealth=target.getHealth();previousYaw=architect.getYRot();
        status="RUNNING";reason="Pursuing moving target";
        if(weather!=null){weather.begin();event("WEATHER_BEGIN",weather.summary().toString());}
        event("BEGIN","scenario="+scenario.id()+" target="+target.getUUID()+" health="+target.getHealth());
    }

    boolean running(){return status.equals("RUNNING");}
    boolean finished(){return !running()&&!status.equals("PREPARED");}
    String status(){return status;}
    String reason(){return reason;}
    long elapsed(){return status.equals("PREPARED")?0:(running()?level.getGameTime():end)-start;}

    void tick(){
        if(!running()||lastTick==level.getGameTime())return;
        lastTick=level.getGameTime();long t=elapsed();
        if(villager.recoveryFailure()!=null){finish("TARGET_FAILED",villager.recoveryFailure()+"; Architect result is inconclusive");return;}
        if(!architect.isAlive()||architect.isRemoved()){finish("FAILED","ARCHITECT_LOST");return;}
        if(!target.isAlive()||target.isRemoved()||target.level()!=level){finish(scenario.exploratory()?"OBSERVED":"TARGET_FAILED","Target died, disconnected, or left the dimension");return;}
        if(!terrain.contains(architect.position())){finish("FAILED","Architect left the wilderness bounds");return;}
        if(!terrain.contains(target.position())){finish("TARGET_FAILED","Target left the wilderness bounds");return;}
        double actorStep=previousActor.distanceTo(architect.position()),targetStep=ArchitectWildernessProgress.horizontalDistance(previousTarget,target.position());
        actorDistance+=actorStep;targetDistance+=targetStep;actorWindowDistance+=actorStep;
        actorStill=actorStep<0.025?actorStill+1:0;targetStill=targetProgress.update(target.position());
        maxActorStill=Math.max(maxActorStill,actorStill);maxTargetStill=Math.max(maxTargetStill,targetStill);
        turnWindow+=Math.abs(Mth.wrapDegrees(architect.getYRot()-previousYaw));
        previousYaw=architect.getYRot();previousActor=architect.position();previousTarget=target.position();
        var counts=architect.decisionJournal().eventCounts();
        long melee=counts.getOrDefault("MELEE_HIT",0L),breaks=architect.successfulBreakCount(),places=counts.getOrDefault("SCAFFOLD_PLACE",0L);
        if(melee>lastMelee&&target.getHealth()<previousHealth&&target.getLastHurtByMob()==architect){
            hitCount++;lastProgress=t;
            if(!hitThisLeg){
                hitThisLeg=true;event("CATCH","waypoint="+waypoint+" damage="+(previousHealth-target.getHealth()));
                // Outlast the Architect's 60-tick slowness so the target can open
                // enough space for obstacles that require a clear gap between them.
                if(target==villager){villager.setHealth(villager.getMaxHealth());boostUntil=t+80;}
            }
            if(holdShelter && breaks>shelterBreakBaseline){
                holdShelter=false;breachCompleted=true;jukeGoal=null;
                clearDoor(terrain.shelterExit);clearDoor(terrain.shelterDoor);
                event("SHELTER_BREACHED","breaks="+(breaks-shelterBreakBaseline));
                villager.guideTo(route.get(waypoint));
            }
        }
        previousHealth=target.getHealth();lastMelee=melee;
        if(breaks>lastBreaks||places>lastPlaces)lastProgress=t;
        lastBreaks=breaks;lastPlaces=places;
        actorCells.add(architect.blockPosition());
        if(progressCells.add(architect.blockPosition())||architect.distanceToSqr(target)+0.25<stageBestDistance){
            lastProgress=t;stageBestDistance=Math.min(stageBestDistance,architect.distanceToSqr(target));
        }
        if(t%200==0){
            if(turnWindow>540 && actorWindowDistance<6){spinWarnings++;event("SPIN_WARNING","rotation="+turnWindow+" travel="+actorWindowDistance);}
            if(actorWindowDistance>8 && actorCells.size()<6){closeLoops++;event("CIRCLING_WARNING","travel="+actorWindowDistance+" distinctCells="+actorCells.size());}
            turnWindow=actorWindowDistance=0;actorCells.clear();
        }
        if(!scenario.exploratory()){
            if(scenario==Scenario.CONSTRUCTION){
                int stage=terrain.course.stage();
                terrain.course.tick(this,t,hitCount,places,breaks);
                if(stage!=terrain.course.stage()){waypoint=Math.min(4,terrain.course.stage()+2);hitThisLeg=false;progressCells.clear();stageBestDistance=Double.POSITIVE_INFINITY;}
            }else{guide(t);mutate(t);}
            if(weather!=null)weather.tick(t);
            if(!running())return;
            if(scenario==Scenario.SHELTER_OPEN && breaks>0){finish("FAILED","Open shelter control: unnecessary excavation despite the open entrances");return;}
            if(targetStill==160 && target.distanceToSqr(destination())>2)event("TARGET_STALL_WARNING","reachable="+villager.pathReachable()+" waypoint="+waypoint);
            if(targetStill>=400 && target.distanceToSqr(destination())>2){finish("TARGET_FAILED","Villager made no horizontal progress for 400 ticks; Architect result is inconclusive");return;}
            if(actorStill==160)event("ACTOR_STALL_WARNING","waypoint="+waypoint+" mining="+architect.isMiningBlock());
            if((actorStill>=600&&!architect.isMiningBlock())||t-lastProgress>=600){finish("FAILED","Architect made no useful pursuit progress for 600 ticks");return;}
            Vec3 actorPos=architect.position();
            if(actorPos.x>=70&&actorPos.x<=75&&actorPos.z>=96&&actorPos.z<=113
                    &&actorPos.y<terrain.floor((int)actorPos.x,104).getY()-2){finish("FAILED","Architect fell into the crossing ravine");return;}
            if(architect.isInLava()||architect.isOnFire()){finish("FAILED","Architect entered lava or fire");return;}
            if(spinWarnings>=3||closeLoops>=3){finish("FAILED","Repeated spinning/circling during pursuit");return;}
            if(breaks>32+scenario.duration/1200*24 || places>64+scenario.duration/1200*32){finish("FAILED","Runaway excavation or scaffolding budget exceeded");return;}
            if(scenario!=Scenario.CONSTRUCTION && t-checkpointTick>=1200 && !checkpoint(t))return;
        }
        largestFall=Math.max(largestFall,architect.fallDistance);
        if(architect.onGround()&&largestFall>0){if(largestFall>3)event("FALL","distance="+largestFall+" health="+architect.getHealth());largestFall=0;}
        if(t%20==0){double ms=level.getServer().getAverageTickTimeNanos()/1_000_000.0;tickCosts.add(ms);peakMeanMs=Math.max(peakMeanMs,ms);}
        if(t%5==0)sample(t);
        if(t>=scenario.duration){
            if(scenario.exploratory()){finish("OBSERVED","Exploratory capture completed; no scripted pass claim");return;}
            String missing=missingEvent();
            finish(missing==null?"PASSED":"SCENARIO_INCOMPLETE",missing==null?"Every minute met movement and catch checkpoints":missing);
        }
    }

    private Vec3 destination(){
        if(scenario==Scenario.CONSTRUCTION)return terrain.course.goal();
        if(jukeGoal!=null)return jukeGoal;
        if(holdShelter){int[][] points={{106,52},{110,52},{110,56},{106,56}};int[] p=points[shelterPatrol%4];return new Vec3(p[0]+0.5,terrain.shelterY+1,p[1]+0.5);}
        Vec3 original=route.get(waypoint);
        return weather==null?original:ArchitectWildernessWaypoint.surface(level,villager,original);
    }

    private void guide(long t){
        if(jukeGoal!=null && (t>=jukeUntil || target.distanceToSqr(jukeGoal)<2.25)){
            jukeGoal=null;
            villager.guideTo(destination());
            event("TARGET_JUKE_END","resume waypoint="+waypoint);
        }
        Vec3 goal=destination();
        if(jukeGoal==null && (weather==null?target.distanceToSqr(goal)<2.25:ArchitectWildernessWaypoint.arrived(target.position(),goal))){
            if(holdShelter)shelterPatrol++;
            else {waypoint=(waypoint+1)%route.size();if(waypoint==0)lap++;hitThisLeg=false;stageBestDistance=Double.POSITIVE_INFINITY;}
            goal=destination();
            progressCells.clear();
            event("TARGET_WAYPOINT","index="+waypoint+" lap="+lap+" goal="+goal);
            villager.guideTo(goal);
        }
        if(weather!=null)villager.guideTo(goal); // Refresh height when snowfall changes support.
        double gap=architect.distanceTo(target);
        villager.travelSpeed(gap>30?0.25:t<boostUntil?0.90:0.45);
    }

    private boolean checkpoint(long t){
        long hits=hitCount-checkpointHits;
        double travel=targetDistance-checkpointTargetDistance;
        Map<String,Object> point=new LinkedHashMap<>();point.put("tick",t);point.put("hits",hits);point.put("targetTravel",travel);
        point.put("actorTravel",actorDistance-checkpointActorDistance);point.put("waypoint",waypoint);point.put("lap",lap);
        point.put("breaks",lastBreaks);point.put("scaffolds",lastPlaces);checkpoints.add(point);
        if(travel<12){finish("TARGET_FAILED","Minute checkpoint: villager travelled less than 12 blocks");return false;}
        if(hits==0){finish("FAILED","Minute checkpoint: no verified hit on moving target");return false;}
        completedCheckpoints++;checkpointHits=hitCount;checkpointTick=t;
        checkpointTargetDistance=targetDistance;checkpointActorDistance=actorDistance;
        event("CHECKPOINT_PASSED","number="+completedCheckpoints+" hits="+hits+" targetTravel="+travel);
        return true;
    }

    private boolean safeToEdit(List<BlockPos> positions){
        for(BlockPos p:positions){AABB cell=new AABB(p).inflate(0.05);if(cell.intersects(architect.getBoundingBox())||cell.intersects(target.getBoundingBox()))return false;}
        return true;
    }
    private void clearDoor(BlockPos p){for(int y=0;y<3;y++)level.setBlockAndUpdate(p.above(y),Blocks.AIR.defaultBlockState());}
    private String cycleKey(String kind){return lap+":"+kind;}
    private boolean once(String kind){return mutations.contains(cycleKey(kind));}
    private void changed(String kind){mutations.add(cycleKey(kind));event("TERRAIN_CHANGED",kind+" lap="+lap);}

    private void mutate(long t){
        Vec3 a=architect.position(),v=target.position();
        if((scenario==Scenario.SHELTER_BREACH||scenario==Scenario.ENDURANCE)&&!once("shelter_sealed")
                && v.x>105&&v.x<112&&v.z>51&&v.z<57 && !(a.x>103&&a.x<114&&a.z>49&&a.z<60)){
            List<BlockPos> doors=List.of(terrain.shelterDoor,terrain.shelterDoor.above(),terrain.shelterExit,terrain.shelterExit.above());
            if(safeToEdit(doors)){
                for(BlockPos p:doors)level.setBlockAndUpdate(p,ModBlocks.FROZEN_PLANKS.get().defaultBlockState());
                holdShelter=true;jukeGoal=null;shelterBreakBaseline=lastBreaks;villager.guideTo(destination());changed("shelter_sealed");
            }
        }
        if(!scenario.dynamic())return;
        // Keep the actor in its current action while the same living target changes heading.
        boolean actionInProgress=architect.isMiningBlock()||architect.hasQueuedScaffoldStep();
        if(!once("target_juke") && jukeGoal==null && actionInProgress){
            Vec3 alternate;
            if(holdShelter){shelterPatrol++;alternate=destination();shelterPatrol--;}
            else alternate=route.get(Math.floorMod(waypoint-2,route.size()));
            var path=villager.getNavigation().createPath(BlockPos.containing(alternate),0);
            if(target.distanceToSqr(alternate)>4 && target.distanceToSqr(alternate)<24*24 && path!=null && path.canReach()){
                jukeGoal=alternate;jukeUntil=t+60;villager.guideTo(alternate);
                changed("target_juke");event("TARGET_JUKE","goal="+alternate+" mining="+architect.isMiningBlock());
            }
        }
        if(!once("gate_closed") && v.x>68 && a.x<66 && Math.abs(v.z-22)<5 && safeToEdit(List.of(terrain.gate))){
            level.setBlockAndUpdate(terrain.gate,Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,net.minecraft.core.Direction.EAST).setValue(FenceGateBlock.OPEN,false));
            changed("gate_closed");
        }
        if(!once("crystal_growth") && v.x>52&&a.x<50&&Math.abs(v.z-22)<4 && safeToEdit(List.of(terrain.growth))){
            if(crystalStage<0){crystalStage=0;crystalStageTick=t;}
        }
        if(crystalStage>=0 && t>=crystalStageTick && safeToEdit(List.of(terrain.growth))){
            level.setBlockAndUpdate(terrain.growth,ModBlocks.ACHERONITE_CRYSTAL.get().defaultBlockState().setValue(AcheroniteCrystalBlock.AGE,crystalStage));
            event("CRYSTAL_STAGE","age="+crystalStage);
            if(crystalStage==3){changed("crystal_growth");crystalStage=-1;}
            else{crystalStage++;crystalStageTick=t+10;}
        }
        // Earlier cycles use phase-5 snow. Later cycles retain it and add phase-6 deposits.
        if(!once("drift_or_deposit")&&v.x>(t<scenario.duration/2?83:91)&&a.x<(t<scenario.duration/2?80:88)&&Math.abs(v.z-22)<5){
            List<BlockPos> bank=new ArrayList<>();
            boolean snow=t<scenario.duration/2;
            for(int z=-1;z<=1;z++)for(int y=0;y<(snow?3:1);y++) {
                BlockPos p=(snow?terrain.drift:terrain.floor(88,22).above()).offset(0,y,z);
                // Late deposits replace only air or thin snow, as the production formation does.
                if(snow||level.getBlockState(p).isAir()||level.getBlockState(p).is(Blocks.SNOW))bank.add(p);
            }
            if(!bank.isEmpty()&&safeToEdit(bank)){
                for(BlockPos p:bank)level.setBlockAndUpdate(p,snow?Blocks.SNOW_BLOCK.defaultBlockState():ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState());
                changed("drift_or_deposit");event("ENVIRONMENT_STAGE",snow?"phase_5_snow":"phase_6_late_deposition");
            }
        }
        if(!once("bridge_removed")&&v.x<69&&a.x>75&&Math.abs(v.z-104)<4&&safeToEdit(terrain.bridgeDeck)){
            for(BlockPos p:terrain.bridgeDeck)level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
            changed("bridge_removed");
        }
        if(once("bridge_removed")&&!once("bridge_restored")&&a.x<69&&v.x<69&&safeToEdit(terrain.bridgeDeck)){
            for(BlockPos p:terrain.bridgeDeck)level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());
            changed("bridge_restored"); // Prepare the next lap only after both actors clear this crossing.
        }
        // An opening appearing during a real mining attempt exercises cancellation/replanning.
        if(!once("route_opened_mining")&&architect.isMiningBlock()&&once("gate_closed")
                &&a.distanceToSqr(Vec3.atCenterOf(terrain.gate))<12*12&&safeToEdit(List.of(terrain.gate))){
            level.setBlockAndUpdate(terrain.gate,Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,net.minecraft.core.Direction.EAST).setValue(FenceGateBlock.OPEN,true));
            changed("route_opened_mining");
        }
    }

    private String missingEvent(){
        if(scenario==Scenario.CONSTRUCTION)return "Construction stations unfinished: "+terrain.course.summary();
        if(weather!=null&&!weather.observedChange())return "No production snowfall mutations were observed";
        if((scenario==Scenario.SHELTER_BREACH||scenario==Scenario.ENDURANCE)&&!breachCompleted)return "Required shelter breach and verified catch did not occur";
        if(scenario.dynamic())for(String required:List.of("gate_closed","crystal_growth","drift_or_deposit","bridge_removed"))
            if(mutations.stream().noneMatch(e->e.endsWith(":"+required)))return "Progress trigger was never reached: "+required;
        if(scenario.dynamic())for(String stage:List.of("phase_5_snow","phase_6_late_deposition"))
            if(events.stream().noneMatch(e->stage.equals(e.get("detail"))))return "Environment stage was never reached: "+stage;
        if(scenario==Scenario.CAVES||scenario==Scenario.ENDURANCE){
            if(!actorEnteredCave||!actorExitedCave)return "Architect did not complete the cave entrance and exit";
            if(events.stream().noneMatch(e->String.valueOf(e.get("detail")).contains("cave_entered")))return "Target never traversed the cave";
        }
        if(scenario==Scenario.ENDURANCE&&mutations.stream().noneMatch(e->e.endsWith(":target_juke")))return "No target direction change occurred during mining or scaffolding";
        return null;
    }

    void event(String kind,String detail){
        events.add(Map.of("tick",elapsed(),"event",kind,"detail",detail));
        architect.recordDecision("WILDERNESS_"+kind,null,detail);
    }
    private void sample(long t){
        Vec3 a=architect.position(),v=target.position();
        if((scenario==Scenario.CAVES||scenario==Scenario.ENDURANCE)&&v.x>28&&v.x<33&&v.z>58&&v.z<82&&!once("cave_entered"))changed("cave_entered");
        if(scenario==Scenario.CAVES||scenario==Scenario.ENDURANCE){
            if(!actorEnteredCave && a.x>28 && a.x<33 && a.z>58 && a.z<82
                    && terrain.cave.stream().anyMatch(p->p.distanceToSqr(a)<4)){
                actorEnteredCave=true;event("ACTOR_CAVE_ENTERED","position="+a);
            }
            if(actorEnteredCave&&!actorExitedCave&&a.distanceToSqr(terrain.cave.getLast())<9){
                actorExitedCave=true;event("ACTOR_CAVE_EXITED","position="+a);
            }
        }
        frames.append(String.format(Locale.ROOT,"%d\t%.4f\t%.4f\t%.4f\t%.2f\t%.2f\t%.2f\t%s\t%.4f\t%.4f\t%.4f\t%.2f\t%b\t%d\t%d\t%d\t%d\t%.3f%n",
                t,a.x,a.y,a.z,architect.getYRot(),architect.yHeadRot,architect.yBodyRot,ArchitectEntity.actionName(architect.getBrainAction()),
                v.x,v.y,v.z,target.getHealth(),villager.pathReachable(),waypoint,hitCount,lastBreaks,lastPlaces,level.getServer().getAverageTickTimeNanos()/1_000_000.0));
        lastFrameTick=t;
    }

    void finish(String status,String reason){
        if(finished())return;
        end=level.getGameTime();this.status=status;this.reason=reason;
        if(weather!=null){weather.close();event("WEATHER_END",weather.summary().toString());}
        if(architect.decisionJournal().enabled()){
            if(lastFrameTick!=elapsed())sample(elapsed());
            event("END",status+": "+reason);
            ArchitectVisualDebug.capture(architect, true, debugContext());
            architect.decisionJournal().finish(end,status,reason);
        }
        architect.setNoAi(true);architect.getNavigation().stop();architect.setDeltaMovement(Vec3.ZERO);
        if(target==villager){villager.setNoAi(true);villager.getNavigation().stop();villager.setDeltaMovement(Vec3.ZERO);}
    }

    ArchitectDebugSnapshot.Lab debugContext() {
        Vec3 goal = scenario.exploratory() ? null : villager.recoveryGoal()!=null?villager.recoveryGoal():destination();
        return new ArchitectDebugSnapshot.Lab("wilderness_" + scenario.id(), status, reason, elapsed(), scenario.duration,
                goal == null ? null : new ArchitectDebugSnapshot.Point(goal.x, goal.y, goal.z),
                scenario.exploratory() ? -1 : waypoint, lap, hitCount, actorStill, targetStill);
    }

    Path export() throws IOException {
        if(status.equals("PREPARED"))throw new IllegalStateException("Start the wilderness run before exporting");
        ArchitectVisualDebug.capture(architect, true, debugContext());
        Map<String,Object> context=new LinkedHashMap<>();
        if(weather!=null)context.put("weather",weather.summary());
        if(scenario==Scenario.CONSTRUCTION)context.put("construction",terrain.course.summary());
        context.put("scenario","wilderness_"+scenario.id());context.put("recipeVersion",ArchitectWildernessTerrain.RECIPE);
        context.put("worldSeed",level.getSeed());context.put("terrainSeed",terrain.seed);context.put("preparedTerrainSha256",terrain.hash());
        context.put("difficulty",level.getDifficulty().name());
        context.put("randomTickSpeed",level.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING));
        context.put("mobSpawning",level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING));
        context.put("fallDamage",level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_FALL_DAMAGE));
        context.put("nativeChunkHashes",terrain.nativeHashes());context.put("dimension",level.dimension().location().toString());
        context.put("targetMode",scenario==Scenario.PLAYER?"PLAYER":scenario==Scenario.ROAM?"AUTONOMOUS":"GUIDED_NAVIGATION");
        context.put("actorUuid",architect.getUUID().toString());context.put("targetUuid",target.getUUID().toString());
        context.put("durationTicks",scenario.duration);context.put("elapsedTicks",elapsed());context.put("checkpoints",checkpoints);
        context.put("events",events);context.put("route",route.stream().map(p->List.of(p.x,p.y,p.z)).toList());
        context.put("verifiedHits",hitCount);context.put("targetHealth",target.getHealth());context.put("actorHealth",architect.getHealth());
        context.put("actorTravel",actorDistance);context.put("targetTravel",targetDistance);context.put("maxActorStillTicks",maxActorStill);context.put("maxTargetStillTicks",maxTargetStill);
        context.put("spinWarnings",spinWarnings);context.put("circlingWarnings",closeLoops);
        context.put("actorEnteredCave",actorEnteredCave);context.put("actorExitedCave",actorExitedCave);context.put("shelterBreachCompleted",breachCompleted);
        context.put("peakServer100TickMeanMs",peakMeanMs);
        var sorted=new ArrayList<>(tickCosts);Collections.sort(sorted);
        context.put("p95Server100TickMeanMs",sorted.isEmpty()?0:sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*0.95))));
        context.put("frameSha256",ArchitectDebugReports.sha256(frames.toString().getBytes(StandardCharsets.UTF_8)));
        String readme="# Wilderness "+scenario.id()+"\n\n"+status+": "+reason+"\n\nSeed: "+terrain.seed+". Recipe: "+ArchitectWildernessTerrain.RECIPE+".\n\nSee summary.json for every checkpoint and world change; movement.tsv contains both actors and rotation samples. TARGET_FAILED and SCENARIO_INCOMPLETE are inconclusive Architect results. OBSERVED is an ungraded exploratory capture. Tick costs describe the whole server, not this entity alone.\n";
        Map<String,String> files=new LinkedHashMap<>();files.put("movement.tsv",frames.toString());files.put("README.md",readme);
        if(weather!=null)files.put("weather.tsv",weather.table());
        return ArchitectDebugReports.export(ArchitectLab.reports(level).resolve("wilderness"),architect.decisionJournal(),context,files);
    }

    void dispose(){if(weather!=null)weather.close();architect.discardLabActor();villager.discard();}
    Vec3 observation(){return route.get(0).add(0,8,0);}
}
