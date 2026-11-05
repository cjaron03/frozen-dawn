package com.frozendawn.miller;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/** What she wants. Each want rises with time toward its ceiling and falls when it is fed. One set per world: there is one of her. */
public final class Drives {
    public enum Drive {
        COMPANY(1.0, 12000),
        SKY(1.0, 48000),
        OTHERS(0.8, 96000),
        STAYING_ON(0.8, 48000),
        CURIOSITY(1.0, 24000);

        private final double ceiling;
        private final long riseHalfLife;

        Drive(double ceiling, long riseHalfLife) {
            this.ceiling = ceiling;
            this.riseHalfLife = riseHalfLife;
        }

        public double ceiling() { return ceiling; }
        public long riseHalfLife() { return riseHalfLife; }
    }

    private final Map<Drive, Fading> values = new EnumMap<>(Drive.class);

    /** Her clocks start the first time she notices anything, so a save older than this state does not jump. */
    void begin(long now) {
        for (Drive drive : Drive.values()) values.computeIfAbsent(drive, d -> new Fading(Temperament.DRIVE_START, now));
    }

    public double value(Drive drive, long now) {
        var value = values.get(drive);
        return value == null ? Temperament.DRIVE_START : value.toward(drive.ceiling, drive.riseHalfLife, now);
    }

    void feed(Drive drive, double amount, long now) {
        values.put(drive, new Fading(Math.max(0, value(drive, now) - amount), now));
    }

    /** {@code /fd shepherd want}: this want at exactly this, from now. */
    void set(Drive drive, double value, long now) {
        begin(now);
        values.put(drive, new Fading(Math.max(0, Math.min(1, value)), now));
    }

    void age(long ticks) {
        values.replaceAll((drive, value) -> value.aged(ticks));
    }

    void clear() {
        values.clear();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        values.forEach((drive, value) -> tag.put(drive.name(), value.save()));
        return tag;
    }

    static Drives load(CompoundTag tag) {
        var drives = new Drives();
        for (Drive drive : Drive.values()) if (tag.contains(drive.name())) drives.values.put(drive, Fading.load(tag.getCompound(drive.name())));
        return drives;
    }
}
