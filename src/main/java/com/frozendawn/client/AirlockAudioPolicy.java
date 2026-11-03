package com.frozendawn.client;

/** Local mechanical feedback, including the airless portion of an airlock cycle. */
public final class AirlockAudioPolicy {
    private AirlockAudioPolicy() {}

    public static boolean isMachinery(String namespace, String path) {
        return "frozendawn".equals(namespace) && path.startsWith("block.airlock.");
    }

    public static float vacuumGain(double distanceSquared) {
        return distanceSquared >= 0 && distanceSquared <= 16 ? 0.65F : 0;
    }
}
