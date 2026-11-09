package com.frozendawn.lore;

import com.frozendawn.homo.HeartScavengerWaveManager;
import java.util.Optional;

/** The loop's decisions, with no side effects. */
public final class ThaevenLoopPolicy {
    private ThaevenLoopPolicy() {
    }

    /** The world's first takeover shows the crossing, its second the crossing again, and later ones nothing. */
    public static Optional<ThaevenLoopBeat> takeoverBeat(int shownMask) {
        if ((shownMask & ThaevenLoopBeat.CROSSED.bit()) == 0) {
            return Optional.of(ThaevenLoopBeat.CROSSED);
        }
        if ((shownMask & ThaevenLoopBeat.CROSSED_AGAIN.bit()) == 0) {
            return Optional.of(ThaevenLoopBeat.CROSSED_AGAIN);
        }
        return Optional.empty();
    }

    /** The Heart's arena: the major Hearth's live Heart, in its dimension, within the scavenger waves' radius. */
    public static boolean inArena(double distanceSq, boolean heartLive, boolean sameDimension) {
        double radius = HeartScavengerWaveManager.ARENA_RADIUS;
        return heartLive && sameDimension && distanceSq <= radius * radius;
    }
}
