package com.frozendawn.event;

import com.frozendawn.compat.curios.CuriosCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class BlizzardGogglesHandler {

    public static final float BLIZZARD_FOG_DISTANCE_BLOCKS = 32.0F;

    private BlizzardGogglesHandler() {
    }

    public static boolean isVisionActive(int phase, float progress) {
        return BlizzardGogglesLogic.isVisionActive(phase, progress);
    }

    static void tick(ServerPlayer player, int phase, float progress) {
        if (player.level().dimension() != Level.OVERWORLD
                || player.isCreative()
                || player.isSpectator()) {
            return;
        }

        if (CuriosCompat.hasBlizzardGogglesEquipped(player) && isVisionActive(phase, progress)) {
            WorldTickHandler.grantAdvancement(player, "foresight");
        }
    }
}
