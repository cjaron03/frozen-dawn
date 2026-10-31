package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Gentle clear-day forest wind; independent of rainy gusts and weather scheduling. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class PhaseOneNatureAmbience {
    private static final PhaseOneWindExposure COVER = new PhaseOneWindExposure();
    private static TickableNatureSound phrase;
    private static int cooldown = 40;

    private PhaseOneNatureAmbience() { }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { reset(mc); return; }
        if (mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) { reset(mc); return; }

        var position = mc.player.blockPosition();
        var biome = mc.level.getBiome(position);
        boolean woodland = biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)
                || biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.CHERRY_GROVE)
                || biome.is(Biomes.WINDSWEPT_FOREST);
        boolean suppressed = mc.player.isUnderWater() || mc.player.isInLava()
                || position.getY() < 50 || PostMaeveClientState.isMaeveErased()
                || MasterArchitectWeather.getStrength() > .01F
                || !PhaseOneNaturePolicy.dry(mc.level.getLevelData().isRaining(),
                        mc.level.getRainLevel(1), mc.level.getThunderLevel(1));
        boolean open = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                position.getX(), position.getZ()) <= mc.player.getEyeY();
        float exposure = COVER.tick(open);
        float progress = ApocalypseClientData.getProgress();
        float target = PhaseOneNaturePolicy.eligible(ApocalypseClientData.getPhase(), woodland, suppressed)
                ? PhaseOneNaturePolicy.volume(progress, mc.level.getDayTime(), exposure) : 0;
        target *= MasterArchitectFourthWallMoment.weatherAudioMultiplier();
        target *= MasterArchitectSeverTelegraph.weatherAudioMultiplier();
        target *= HeartQuietClient.environmentMultiplier() * BloomClientState.windMultiplier();

        if (phrase != null) {
            // Reuse the existing smooth ambient sound envelope, at original recording pitch.
            phrase.setTargetVolume(target, .0125F);
            if (phrase.isStopped()) {
                phrase = null;
                cooldown = PhaseOneNaturePolicy.gapTicks(progress, mc.level.random.nextFloat());
            }
        }
        if (cooldown > 0) cooldown--;
        if (phrase == null && cooldown == 0 && target > .02F) {
            phrase = new TickableNatureSound(ModSounds.WIND_EARLY_FOREST.get());
            phrase.setTargetVolume(target, .0125F);
            mc.getSoundManager().play(phrase);
            if (Boolean.getBoolean("frozendawn.labBridge")) {
                FrozenDawn.LOGGER.info("Clear forest wind: biome={} gain={} dayTime={}",
                        biome.unwrapKey().map(key -> key.location().toString()).orElse("unregistered"),
                        target, mc.level.getDayTime());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset(Minecraft.getInstance());
    }

    private static void reset(Minecraft mc) {
        if (phrase != null) mc.getSoundManager().stop(phrase);
        phrase = null;
        cooldown = 40;
        COVER.reset();
    }
}
