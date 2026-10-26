package com.frozendawn.client;

import com.frozendawn.block.IsotopeColumnBlock;
import com.frozendawn.block.IsotopeColumnBlockEntity;
import com.frozendawn.init.ModSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Quiet bubbling hum from a working Isotope Column, following the CFU-7 hum: one loop per Column while LIT, a
 * heartbeat shows the engine still ticks it, and a muted start is retried every two seconds. */
public final class IsotopeColumnSound extends AbstractTickableSoundInstance {
    private static final Map<IsotopeColumnBlockEntity, IsotopeColumnSound> PLAYING = new WeakHashMap<>();
    private static final Map<IsotopeColumnBlockEntity, Long> RETRY = new WeakHashMap<>();
    private static final float FADE = 0.04F, VOLUME = .5F;
    private final IsotopeColumnBlockEntity column;
    private long heartbeat = Long.MIN_VALUE / 2;

    private IsotopeColumnSound(IsotopeColumnBlockEntity column) {
        super(ModSounds.SFR_COLUMN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.column = column;
        var pos = column.getBlockPos();
        x = pos.getX() + .5; y = pos.getY() + .5; z = pos.getZ() + .5;
        looping = true; delay = 0; volume = 0; pitch = 1F;
        attenuation = Attenuation.LINEAR;
    }
    private static boolean lit(IsotopeColumnBlockEntity column) {
        return !column.isRemoved() && column.getBlockState().getValue(IsotopeColumnBlock.LIT);
    }

    /** Client block entity ticker. */
    public static void tick(IsotopeColumnBlockEntity column) {
        if (!lit(column)) return; // a playing loop fades itself out
        var manager = Minecraft.getInstance().getSoundManager(); var playing = PLAYING.get(column);
        long now = column.getLevel() == null ? 0 : column.getLevel().getGameTime();
        if (playing != null && !playing.isStopped() && now - playing.heartbeat < 40) return;
        if (playing != null && !playing.isStopped() && now - RETRY.getOrDefault(column, Long.MIN_VALUE / 2) < 40) return;
        if (playing != null) { playing.stop(); manager.stop(playing); }
        RETRY.put(column, now);
        var sound = new IsotopeColumnSound(column);
        PLAYING.put(column, sound);
        manager.play(sound);
    }

    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        if (column.getLevel() != null) heartbeat = column.getLevel().getGameTime();
        float target = lit(column) ? VOLUME : 0;
        volume = volume < target ? Math.min(target, volume + FADE) : Math.max(target, volume - FADE);
        if (volume <= 0.001F && target <= 0) { stop(); PLAYING.remove(column); }
    }
}
