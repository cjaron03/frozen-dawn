package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.homo.ArchivistPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistent authority for rare Archivists and their arranged collection sites. */
public final class ArchivistSavedData extends SavedData {
    public static final int CURRENT_VERSION = 1;
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_archivists";

    private final Map<Long, RegionRecord> regions = new LinkedHashMap<>();
    private final Map<UUID, SiteRecord> sites = new LinkedHashMap<>();

    public static ArchivistSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ArchivistSavedData::new,
                        ArchivistSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public static ArchivistSavedData load(CompoundTag tag,
                                          HolderLookup.Provider registries) {
        ArchivistSavedData data = new ArchivistSavedData();
        for (Tag raw : tag.getList("regions", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag value)) {
                continue;
            }
            long key = value.getLong("key");
            RegionRecord region = new RegionRecord(key);
            region.activeArchivistId = value.hasUUID("activeArchivistId")
                    ? value.getUUID("activeArchivistId") : null;
            region.lastKnownPos = value.contains("lastKnownPos", Tag.TAG_LONG)
                    ? BlockPos.of(value.getLong("lastKnownPos")) : null;
            region.siteId = value.hasUUID("siteId") ? value.getUUID("siteId") : null;
            region.nextSpawnGameTime = Math.max(0L,
                    value.getLong("nextSpawnGameTime"));
            data.regions.put(key, region);
        }
        for (Tag raw : tag.getList("sites", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag value) || !value.hasUUID("id")
                    || !value.contains("anchor", Tag.TAG_LONG)) {
                continue;
            }
            SiteRecord site = new SiteRecord(value.getUUID("id"),
                    value.getLong("regionKey"),
                    BlockPos.of(value.getLong("anchor")),
                    value.getLong("layoutSeed"));
            for (Tag relicRaw : value.getList("relics", Tag.TAG_COMPOUND)) {
                if (!(relicRaw instanceof CompoundTag relicTag)
                        || !relicTag.hasUUID("id")
                        || !relicTag.contains("stack", Tag.TAG_COMPOUND)) {
                    continue;
                }
                ItemStack stack = ItemStack.parseOptional(registries,
                        relicTag.getCompound("stack"));
                if (stack.isEmpty()) {
                    continue;
                }
                RelicRecord relic = new RelicRecord(
                        relicTag.getUUID("id"), stack,
                        relicTag.getInt("slot"));
                relic.entityId = relicTag.hasUUID("entityId")
                        ? relicTag.getUUID("entityId") : null;
                site.relics.put(relic.id, relic);
            }
            data.sites.put(site.id, site);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", CURRENT_VERSION);
        ListTag regionList = new ListTag();
        for (RegionRecord region : regions.values()) {
            CompoundTag value = new CompoundTag();
            value.putLong("key", region.regionKey);
            if (region.activeArchivistId != null) {
                value.putUUID("activeArchivistId", region.activeArchivistId);
            }
            if (region.lastKnownPos != null) {
                value.putLong("lastKnownPos", region.lastKnownPos.asLong());
            }
            if (region.siteId != null) {
                value.putUUID("siteId", region.siteId);
            }
            value.putLong("nextSpawnGameTime", region.nextSpawnGameTime);
            regionList.add(value);
        }
        tag.put("regions", regionList);

