package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Owns the persistent moral and combat roles of the Intact Hearth congregation. */
public final class HearthCombatRosterManager {
    private static final double TETHER_CANDIDATE_RANGE_SQUARED = 48.0D * 48.0D;
    private static final Map<UUID, CastWindow> ACTIVE_CASTS = new HashMap<>();
    private static final Set<UUID> ACTIVE_FLOOD_HEARTHS = new HashSet<>();

    private static long rostersCreated;
    private static long residentsSpent;
    private static long casualtiesRecorded;

    private HearthCombatRosterManager() {
    }

    public static void ensureRoster(
            ServerLevel level, UUID hearthId, ServerPlayer aggressor) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data.hearth(hearthId).orElse(null);
        if (hearth == null) {
            return;
        }

        if (!hearth.combatRosterInitialized()) {
            List<ResidentCandidate> candidates = residentCandidates(hearth);
            Map<UUID, HearthEncounterRole> assignments = new LinkedHashMap<>();
            candidates.forEach(candidate -> assignments.put(
                    candidate.entityId(), HearthEncounterRole.RESERVED));

            List<ResidentCandidate> living = candidates.stream()
                    .filter(candidate -> isLivingMob(level.getEntity(candidate.entityId())))
                    .sorted(Comparator.comparingInt(ResidentCandidate::dispatchPriority)
                            .thenComparing(candidate -> candidate.entityId().toString()))
                    .toList();
            int dispatchCount = HearthCombatRosterPolicy.dispatchedCountWithReserve(
                    living.size(), MasterArchitectCombatPolicy.TETHER_MAX_MEMBERS);
            for (int index = 0; index < dispatchCount; index++) {
                assignments.put(living.get(index).entityId(),
                        HearthEncounterRole.DISPATCHED);
            }

            if (data.initializeCombatRoster(hearthId, assignments)) {
                rostersCreated++;
                FrozenDawn.LOGGER.info(
                        "Initialized Hearth combat roster {} with {} dispatched and {} tether reserves",
                        shortId(hearthId), dispatchCount,
                        Math.max(0, assignments.size() - dispatchCount));
                hearth = data.hearth(hearthId).orElse(hearth);
            }
        }

