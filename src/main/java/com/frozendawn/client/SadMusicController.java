package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Owns in-world music so Frozen Dawn can keep the soundtrack sparse and melancholic.
 * Vanilla MusicManager is suppressed by mixin; this controller schedules only the
 * approved tracks and hard-mutes late phase 6.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SadMusicController {

    private static final int WORLD_ENTRY_DELAY_MIN = 100;
    private static final int WORLD_ENTRY_DELAY_MAX = 240;
    private static final int BETWEEN_TRACK_DELAY_MIN = 600;
    private static final int BETWEEN_TRACK_DELAY_MAX = 1600;
    private static final int FOREST_TRACK_DELAY_MIN = 80;
    private static final int FOREST_TRACK_DELAY_MAX = 180;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final TrackEntry FOREST_TRACK = track(ModSounds.FOREST_NIGHT, 1);
    private static final Set<ResourceLocation> FOREST_BIOMES = Set.of(
            ResourceLocation.withDefaultNamespace("forest"),
            ResourceLocation.withDefaultNamespace("flower_forest"),
            ResourceLocation.withDefaultNamespace("birch_forest"),
            ResourceLocation.withDefaultNamespace("old_growth_birch_forest"),
            ResourceLocation.withDefaultNamespace("dark_forest"),
            ResourceLocation.withDefaultNamespace("taiga"),
            ResourceLocation.withDefaultNamespace("snowy_taiga"),
            ResourceLocation.withDefaultNamespace("old_growth_pine_taiga"),
            ResourceLocation.withDefaultNamespace("old_growth_spruce_taiga"),
            ResourceLocation.withDefaultNamespace("windswept_forest")
    );

    private static final List<TrackEntry> MELANCHOLY_TRACKS = List.of(
            track(ModSounds.SAD_MOOG_CITY_2, 4),
            track(ModSounds.SAD_DREITON, 4),
            track(ModSounds.SAD_MICE_ON_VENUS, 3),
            track(ModSounds.SAD_DRY_HANDS, 3),
            track(ModSounds.SAD_SWEDEN, 3),
            track(ModSounds.SAD_WET_HANDS, 3),
            track(ModSounds.SAD_MUTATION, 2),
            track(ModSounds.SAD_HAUNT_MUSKIE, 2),
            track(ModSounds.SAD_TASWELL, 2),
            track(ModSounds.SAD_LIVING_MICE, 2),
            track(ModSounds.SAD_ONE_MORE_DAY, 2),
            track(ModSounds.SAD_OXYGENE, 2),
            track(ModSounds.SAD_SUBWOOFER_LULLABY, 2),
            track(ModSounds.SAD_MINECRAFT, 1),
            track(ModSounds.SAD_CLARK, 1)
    );

    private static SoundInstance currentTrack;
    private static TrackMode currentTrackMode = TrackMode.NONE;
    private static ResourceLocation lastTrackId;
    private static int ticksUntilNext = 0;
    private static boolean needsWorldEntryDelay = true;

    private SadMusicController() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null) {
            hardReset(mc);
            return;
        }

        mc.getMusicManager().stopPlaying();

        if (shouldMuteAllMusic()) {
            enterMutedState(mc);
            return;
        }

        boolean wantsForestTrack = shouldPlayForestTrack(mc);

        if (currentTrack != null && !mc.getSoundManager().isActive(currentTrack)) {
            currentTrack = null;
            currentTrackMode = TrackMode.NONE;
            ticksUntilNext = wantsForestTrack
                    ? randomDelay(FOREST_TRACK_DELAY_MIN, FOREST_TRACK_DELAY_MAX)
                    : randomDelay(BETWEEN_TRACK_DELAY_MIN, BETWEEN_TRACK_DELAY_MAX);
        }

        if (wantsForestTrack) {
            if (currentTrackMode == TrackMode.WORLD) {
                stopCurrentTrack(mc);
                ticksUntilNext = 0;
            }

            if (mc.isPaused() || currentTrack != null) {
                return;
            }

            if (ticksUntilNext > 0) {
                ticksUntilNext--;
                return;
            }

            playTrack(mc, FOREST_TRACK, TrackMode.FOREST, 0.9f);
            return;
        }

        if (currentTrackMode == TrackMode.FOREST) {
            stopCurrentTrack(mc);
            ticksUntilNext = randomDelay(BETWEEN_TRACK_DELAY_MIN, BETWEEN_TRACK_DELAY_MAX);
        }

        if (mc.isPaused() || currentTrack != null) {
            return;
        }

        if (needsWorldEntryDelay) {
            ticksUntilNext = randomDelay(WORLD_ENTRY_DELAY_MIN, WORLD_ENTRY_DELAY_MAX);
            needsWorldEntryDelay = false;
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            return;
        }

        playNextTrack(mc);
    }

    @SubscribeEvent
    public static void onJoinWorld(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        stopCurrentTrack(mc);
        ticksUntilNext = 0;
        needsWorldEntryDelay = true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hardReset(Minecraft.getInstance());
    }

    private static void playNextTrack(Minecraft mc) {
        TrackEntry selected = chooseTrack(ApocalypseClientData.getPhase());
        if (selected == null) {
            ticksUntilNext = randomDelay(BETWEEN_TRACK_DELAY_MIN, BETWEEN_TRACK_DELAY_MAX);
            return;
        }

        playTrack(mc, selected, TrackMode.WORLD, 0.85f);
    }

    private static TrackEntry chooseTrack(int phase) {
        List<TrackEntry> candidates = new ArrayList<>(MELANCHOLY_TRACKS);
        if (phase >= 4) {
            candidates.addAll(phaseFourGuestTracks());
        }

        if (candidates.isEmpty()) {
            return null;
        }

        if (lastTrackId != null && candidates.size() > 1) {
            candidates.removeIf(track -> track.sound().get().getLocation().equals(lastTrackId));
        }

        int totalWeight = 0;
        for (TrackEntry candidate : candidates) {
            totalWeight += candidate.weight();
        }

        int pick = RANDOM.nextInt(totalWeight);
        for (TrackEntry candidate : candidates) {
            pick -= candidate.weight();
            if (pick < 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static List<TrackEntry> phaseFourGuestTracks() {
        return List.of(
                track(ModSounds.PHASE4_GUEST_1, 2)
        );
    }

    private static boolean shouldMuteAllMusic() {
        return PhaseManager.isVacuumActive(ApocalypseClientData.getPhase(), ApocalypseClientData.getProgress());
    }

    private static boolean shouldPlayForestTrack(Minecraft mc) {
        if (ApocalypseClientData.getPhase() > 2) {
            return false;
        }

        Holder<Biome> biomeHolder = mc.level.getBiome(mc.player.blockPosition());
        return biomeHolder.unwrapKey()
                .map(key -> FOREST_BIOMES.contains(key.location()))
                .orElse(false);
    }

    private static void playTrack(Minecraft mc, TrackEntry selected, TrackMode mode, float volume) {
        SoundEvent event = selected.sound().get();
        lastTrackId = event.getLocation();
        currentTrackMode = mode;
        currentTrack = new SimpleSoundInstance(
                event.getLocation(),
                SoundSource.MUSIC,
                volume,
                1.0f,
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0,
                0.0,
                0.0,
                true
        );
        mc.getSoundManager().play(currentTrack);
    }

    private static void enterMutedState(Minecraft mc) {
        stopCurrentTrack(mc);
        ticksUntilNext = 0;
        needsWorldEntryDelay = true;
    }

    private static void hardReset(Minecraft mc) {
        stopCurrentTrack(mc);
        lastTrackId = null;
        ticksUntilNext = 0;
        needsWorldEntryDelay = true;
    }

    private static void stopCurrentTrack(Minecraft mc) {
        if (currentTrack != null) {
            mc.getSoundManager().stop(currentTrack);
            currentTrack = null;
        }
        currentTrackMode = TrackMode.NONE;
    }

    private static int randomDelay(int minTicks, int maxTicks) {
        return minTicks + RANDOM.nextInt(maxTicks - minTicks + 1);
    }

    private static TrackEntry track(DeferredHolder<SoundEvent, SoundEvent> sound, int weight) {
        return new TrackEntry(sound, weight);
    }

    private enum TrackMode {
        NONE,
        WORLD,
        FOREST
    }

    private record TrackEntry(DeferredHolder<SoundEvent, SoundEvent> sound, int weight) {}
}
