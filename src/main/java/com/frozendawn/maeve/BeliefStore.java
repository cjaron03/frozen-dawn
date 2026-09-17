package com.frozendawn.maeve;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Bounded tactical hypotheses. This store has no access to world/player objects. */
final class BeliefStore {
    static final String RANGED = "PLAYER_PREFERS_RANGED";
    static final String RECOVERY = "PLAYER_USES_RECOVERY_UNDER_COVER";
    static final int MAX_CONTACTS = 8;
    private final Map<UUID, Profile> players = new LinkedHashMap<>();

    void record(UUID player, UUID observer, String dimension, BlockPos position,
                long now, String pattern, boolean supporting, String action) {
        Profile profile = players.get(player);
        if (profile == null) {
            if (players.size() == BeliefPolicy.MAX_PLAYERS) evictPlayer();
            profile = new Profile(player);
            players.put(player, profile);
        }
        profile.contact(now);
        profile.observers.remove(observer);
        profile.observers.put(observer, dimension);
        if (profile.observers.size() > MAX_CONTACTS) profile.observers.remove(profile.observers.keySet().iterator().next());
        Belief belief = profile.beliefs.get(pattern);
        if (belief == null) {
            if (profile.beliefs.size() == BeliefPolicy.MAX_BELIEFS) profile.evictBelief();
            belief = new Belief(pattern);
            profile.beliefs.put(pattern, belief);
        }
        belief.record(new ObservedEvidence(observer, profile.encounter, dimension, position, now, action, supporting));
    }

    boolean contact(UUID player, long now) {
        Profile profile = players.get(player);
        if (profile == null) return false;
        profile.contact(now);
        return true;
    }

    List<UUID> players() { return players.keySet().stream().sorted().toList(); }
    int size() { return players.size(); }
    int beliefCount() { return players.values().stream().mapToInt(p -> p.beliefs.size()).sum(); }
    void clear() { players.clear(); }

    List<Contact> contacts() {
        return players.values().stream().flatMap(p -> p.observers.entrySet().stream()
                .map(e -> new Contact(p.player, e.getKey(), e.getValue()))).toList();
    }

    record Contact(UUID player, UUID observer, String dimension) { }

    List<MaeveDirector.BeliefSnapshot> snapshot(UUID player, long now) {
        Profile profile = players.get(player);
        return profile == null ? List.of() : profile.beliefs.values().stream()
                .sorted(Comparator.comparing(b -> b.pattern)).map(b -> b.snapshot(now)).toList();
    }

    private void evictPlayer() {
        UUID oldest = players.values().stream().min(Comparator.comparingLong((Profile p) -> p.lastContact)
                .thenComparing(p -> p.player)).orElseThrow().player;
        players.remove(oldest);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag profiles = new ListTag();
        players.values().forEach(p -> profiles.add(p.save()));
        tag.put("players", profiles);
        return tag;
    }

    static BeliefStore load(CompoundTag tag) {
        BeliefStore store = new BeliefStore();
        for (Tag raw : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("player")) continue;
            Profile profile = new Profile(entry.getUUID("player"));
            profile.lastContact = Math.max(0, entry.getLong("lastContact"));
            profile.encounter = entry.hasUUID("encounter") ? entry.getUUID("encounter") : UUID.randomUUID();
            for (Tag rawContact : entry.getList("observers", Tag.TAG_COMPOUND)) {
                CompoundTag contact = (CompoundTag) rawContact;
                if (profile.observers.size() == MAX_CONTACTS) break;
                if (contact.hasUUID("id") && ResourceLocation.tryParse(contact.getString("dimension")) != null) {
                    profile.observers.put(contact.getUUID("id"), contact.getString("dimension"));
                }
            }
            for (Tag value : entry.getList("beliefs", Tag.TAG_COMPOUND)) {
                Belief belief = Belief.load((CompoundTag) value);
                if (belief == null) continue;
                profile.beliefs.put(belief.pattern, belief);
                if (profile.beliefs.size() > BeliefPolicy.MAX_BELIEFS) profile.evictBelief();
            }
            if (profile.beliefs.isEmpty()) continue;
            store.players.put(profile.player, profile);
            if (store.players.size() > BeliefPolicy.MAX_PLAYERS) store.evictPlayer();
        }
        return store;
    }

    private static final class Profile {
        final UUID player;
        final Map<String, Belief> beliefs = new LinkedHashMap<>();
        final Map<UUID, String> observers = new LinkedHashMap<>();
        UUID encounter;
        long lastContact;

        Profile(UUID player) { this.player = player; }

        void contact(long now) {
            if (encounter == null || now < lastContact || now - lastContact >= BeliefPolicy.ENCOUNTER_GAP) {
                encounter = UUID.randomUUID();
            }
            lastContact = now;
        }

        void evictBelief() {
            String oldest = beliefs.values().stream().min(Comparator.comparingLong(Belief::lastObserved)
                    .thenComparing(b -> b.pattern)).orElseThrow().pattern;
            beliefs.remove(oldest);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player);
            if (encounter != null) tag.putUUID("encounter", encounter);
            tag.putLong("lastContact", lastContact);
            ListTag entries = new ListTag();
            beliefs.values().forEach(b -> entries.add(b.save()));
            tag.put("beliefs", entries);
            ListTag contacts = new ListTag();
            observers.forEach((id, dimension) -> {
                CompoundTag contact = new CompoundTag();
                contact.putUUID("id", id);
                contact.putString("dimension", dimension);
                contacts.add(contact);
            });
            tag.put("observers", contacts);
            return tag;
        }
    }
}
