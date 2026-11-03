package com.frozendawn.maeve;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindSignalMemory;

/** Upload only an acquired frozen acoustic estimate. Visual learning is deliberately excluded. */
final class BlindObservations {
    static void report(MaeveSavedData data, ArchitectEntity actor, BlindSignalMemory.Signal signal) {
        if (!actor.isBlindArchitect() || data.store() == null || !signal.lock(actor.level().getGameTime())) return;
        long now = actor.getServer().overworld().getGameTime();
        String dimension = actor.level().dimension().location().toString();
        var world = data.store().observeContact(signal.subject(), actor.getUUID(), dimension, now);
        var evidence = new ObservedEvidence(actor.getUUID(), java.util.UUID.randomUUID(), dimension,
                signal.region(), now, "ACOUSTIC_ECHO uncertainty=" + signal.uncertainty() + " travel=" + signal.travel(), true, signal.confidence());
        world.event("ACOUSTIC_CONTACT", dimension, signal.region(), evidence);
        data.setDirty();
    }
    private BlindObservations() { }
}
