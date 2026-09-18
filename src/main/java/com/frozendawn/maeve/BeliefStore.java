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
    static final String PURSUIT = "PLAYER_PURSUES_WITHDRAWING_ARCHITECT";
    static final String RANGED = "PLAYER_PREFERS_RANGED";
    static final String RECOVERY = "PLAYER_USES_RECOVERY_UNDER_COVER";
    static final int MAX_CONTACTS = 8;
    private final Map<UUID, Profile> players = new LinkedHashMap<>();

    void record(UUID player, UUID observer, String dimension, BlockPos position,
                long now, String pattern, boolean supporting, String action) {
        record(player, observer, dimension, position, now, pattern, supporting, action,
                supporting ? BeliefPolicy.SUPPORT : -BeliefPolicy.CONTRADICTION);
    }

    void record(UUID player, UUID observer, String dimension, BlockPos position,
                long now, String pattern, boolean supporting, String action, double adjustment) {
        Profile profile = profile(player);
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
        boolean contributed = belief.record(new ObservedEvidence(observer, profile.encounter, dimension, position, now, action, supporting), adjustment);
        if (supporting) profile.commitment.confirm(pattern);
        if (!supporting && contributed) profile.commitment.contradict(pattern, now);
    }

    boolean disprove(MaeveDirector.PositionDirective directive, UUID observer, BlockPos obstruction, long now) {
        var profile = players.get(directive.player());
        if (profile == null || !directive.encounter().equals(profile.encounter)) return false;
        var belief = profile.beliefs.get(directive.pattern());
        if (belief == null) return false;
        boolean contributed = belief.record(new ObservedEvidence(observer, profile.encounter,
                directive.evidence().dimension(), obstruction, now, "ACCESS_POINT_DIRECTLY_DISPROVED", false), -.65);
        if (contributed) profile.commitment.contradict(directive.pattern(), now);
        return true;
    }

    private Profile profile(UUID player) {
        Profile profile = players.get(player);
        if (profile == null) {
            if (players.size() == BeliefPolicy.MAX_PLAYERS) evictPlayer();
            profile = new Profile(player); players.put(player, profile);
        }
        return profile;
    }

    WorldModel world(UUID player) { var profile = players.get(player); return profile == null ? null : profile.world; }
    WorldModel observeContact(UUID player, UUID observer, String dimension, long now) {
        profile(player); contact(player, observer, dimension, now); return world(player);
    }

    boolean contact(UUID player, long now) {
        Profile profile = players.get(player);
        if (profile == null) return false;
        profile.contact(now);
        return true;
    }

    boolean contact(UUID player, UUID observer, String dimension, long now) {
        if (!contact(player, now)) return false;
        var contacts = players.get(player).observers;
        contacts.remove(observer);
        contacts.put(observer, dimension);
        if (contacts.size() > MAX_CONTACTS) contacts.remove(contacts.keySet().iterator().next());
        return true;
    }

    CommitmentPolicy commitment(UUID player) {
        Profile profile = players.get(player);
        return profile == null ? null : profile.commitment;
    }

    CommitmentPolicy commitmentFor(UUID observer, long now) {
        for (Profile profile : players.values()) {
            var selected = profile.commitment.selected();
            if (selected != null && selected.observer().equals(observer) && profile.commitment.active(now) != null) return profile.commitment;
        }
        return null;
    }

    List<CommitmentPolicy> commitments() { return players.values().stream().map(p -> p.commitment).toList(); }

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
            profile.commitment = CommitmentPolicy.load(entry.getCompound("commitment"));
            profile.world = WorldModel.load(entry.getCompound("world"));
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
            if (profile.beliefs.isEmpty() && profile.world.empty()) continue;
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
        CommitmentPolicy commitment = new CommitmentPolicy();
        WorldModel world = new WorldModel();

        Profile(UUID player) { this.player = player; }

        void contact(long now) {
            if (encounter == null || now < lastContact || now - lastContact >= BeliefPolicy.ENCOUNTER_GAP) {
                encounter = UUID.randomUUID();
                commitment.begin(encounter, List.copyOf(beliefs.values()), now);
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
            tag.put("commitment", commitment.save());
            tag.put("world", world.save());
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
