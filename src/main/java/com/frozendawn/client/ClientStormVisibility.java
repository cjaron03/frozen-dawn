package com.frozendawn.client;

import net.minecraft.client.Minecraft;

final class ClientStormVisibility {

    private ClientStormVisibility() {}

    static boolean isStormExposed(Minecraft mc) {
        return mc.level != null && mc.player != null
                && mc.level.canSeeSky(mc.player.blockPosition().above());
    }

    static boolean isUndergroundOrCovered(Minecraft mc) {
        return mc.player != null
                && mc.level != null
                && (mc.player.blockPosition().getY() < 50 || !isStormExposed(mc));
    }
}
