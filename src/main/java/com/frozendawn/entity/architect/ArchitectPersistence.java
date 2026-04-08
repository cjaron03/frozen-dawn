package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.List;

public final class ArchitectPersistence {

    private static final String KEY_TEXTURE_VARIANT = "TextureVariant";
    private static final String KEY_DESPAWN_TIMER = "DespawnTimer";
    private static final String KEY_CURRENT_ACTION = "CurrentAction";
    private static final String KEY_HAS_OBSERVED = "HasObserved";
    private static final String KEY_OBSERVE_DIRTY = "ObserveDirty";
    private static final String KEY_RANGED_HITS_RECEIVED = "RangedHitsReceived";
    private static final String KEY_HEAL_COOLDOWN = "HealCooldown";
    private static final String KEY_SURFACE_Y = "SurfaceY";
    private static final String KEY_TOWER_ENCOUNTER = "TowerEncounter";
    private static final String KEY_TOWER_ENCOUNTER_ID = "TowerEncounterId";
    private static final String KEY_SCAFFOLD_ICE = "ScaffoldIce";
    private static final String KEY_TACTICAL_ICE = "TacticalIce";
    private static final String KEY_LAST_KNOWN_PLAYER_POS = "LastKnownPlayerPos";
    private static final String KEY_LAST_OBSERVED_POS = "LastObservedPos";

    private ArchitectPersistence() {
    }

    public record CoreState(
            int textureVariant,
            int despawnTimer,
            int currentAction,
            boolean towerEncounter,
            long towerEncounterId
    ) {
    }

    public static void writeCoreState(
            CompoundTag tag,
            int textureVariant,
            int despawnTimer,
            int currentAction,
            boolean towerEncounter,
            long towerEncounterId
    ) {
        tag.putInt(KEY_TEXTURE_VARIANT, textureVariant);
        tag.putInt(KEY_DESPAWN_TIMER, despawnTimer);
        tag.putInt(KEY_CURRENT_ACTION, currentAction);
        tag.putBoolean(KEY_TOWER_ENCOUNTER, towerEncounter);
        tag.putLong(KEY_TOWER_ENCOUNTER_ID, towerEncounterId);
    }

    public static CoreState readCoreState(CompoundTag tag) {
        return new CoreState(
                tag.getInt(KEY_TEXTURE_VARIANT),
                tag.getInt(KEY_DESPAWN_TIMER),
                tag.getInt(KEY_CURRENT_ACTION),
                tag.getBoolean(KEY_TOWER_ENCOUNTER),
                tag.contains(KEY_TOWER_ENCOUNTER_ID) ? tag.getLong(KEY_TOWER_ENCOUNTER_ID) : Long.MIN_VALUE
        );
    }

    public static void writeObservationMemory(CompoundTag tag, ArchitectObservationMemory observationMemory) {
        tag.putBoolean(KEY_HAS_OBSERVED, observationMemory.hasObserved());
        tag.putBoolean(KEY_OBSERVE_DIRTY, observationMemory.isObserveDirty());
        putOptionalBlockPos(tag, KEY_LAST_KNOWN_PLAYER_POS, observationMemory.getLastKnownPlayerPos());
        putOptionalBlockPos(tag, KEY_LAST_OBSERVED_POS, observationMemory.getLastObservedPos());
    }

    public static void readObservationMemory(CompoundTag tag, ArchitectObservationMemory observationMemory) {
        observationMemory.setHasObserved(tag.getBoolean(KEY_HAS_OBSERVED));
        observationMemory.setObserveDirty(tag.getBoolean(KEY_OBSERVE_DIRTY));
        observationMemory.setObserveTicks(0);
        observationMemory.setObserveTargetTicks(0);
        observationMemory.setLastKnownPlayerPos(getOptionalBlockPos(tag, KEY_LAST_KNOWN_PLAYER_POS));
        observationMemory.setLastObservedPos(getOptionalBlockPos(tag, KEY_LAST_OBSERVED_POS));
    }

    public static void writeCombatState(CompoundTag tag, ArchitectCombatState combatState) {
        tag.putInt(KEY_RANGED_HITS_RECEIVED, combatState.rangedHitsReceived);
        tag.putInt(KEY_HEAL_COOLDOWN, combatState.healCooldown);
    }

    public static void readCombatState(CompoundTag tag, ArchitectCombatState combatState) {
        combatState.rangedHitsReceived = tag.getInt(KEY_RANGED_HITS_RECEIVED);
        combatState.healCooldown = tag.getInt(KEY_HEAL_COOLDOWN);
    }

    public static void writeApproachState(
            CompoundTag tag,
            ArchitectApproachState approachState,
            List<BlockPos> scaffoldIce,
            List<BlockPos> tacticalIce
    ) {
        tag.putInt(KEY_SURFACE_Y, approachState.surfaceY);
        putBlockPosList(tag, KEY_SCAFFOLD_ICE, scaffoldIce);
        putBlockPosList(tag, KEY_TACTICAL_ICE, tacticalIce);
    }

    public static void readApproachState(
            CompoundTag tag,
            ArchitectApproachState approachState,
            List<BlockPos> scaffoldIce,
            List<BlockPos> tacticalIce
    ) {
        approachState.surfaceY = tag.getInt(KEY_SURFACE_Y);
        readBlockPosList(tag, KEY_SCAFFOLD_ICE, scaffoldIce);
        readBlockPosList(tag, KEY_TACTICAL_ICE, tacticalIce);
    }

    public static void putBlockPosList(CompoundTag tag, String key, List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put(key, list);
    }

    public static void readBlockPosList(CompoundTag tag, String key, List<BlockPos> positions) {
        positions.clear();
        ListTag list = tag.getList(key, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            positions.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }
    }

    public static void putOptionalBlockPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putLong(key, pos.asLong());
        }
    }

    @Nullable
    public static BlockPos getOptionalBlockPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }
}
