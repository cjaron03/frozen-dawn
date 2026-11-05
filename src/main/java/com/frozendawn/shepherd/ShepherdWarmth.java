package com.frozendawn.shepherd;

import net.minecraft.server.level.ServerPlayer;

/** A player adjustment and not a heater: Mimics and Hunters react to those, and rooms do not warm. */
public final class ShepherdWarmth {
    private ShepherdWarmth() {}

    public static float of(ServerPlayer player) {
        var she = ShepherdReactions.loaded(player.getServer());
        return she == null || she.level() != player.level() ? 0 : ShepherdPolicy.warmth(player.distanceTo(she), she.offline());
    }
}
