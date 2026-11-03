package com.frozendawn.cyclone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Shared wind policy. Horizontal motion only; weather never applies damage or vertical lift. */
public final class CycloneWind {
    private CycloneWind() {}
    public static Vec3 direction(float heading){return new Vec3(Math.cos(heading),0,Math.sin(heading));}
    /** A smooth deterministic pulse; each seed chooses a period of 60..140 simulation ticks. */
    public static float gust(long tick,long seed) {
        int period=60+(int)Math.floorMod(seed,81L);
        double phase=Math.floorMod(seed>>>8,period);
        return (float)(1+.25*Math.sin((Math.floorMod(tick,period)+phase)*Math.PI*2/period));
    }
    public static Vec3 resist(Vec3 motion,CycloneSample sample,CycloneSeverity severity,float heading,float shelter) {
        if(sample.intensity()<=0||shelter<=0)return motion;
        var direction=direction(heading);double along=motion.dot(direction);
        if(along>=0)return motion;
        double weight=Math.clamp(sample.intensity()*shelter,0,1);
        return motion.add(direction.scale(-along*(1-severity.intoWindSpeed())*weight));
    }
    public static Vec3 drift(CycloneSample sample,CycloneSeverity severity,float heading,boolean crouching,float shelter,float gust,float scale) {
        if(crouching||sample.intensity()<=0||shelter<=0||scale<=0)return Vec3.ZERO;
        return direction(heading).scale(.04*sample.intensity()*severity.windStrength*Math.clamp(shelter,0,1)
                *Math.clamp(gust,.75f,1.25f)*Math.clamp(scale,0,2));
    }
    public static Vec3 adjust(Vec3 motion,CycloneSample sample,CycloneSeverity severity,float heading,boolean crouching,float shelter,float gust,float scale) {
        var drift=drift(sample,severity,heading,crouching,shelter,gust,scale);
        var resisted=resist(motion,sample,severity,heading,shelter);
        return drift==Vec3.ZERO?resisted:resisted.add(drift);
    }
    public static boolean blocksSprint(Vec3 input,CycloneSample sample,CycloneSeverity severity,float heading,float shelter) {
        return !severity.sprintIntoWind()&&sample.intensity()>.5f&&shelter>0&&input.dot(direction(heading))<-.001;
    }
    /** Client/server roof geometry agrees even through glass; every query stays in loaded chunks. */
    public static float exposure(Level level,Entity entity,float heading) {
        if(level.dimension()!=Level.OVERWORLD||entity.isInWaterOrBubble()||entity.isInLava())return 0;
        float surface=CycloneThermal.surfaceWeight(entity.getY());if(surface<=0)return 0;
        var head=BlockPos.containing(entity.getEyePosition());
        if(!level.hasChunkAt(head)||!level.canSeeSky(head))return 0;
        if(level.getHeight(Heightmap.Types.MOTION_BLOCKING,head.getX(),head.getZ())>head.getY())return 0;
        if(level instanceof ServerLevel server&&com.frozendawn.world.RoomAtmosphere.cachedSealAt(server,head)
                ==com.frozendawn.world.RoomAtmosphere.Seal.SEALED)return 0;
        var wind=direction(heading);
        return surface*CycloneThermal.windbreakFactor(level,head,wind.x,wind.z);
    }
    public static boolean eligiblePlayer(Player player) {
        return player.isAlive()&&!player.isCreative()&&!player.isSpectator()&&!player.isPassenger()
                &&!player.isFallFlying()&&!player.getAbilities().flying&&!player.isSleeping();
    }
    /** Vanilla yaw convention, shared with native math checks and the client input hook. */
    public static Vec3 worldInput(float left,float forward,float yaw) {
        double radians=Math.toRadians(yaw),s=Math.sin(radians),c=Math.cos(radians);
        return new Vec3(left*c-forward*s,0,forward*c+left*s);
    }
}
