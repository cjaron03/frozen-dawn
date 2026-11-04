package com.frozendawn.shepherd;

/** Her numbers, and the one rule about when a conversation has to stop. */
public final class ShepherdPolicy {
    public static final int MAX_HEALTH = 40;
    public static final int TELEPORT_BLOCKS = 24;
    public static final int REBOOT_TICKS = 2400;
    public static final int TALK_RANGE = 8;
    public static final int DANGER_RANGE = 12;
    public static final int TICKS_PER_CHAR = 2;
    public static final int BLIP_EVERY = 3;

    private ShepherdPolicy() {}

    /** The speaker walked off, was hurt, or has a hostile on them within range. */
    public static boolean talkMustClose(double distance, boolean hurt, double nearestHostileOnSpeaker) {
        return distance > TALK_RANGE || hurt || nearestHostileOnSpeaker <= DANGER_RANGE;
    }
}
