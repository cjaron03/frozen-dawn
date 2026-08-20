package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.lore.ThaevenRecordId;
import com.frozendawn.lore.ThaevenSemanticKey;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Permanent server authority for player lore archives and semantic context. */
public final class ThaevenLoreSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 3;
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_thaeven_lore";

    private final Map<UUID, PlayerArchive> playerArchives = new LinkedHashMap<>();
    private final EnumMap<ThaevenSemanticKey, Integer> semanticRevisions =
            new EnumMap<>(ThaevenSemanticKey.class);
    private final Map<UUID, BlockPos> velAnRelics = new LinkedHashMap<>();
    private ResourceKey<Level> heartScarDimension;
    private BlockPos heartScarPos;
    private BlockPos firstCrossingVesselPos;
    private BlockPos unthreadingVesselPos;
    private BlockPos humanCarrierCratePos;
    private boolean humanCarrierReconciled;
    private boolean legacyAuthorityReconciled;

    public ThaevenLoreSavedData() {
        for (ThaevenSemanticKey key : ThaevenSemanticKey.values()) {
            semanticRevisions.put(key, 0);
        }
    }

    public static ThaevenLoreSavedData get(MinecraftServer server) {
        ThaevenLoreSavedData data = server.overworld().getDataStorage()
                .computeIfAbsent(
                new SavedData.Factory<>(ThaevenLoreSavedData::new,
                        ThaevenLoreSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
        data.reconcileLegacyAuthority(server);
        return data;
    }

    public static ThaevenLoreSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        ThaevenLoreSavedData data = new ThaevenLoreSavedData();
        ListTag archives = tag.getList("playerArchives", Tag.TAG_COMPOUND);
        for (Tag entry : archives) {
            if (!(entry instanceof CompoundTag archiveTag)
                    || !archiveTag.hasUUID("player")) {
                continue;
            }
            UUID playerId = archiveTag.getUUID("player");
            data.playerArchives.put(playerId, PlayerArchive.load(archiveTag));
        }
        CompoundTag semantics = tag.getCompound("semanticRevisions");
        for (ThaevenSemanticKey key : ThaevenSemanticKey.values()) {
            data.semanticRevisions.put(key,
                    Math.max(0, semantics.getInt(key.name())));
        }
        ListTag relics = tag.getList("velAnRelics", Tag.TAG_COMPOUND);
        for (Tag entry : relics) {
            if (entry instanceof CompoundTag relic && relic.hasUUID("hearth")
                    && relic.contains("pos", Tag.TAG_LONG)) {
                data.velAnRelics.put(relic.getUUID("hearth"),
                        BlockPos.of(relic.getLong("pos")));
            }
        }
        if (tag.contains("heartScarPos", Tag.TAG_LONG)) {
            data.heartScarPos = BlockPos.of(tag.getLong("heartScarPos"));
            ResourceLocation dimension = ResourceLocation.tryParse(
                    tag.getString("heartScarDimension"));
            if (dimension != null) {
                data.heartScarDimension = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        dimension);
            }
        }
        if (tag.contains("firstCrossingVesselPos", Tag.TAG_LONG)) {
            data.firstCrossingVesselPos = BlockPos.of(
                    tag.getLong("firstCrossingVesselPos"));
        }
        if (tag.contains("unthreadingVesselPos", Tag.TAG_LONG)) {
            data.unthreadingVesselPos = BlockPos.of(
                    tag.getLong("unthreadingVesselPos"));
        }
        if (tag.contains("humanCarrierCratePos", Tag.TAG_LONG)) {
            data.humanCarrierCratePos = BlockPos.of(
                    tag.getLong("humanCarrierCratePos"));
        }
        data.humanCarrierReconciled = tag.getBoolean("humanCarrierReconciled");
        data.legacyAuthorityReconciled = tag.getBoolean(
                "legacyAuthorityReconciled");
        if (tag.getInt("dataVersion") != CURRENT_DATA_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", CURRENT_DATA_VERSION);
        ListTag archives = new ListTag();
        for (Map.Entry<UUID, PlayerArchive> entry : playerArchives.entrySet()) {
            CompoundTag archive = entry.getValue().save();
            archive.putUUID("player", entry.getKey());
            archives.add(archive);
        }
        tag.put("playerArchives", archives);
        CompoundTag semantics = new CompoundTag();
        for (Map.Entry<ThaevenSemanticKey, Integer> entry
                : semanticRevisions.entrySet()) {
            semantics.putInt(entry.getKey().name(), entry.getValue());
        }
        tag.put("semanticRevisions", semantics);
        ListTag relics = new ListTag();
        for (Map.Entry<UUID, BlockPos> entry : velAnRelics.entrySet()) {
            CompoundTag relic = new CompoundTag();
            relic.putUUID("hearth", entry.getKey());
            relic.putLong("pos", entry.getValue().asLong());
            relics.add(relic);
        }
        tag.put("velAnRelics", relics);
        if (heartScarPos != null && heartScarDimension != null) {
            tag.putLong("heartScarPos", heartScarPos.asLong());
            tag.putString("heartScarDimension",
                    heartScarDimension.location().toString());
        }
        if (firstCrossingVesselPos != null) {
            tag.putLong("firstCrossingVesselPos",
                    firstCrossingVesselPos.asLong());
        }
        if (unthreadingVesselPos != null) {
            tag.putLong("unthreadingVesselPos",
                    unthreadingVesselPos.asLong());
        }
        if (humanCarrierCratePos != null) {
            tag.putLong("humanCarrierCratePos",
                    humanCarrierCratePos.asLong());
        }
        tag.putBoolean("humanCarrierReconciled", humanCarrierReconciled);
        tag.putBoolean("legacyAuthorityReconciled",
                legacyAuthorityReconciled);
        return tag;
    }

    public ArchiveSnapshot snapshot(UUID playerId) {
        PlayerArchive archive = playerArchives.get(playerId);
        int[] seen = new int[ThaevenRecordId.values().length];
        Arrays.fill(seen, -1);
        if (archive != null) {
            for (ThaevenRecordId record : ThaevenRecordId.values()) {
                seen[record.ordinal()] = archive.seenRevision(record);
            }
        }
        return new ArchiveSnapshot(
                archive == null ? 0L : archive.discoveredMask,
                archive != null && archive.recipeDiscovered,
                seen,
                semanticRevision(ThaevenSemanticKey.ARCHITECT_LID_REVEAL));
    }

    public boolean discoverRecipe(UUID playerId) {
        PlayerArchive archive = archive(playerId);
        if (archive.recipeDiscovered) {
            return false;
        }
        archive.recipeDiscovered = true;
        setDirty();
        return true;
    }

    public boolean grantRecord(UUID playerId, ThaevenRecordId record) {
        PlayerArchive archive = archive(playerId);
        if ((archive.discoveredMask & record.bit()) != 0L) {
            return false;
        }
        archive.discoveredMask |= record.bit();
        setDirty();
        return true;
    }

    public boolean hasRecord(UUID playerId, ThaevenRecordId record) {
        PlayerArchive archive = playerArchives.get(playerId);
        return archive != null && (archive.discoveredMask & record.bit()) != 0L;
    }

    public int currentRevision(ThaevenRecordId record) {
        return record == ThaevenRecordId.THE_PASSAGE
                ? semanticRevision(ThaevenSemanticKey.ARCHITECT_LID_REVEAL)
                : 0;
    }

    public boolean markViewed(
            UUID playerId, ThaevenRecordId record, int revision) {
        PlayerArchive archive = archive(playerId);
        if ((archive.discoveredMask & record.bit()) == 0L) {
            return false;
        }
        int clamped = Math.min(Math.max(0, revision), currentRevision(record));
        if (archive.seenRevision(record) >= clamped) {
            return false;
        }
        archive.seenRevisions.put(record, clamped);
        setDirty();
        return true;
    }

    public int semanticRevision(ThaevenSemanticKey key) {
        return semanticRevisions.getOrDefault(key, 0);
    }

    public boolean unlockSemantic(ThaevenSemanticKey key) {
        if (semanticRevision(key) > 0) {
            return false;
        }
        semanticRevisions.put(key, 1);
        setDirty();
        return true;
    }

    public boolean ensureHeartScarAnchor(MinecraftServer server) {
        if (heartScarPos != null && heartScarDimension != null) {
            return false;
        }
        ReturnedHearthSavedData.HearthRecord major = ReturnedHearthSavedData
                .get(server).hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .orElse(null);
        if (major == null || major.heartCollapseStartGameTime() < 0L
                && !major.heartCollapseComplete()
                && !major.heartMaeveExposed()
                && !ReturnedHearthSavedData.get(server).maeveErased()) {
            return false;
        }
        heartScarDimension = Level.OVERWORLD;
        heartScarPos = major.heartAnchor().orElse(major.center()).immutable();
        setDirty();
        return true;
    }

    public boolean setHeartScarAnchor(
            ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null
                || dimension.equals(heartScarDimension)
                && pos.equals(heartScarPos)) {
            return false;
        }
        heartScarDimension = dimension;
        heartScarPos = pos.immutable();
        setDirty();
        return true;
    }

    public Optional<HeartScarAnchor> heartScarAnchor() {
        return heartScarPos == null || heartScarDimension == null
                ? Optional.empty()
                : Optional.of(new HeartScarAnchor(
                        heartScarDimension, heartScarPos));
    }

    public Optional<BlockPos> velAnRelic(UUID hearthId) {
        return Optional.ofNullable(velAnRelics.get(hearthId));
    }

    public int velAnRelicCount() {
        return velAnRelics.size();
    }

    public List<BlockPos> velAnRelicPositions() {
        return List.copyOf(velAnRelics.values());
    }

    public void bindVelAnRelic(UUID hearthId, BlockPos pos) {
        if (!velAnRelics.containsKey(hearthId)) {
            velAnRelics.put(hearthId, pos.immutable());
            setDirty();
        }
    }

    public void clearVelAnRelic(UUID hearthId) {
        if (velAnRelics.remove(hearthId) != null) {
            setDirty();
        }
    }

    public boolean humanCarrierReconciled() {
        return humanCarrierReconciled;
    }

    public Optional<BlockPos> humanCarrierCratePos() {
        return Optional.ofNullable(humanCarrierCratePos);
    }

    public void markHumanCarrierReconciled(BlockPos cratePos) {
        if (!humanCarrierReconciled
                || !cratePos.equals(humanCarrierCratePos)) {
            humanCarrierReconciled = true;
            humanCarrierCratePos = cratePos.immutable();
            setDirty();
        }
    }

    public Optional<BlockPos> firstCrossingVesselPos() {
        return Optional.ofNullable(firstCrossingVesselPos);
    }

    public void setFirstCrossingVesselPos(BlockPos pos) {
        if (firstCrossingVesselPos == null) {
            firstCrossingVesselPos = pos.immutable();
            setDirty();
        }
    }

    public Optional<BlockPos> unthreadingVesselPos() {
        return Optional.ofNullable(unthreadingVesselPos);
    }

    public void setUnthreadingVesselPos(BlockPos pos) {
        if (unthreadingVesselPos == null) {
            unthreadingVesselPos = pos.immutable();
            setDirty();
        }
    }

    public void clearUnthreadingVesselPos() {
        if (unthreadingVesselPos != null) {
            unthreadingVesselPos = null;
            setDirty();
        }
    }

    public void resetPlayer(UUID playerId) {
        if (playerArchives.remove(playerId) != null) {
            setDirty();
        }
    }

    public void resetSemanticsForDebug() {
        for (ThaevenSemanticKey key : ThaevenSemanticKey.values()) {
            semanticRevisions.put(key, 0);
        }
        for (PlayerArchive archive : playerArchives.values()) {
            archive.seenRevisions.clear();
        }
        setDirty();
    }

    private PlayerArchive archive(UUID playerId) {
        return playerArchives.computeIfAbsent(playerId,
                ignored -> new PlayerArchive());
    }

    private void reconcileLegacyAuthority(MinecraftServer server) {
        if (legacyAuthorityReconciled) {
            return;
        }
        ReturnedHearthSavedData.HearthRecord major = ReturnedHearthSavedData
                .get(server).hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .orElse(null);
        if (major != null && major.decoherenceGranted()) {
            unlockSemantic(ThaevenSemanticKey.ARCHITECT_LID_REVEAL);
        }
        legacyAuthorityReconciled = true;
        setDirty();
    }

    public record ArchiveSnapshot(
            long discoveredMask,
            boolean recipeDiscovered,
            int[] seenRevisions,
            int architectLidRevision) {
    }

    public record HeartScarAnchor(
            ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class PlayerArchive {
        private long discoveredMask;
        private boolean recipeDiscovered;
        private final EnumMap<ThaevenRecordId, Integer> seenRevisions =
                new EnumMap<>(ThaevenRecordId.class);

        private int seenRevision(ThaevenRecordId record) {
            return seenRevisions.getOrDefault(record, -1);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("discoveredMask", discoveredMask);
            tag.putBoolean("recipeDiscovered", recipeDiscovered);
            CompoundTag seen = new CompoundTag();
            for (Map.Entry<ThaevenRecordId, Integer> entry
                    : seenRevisions.entrySet()) {
                seen.putInt(entry.getKey().serializedName(), entry.getValue());
            }
            tag.put("seenRevisions", seen);
            return tag;
        }

        private static PlayerArchive load(CompoundTag tag) {
            PlayerArchive archive = new PlayerArchive();
            archive.discoveredMask = tag.getLong("discoveredMask");
            archive.recipeDiscovered = tag.getBoolean("recipeDiscovered");
            CompoundTag seen = tag.getCompound("seenRevisions");
            for (ThaevenRecordId record : ThaevenRecordId.values()) {
                if (seen.contains(record.serializedName(), Tag.TAG_INT)) {
                    archive.seenRevisions.put(record,
                            Math.max(-1, seen.getInt(record.serializedName())));
                }
            }
            return archive;
        }
    }
}
