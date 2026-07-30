package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Spawns ambient snowflake particles around the player in phases 3+.
 * Phase 3: light snow. Phase 4: heavy. Phase 5: extreme blizzard whiteout.
 * Phase 6 early: maximum blizzard (60 particles). Mid: particles fade to 0.
 * Late: none except inside a living Master Architect's local Hearth storm.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class WeatherParticles {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        if (mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 3) return;

        float exposure = StormExposureController.getExposure();
        float progress = ApocalypseClientData.getProgress();
        float eyeStormFactor = MasterArchitectAuraClient.localStormFactor(
                mc.player.position());
        float masterStrength = MasterArchitectWeather.getStrength()
                * exposure * eyeStormFactor;
        float heartStrength = HeartQuietClient.localStormStrength() * exposure;
        if (exposure <= 0.08F && masterStrength <= 0.08F
                && heartStrength <= 0.08F) return;

        boolean vacuum = PhaseManager.isVacuumActive(phase, progress);
        if (vacuum && masterStrength <= 0.0F && heartStrength <= 0.0F) return;

        int globalParticleCount = vacuum
                ? 0 : Math.round(getParticleCount(phase, progress) * exposure);
        int localParticleCount = Math.round(72.0F * masterStrength);
        int heartParticleCount = Math.round(28.0F * heartStrength);
        float heartQuiet = HeartQuietClient.environmentMultiplier();
        globalParticleCount = Math.round(globalParticleCount * heartQuiet);
        localParticleCount = Math.round(localParticleCount * heartQuiet);
        int totalParticleCount = globalParticleCount
                + localParticleCount + heartParticleCount;
        if (totalParticleCount <= 0) return;

        RandomSource random = mc.level.random;
        double px = mc.player.getX();
        double py = mc.player.getEyeY();
        double pz = mc.player.getZ();

        long gameTime = mc.level.getGameTime();

        if (phase >= 5) {
            // Phase 5+: particles blow sideways at surface level, like a ground blizzard
            float globalWindSpeed = BlizzardWindHelper.getSurfaceWindSpeed(
                    phase, progress, gameTime) * exposure * heartQuiet;
            float localWindSpeed = BlizzardWindHelper.getMasterArchitectWindSpeed(
                    gameTime, masterStrength) * heartQuiet;
            float heartWindSpeed = BlizzardWindHelper.getMasterArchitectWindSpeed(
                    gameTime, heartStrength * 0.72F);
            float windAngle = BlizzardWindHelper.getWindAngleRad(gameTime);
            spawnHorizontalSnow(
                    mc,
                    random,
                    px,
                    py,
                    pz,
                    globalParticleCount,
                    globalWindSpeed * Mth.sin(windAngle),
                    globalWindSpeed * Mth.cos(windAngle),
                    false);
            spawnHorizontalSnow(
                    mc,
                    random,
                    px,
                    py,
                    pz,
                    localParticleCount,
                    localWindSpeed * Mth.sin(windAngle),
                    localWindSpeed * Mth.cos(windAngle),
                    true);
            spawnHorizontalSnow(
                    mc,
                    random,
                    px,
                    py,
                    pz,
                    heartParticleCount,
                    heartWindSpeed * Mth.sin(windAngle),
                    heartWindSpeed * Mth.cos(windAngle),
                    false);
        } else {
            // Phase 3-4: normal falling snow with mild wind
            float windStrength = 0.5f + 0.5f * (float) Math.sin(gameTime * 0.02);
            float windMult = phase >= 4 ? 0.4f : 0.2f;
            float windX = windStrength * windMult * exposure * heartQuiet
                    * (float) Math.sin(gameTime * 0.007);
            float windZ = windStrength * windMult * exposure * heartQuiet
                    * (float) Math.cos(gameTime * 0.011);
            double fallSpeed = -0.3;

            for (int i = 0; i < totalParticleCount; i++) {
                double spread = 16;
                double x = px + random.nextGaussian() * spread;
                double y = py + 8 + random.nextDouble() * 12;
                double z = pz + random.nextGaussian() * spread;
                mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, windX, fallSpeed, windZ);
            }
        }
    }

    private static void spawnHorizontalSnow(
            Minecraft minecraft,
            RandomSource random,
            double playerX,
            double playerY,
            double playerZ,
            int count,
            double windX,
            double windZ,
            boolean confineToMasterEye) {
        int spawned = 0;
        int attempts = Math.max(count, count * 4);
        for (int attempt = 0; attempt < attempts && spawned < count; attempt++) {
            double x = playerX + random.nextGaussian() * 20.0D;
            double y = playerY + random.nextGaussian() * 3.0D;
            double z = playerZ + random.nextGaussian() * 20.0D;
            if (confineToMasterEye
                    && MasterArchitectAuraClient.localStormFactor(new Vec3(x, y, z))
                    <= 0.08F) {
                continue;
            }
            minecraft.level.addParticle(
                    ParticleTypes.SNOWFLAKE,
                    x,
                    y,
                    z,
                    windX,
                    -0.08D,
                    windZ);
            spawned++;
        }
    }

    private static int getParticleCount(int phase, float progress) {
        if (phase < 6) {
            return switch (phase) {
                case 3 -> 4;
                case 4 -> 12;
                default -> 40;
            };
        }

        return switch (PhaseManager.getPhase6Stage(phase, progress)) {
            case EARLY -> 60;
            case MID -> (int) Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), 60f, 0f);
            case VACUUM, INACTIVE -> 0;
        };
    }
}
