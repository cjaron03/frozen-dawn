package com.frozendawn.client.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Uses NeoForge's sound-stream hook to transform the installed game's audio privately at playback. */
@EventBusSubscriber(modid="frozendawn",value=Dist.CLIENT)
public final class BlindHurtSound implements SoundInstance {
    private static final ResourceLocation EVENT=ResourceLocation.fromNamespaceAndPath("frozendawn","entity.blind_architect.hurt");
    private final SoundInstance original;
    private BlindHurtSound(SoundInstance original) { this.original=original; }
    @SubscribeEvent public static void play(PlaySoundEvent event) {
        var sound=event.getSound();
        if(sound!=null&&EVENT.equals(sound.getLocation())&&!(sound instanceof BlindHurtSound))event.setSound(new BlindHurtSound(sound));
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,Sound sound,boolean looping) {
        if(looping)return original.getStream(buffers,sound,true);
        return original.getStream(buffers,sound,false).thenApplyAsync(stream -> {
            try(stream) {
                var bytes=new ByteArrayOutputStream();
                while(true) {
                    var block=stream.read(16384);if(!block.hasRemaining())break;
                    byte[] part=new byte[block.remaining()];block.get(part);bytes.write(part);
                    if(bytes.size()>4_000_000)throw new IOException("Oversized hurt audio");
                }
                double pitch=sound.getLocation().getPath().startsWith("mob/warden/")?1.65:.66;
                var pcm=BlindHurtTexture.process(ByteBuffer.wrap(bytes.toByteArray()),stream.getFormat(),pitch);
                return (AudioStream)new PcmStream(pcm);
            }catch(IOException|IllegalArgumentException failure){throw new CompletionException(failure);}
        },net.minecraft.Util.nonCriticalIoPool());
    }
    private static final class PcmStream implements AudioStream {
        private ByteBuffer pcm;
        PcmStream(ByteBuffer pcm) {this.pcm=pcm;}
        public AudioFormat getFormat(){return new AudioFormat(48000,16,1,true,false);}
        public ByteBuffer read(int size){int n=Math.min(pcm.remaining(),size-size%2);var block=pcm.slice();block.limit(n);pcm.position(pcm.position()+n);return block;}
        public void close(){pcm=ByteBuffer.allocate(0);}
    }
    public ResourceLocation getLocation(){return original.getLocation();}
    public WeighedSoundEvents resolve(SoundManager manager){return original.resolve(manager);}
    public Sound getSound(){return original.getSound();}
    public SoundSource getSource(){return original.getSource();}
    public boolean isLooping(){return original.isLooping();}
    public boolean isRelative(){return original.isRelative();}
    public int getDelay(){return original.getDelay();}
    public float getVolume(){return original.getVolume();}
    public float getPitch(){return original.getPitch();}
    public double getX(){return original.getX();}
    public double getY(){return original.getY();}
    public double getZ(){return original.getZ();}
    public Attenuation getAttenuation(){return original.getAttenuation();}
    public boolean canStartSilent(){return original.canStartSilent();}
    public boolean canPlaySound(){return original.canPlaySound();}
}
