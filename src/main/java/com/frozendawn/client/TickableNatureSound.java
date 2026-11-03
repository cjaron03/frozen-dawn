package com.frozendawn.client;

import net.minecraft.sounds.SoundEvent;

/** The sound engine must keep this initially silent phrase so its fade-in can tick. */
final class TickableNatureSound extends TickableWindSound {
    TickableNatureSound(SoundEvent sound) {
        super(sound, 0, 1, 602);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
