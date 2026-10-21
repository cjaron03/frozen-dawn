package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Separate phase-2 bed: preserve the accepted phase-1 and later cyclone players. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PhaseTwoWindAmbience {
    static final int CLIP_TICKS = 1080;
    static final int OVERLAP_TICKS = 100;
    private static final WindBedLayer BED = new WindBedLayer();
    // Reuse the accepted two-second outdoor / two-and-a-half-second indoor easing.
    private static final PhaseOneWindExposure COVER = new PhaseOneWindExposure();
    private static boolean wasEligible;
    private static int lastCoverBand = -1;

    private PhaseTwoWindAmbience() { }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
            reset(mc);
            return;
        }
        if (mc.isPaused()) return;
        boolean suppressed = mc.player.isUnderWater() || mc.player.isInLava()
                || mc.player.blockPosition().getY() < 50 || PostMaeveClientState.isMaeveErased()
                || MasterArchitectWeather.getStrength() > .01F;
        // Forest canopies should not be mistaken for the owner's enclosed shelter.
        var position = mc.player.blockPosition();
        // NO_LEAVES is not sent in chunk packets and may be empty on the client.
        // Use the transmitted height only as a bound; inspect actual blocks for roofs.
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                position.getX(), position.getZ());
        boolean open = PhaseTwoWindCover.isExposed(mc.level,
                net.minecraft.core.BlockPos.containing(mc.player.getEyePosition()), top);
        float exposure = COVER.tick(open);
        float gain = PhaseTwoWindPolicy.gain(ApocalypseClientData.getPhase(),
                ApocalypseClientData.getProgress(), mc.level.getGameTime(), exposure, suppressed);
        gain *= MasterArchitectFourthWallMoment.weatherAudioMultiplier();
        gain *= MasterArchitectSeverTelegraph.weatherAudioMultiplier();
        gain *= HeartQuietClient.environmentMultiplier() * BloomClientState.windMultiplier();
        float fade = MasterArchitectSeverTelegraph.isActive()
                ? MasterArchitectSeverTelegraph.windFadeRate() : 1F / 120;
        BED.tick(mc, ModSounds.WIND_PHASE_TWO.get(), gain, CLIP_TICKS, OVERLAP_TICKS, true, fade);
        boolean eligible = ApocalypseClientData.getPhase() == 2 && !suppressed;
        int coverBand = exposure <= 0 ? 0 : exposure >= 1 ? 2 : 1;
        if (Boolean.getBoolean("frozendawn.labBridge")
                && (eligible != wasEligible || eligible && coverBand != lastCoverBand)) {
            FrozenDawn.LOGGER.info("Phase2 wind target: eligible={} open={} exposure={} gain={} gameTime={}",
                    eligible, open, exposure, gain, mc.level.getGameTime());
        }
        wasEligible = eligible;
        lastCoverBand = coverBand;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset(Minecraft.getInstance());
    }

    private static void reset(Minecraft mc) {
        BED.stop(mc);
        COVER.reset();
        wasEligible = false;
        lastCoverBand = -1;
    }
}
