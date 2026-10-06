package com.frozendawn.maeve;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** The single §9.4b claim and its cooldown. Tactical state: lives in the erasable save and is wiped on ERASED. */
final class ScribeMemory {
    record Claim(UUID scribe, UUID subject, String dimension, BlockPos watch, String watchLabel, long designatedAt) {
        Claim { watch = watch == null ? null : watch.immutable(); }
        long expiresAt() { return designatedAt + ScribePolicy.LIFETIME; }
    }
    Claim active;
    long lastEnded = -1;

    /** A lost or unloaded Scribe still ends at its lifetime, and the cooldown counts from then. */
    boolean expire(long now) {
        if (active == null || now >= active.designatedAt() && now < active.expiresAt()) return false;
        end(now >= active.designatedAt() ? active.expiresAt() : now);
        return true;
    }

    void end(long now) { active = null; lastEnded = now; }

    CompoundTag save() {
        var tag = new CompoundTag(); tag.putLong("lastEnded", lastEnded);
        if (active != null) {
            var claim = new CompoundTag(); claim.putUUID("scribe", active.scribe()); claim.putUUID("subject", active.subject());
            claim.putString("dimension", active.dimension()); claim.putString("watchLabel", active.watchLabel());
            if (active.watch() != null) claim.putLong("watch", active.watch().asLong());
            claim.putLong("designatedAt", active.designatedAt()); tag.put("active", claim);
        }
        return tag;
    }

    static ScribeMemory load(CompoundTag tag) {
        var memory = new ScribeMemory();
        memory.lastEnded = tag.contains("lastEnded") ? Math.max(-1, tag.getLong("lastEnded")) : -1;
        var claim = tag.getCompound("active");
        if (claim.hasUUID("scribe") && claim.hasUUID("subject") && ResourceLocation.tryParse(claim.getString("dimension")) != null)
            memory.active = new Claim(claim.getUUID("scribe"), claim.getUUID("subject"), claim.getString("dimension"),
                    claim.contains("watch") ? BlockPos.of(claim.getLong("watch")) : null, claim.getString("watchLabel"),
                    Math.max(0, claim.getLong("designatedAt")));
        return memory;
    }
}
