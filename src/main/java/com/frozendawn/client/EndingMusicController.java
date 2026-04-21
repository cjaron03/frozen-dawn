package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class EndingMusicController {
    private static final int MUSIC_START_OFFSET_SECONDS = 6 * 60 + 30;
    private static final int FADE_IN_TICKS = 8 * 20;
    private static final int STREAM_SKIP_CHUNK_SECONDS = 8;
    private static EndingMusicSound currentSound;
    private static CompletableFuture<AudioStream> pendingStream;
    private static int manualFadeTicks;
    private static int manualFadeTotalTicks;
    private static int startGeneration;
    private static int lastEndingTicks;

    private EndingMusicController() {
    }

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        stop();
        mc.getMusicManager().stopPlaying();
        mc.getSoundManager().stop(null, SoundSource.MUSIC);
        try {
            int generation = ++startGeneration;
            EndingMusicSound resolver = new EndingMusicSound(null, lastEndingTicks);
            resolver.resolve(mc.getSoundManager());
            Sound sound = resolver.getSound();
            if (sound == null || sound == SoundManager.EMPTY_SOUND) {
                playWithoutOffset(mc, generation);
                return;
            }

            SoundBufferLibrary soundBuffers = new SoundBufferLibrary(mc.getResourceManager());
            pendingStream = soundBuffers.getStream(sound.getPath(), false)
                    .thenApply(EndingMusicSound::skipToEndingExcerpt);
            manualFadeTicks = 0;
            manualFadeTotalTicks = 0;
            pendingStream.whenComplete((stream, error) -> mc.execute(() -> {
                if (generation != startGeneration) {
                    closeQuietly(stream);
                    return;
                }

                pendingStream = null;
                if (error != null || stream == null) {
                    playWithoutOffset(mc, generation);
                    return;
                }

                currentSound = new EndingMusicSound(CompletableFuture.completedFuture(stream), lastEndingTicks);
                mc.getSoundManager().play(currentSound);
            }));
        } catch (RuntimeException exception) {
            stop();
        }
    }

    public static void tick(int endingTicks) {
        lastEndingTicks = endingTicks;
        if (currentSound == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getSoundManager().isActive(currentSound)) {
            currentSound = null;
            return;
        }
        if (manualFadeTicks > 0) {
            currentSound.setManualFadeVolume(manualFadeTicks / (float) Math.max(1, manualFadeTotalTicks));
            manualFadeTicks--;
            if (manualFadeTicks <= 0) {
                stop();
            }
            return;
        }
        currentSound.setVolumeForEndingTick(endingTicks);
    }

    public static void fadeOutAndStop(int fadeTicks) {
        if (currentSound == null) {
            if (pendingStream != null) {
                stop();
            }
            return;
        }
        manualFadeTicks = Math.max(1, fadeTicks);
        manualFadeTotalTicks = manualFadeTicks;
    }

    public static void stop() {
        startGeneration++;
        if (pendingStream != null) {
            pendingStream.cancel(true);
            pendingStream = null;
        }
        if (currentSound != null) {
            Minecraft.getInstance().getSoundManager().stop(currentSound);
            currentSound = null;
        }
        manualFadeTicks = 0;
        manualFadeTotalTicks = 0;
    }

    private static void playWithoutOffset(Minecraft mc, int generation) {
        if (generation != startGeneration) {
            return;
        }
        currentSound = new EndingMusicSound(null, lastEndingTicks);
        mc.getSoundManager().play(currentSound);
    }

    private static void closeQuietly(@Nullable AudioStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    private static final class EndingMusicSound extends AbstractTickableSoundInstance {
        @Nullable
        private final CompletableFuture<AudioStream> preparedStream;
        private final int startEndingTicks;

        private EndingMusicSound(@Nullable CompletableFuture<AudioStream> preparedStream, int startEndingTicks) {
            super(SoundEvents.MUSIC_END.value(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.preparedStream = preparedStream;
            this.startEndingTicks = startEndingTicks;
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.looping = false;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        private void setVolumeForEndingTick(int endingTicks) {
            float fade = Mth.clamp((endingTicks - startEndingTicks) / (float) FADE_IN_TICKS, 0.0F, 1.0F);
            this.volume = fade * fade * (3.0F - 2.0F * fade);
        }

        private void setManualFadeVolume(float fadeMultiplier) {
            this.volume = Mth.clamp(fadeMultiplier, 0.0F, 1.0F);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            if (preparedStream != null) {
                return preparedStream;
            }
            return soundBuffers.getStream(sound.getPath(), looping);
        }

        private static AudioStream skipToEndingExcerpt(AudioStream stream) {
            try {
                skipDecodedAudio(stream, MUSIC_START_OFFSET_SECONDS);
            } catch (IOException ignored) {
                // If seeking fails, continue from the stream's current position rather than dropping music entirely.
            }
            return stream;
        }

        private static void skipDecodedAudio(AudioStream stream, int seconds) throws IOException {
            AudioFormat format = stream.getFormat();
            int frameSize = Math.max(1, format.getFrameSize());
            int sampleRate = Math.max(1, Math.round(format.getSampleRate()));
            long bytesToSkip = (long) seconds * sampleRate * frameSize;
            int chunkSize = Math.max(frameSize, sampleRate * frameSize * STREAM_SKIP_CHUNK_SECONDS);
            while (bytesToSkip > 0) {
                ByteBuffer skipped = stream.read((int) Math.min(chunkSize, bytesToSkip));
                if (skipped == null || skipped.remaining() <= 0) {
                    return;
                }
                bytesToSkip -= skipped.remaining();
            }
        }

        @Override
        public void tick() {
        }
    }
}
