package com.frozendawn.cyclone;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.homo.*;
import com.frozendawn.network.CycloneSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CycloneController {
    private CycloneController() {}
    public static long phase5Start(int days){return (long)Math.ceil(days*.46)*24000;}
    public static long phase6Start(int days){return (long)Math.ceil(days*.60)*24000;}
    public static boolean suppressed(ServerLevel level) {
        return PostMaeveWorldState.isErased(level)||HearthMasterArchitectWeatherManager.isMasterFightActive(level);
    }
    public static void tick(ServerLevel level,ApocalypseState apocalypse) {
        var data=CycloneSavedData.get(level.getServer());long now=level.getGameTime();
        long p5=phase5Start(apocalypse.getTotalDays()),p6=phase6Start(apocalypse.getTotalDays());
        var players=level.players().stream().filter(p->!p.isCreative()&&!p.isSpectator()).toList();
        boolean fight=HearthMasterArchitectWeatherManager.isMasterFightActive(level);
        var action=CycloneTransitions.decide(apocalypse.getPhase(),PostMaeveWorldState.isErased(level),
                FrozenDawnConfig.CYCLONES_ENABLED.get(),data.plannedFor()==p5,
                data.due(apocalypse.getApocalypseTicks(),p6-CyclonePlanner.DAY),data.active(),now,!players.isEmpty(),fight);
        switch(action) {
            case PLAN -> data.setPlan(p5,CyclonePlanner.plan(p5,p6,level.getSeed()^p5,
                    FrozenDawnConfig.CYCLONE_MAX_PER_PHASE.get(),FrozenDawnConfig.CYCLONE_CATASTROPHIC_ENABLED.get(),
                    FrozenDawnConfig.CYCLONE_CATASTROPHIC_CHANCE.get()));
            case START -> {
                var player=players.get(level.random.nextInt(players.size()));
                var severity=CycloneTransitions.effectiveSeverity(data.plan().get(data.nextIndex()).severity(),FrozenDawnConfig.CYCLONE_CATASTROPHIC_ENABLED.get());
                data.startPlanned(CycloneStorm.roll(level.random.nextLong(),severity,player.getX(),player.getZ(),now,
                        player.getUUID(),level.random.nextFloat()*(float)(Math.PI*2)),apocalypse.getApocalypseTicks());
            }
            case COLLAPSE -> data.replaceActive(data.active().orElseThrow().withCollapse(now));
            case END -> data.end();
            default -> { }
        }
        if(action!=CycloneTransitions.Action.NONE)FrozenDawn.LOGGER.info("Cyclone {}: {}",action,describe(level));
        if(action!=CycloneTransitions.Action.NONE || now%20==0)sync(level);
        if(now%20==0 && !suppressed(level))data.active().ifPresent(storm->{
            for(var player:level.players()) {
                var s=CycloneField.sample(storm,player.getX(),player.getZ(),now);
                if(s.stage()==CycloneStage.BOMBOGENESIS && CycloneField.localTick(storm,player.getX(),player.getZ(),now)>=CycloneForecast.earPopTick(storm)
                        && data.warnOnce(player.getUUID()))player.displayClientMessage(Component.translatable("message.frozendawn.cyclone.ears_popped"),true);
            }
        });
    }
    public static CycloneSample sampleAt(ServerLevel level,double x,double z) {
        if(level.dimension()!=Level.OVERWORLD || suppressed(level))return CycloneSample.NONE;
        return CycloneSavedData.get(level.getServer()).active().map(s->CycloneConfigValues.sample(s,x,z,level.getGameTime())).orElse(CycloneSample.NONE);
    }
    public static CycloneForecast.Reading forecast(ServerLevel level,double x,double z) {
        if(level.dimension()!=Level.OVERWORLD || suppressed(level))return CycloneForecast.NONE;
        return CycloneSavedData.get(level.getServer()).active().map(s->CycloneForecast.read(s,x,z,level.getGameTime())).orElse(CycloneForecast.NONE);
    }
    public static void forceStart(ServerLevel level,CycloneSeverity severity,ServerPlayer target,float bearing) {
        var a=ApocalypseState.get(level.getServer());
        if(a.getPhase()!=5 || PostMaeveWorldState.isErased(level))throw new IllegalArgumentException("Cyclones require an unerased Phase 5 world");
        if(target.level().dimension()!=Level.OVERWORLD)throw new IllegalArgumentException("Target must be in the Overworld");
        CycloneSavedData.get(level.getServer()).startForced(CycloneStorm.roll(level.random.nextLong(),severity,target.getX(),target.getZ(),level.getGameTime(),target.getUUID(),bearing));
        sync(level);FrozenDawn.LOGGER.info("Cyclone forced: {}",describe(level));
    }
    public static void forceStage(ServerLevel level,CycloneStage stage) { forceStage(level,stage,0); }
    public static void forceStage(ServerLevel level,CycloneStage stage,float progress) {
        var d=CycloneSavedData.get(level.getServer());if(stage==CycloneStage.NONE){d.end();sync(level);return;}
        var s=d.active().orElseThrow(()->new IllegalArgumentException("No active cyclone"));
        d.replaceActive(s.atStage(stage,level.getGameTime(),progress));sync(level);
        FrozenDawn.LOGGER.info("Cyclone stage forced to {}: {}",stage,describe(level));
    }
    public static void end(ServerLevel level){CycloneSavedData.get(level.getServer()).end();sync(level);}
    private static CycloneSyncPayload payload(ServerLevel level) {
        return new CycloneSyncPayload(CycloneSavedData.get(level.getServer()).active(),suppressed(level),level.getGameTime(),FrozenDawnConfig.CYCLONE_PUSH_SCALE.get().floatValue(),CycloneConfigValues.visibility());
    }
    public static void sync(ServerLevel level){PacketDistributor.sendToAllPlayers(payload(level));}
    public static void syncTo(ServerPlayer p){PacketDistributor.sendToPlayer(p,payload(p.getServer().overworld()));}
    public static String describe(ServerLevel level) {
        var d=CycloneSavedData.get(level.getServer());var a=d.active();
        return "time="+level.getGameTime()+" next="+d.nextIndex()+"/"+d.plan().size()+" suppressed="+suppressed(level)
                +a.map(s->" seed="+s.seed()+" severity="+s.severity()+" target="+s.target()+" at="+s.targetX()+","+s.targetZ()
                +" start="+s.startGameTime()+" end="+s.endGameTime()+" stage="+CycloneField.sample(s,s.targetX(),s.targetZ(),level.getGameTime()).stage()).orElse(" active=none");
    }
}
