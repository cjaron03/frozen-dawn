package com.frozendawn.maeve;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** A physical population cap, not regenerating attention. Unload never frees a population claim. */
final class PawnPopulation {
    static final int MAX_TRACKED = 256;
    record Pawn(String dimension, BlockPos lastPosition) { }
    final Map<UUID, Pawn> pawns = new LinkedHashMap<>();
    boolean overflow;
    boolean register(UUID id, String dimension, BlockPos pos) {
        if (!pawns.containsKey(id) && pawns.size() >= MAX_TRACKED) { boolean changed = !overflow; overflow = true; return changed; }
        Pawn next = new Pawn(dimension, pos.immutable()); return !next.equals(pawns.put(id, next));
    }
    boolean destroyed(UUID id) { return pawns.remove(id) != null; }
    boolean canSpawn(int players) { return !overflow && pawns.size() < ConvergencePolicy.populationLimit(players); }
    CompoundTag save() {
        var t = new CompoundTag(); t.putBoolean("overflow", overflow); var list = new ListTag();
        pawns.forEach((id, p) -> { var r = new CompoundTag(); r.putUUID("id", id); r.putString("dimension", p.dimension()); r.putLong("position", p.lastPosition().asLong()); list.add(r); }); t.put("pawns", list); return t;
    }
    void load(CompoundTag t) {
        overflow = t.getBoolean("overflow");
        for (Tag raw : t.getList("pawns", Tag.TAG_COMPOUND)) { var r = (CompoundTag) raw;
            if (r.hasUUID("id") && ResourceLocation.tryParse(r.getString("dimension")) != null) register(r.getUUID("id"), r.getString("dimension"), BlockPos.of(r.getLong("position")));
            if (pawns.size() == MAX_TRACKED) break;
        }
    }
}
