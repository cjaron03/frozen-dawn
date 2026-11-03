package com.frozendawn.client;
import com.frozendawn.FrozenDawn;
import com.frozendawn.cyclone.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
@EventBusSubscriber(modid=FrozenDawn.MOD_ID,value=Dist.CLIENT)
public final class CycloneSkyEffects {
    private CycloneSkyEffects() {}
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (!com.frozendawn.config.FrozenDawnConfig.CYCLONE_CAMERA_SHAKE.get() || mc.isPaused()
                || mc.level == null || mc.player == null) return;
        var storm = CycloneClientState.storm().orElse(null);
        var sample = CycloneClientState.sampleLocal(0);
        if (storm == null || sample.intensity() <= 0) return;
        float exposure = CycloneWind.exposure(mc.level, mc.player, storm.headingRad());
        double t = CycloneClientState.time((float) event.getPartialTick());
        float strength = 1.5f * sample.intensity() * exposure * (CycloneWind.gust((long)t, storm.seed()) - .75f) * 2;
        event.setYaw(event.getYaw() + (float)Math.sin(t * .17) * strength);
        event.setRoll(event.getRoll() + (float)Math.cos(t * .13) * strength);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void color(ViewportEvent.ComputeFogColor event) {
        var sample=CycloneClientState.sampleLocal(0);
        if(sample.stage()==CycloneStage.NONE)return;
        float darkness=sample.intensity()*CycloneClientState.storm().orElseThrow().severity().darkness
                *StormExposureController.getExposure();
        event.setRed(event.getRed()+(0.043f-event.getRed())*darkness);
        event.setGreen(event.getGreen()+(0.071f-event.getGreen())*darkness);
        event.setBlue(event.getBlue()+(0.125f-event.getBlue())*darkness);
        float flash = CycloneThunderClient.worldFlash((float) event.getPartialTick()) * .65f;
        event.setRed(event.getRed() + (.72f - event.getRed()) * flash);
        event.setGreen(event.getGreen() + (.85f - event.getGreen()) * flash);
        event.setBlue(event.getBlue() + (1f - event.getBlue()) * flash);
    }
}
