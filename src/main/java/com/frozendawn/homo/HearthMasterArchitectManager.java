package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * Reconciles the single persistent apex bound to the INTACT Major Hearth.
 */
public final class HearthMasterArchitectManager {
    private static final double ADOPTION_RADIUS = 20.0D;
    private static final double DEBUG_REMOVAL_RADIUS = 64.0D;

    private static long mastersSpawned;
    private static long mastersAdopted;
    private static long mastersDefeated;
    private static String lastFailure = "none";

    private HearthMasterArchitectManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD
                || level.getGameTime() % HearthMasterArchitectPolicy.CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        reconcile(level);
    }

    public static int reconcileNow(ServerLevel level) {
        return reconcile(level);
    }

    public static DebugRespawnResult respawnForDebug(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null || !level.isLoaded(hearth.center())) {
            return new DebugRespawnResult(false, 0, 0);
        }

        AABB area = new AABB(hearth.center()).inflate(DEBUG_REMOVAL_RADIUS);
        int removed = 0;
        for (ArchitectEntity architect : level.getEntitiesOfClass(ArchitectEntity.class, area,
                candidate -> candidate.isBoundToHearthMasterArchitect(hearth.id()))) {
            architect.discard();
            removed++;
        }
        data.resetMasterArchitectForDebug(hearth.id());
        int spawned = reconcile(level);
        return new DebugRespawnResult(true, removed, spawned);
    }

    public static void recordDefeat(ServerLevel level, UUID hearthId, UUID entityId) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        if (!data.markMasterArchitectDefeated(
                hearthId, entityId, level.getGameTime())) {
            return;
        }
        HearthMasterArchitectWeatherManager.onMasterDefeated(level, hearthId);
        mastersDefeated++;
        FrozenDawn.LOGGER.info(
                "Master Architect {} defeated at Major Hearth {}; apex will not respawn",
                shortId(entityId), shortId(hearthId));
    }

    public static String statusLine() {
        return "spawned=" + mastersSpawned
                + " adopted=" + mastersAdopted
                + " defeated=" + mastersDefeated
                + " lastFailure=" + lastFailure;
    }

    public static String phaseStatus(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            return "unavailable: Major Hearth does not exist";
        }
        UUID entityId = hearth.masterArchitectEntityId().orElse(null);
        if (entityId == null) {
            return hearth.masterArchitectDefeated()
                    ? "unavailable: Master Architect was defeated"
                    : "unavailable: Master Architect is not bound";
        }
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof ArchitectEntity master)
                || !master.isBoundToHearthMasterArchitect(hearth.id())) {
            return "unavailable: bound Master Architect is not loaded";
        }

        float maxHealth = master.getMaxHealth();
        float healthFraction = maxHealth > 0.0F
                ? master.getHealth() / maxHealth
                : 0.0F;
        MasterArchitectCombatPhase phase = master.getMasterCombatPhase();
        return String.format(Locale.ROOT,
                "phase=%s health=%.1f/%.1f (%.1f%%) next=%.1f%% entity=%s",
                phase.serializedName(), master.getHealth(), maxHealth,
                healthFraction * 100.0F,
                MasterArchitectPhasePolicy.nextThreshold(phase) * 100.0F,
                shortId(entityId));
    }

    public static void reset() {
        mastersSpawned = 0L;
        mastersAdopted = 0L;
        mastersDefeated = 0L;
        lastFailure = "none";
    }

    private static int reconcile(ServerLevel level) {
        if (!FrozenDawnConfig.ENABLE_ARCHITECT.get()) {
            return 0;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null
                || !HearthMasterArchitectPolicy.canHostMasterArchitect(hearth)
                || hearth.masterArchitectDefeated()
                || !level.isLoaded(hearth.center())) {
            return 0;
        }

        BlockPos anchor = HearthMasterArchitectPolicy.anchor(hearth);
        if (!masterAreaLoaded(level, anchor)) {
            return 0;
        }

        UUID boundId = hearth.masterArchitectEntityId().orElse(null);
        if (boundId != null) {
            Entity bound = level.getEntity(boundId);
            if (bound == null) {
                // The entity may be persistent in an unloaded neighboring chunk.
                return 0;
            }
            if (bound instanceof ArchitectEntity architect
                    && architect.isBoundToHearthMasterArchitect(hearth.id())) {
                architect.refreshMasterArchitectStats();
                lastFailure = "none";
            } else {
                lastFailure = "bound UUID resolved to a non-Master Architect entity";
            }
            return 0;
        }

        ArchitectEntity existing = findExistingMaster(level, hearth.id(), anchor);
        if (existing != null) {
            existing.refreshMasterArchitectStats();
            if (data.bindMasterArchitect(hearth.id(), existing.getUUID())) {
                mastersAdopted++;
                lastFailure = "none";
            }
            return 0;
        }

        HearthPopulationManager.SpawnPoint spawnPoint =
                HearthPopulationManager.findSpawnPoint(level, anchor);
        if (spawnPoint == null) {
            lastFailure = "Master Architect anchor has no collision-safe floor";
            return 0;
        }

        ArchitectEntity master = createMaster(level, hearth, spawnPoint);
        if (master == null) {
            return 0;
        }
        if (!data.bindMasterArchitect(hearth.id(), master.getUUID())) {
            master.discard();
            lastFailure = "Master Architect SavedData binding rejected";
            return 0;
        }

        mastersSpawned++;
        lastFailure = "none";
        FrozenDawn.LOGGER.info(
                "Spawned Master Architect {} for Major Hearth {} at ({}, {}, {})",
                shortId(master.getUUID()), shortId(hearth.id()),
                spawnPoint.blockPos().getX(), String.format("%.3f", spawnPoint.y()),
                spawnPoint.blockPos().getZ());
        return 1;
    }

    @Nullable
    private static ArchitectEntity createMaster(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            HearthPopulationManager.SpawnPoint spawnPoint) {
        ArchitectEntity master = ModEntities.ARCHITECT.get().create(
                level, null, spawnPoint.blockPos(), MobSpawnType.EVENT, true, false);
        if (master == null) {
            lastFailure = "Master Architect entity creation returned null";
            return null;
        }

        master.moveTo(
                spawnPoint.blockPos().getX() + 0.5D,
                spawnPoint.y(),
                spawnPoint.blockPos().getZ() + 0.5D,
                master.getYRot(), master.getXRot());
        if (!level.noCollision(master)) {
            lastFailure = "Master Architect spawn collision changed before insertion";
            return null;
        }

        master.bindToHearthMasterArchitect(
                hearth.id(), spawnPoint.blockPos(),
                HearthMasterArchitectPolicy.textureVariant(hearth.layoutSeed()));
        if (!level.addFreshEntity(master)) {
            lastFailure = "level rejected Master Architect insertion";
            return null;
        }
        return master;
    }

    @Nullable
    private static ArchitectEntity findExistingMaster(
            ServerLevel level, UUID hearthId, BlockPos anchor) {
        return level.getEntitiesOfClass(
                        ArchitectEntity.class,
                        new AABB(anchor).inflate(ADOPTION_RADIUS),
                        candidate -> candidate.isAlive()
                                && candidate.isBoundToHearthMasterArchitect(hearthId))
                .stream().findFirst().orElse(null);
    }

    private static boolean masterAreaLoaded(ServerLevel level, BlockPos anchor) {
        for (int xOffset = -16; xOffset <= 16; xOffset += 16) {
            for (int zOffset = -16; zOffset <= 16; zOffset += 16) {
                if (!level.isLoaded(anchor.offset(xOffset, 0, zOffset))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    public record DebugRespawnResult(boolean hearthLoaded, int removed, int spawned) {
    }
}