        ListTag siteList = new ListTag();
        for (SiteRecord site : sites.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", site.id);
            value.putLong("regionKey", site.regionKey);
            value.putLong("anchor", site.anchor.asLong());
            value.putLong("layoutSeed", site.layoutSeed);
            ListTag relicList = new ListTag();
            for (RelicRecord relic : site.relics.values()) {
                if (relic.stack.isEmpty()) {
                    continue;
                }
                CompoundTag relicTag = new CompoundTag();
                relicTag.putUUID("id", relic.id);
                relicTag.putInt("slot", relic.slot);
                relicTag.put("stack", relic.stack.save(registries));
                if (relic.entityId != null) {
                    relicTag.putUUID("entityId", relic.entityId);
                }
                relicList.add(relicTag);
            }
            value.put("relics", relicList);
            siteList.add(value);
        }
        tag.put("sites", siteList);
        return tag;
    }

    public RegionRecord region(long key) {
        return regions.computeIfAbsent(key, RegionRecord::new);
    }

    public Optional<RegionRecord> existingRegion(long key) {
        return Optional.ofNullable(regions.get(key));
    }

    public Collection<RegionRecord> regions() {
        return java.util.List.copyOf(regions.values());
    }

    public Optional<SiteRecord> site(UUID id) {
        return Optional.ofNullable(sites.get(id));
    }

    public Optional<SiteRecord> siteForRegion(long regionKey) {
        RegionRecord region = regions.get(regionKey);
        return region == null || region.siteId == null
                ? Optional.empty() : site(region.siteId);
    }

    public SiteRecord ensureSite(long regionKey, BlockPos anchor, long seed) {
        RegionRecord region = region(regionKey);
        if (region.siteId != null && sites.containsKey(region.siteId)) {
            return sites.get(region.siteId);
        }
        SiteRecord site = new SiteRecord(UUID.randomUUID(), regionKey,
                anchor.immutable(), seed);
        sites.put(site.id, site);
        region.siteId = site.id;
        setDirty();
        return site;
    }

    public void bindArchivist(long regionKey, UUID entityId, BlockPos pos,
                              UUID siteId) {
        RegionRecord region = region(regionKey);
        region.activeArchivistId = entityId;
        region.lastKnownPos = pos.immutable();
        region.siteId = siteId;
        setDirty();
    }

    public void updateArchivist(long regionKey, UUID entityId, BlockPos pos) {
        RegionRecord region = region(regionKey);
        if (entityId.equals(region.activeArchivistId)) {
            region.lastKnownPos = pos.immutable();
            setDirty();
        }
    }

    public void releaseArchivist(long regionKey, UUID entityId, long gameTime) {
        RegionRecord region = region(regionKey);
        if (!entityId.equals(region.activeArchivistId)) {
            return;
        }
        region.activeArchivistId = null;
        region.lastKnownPos = null;
        region.nextSpawnGameTime = Math.max(region.nextSpawnGameTime,
                gameTime + ArchivistPolicy.REPLACEMENT_COOLDOWN_TICKS);
        setDirty();
    }

    public Optional<RelicRecord> addRelic(UUID siteId, ItemStack stack,
                                          boolean badge) {
        SiteRecord site = sites.get(siteId);
        if (site == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Set<Integer> occupied = site.occupiedSlots();
        int slot = ArchivistPolicy.firstSlot(badge, occupied);
        if (slot < 0) {
            return Optional.empty();
        }
        RelicRecord relic = new RelicRecord(UUID.randomUUID(), stack.copy(), slot);
        site.relics.put(relic.id, relic);
        setDirty();
        return Optional.of(relic);
    }

    public Optional<RelicRecord> addRelicAtSlot(UUID siteId, ItemStack stack,
                                                int slot) {
        SiteRecord site = sites.get(siteId);
        if (site == null || stack.isEmpty() || slot < 0
                || slot >= ArchivistPolicy.TOTAL_SLOTS
                || site.occupiedSlots().contains(slot)) {
            return Optional.empty();
        }
        RelicRecord relic = new RelicRecord(UUID.randomUUID(), stack.copy(), slot);
        site.relics.put(relic.id, relic);
        setDirty();
        return Optional.of(relic);
    }

    public Optional<ItemStack> claimRelic(UUID siteId, UUID relicId) {
        SiteRecord site = sites.get(siteId);
        RelicRecord removed = site == null ? null : site.relics.remove(relicId);
        if (removed == null) {
            return Optional.empty();
        }
        setDirty();
        return Optional.of(removed.stack.copy());
    }

    public void bindRelicEntity(UUID siteId, UUID relicId, UUID entityId) {
        SiteRecord site = sites.get(siteId);
        RelicRecord relic = site == null ? null : site.relics.get(relicId);
        if (relic != null && !entityId.equals(relic.entityId)) {
            relic.entityId = entityId;
            setDirty();
        }
    }

    public boolean moveRelic(UUID siteId, UUID relicId, int slot) {
        SiteRecord site = sites.get(siteId);
        RelicRecord relic = site == null ? null : site.relics.get(relicId);
        if (relic == null || slot < 0 || slot >= ArchivistPolicy.TOTAL_SLOTS
                || site.occupiedSlots().contains(slot)) {
            return false;
        }
        relic.slot = slot;
        relic.entityId = null;
        setDirty();
        return true;
    }

    public java.util.List<RelicRecord> clearSiteRelics(UUID siteId) {
        SiteRecord site = sites.get(siteId);
        if (site == null || site.relics.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<RelicRecord> removed = java.util.List.copyOf(
                site.relics.values());
        site.relics.clear();
        setDirty();
        return removed;
    }

    public int recordCount() {
        return regions.size();
    }

    public int siteCount() {
        return sites.size();
    }

    public int relicCount() {
        return sites.values().stream().mapToInt(site -> site.relics.size()).sum();
    }

    public void removeSite(UUID siteId) {
        SiteRecord removed = sites.remove(siteId);
        if (removed == null) {
            return;
        }
        RegionRecord region = regions.get(removed.regionKey);
        if (region != null && siteId.equals(region.siteId)) {
            region.siteId = null;
        }
        setDirty();
    }

    public void clearRegionBinding(long regionKey, long nextSpawnGameTime) {
        RegionRecord region = region(regionKey);
        region.activeArchivistId = null;
        region.lastKnownPos = null;
        region.nextSpawnGameTime = Math.max(0L, nextSpawnGameTime);
        setDirty();
    }

    public static final class RegionRecord {
        private final long regionKey;
        private UUID activeArchivistId;
        private BlockPos lastKnownPos;
        private UUID siteId;
        private long nextSpawnGameTime;

        private RegionRecord(long regionKey) {
            this.regionKey = regionKey;
        }

        public long regionKey() {
            return regionKey;
        }

        public Optional<UUID> activeArchivistId() {
            return Optional.ofNullable(activeArchivistId);
        }

        public Optional<BlockPos> lastKnownPos() {
            return Optional.ofNullable(lastKnownPos);
        }

        public Optional<UUID> siteId() {
            return Optional.ofNullable(siteId);
        }

        public long nextSpawnGameTime() {
            return nextSpawnGameTime;
        }
    }

    public static final class SiteRecord {
        private final UUID id;
        private final long regionKey;
        private BlockPos anchor;
        private final long layoutSeed;
        private final Map<UUID, RelicRecord> relics = new LinkedHashMap<>();

        private SiteRecord(UUID id, long regionKey, BlockPos anchor, long layoutSeed) {
            this.id = id;
            this.regionKey = regionKey;
            this.anchor = anchor.immutable();
            this.layoutSeed = layoutSeed;
        }

        public UUID id() {
            return id;
        }

        public long regionKey() {
            return regionKey;
        }

        public BlockPos anchor() {
            return anchor;
        }

        public long layoutSeed() {
            return layoutSeed;
        }

        public Collection<RelicRecord> relics() {
            return java.util.List.copyOf(relics.values());
        }

        public Set<Integer> occupiedSlots() {
            Set<Integer> result = new LinkedHashSet<>();
            for (RelicRecord relic : relics.values()) {
                result.add(relic.slot);
            }
            return result;
        }
    }

    public static final class RelicRecord {
        private final UUID id;
        private final ItemStack stack;
        private int slot;
        private UUID entityId;

        private RelicRecord(UUID id, ItemStack stack, int slot) {
            this.id = id;
            this.stack = stack.copy();
            this.slot = slot;
        }

        public UUID id() {
            return id;
        }

        public ItemStack stack() {
            return stack.copy();
        }

        public int slot() {
            return slot;
        }

        public Optional<UUID> entityId() {
            return Optional.ofNullable(entityId);
        }
    }
}
