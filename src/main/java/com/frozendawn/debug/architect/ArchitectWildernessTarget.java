package com.frozendawn.debug.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** Disposable lab villager. Guided mode replaces only the brain's destination selection. */
final class ArchitectWildernessTarget extends Villager {
    private Vec3 destination;
    private Vec3 detour;
    private boolean recoveryEnabled;
    private int ineffectiveTicks, attempts, detourTicks, collisionTicks;
    private final ArchitectWildernessProgress movement = new ArchitectWildernessProgress();
    private double bestDistance = Double.POSITIVE_INFINITY;
    private final java.util.Set<BlockPos> triedDetours = new java.util.HashSet<>();
    private String recoveryFailure;
    private java.util.function.BiConsumer<String,String> recoveryEvents = (kind,detail)->{};

    private boolean autonomous;
    private int repath;
    private double travelSpeed = 0.45;
    private boolean lastPathReachable;

    ArchitectWildernessTarget(ServerLevel level) {
        super(EntityType.VILLAGER, level);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
        setHealth(100);
        setPersistenceRequired();
        addTag("fd_lab");
        addTag("fd_wilderness");
        setCustomName(net.minecraft.network.chat.Component.literal("Wilderness pursuit target"));
        setCustomNameVisible(true);
    }

    void guideTo(Vec3 destination) {
        if (destination.equals(this.destination)) return;
        if(this.destination==null || ArchitectWildernessProgress.horizontalDistance(this.destination,destination)>0.1){
            detour=null;ineffectiveTicks=attempts=detourTicks=0;
            bestDistance=Double.POSITIVE_INFINITY;triedDetours.clear();recoveryFailure=null;
            movement.reset(position());collisionTicks=0;
        }
        this.destination = destination;
        repath = 0;
    }

    void enableRecovery(java.util.function.BiConsumer<String,String> events) {
        recoveryEnabled=true;recoveryEvents=events;
    }
    String recoveryFailure(){return recoveryFailure;}
    Vec3 recoveryGoal(){return detour;}

    private boolean recover() {
        if(recoveryFailure!=null){getNavigation().stop();return false;}
        long still=movement.update(position());
        collisionTicks=horizontalCollision?Math.min(60,collisionTicks+1):Math.max(0,collisionTicks-1);
        double distance=ArchitectWildernessProgress.horizontalDistance(position(),destination);
        if(distance<bestDistance-0.25){
            bestDistance=distance;
            if(detour==null)ineffectiveTicks=0;
        }else if(detour==null)ineffectiveTicks++;
        if(detour!=null){
            detourTicks++;
            if(distanceToSqr(detour)<1){
                recoveryEvents.accept("TARGET_DETOUR_REACHED","position="+position()+" resume="+destination);
                detour=null;ineffectiveTicks=0;repath=0;
            }else if(detourTicks>=120){
                recoveryEvents.accept("TARGET_DETOUR_BLOCKED","goal="+detour+" position="+position());
                detour=null;ineffectiveTicks=60;
            }
        }
        if(detour==null && ineffectiveTicks>=60 && onGround() && (still>=40||collisionTicks>=20)){
            recoveryEvents.accept("TARGET_PATH_INEFFECTIVE","ticks="+ineffectiveTicks+" collision="+horizontalCollision
                    +" pathReachable="+lastPathReachable+" position="+position()+" goal="+destination);
            if(attempts>=3){
                recoveryFailure="Target could not reach its waypoint after three bounded detour attempts";
                recoveryEvents.accept("TARGET_RECOVERY_FAILED",recoveryFailure);
                getNavigation().stop();return false;
            }
            attempts++;
            detour=chooseDetour();
            ineffectiveTicks=0;detourTicks=0;repath=0;
            recoveryEvents.accept("TARGET_DETOUR_ATTEMPT","attempt="+attempts+" selected="+detour+" original="+destination);
        }
        return true;
    }

    private Vec3 chooseDetour(){
        var level=(ServerLevel)level();
        Vec3 selected=null;double bestScore=Double.POSITIVE_INFINITY;
        // Fixed local search budget, independent of spectator position.
        for(int radius:new int[]{2,4,6})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0)continue;
            Vec3 base=new Vec3(Math.floor(getX())+dx*radius+0.5,Math.floor(getY())-1,
                    Math.floor(getZ())+dz*radius+0.5);
            if(!level.hasChunkAt(BlockPos.containing(base)))continue;
            Vec3 candidate=ArchitectWildernessWaypoint.surface(level,this,base);
            BlockPos cell=BlockPos.containing(candidate);
            if(triedDetours.contains(cell)||!level.hasChunkAt(cell))continue;
            var box=getBoundingBox().move(candidate.subtract(position()));
            if(!level.noCollision(this,box.deflate(1.0E-5))
                    ||level.noCollision(this,box.move(0,-0.08,0).deflate(1.0E-5)))continue;
            var state=level.getBlockState(cell.below());
            if(!level.getFluidState(cell).isEmpty()||!level.getFluidState(cell.below()).isEmpty()
                    ||state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    ||state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                    ||level.getBlockState(cell).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW))continue;
            var path=getNavigation().createPath(cell,0);
            if(path==null||!path.canReach()||!walkable(path))continue;
            double score=candidate.distanceTo(destination)+position().distanceTo(candidate)*0.5;
            if(score<bestScore){bestScore=score;selected=candidate;}
        }
        if(selected!=null)triedDetours.add(BlockPos.containing(selected));
        return selected;
    }

    private boolean walkable(net.minecraft.world.level.pathfinder.Path path){
        Vec3 previous=position();
        for(int i=0;i<path.getNodeCount();i++){
            var node=path.getNode(i);
            Vec3 feet=ArchitectWildernessWaypoint.surface((ServerLevel)level(),this,new Vec3(node.x+0.5,node.y-1,node.z+0.5));
            var box=getBoundingBox().move(feet.subtract(position()));
            if(!level().noCollision(this,box.deflate(1.0E-5))
                    ||level().noCollision(this,box.move(0,-0.08,0).deflate(1.0E-5))
                    ||feet.y-previous.y>1.25||previous.y-feet.y>3)return false;
            previous=feet;
        }
        return true;
    }

    void setAutonomous(boolean autonomous) { this.autonomous = autonomous; }
    void travelSpeed(double speed) { travelSpeed = speed; }
    boolean pathReachable() { return lastPathReachable; }

    @Override
    protected void customServerAiStep() {
        if (autonomous) {
            super.customServerAiStep();
            return;
        }
        // Mob's normal navigation, move/jump controls, collisions, gravity and damage still tick.
        // No teleports, velocity injection, or invulnerability during a run.
        if (destination == null || distanceToSqr(destination) < 1.0) {
            getNavigation().stop();
            return;
        }
        if(recoveryEnabled&&!recover())return;
        Vec3 goal=detour==null?destination:detour;
        if (--repath <= 0) {
            var path = getNavigation().createPath(BlockPos.containing(goal), 0);
            lastPathReachable = path != null && path.canReach();
            if (path != null) getNavigation().moveTo(path, travelSpeed);
            repath = 15;
        }
    }
}
