package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.util.Mth;

/** Shared server/client tier ids for the Master Architect's non-damaging aura. */
public final class MasterArchitectAuraTier {
    public static final int NONE = 0;
    public static final int PASSIVE = 1;
    public static final int NOTICED = 2;
    public static final int FIGHT = 3;

    private MasterArchitectAuraTier() {
    }

    public static int fromMood(
            ReturnedHearthSavedData.HearthDisposition mood,
            boolean fightActive) {
        if (fightActive || mood == ReturnedHearthSavedData.HearthDisposition.HOSTILE) {
            return FIGHT;
        }
        if (mood == ReturnedHearthSavedData.HearthDisposition.AGITATED) {
            return NOTICED;
        }
        return PASSIVE;
    }

    public static int clamp(int tier) {
        return Mth.clamp(tier, NONE, FIGHT);
    }
}
