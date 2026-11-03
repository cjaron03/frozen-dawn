package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.cyclone.WindAudioPolicy;
import com.frozendawn.mixin.SoundEngineAccessor;
import com.frozendawn.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Applies listener-side masking after ordinary sound processing, including mod mob exemptions. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID,value=Dist.CLIENT)
public final class CycloneMobSoundMask {
    private static boolean wasMasking;
    private CycloneMobSoundMask() {}

    static boolean eligible(SoundSource source,boolean relative,String namespace,String path) {
        if(relative || source==SoundSource.MASTER || source==SoundSource.MUSIC
                || source==SoundSource.VOICE || source==SoundSource.PLAYERS)return false;
        return source==SoundSource.HOSTILE || source==SoundSource.NEUTRAL
                || (source==SoundSource.AMBIENT && namespace.equals(FrozenDawn.MOD_ID)
                    && path.startsWith("entity."));
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null){wasMasking=false;return;}
        if(mc.isPaused())return;
        float strength=WindAmbience.mobMaskingStrength();
        if(strength==0 && !wasMasking)return;
        // Recalculate from the engine's unmasked gain each tick. Never multiply last tick's
        // channel gain, change sliders, replace streams, or restart an already-playing sound.
        var engine=(SoundEngineAccessor)((SoundManagerAccessor)mc.getSoundManager()).frozendawn$getSoundEngine();
        var ear=mc.player.getEyePosition();
        for(var entry:engine.frozendawn$getInstanceToChannel().entrySet()) {
            SoundInstance sound=entry.getKey();var id=sound.getLocation();
            if(!eligible(sound.getSource(),sound.isRelative(),id.getNamespace(),id.getPath()))continue;
            double distance=ear.distanceTo(new net.minecraft.world.phys.Vec3(sound.getX(),sound.getY(),sound.getZ()));
            float gain=engine.frozendawn$calculateVolume(sound)*WindAudioPolicy.mobGain(strength,distance);
            entry.getValue().execute(channel->channel.setVolume(gain));
        }
        wasMasking=strength>0;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){wasMasking=false;}
}
