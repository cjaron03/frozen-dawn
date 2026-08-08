package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.server.level.ServerLevel;

/** World authority for scheduling and advancing the one-time lunar return. */
public final class PostMaeveMoonManager {
    private static final long VISUAL_SEED_SALT = 0x4d4f4f4e5f524554L;
    private static final long PERIODIC_SYNC_TICKS = 200L;

    private PostMaeveMoonManager() {
    }

    public static void tick(ServerLevel overworld) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(
                overworld.getServer());
        if (!data.maeveErased()) {
            return;
        }

        boolean scheduled = false;
        if (data.postMaeveMoonriseStartDayTime() < 0L
                && aftermathWarningHasPlayed(data)) {
            long dayTime = overworld.getDayTime();
            scheduled = data.schedulePostMaeveMoonrise(
                    PostMaeveMoonPolicy.nextDusk(dayTime),
                    dayTime,
                    visualSeed(overworld));
        }

        boolean wasStarted = data.postMaeveMoonriseStarted();
        long advanced = data.advancePostMaeveMoon(overworld.getDayTime());
        boolean riseStarted = !wasStarted && data.postMaeveMoonriseStarted();
        if (scheduled || riseStarted || advanced > 1L
                || overworld.getGameTime() % PERIODIC_SYNC_TICKS == 0L) {
            PostMaeveWorldState.syncAll(overworld.getServer());
        }
    }

    public static long visualSeed(ServerLevel overworld) {
        return mix64(overworld.getSeed() ^ VISUAL_SEED_SALT);
    }

    public static PostMaeveMoonPolicy.Snapshot snapshot(
            ReturnedHearthSavedData data) {
        if (!data.maeveErased()
                || data.postMaeveMoonriseStartDayTime() < 0L
                || !data.postMaeveMoonriseStarted()) {
            return PostMaeveMoonPolicy.snapshot(-1L,
                    data.postMaeveMoonVisualSeed());
        }
        return PostMaeveMoonPolicy.snapshot(
                data.postMaeveMoonElapsedDayTicks(),
                data.postMaeveMoonVisualSeed());
    }

    private static boolean aftermathWarningHasPlayed(ReturnedHearthSavedData data) {
        ReturnedHearthSavedData.HearthRecord major = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        return major != null && major.heartMaeveBiologicalWarningPlayed();
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }
}
