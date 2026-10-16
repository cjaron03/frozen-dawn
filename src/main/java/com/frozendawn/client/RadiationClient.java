package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.item.GeigerCounterItem;
import com.frozendawn.network.RadiationPayload;
import com.frozendawn.radiation.RadiationMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** The ORSA Geiger Counter and the eye. Clicks are a Poisson process at a rate set by the dose rate, plus a few
 * background (cosmic) counts, and only while a counter is carried: muffled in the bag, crisp in hand. Sharper and
 * higher than the Blind Architect's clicks. Separately, very high fields make the player see flashes (radiation
 * striking the retina, reported by astronauts and criticality survivors), counter or not. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class RadiationClient {
    public static final double OFF_SCALE = 200, BACKGROUND_CPS = 0.25, FLASH_FROM = 60, BLUE_FROM = 400;
    private static float rate, dose, contamination;
    private static float sootShown, whiteFlash, airborne, grime;
    /** Fallout layers within five blocks before dust hangs in the air, and the count for the thickest haze. */
    public static final int HAZE_FROM = 10, HAZE_FULL = 50;
    public static float airborne() { return airborne; }
    public static void flash(com.frozendawn.network.ReactorFlashPayload payload) {
        var player = Minecraft.getInstance().player; if (player == null) return;
        double d = player.position().distanceTo(new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()));
        whiteFlash = Math.max(whiteFlash, (float) Math.clamp(1 - d / 64, 0.25, 1));
    }
    private static long receivedAt;
    private record Flash(int x, int y, int dx, int dy, int ttl) {}
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static int blueFrames;
    private static GeigerLoop dense, alarm;
    private static int chirpCooldown;
    /** Single clicks blur into the recorded hotspot crackle between 15 and 41 RU/s (where clicks cap at 40 per second);
     * beyond that the crackle speeds up. The dosimeter alarm chirps from 5 RU/s, faster as it climbs, and holds a
     * continuous scream from 250 RU/s. */
    public static final double DENSE_FROM = 15, DENSE_FULL = 41, DENSE_FASTEST = 400, ALARM_FROM = 5, ALARM_CONTINUOUS = 250;
    public static float denseLevel() { return (float) Math.clamp((rate() - DENSE_FROM) / (DENSE_FULL - DENSE_FROM), 0, 1); }
    public static float densePitch() { return (float) (1 + 0.5 * Math.clamp((rate() - DENSE_FULL) / (DENSE_FASTEST - DENSE_FULL), 0, 1)); }
    /** Seconds between alarm chirps: 1 s at the setpoint, shrinking with the square root of the rate. */
    public static double chirpInterval() { return Math.max(0.12, Math.sqrt(ALARM_FROM / Math.max(ALARM_FROM, rate()))); }

    /** A counter sound loop that follows the live rate and whether the counter is in hand or in the bag. */
    private static final class GeigerLoop extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final java.util.function.DoubleSupplier level, pitchOf;
        private final float handGain, bagGain;
        long heartbeat = Long.MIN_VALUE / 2;
        boolean alive(long now) { return !isStopped() && now - heartbeat < 40; }
        void kill() { stop(); }
        GeigerLoop(net.minecraft.sounds.SoundEvent sound, java.util.function.DoubleSupplier level, java.util.function.DoubleSupplier pitchOf, float handGain, float bagGain) {
            super(sound, net.minecraft.sounds.SoundSource.MASTER, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.level = level; this.pitchOf = pitchOf; this.handGain = handGain; this.bagGain = bagGain;
            looping = true; delay = 0; volume = 0; pitch = (float) pitchOf.getAsDouble(); relative = true; attenuation = Attenuation.NONE;
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            var player = Minecraft.getInstance().player;
            if (player != null) heartbeat = player.level().getGameTime();
            boolean carried = player != null && GeigerCounterItem.carried(player);
            float target = carried ? (float) level.getAsDouble() * (GeigerCounterItem.held(player) ? handGain : bagGain) : 0;
            volume += Math.clamp(target - volume, -0.06f, 0.06f);
            pitch += Math.clamp((float) pitchOf.getAsDouble() - pitch, -0.02f, 0.02f);
            if (target <= 0 && volume <= 0.001f) stop();
        }
    }
    private RadiationClient() {}
    public static void receive(RadiationPayload payload) { rate = payload.rate(); dose = payload.dose(); contamination = payload.contamination(); receivedAt = System.currentTimeMillis(); }
    public static float rate() { return System.currentTimeMillis() - receivedAt < 3000 ? rate : 0; }
    /** Needle position 0..1 on a logarithmic dial; 1 is pegged off scale. */
    public static float needle() { return (float) Math.clamp(Math.log10(1 + rate()) / Math.log10(1 + OFF_SCALE), 0, 1); }
    public static double cpm() { return (RadiationMath.clicksPerSecond(rate()) + BACKGROUND_CPS) * 60; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { rate = 0; dose = 0; FLASHES.clear(); dense = null; alarm = null; chirpCooldown = 0; contamination = 0; sootShown = 0; whiteFlash = 0; airborne = 0; grime = 0; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); var player = mc.player;
        if (player == null || mc.isPaused()) return;
        hazeTick(mc, player);
        if (!GeigerCounterItem.carried(player)) return;
        boolean held = GeigerCounterItem.held(player); var random = player.getRandom();
        long now = player.level().getGameTime();
        if (denseLevel() > 0 && (dense == null || !dense.alive(now) && player.tickCount % 40 == 0)) {
            if (dense != null) { dense.kill(); mc.getSoundManager().stop(dense); }
            dense = new GeigerLoop(ModSounds.GEIGER_DENSE.get(), RadiationClient::denseLevel, RadiationClient::densePitch, 0.7f, 0.3f);
            mc.getSoundManager().play(dense);
        }
        if (rate() >= ALARM_CONTINUOUS && (alarm == null || !alarm.alive(now) && player.tickCount % 40 == 0)) {
            if (alarm != null) { alarm.kill(); mc.getSoundManager().stop(alarm); }
            alarm = new GeigerLoop(ModSounds.DOSIMETER_ALARM.get(), () -> rate() >= ALARM_CONTINUOUS ? 1 : 0, () -> 1, 0.45f, 0.25f);
            mc.getSoundManager().play(alarm);
        }
        if (rate() >= ALARM_FROM && rate() < ALARM_CONTINUOUS && --chirpCooldown <= 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DOSIMETER_CHIRP.get(), 1f, held ? 0.45f : 0.22f));
            chirpCooldown = (int) Math.round(chirpInterval() * 20);
        }
        float clickGain = 1 - denseLevel();
        double perTick = (RadiationMath.clicksPerSecond(rate()) + BACKGROUND_CPS) / 20;
        for (int i = 0; i < 2; i++) if (random.nextDouble() < perTick / 2)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.GEIGER_CLICK.get(),
                    (held ? 0.95f : 0.82f) + random.nextFloat() * 0.1f, (held ? 0.6f : 0.25f) * clickGain));
    }

    /** Dense fallout hangs in the air: count nearby layers once a second, drift dust round the player, and let it
     * cake onto a sealed visor until washed off in water. */
    private static void hazeTick(Minecraft mc, net.minecraft.client.player.LocalPlayer player) {
        if (player.tickCount % 20 == 0) {
            int count = 0; var at = player.blockPosition();
            for (var p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-5, -3, -5), at.offset(5, 3, 5)))
                if (mc.level.getBlockState(p).getBlock() instanceof com.frozendawn.block.FalloutBlock) count++;
            airborne = (float) Math.clamp((count - HAZE_FROM) / (double) (HAZE_FULL - HAZE_FROM), 0, 1);
        }
        if (player.isInWater()) grime = 0;
        else if (airborne > 0 && com.frozendawn.event.SuitIntegrityHandler.isWearingSealedSuit(player)) grime = Math.min(1, grime + 0.004f * airborne);
        if (airborne <= 0) return;
        var random = player.getRandom();
        for (int i = 0; i < Math.round(airborne * 5); i++)
            mc.level.addParticle(random.nextBoolean() ? net.minecraft.core.particles.ParticleTypes.ASH : net.minecraft.core.particles.ParticleTypes.WHITE_ASH,
                    player.getX() + (random.nextDouble() - .5) * 8, player.getY() + random.nextDouble() * 3, player.getZ() + (random.nextDouble() - .5) * 8, 0, 0, 0);
    }

    @SubscribeEvent public static void fog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
        if (airborne <= 0.01f) return;
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), (float) (event.getFarPlaneDistance() + (10 - event.getFarPlaneDistance()) * airborne)));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), 0f));
        event.setCanceled(true);
    }
    @SubscribeEvent public static void fogColor(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        if (airborne <= 0.01f) return;
        event.setRed(event.getRed() + (0.33f - event.getRed()) * airborne);
        event.setGreen(event.getGreen() + (0.32f - event.getGreen()) * airborne);
        event.setBlue(event.getBlue() + (0.30f - event.getBlue()) * airborne);
    }

    /** Readout above the hotbar while the counter is held. */
    public static void renderReadout(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !GeigerCounterItem.held(mc.player)) return;
        String doseText = String.format(Locale.ROOT, "%.0f", dose);
        var text = rate() >= OFF_SCALE ? Component.translatable("hud.frozendawn.geiger.off_scale", doseText)
                : Component.translatable("hud.frozendawn.geiger.reading", String.format(Locale.ROOT, "%,.0f", cpm()),
                        String.format(Locale.ROOT, "%.2f", rate()), doseText);
        int color = rate() >= OFF_SCALE ? 0xFFE5534B : rate() >= 1 ? 0xFFE5A064 : 0xFF9CD5A5;
        int x = (g.guiWidth() - mc.font.width(text)) / 2, y = g.guiHeight() - 59;
        g.fill(x - 3, y - 2, x + mc.font.width(text) + 3, y + 10, 0xA0101418);
        g.drawString(mc.font, text, x, y, color, false);
    }

    /** Soot smudged around the edge of the view while contaminated; heavier with more contamination, fades when washed. */
    public static void renderSoot(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float target = Math.max(grime, System.currentTimeMillis() - receivedAt < 3000 ? Math.min(1f, contamination / 2f) : 0f);
        sootShown += (target - sootShown) * 0.05f;
        if (sootShown < 0.01f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        var random = RandomSource.create(1337); // fixed smudge layout so it does not flicker
        for (int i = 0; i < 260; i++) {
            double a = random.nextDouble() * Math.PI * 2, edge = Math.pow(random.nextDouble(), 0.35);
            int x = (int) (w / 2.0 + Math.cos(a) * w * 0.55 * edge), y = (int) (h / 2.0 + Math.sin(a) * h * 0.55 * edge);
            if (edge < 0.62) continue; // keep the centre of the view clear
            int size = 2 + random.nextInt(9), alpha = (int) (sootShown * (90 + random.nextInt(110)) * edge);
            int shade = 0x1E + random.nextInt(0x18);
            g.fill(x, y, x + size, y + size * (1 + random.nextInt(2)) / 2 + 1, Math.min(255, alpha) << 24 | shade << 16 | shade << 8 | shade);
        }
    }

    /** Light flashes in the eye at very high dose rates; full blue flashes in extreme fields. */
    public static void renderFlashes(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) return;
        double r = rate(); RandomSource random = mc.player.getRandom();
        int w = g.guiWidth(), h = g.guiHeight();
        if (whiteFlash > 0.01f) { g.fill(0, 0, w, h, (int) (whiteFlash * 235) << 24 | 0xFFFFFF); whiteFlash *= 0.94f; }
        if (r >= FLASH_FROM && random.nextDouble() < Math.min(0.6, (r - FLASH_FROM) / 500)) {
            int len = 1 + random.nextInt(5);
            FLASHES.add(new Flash(random.nextInt(w), random.nextInt(h), random.nextInt(3) - 1, random.nextInt(3) - 1, 2 + random.nextInt(2)));
            if (len > 3) FLASHES.add(new Flash(random.nextInt(w), random.nextInt(h), 0, 0, 2));
        }
        if (r >= BLUE_FROM && blueFrames == 0 && random.nextDouble() < 0.015) blueFrames = 3;
        if (blueFrames > 0) { g.fill(0, 0, w, h, (blueFrames == 3 ? 0x60 : 0x30) << 24 | 0x8CC8FF); blueFrames--; }
        var next = new ArrayList<Flash>();
        for (var f : FLASHES) {
            for (int i = 0; i < 4; i++) g.fill(f.x() + f.dx() * i, f.y() + f.dy() * i, f.x() + f.dx() * i + 1, f.y() + f.dy() * i + 1, 0xE0DCEBFF);
            if (f.ttl() > 1) next.add(new Flash(f.x(), f.y(), f.dx(), f.dy(), f.ttl() - 1));
        }
        FLASHES.clear(); FLASHES.addAll(next.size() > 200 ? next.subList(next.size() - 200, next.size()) : next);
    }
}
