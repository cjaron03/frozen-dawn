package com.frozendawn.homo;

/** Score movements for the hostile Master Architect encounter. */
public enum MasterArchitectMusicStage {
    OFF(0),
    KIT(1),
    TETHER(2),
    LAST_WALL(3);

    private final int id;

    MasterArchitectMusicStage(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static MasterArchitectMusicStage fromId(int id) {
        for (MasterArchitectMusicStage stage : values()) {
            if (stage.id == id) {
                return stage;
            }
        }
        return OFF;
    }

    public static MasterArchitectMusicStage forCombatState(
            boolean tetherUsed, boolean lastWallUsed) {
        if (lastWallUsed) {
            return LAST_WALL;
        }
        return tetherUsed ? TETHER : KIT;
    }
}
