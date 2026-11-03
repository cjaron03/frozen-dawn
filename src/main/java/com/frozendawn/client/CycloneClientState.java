package com.frozendawn.client;
import com.frozendawn.FrozenDawn;
import com.frozendawn.cyclone.*;
import com.frozendawn.network.CycloneSyncPayload;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
@EventBusSubscriber(modid=FrozenDawn.MOD_ID,value=Dist.CLIENT)
public final class CycloneClientState {
    private static Optional<CycloneStorm> storm=Optional.empty();
    private static boolean suppressed;
    private static float pushScale=1;
    private static CycloneVisuals.Peaks visibility=CycloneVisuals.Peaks.DEFAULT;
    public static float peakVisibility(CycloneSeverity severity){return visibility.forSeverity(severity);}
    public static float pushScale(){return pushScale;}
    private static long serverTime, clientTime;
    private CycloneClientState() {}
    public static void update(CycloneSyncPayload payload) {
        storm=payload.storm();suppressed=payload.suppressed();visibility=payload.visibility();pushScale=Float.isFinite(payload.pushScale())?Math.clamp(payload.pushScale(),0,2):1;serverTime=payload.serverTime();
        var level=Minecraft.getInstance().level;clientTime=level==null?serverTime:level.getGameTime();
    }
    public static Optional<CycloneStorm> storm() {
        var mc=Minecraft.getInstance();
        return suppressed || PostMaeveClientState.isMaeveErased() || mc.level==null || mc.level.dimension()!=Level.OVERWORLD?Optional.empty():storm;
    }
    public static double time(float partial) {
        var mc=Minecraft.getInstance();return serverTime+(mc.level==null?0:mc.level.getGameTime()-clientTime)+(mc.isPaused()?0:partial);
    }
    public static CycloneSample sampleLocal(float partial) {
        var p=Minecraft.getInstance().player;
        return p==null?CycloneSample.NONE:storm().map(s->CycloneField.sample(s,p.getX(),p.getZ(),time(partial))).orElse(CycloneSample.NONE);
    }
    public static CycloneForecast.Reading forecast(double x,double z) {
        return storm().map(s->CycloneForecast.read(s,x,z,time(0))).orElse(CycloneForecast.NONE);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {storm=Optional.empty();suppressed=false;pushScale=1;visibility=CycloneVisuals.Peaks.DEFAULT;serverTime=clientTime=0;}
}