        applyRoster(level, hearth, aggressor);
    }

    public static HearthEncounterRole role(
            ServerLevel level, UUID hearthId, UUID entityId) {
        return ReturnedHearthSavedData.get(level.getServer())
                .encounterRole(hearthId, entityId);
    }

    public static boolean canEngagePlayer(
            ServerLevel level, UUID hearthId, UUID entityId) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data.hearth(hearthId).orElse(null);
        return !ACTIVE_FLOOD_HEARTHS.contains(hearthId)
                && (hearth == null || !hearth.combatRosterInitialized()
                || HearthCombatRosterPolicy.canAttack(
                        data.encounterRole(hearthId, entityId)))
                && activeCastWindow(level, hearthId) == null;
    }

    /** Clears transient retaliation from bystanders, tether nodes, and spent survivors. */
    public static boolean enforcePassiveRole(
            ServerLevel level, UUID hearthId, Mob resident) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data.hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.combatRosterInitialized()) {
            return false;
        }

        if (ACTIVE_FLOOD_HEARTHS.contains(hearthId)) {
            resident.setTarget(null);
            resident.setLastHurtByMob(null);
            resident.getNavigation().stop();
            resident.setPose(Pose.CROUCHING);
            return true;
        }
        if (resident.getPose() == Pose.CROUCHING) {
            resident.setPose(Pose.STANDING);
        }

        HearthEncounterRole role = data.encounterRole(hearthId, resident.getUUID());
        if (role == HearthEncounterRole.UNASSIGNED) {
            data.setEncounterRole(
                    hearthId, resident.getUUID(), HearthEncounterRole.RESERVED);
            role = HearthEncounterRole.RESERVED;
        }
        CastWindow castWindow = activeCastWindow(level, hearthId);
        if (HearthCombatRosterPolicy.canAttack(role) && castWindow != null) {
            spaceForCast(level, resident, castWindow);
            return true;
        }
        if (HearthCombatRosterPolicy.canAttack(role)) {
            return false;
        }

        resident.setTarget(null);
        resident.setLastHurtByMob(null);
        resident.getNavigation().stop();
        return true;
    }

    public static List<LivingEntity> beginTether(
            ServerLevel level, UUID hearthId, LivingEntity master, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data.hearth(hearthId).orElse(null);
        if (hearth == null || !hearth.combatRosterInitialized()) {
            return List.of();
        }

        List<LivingEntity> selected = hearth.combatRoster().entrySet().stream()
                .filter(entry -> HearthCombatRosterPolicy.canBecomeTether(entry.getValue()))
                .map(entry -> level.getEntity(entry.getKey()))
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(LivingEntity::isAlive)
                .filter(entity -> entity != master
                        && entity.distanceToSqr(master) <= TETHER_CANDIDATE_RANGE_SQUARED)
                .sorted(Comparator.comparingDouble(
                                (LivingEntity entity) -> master.distanceToSqr(entity))
                        .thenComparing(entity -> entity.getUUID().toString()))
                .limit(limit)
                .toList();
        selected.forEach(entity -> data.setEncounterRole(
                hearthId, entity.getUUID(), HearthEncounterRole.TETHERED));
        selected.stream()
                .filter(Mob.class::isInstance)
                .map(Mob.class::cast)
                .forEach(resident -> {
                    resident.setTarget(null);
                    resident.setLastHurtByMob(null);
                    resident.getNavigation().stop();
                    resident.getLookControl().setLookAt(master, 35.0F, 35.0F);
                });
        return selected;
    }

    public static void signalMasterCast(
            ServerLevel level, UUID hearthId, LivingEntity master,
            ServerPlayer target, int durationTicks) {
        ACTIVE_CASTS.put(hearthId, new CastWindow(
                level.getGameTime() + Math.max(1, durationTicks),
                master.getUUID(),
                target.getUUID()));
    }

    public static boolean markSpent(
            ServerLevel level, UUID hearthId, UUID entityId) {
        boolean changed = ReturnedHearthSavedData.get(level.getServer())
                .setEncounterRole(hearthId, entityId, HearthEncounterRole.SPENT);
        if (changed) {
            residentsSpent++;
        }
        return changed;
    }

    public static void releaseTethers(ServerLevel level, UUID hearthId) {
        ReturnedHearthSavedData.get(level.getServer()).releaseEncounterTethers(hearthId);
    }

    public static void onMasterDefeated(ServerLevel level, UUID hearthId) {
        setFloodKneeling(level, hearthId, false);
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        data.pacifyCombatRoster(hearthId);
        ReturnedHearthSavedData.HearthRecord hearth = data.hearth(hearthId).orElse(null);
        if (hearth == null) {
            return;
        }
        for (UUID entityId : hearth.combatRoster().keySet()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof Mob mob && mob.isAlive()) {
                mob.setTarget(null);
                mob.setLastHurtByMob(null);
                mob.getNavigation().stop();
            }
        }
    }

    public static DeathResult recordResidentDeath(
            ServerLevel level, UUID hearthId, UUID entityId,
            @Nullable HearthPopulationRole populationRole, DamageSource source) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        HearthEncounterRole role = data.encounterRole(hearthId, entityId);
        ServerPlayer player = source.getEntity() instanceof ServerPlayer directPlayer
                ? directPlayer
                : null;
        boolean permanentCasualty = HearthCombatRosterPolicy.recordsPermanentCasualty(
                role, player != null);
        if (role == HearthEncounterRole.TETHERED) {
            data.setEncounterRole(hearthId, entityId, HearthEncounterRole.SPENT);
        }

        boolean bindingChanged = populationRole != null
                && HearthPopulationManager.recordResidentDeath(
                        level, hearthId, populationRole, entityId, permanentCasualty);
        boolean casualtyChanged = permanentCasualty
                && data.recordCongregationCasualty(
                        player.getUUID(), hearthId, entityId, level.getGameTime());
        if (casualtyChanged) {
            casualtiesRecorded++;
            FrozenDawn.LOGGER.info(
                    "Recorded permanent congregation casualty {} for player {} at Hearth {} (role={})",
                    shortId(entityId), player.getGameProfile().getName(),
                    shortId(hearthId), role.serializedName());
        }
        return new DeathResult(role, bindingChanged, casualtyChanged);
    }

    public static boolean suppressPopulationReplacement(
            ReturnedHearthSavedData.HearthRecord hearth) {
        return HearthCombatRosterPolicy.suppressReplacement(
                hearth.combatRosterInitialized(),
                hearth.masterArchitectEntityId().isPresent()
                        && !hearth.masterArchitectDefeated());
    }

    public static String statusLine() {
        return "rosters=" + rostersCreated
                + " spent=" + residentsSpent
                + " casualties=" + casualtiesRecorded;
    }

    public static void reset() {
        rostersCreated = 0L;
        residentsSpent = 0L;
        casualtiesRecorded = 0L;
        ACTIVE_CASTS.clear();
        ACTIVE_FLOOD_HEARTHS.clear();
    }

    public static FloodPopulation floodPopulation(
            ServerLevel level, UUID hearthId) {
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(level.getServer()).hearth(hearthId).orElse(null);
        if (hearth == null) {
            return new FloodPopulation(0, 0, 0.0F);
        }
        int maximum = hearth.combatRoster().size();
        int surviving = 0;
        for (UUID entityId : hearth.combatRoster().keySet()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                surviving++;
            }
        }
        float strength = MasterArchitectFloodPolicy.strength(surviving, maximum);
        return new FloodPopulation(surviving, maximum, strength);
    }

    public static void setFloodKneeling(
            ServerLevel level, UUID hearthId, boolean active) {
        if (active) {
            ACTIVE_FLOOD_HEARTHS.add(hearthId);
        } else {
            ACTIVE_FLOOD_HEARTHS.remove(hearthId);
        }
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(level.getServer()).hearth(hearthId).orElse(null);
        if (hearth == null) {
            return;
        }
        for (UUID entityId : hearth.combatRoster().keySet()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof Mob resident && resident.isAlive()) {
                resident.setTarget(null);
                resident.setLastHurtByMob(null);
                resident.getNavigation().stop();
                resident.setPose(active ? Pose.CROUCHING : Pose.STANDING);
            }
        }
    }

    /** Gives every loaded resident the same one-tick acknowledgement of an aura escalation. */
    public static void signalAuraTierChange(
            ServerLevel level, UUID hearthId, LivingEntity master, int tier) {
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(level.getServer()).hearth(hearthId).orElse(null);
        if (hearth == null) {
            return;
        }
        for (UUID entityId : hearth.combatRoster().keySet()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof Mob resident && resident.isAlive()) {
                resident.getNavigation().stop();
                resident.getLookControl().setLookAt(master, 45.0F, 45.0F);
            }
        }
        FrozenDawn.LOGGER.debug(
                "Master aura tier changed at Hearth {}: tier={}", shortId(hearthId), tier);
    }

    @Nullable
    private static CastWindow activeCastWindow(ServerLevel level, UUID hearthId) {
        CastWindow window = ACTIVE_CASTS.get(hearthId);
        if (window == null) {
            return null;
        }
        if (level.getGameTime() > window.expiresAtGameTime()) {
            ACTIVE_CASTS.remove(hearthId);
            return null;
        }
        return window;
    }

    private static void spaceForCast(
            ServerLevel level, Mob resident, CastWindow window) {
        resident.setTarget(null);
        resident.setLastHurtByMob(null);
        Entity targetEntity = level.getEntity(window.targetId());
        if (!(targetEntity instanceof LivingEntity target)) {
            resident.getNavigation().stop();
            return;
        }
        Vec3 away = resident.position().subtract(target.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            Entity masterEntity = level.getEntity(window.masterId());
            away = masterEntity == null
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : resident.position().subtract(masterEntity.position());
        }
        if (resident.distanceToSqr(target) >= 7.0D * 7.0D) {
            resident.getNavigation().stop();
            resident.getLookControl().setLookAt(target, 30.0F, 30.0F);
            return;
        }
        if (resident.tickCount % 8 == 0 || resident.getNavigation().isDone()) {
            Vec3 destination = resident.position().add(
                    away.normalize().scale(4.0D));
            resident.getNavigation().moveTo(
                    destination.x, destination.y, destination.z, 0.92D);
        }
    }

    private static void applyRoster(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            ServerPlayer aggressor) {
        for (Map.Entry<UUID, HearthEncounterRole> entry : hearth.combatRoster().entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            mob.getNavigation().stop();
            if (HearthCombatRosterPolicy.canAttack(entry.getValue())) {
                mob.setTarget(aggressor);
                mob.getLookControl().setLookAt(aggressor, 45.0F, 45.0F);
            } else {
                mob.setTarget(null);
                mob.setLastHurtByMob(null);
                mob.getLookControl().setLookAt(aggressor, 30.0F, 30.0F);
            }
        }
    }

    private static List<ResidentCandidate> residentCandidates(
            ReturnedHearthSavedData.HearthRecord hearth) {
        Map<UUID, ResidentCandidate> candidates = new LinkedHashMap<>();
        hearth.populationResidents().forEach(binding -> binding.entityId().ifPresent(entityId ->
                candidates.put(entityId, new ResidentCandidate(
                        entityId, dispatchPriority(binding.role())))));
        hearth.watcherEntityId().ifPresent(entityId -> candidates.putIfAbsent(
                entityId, new ResidentCandidate(entityId, 3)));
        hearth.architectAssessorEntityId().ifPresent(entityId -> candidates.putIfAbsent(
                entityId, new ResidentCandidate(entityId, 5)));
        return new ArrayList<>(candidates.values());
    }

    private static int dispatchPriority(HearthPopulationRole role) {
        return switch (role) {
            case HUNTER -> 0;
            case RETURNED -> 1;
            case MIMIC -> 2;
            case ARCHITECT -> 4;
        };
    }

    private static boolean isLivingMob(Entity entity) {
        return entity instanceof Mob mob && mob.isAlive();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private record ResidentCandidate(UUID entityId, int dispatchPriority) {
    }

    private record CastWindow(
            long expiresAtGameTime, UUID masterId, UUID targetId) {
    }

    public record DeathResult(
            HearthEncounterRole role, boolean bindingChanged, boolean casualtyRecorded) {
    }

    public record FloodPopulation(
            int survivingResidents, int maximumResidents, float strength) {
    }
}
