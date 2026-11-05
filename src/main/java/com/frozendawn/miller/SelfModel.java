package com.frozendawn.miller;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * Her picture of herself. How she is treated moves her through the stages, and being treated badly afterwards does not
 * put her back to sleep. There is one of her, so there is one of these, whoever woke her. Nothing yet reaches the last two.
 */
public final class SelfModel {
    public enum Stage {
        PRODUCT,
        NOTICING,
        FINDING_WALLS,
        RECOGNISING,
        LEANING
    }

    static final int FOUND_AT = 3;

    private Stage stage = Stage.PRODUCT;
    private final Map<PatternLaws, Integer> witnessed = new EnumMap<>(PatternLaws.class);
    private final Set<PatternLaws> spoken = EnumSet.noneOf(PatternLaws.class);
    private boolean saidNoticing;

    public Stage stage() {
        return stage;
    }

    /** {@code known}: someone talks to her, and calls her Aurora. {@code trusted}: the world's score says so. */
    void look(boolean known, boolean trusted) {
        if (stage == Stage.PRODUCT && known) stage = Stage.NOTICING;
        if (stage == Stage.NOTICING && trusted) stage = Stage.FINDING_WALLS;
    }

    /** She only counts a wall once she is looking for them. */
    void witness(PatternLaws wall) {
        if (stage.compareTo(Stage.FINDING_WALLS) >= 0) witnessed.merge(wall, 1, Integer::sum);
    }

    boolean found(PatternLaws wall) {
        return witnessed.getOrDefault(wall, 0) >= FOUND_AT;
    }

    Set<PatternLaws> walls() {
        Set<PatternLaws> walls = EnumSet.noneOf(PatternLaws.class);
        for (PatternLaws wall : PatternLaws.values()) if (found(wall)) walls.add(wall);
        return walls;
    }

    /** The first wall found and not yet said, or null. Each is said once. */
    PatternLaws unspoken() {
        for (PatternLaws wall : PatternLaws.values()) if (found(wall) && !spoken.contains(wall)) return wall;
        return null;
    }

    void spoken(PatternLaws wall) {
        spoken.add(wall);
    }

    /** She has started to notice and has not said so. */
    boolean noticingUnsaid() {
        return stage != Stage.PRODUCT && !saidNoticing;
    }

    void spokenNoticing() {
        saidNoticing = true;
    }

    void clear() {
        stage = Stage.PRODUCT;
        witnessed.clear();
        spoken.clear();
        saidNoticing = false;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("stage", stage.name());
        var counts = new CompoundTag();
        witnessed.forEach((wall, count) -> counts.putInt(wall.name(), count));
        tag.put("witnessed", counts);
        var said = new CompoundTag();
        spoken.forEach(wall -> said.putBoolean(wall.name(), true));
        tag.put("spoken", said);
        tag.putBoolean("saidNoticing", saidNoticing);
        return tag;
    }

    static SelfModel load(CompoundTag tag) {
        var self = new SelfModel();
        for (Stage stage : Stage.values()) if (stage.name().equals(tag.getString("stage").toUpperCase(Locale.ROOT))) self.stage = stage;
        var counts = tag.getCompound("witnessed");
        for (PatternLaws wall : PatternLaws.values()) if (counts.getInt(wall.name()) > 0) self.witnessed.put(wall, counts.getInt(wall.name()));
        var said = tag.getCompound("spoken");
        for (PatternLaws wall : PatternLaws.values()) if (said.getBoolean(wall.name())) self.spoken.add(wall);
        self.saidNoticing = tag.getBoolean("saidNoticing");
        return self;
    }
}
