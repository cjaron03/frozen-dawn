package com.frozendawn.maeve;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** The single §9.4a claim, its cooldown and what this visit has read. Tactical state: erasable, wiped on ERASED. */
final class PyroMemory {
    record Claim(UUID pyro, UUID subject, String dimension, BlockPos home, long designatedAt) {
        Claim { home = home.immutable(); }
        long expiresAt() { return designatedAt + PyroPolicy.LIFETIME; }
        /** One visit is one encounter: repeated looks from the same post never stack confidence. */
        UUID encounter() { return UUID.nameUUIDFromBytes(("pyro/" + pyro + "/" + designatedAt).getBytes(StandardCharsets.UTF_8)); }
    }
    Claim active;
    long lastEnded = -1;
    /** Complete looks this visit, and how many sightings they uploaded. */
    int scans, sightings;

    boolean expire(long now) {
        if (active == null || now >= active.designatedAt() && now < active.expiresAt()) return false;
        end(now >= active.designatedAt() ? active.expiresAt() : now);
        return true;
    }

    void begin(Claim claim) { active = claim; scans = 0; sightings = 0; }
    void end(long now) { active = null; lastEnded = now; scans = 0; sightings = 0; }

    CompoundTag save() {
        var tag = new CompoundTag(); tag.putLong("lastEnded", lastEnded);
        if (active != null) {
            var claim = new CompoundTag(); claim.putUUID("pyro", active.pyro()); claim.putUUID("subject", active.subject());
            claim.putString("dimension", active.dimension()); claim.putLong("home", active.home().asLong());
            claim.putLong("designatedAt", active.designatedAt()); claim.putInt("scans", scans); claim.putInt("sightings", sightings);
            tag.put("active", claim);
        }
        return tag;
    }

    static PyroMemory load(CompoundTag tag) {
        var memory = new PyroMemory();
        memory.lastEnded = tag.contains("lastEnded") ? Math.max(-1, tag.getLong("lastEnded")) : -1;
        var claim = tag.getCompound("active");
        if (claim.hasUUID("pyro") && claim.hasUUID("subject") && claim.contains("home")
                && ResourceLocation.tryParse(claim.getString("dimension")) != null) {
            memory.active = new Claim(claim.getUUID("pyro"), claim.getUUID("subject"), claim.getString("dimension"),
                    BlockPos.of(claim.getLong("home")), Math.max(0, claim.getLong("designatedAt")));
            memory.scans = Math.max(0, claim.getInt("scans")); memory.sightings = Math.max(0, claim.getInt("sightings"));
        }
        return memory;
    }
}
