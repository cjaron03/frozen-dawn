package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3f;

/** Owns readable threshold warnings and the shared Orsathae escalation presentation. */
public final class HearthBoundaryManager {
    private static final double FX_RADIUS_SQUARED = 72.0D * 72.0D;
    private static final double PARTICLE_RADIUS_SQUARED = 34.0D * 34.0D;
    private static final int PARTICLE_INTERVAL_TICKS = 8;
    private static final int BOUNDARY_PARTICLE_STRIDE = 4;
    private static final DustParticleOptions BOUNDARY_DUST = new DustParticleOptions(
            new Vector3f(0.10F, 0.72F, 0.82F), 0.35F);
    private static final DustParticleOptions INTERACTION_DUST = new DustParticleOptions(
            new Vector3f(0.18F, 0.88F, 0.96F), 0.48F);
    private static final Map<UUID, PlayerBoundaryState> playerStates = new HashMap<>();
    private static final Map<UUID, CachedParticleCues> particleCueCache = new HashMap<>();

    private static long warningsSent;
    private static long boundaryEntries;
    private static long escalationEffects;

    private HearthBoundaryManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        if (level.getGameTime() % PARTICLE_INTERVAL_TICKS == 0L) {
            emitConductParticles(level, data);
        }
        Set<UUID> online = new HashSet<>();
        Map<UUID, ServerPlayer> warningPlayers = new HashMap<>();

        for (ServerPlayer player : level.players()) {
            online.add(player.getUUID());
            HearthBoundaryPolicy.BoundaryContact contact = player.isAlive()
                    && !player.isCreative() && !player.isSpectator()
                    ? HearthBoundaryPolicy.contactAt(data, player.blockPosition()).orElse(null)
                    : null;
            PlayerBoundaryState current = PlayerBoundaryState.from(contact);
            PlayerBoundaryState previous = playerStates.put(player.getUUID(), current);

            if (current.zone() == HearthBoundaryPolicy.Zone.WARNING
                    && !sameZone(previous, current)
                    && !HearthMemoryManager.isPermanentOrsathae(level, player.getUUID())) {
                PacketDistributor.sendToPlayer(
                        player, HearthBoundaryEffectPayload.warning());
                warningsSent++;
            }

            if (current.zone() == HearthBoundaryPolicy.Zone.PROTECTED
                    && previous != null && !sameZone(previous, current)
                    && current.hearthId() != null
                    && !HearthMemoryManager.isPermanentOrsathae(level, player.getUUID())) {
                recordBoundaryEntry(level, data, current.hearthId(), player);
            }

            if (current.zone() == HearthBoundaryPolicy.Zone.WARNING
                    && current.hearthId() != null
                    && !HearthMemoryManager.isPermanentOrsathae(level, player.getUUID())) {
                warningPlayers.merge(current.hearthId(), player,
                        (left, right) -> closerToHearth(data, current.hearthId(), left, right));
            }
        }

        playerStates.keySet().retainAll(online);
        warningPlayers.forEach((hearthId, player) ->
                orientResidents(level, data.hearth(hearthId).orElse(null), player));
    }

    public static void triggerOrsathaeEffect(
            ServerLevel level, UUID hearthId, ServerPlayer violator) {
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(level.getServer()).hearth(hearthId).orElse(null);
        if (hearth == null) {
            return;
        }

        HearthCombatRosterManager.ensureRoster(level, hearthId, violator);
        MasterArchitectFightMusicManager.start(violator);
        for (ServerPlayer observer : level.players()) {
            if (observer.isAlive() && observer.distanceToSqr(hearth.center().getCenter())
                    <= FX_RADIUS_SQUARED) {
                PacketDistributor.sendToPlayer(
                        observer, HearthBoundaryEffectPayload.orsathae());
            }
        }
        escalationEffects++;
        FrozenDawn.LOGGER.info(
                "Homo reliquus boundary response fired for player {} at Hearth {}",
                violator.getGameProfile().getName(), shortId(hearthId));
    }

    public static String statusLine() {
        return "warnings=" + warningsSent
                + " entries=" + boundaryEntries
                + " escalations=" + escalationEffects;
    }

    public static void reset() {
        playerStates.clear();
        particleCueCache.clear();
        warningsSent = 0L;
        boundaryEntries = 0L;
        escalationEffects = 0L;
    }

    private static void emitConductParticles(
            ServerLevel level, ReturnedHearthSavedData data) {
        long particleStep = level.getGameTime() / PARTICLE_INTERVAL_TICKS;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()
                    || HearthMemoryManager.isPermanentOrsathae(level, player.getUUID())) {
                continue;
            }
            for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
                if (player.distanceToSqr(hearth.center().getCenter()) > PARTICLE_RADIUS_SQUARED) {
                    continue;
                }
                List<HearthBoundaryParticlePolicy.ParticleCue> cues = cachedCues(hearth);
                int boundaryIndex = 0;
                for (HearthBoundaryParticlePolicy.ParticleCue cue : cues) {
                    BlockPos worldPos = hearth.center().offset(cue.offset());
                    switch (cue.type()) {
                        case BOUNDARY -> {
                            if (Math.floorMod(boundaryIndex++ + (int) particleStep,
                                    BOUNDARY_PARTICLE_STRIDE) == 0) {
                                sendDust(level, player, BOUNDARY_DUST,
                                        worldPos.getX() + 0.5D,
                                        worldPos.getY() + 0.12D,
                                        worldPos.getZ() + 0.5D, 0.015D);
                            }
                        }
                        case DOOR -> emitDoorCue(level, player, worldPos, cue.facing());
                        case INTERACTION -> sendDust(level, player, INTERACTION_DUST,
                                worldPos.getX() + 0.5D,
                                worldPos.getY() + 1.18D,
                                worldPos.getZ() + 0.5D, 0.025D);
                    }
                }
            }
        }
    }

    private static List<HearthBoundaryParticlePolicy.ParticleCue> cachedCues(
            ReturnedHearthSavedData.HearthRecord hearth) {
        CachedParticleCues cached = particleCueCache.get(hearth.id());
        if (cached != null && cached.matches(hearth)) {
            return cached.cues();
        }
        List<HearthBoundaryParticlePolicy.ParticleCue> cues =
                HearthBoundaryParticlePolicy.cuesFor(hearth);
        particleCueCache.put(hearth.id(), new CachedParticleCues(
                hearth.layoutSeed(), hearth.structureStageApplied(), hearth.structurePlaced(), cues));
        return cues;
    }

    private static void emitDoorCue(
            ServerLevel level, ServerPlayer player, BlockPos pos, Direction facing) {
        Direction side = facing.getClockWise();
        double sideX = side.getStepX() * 0.38D;
        double sideZ = side.getStepZ() * 0.38D;
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        sendDust(level, player, INTERACTION_DUST,
                centerX + sideX, pos.getY() + 0.65D, centerZ + sideZ, 0.02D);
        sendDust(level, player, INTERACTION_DUST,
                centerX - sideX, pos.getY() + 1.45D, centerZ - sideZ, 0.02D);
        sendDust(level, player, INTERACTION_DUST,
                centerX, pos.getY() + 2.08D, centerZ, 0.02D);
    }

    private static void sendDust(
            ServerLevel level, ServerPlayer player, DustParticleOptions particle,
            double x, double y, double z, double spread) {
        level.sendParticles(player, particle, false, x, y, z,
                1, spread, spread, spread, 0.0D);
    }

    private static void recordBoundaryEntry(
            ServerLevel level, ReturnedHearthSavedData data, UUID hearthId,
            ServerPlayer player) {
        ReturnedHearthSavedData.HiveRelationship before = data.relationship(player.getUUID());
        boolean localReasonRecorded = HearthMemoryManager.recordProtectedViolation(
                level, hearthId, player,
                ReturnedHearthSavedData.HearthViolationReason.PROTECTED_ENTRY);
        if (localReasonRecorded) {
            boundaryEntries++;
        }
        if (before != ReturnedHearthSavedData.HiveRelationship.ORSATHAE
                && data.relationship(player.getUUID())
                == ReturnedHearthSavedData.HiveRelationship.ORSATHAE) {
            if (!localReasonRecorded) {
                boundaryEntries++;
            }
            triggerOrsathaeEffect(level, hearthId, player);
        }
    }

    private static boolean sameZone(PlayerBoundaryState left, PlayerBoundaryState right) {
        return left != null && left.zone() == right.zone()
                && java.util.Objects.equals(left.hearthId(), right.hearthId());
    }

    private static ServerPlayer closerToHearth(
            ReturnedHearthSavedData data, UUID hearthId,
            ServerPlayer left, ServerPlayer right) {
        ReturnedHearthSavedData.HearthRecord hearth = data.hearth(hearthId).orElse(null);
        if (hearth == null) {
            return left;
        }
        return left.distanceToSqr(hearth.center().getCenter())
                <= right.distanceToSqr(hearth.center().getCenter()) ? left : right;
    }

    private static void orientResidents(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord hearth,
            ServerPlayer player) {
        if (hearth == null) {
            return;
        }
        for (UUID entityId : residentIds(hearth)) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof Mob mob && mob.isAlive()) {
                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
        }
    }

    private static Set<UUID> residentIds(ReturnedHearthSavedData.HearthRecord hearth) {
        Set<UUID> ids = new HashSet<>();
        hearth.watcherEntityId().ifPresent(ids::add);
        hearth.architectAssessorEntityId().ifPresent(ids::add);
        hearth.populationResidents().forEach(
                binding -> binding.entityId().ifPresent(ids::add));
        hearth.masterArchitectEntityId().ifPresent(ids::add);
        return ids;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private record PlayerBoundaryState(UUID hearthId, HearthBoundaryPolicy.Zone zone) {
        private static PlayerBoundaryState from(
                HearthBoundaryPolicy.BoundaryContact contact) {
            return contact == null
                    ? new PlayerBoundaryState(null, HearthBoundaryPolicy.Zone.OUTSIDE)
                    : new PlayerBoundaryState(contact.hearthId(), contact.zone());
        }
    }

    private record CachedParticleCues(
            long layoutSeed,
            ReturnedHearthSavedData.HearthStage stage,
            boolean structurePlaced,
            List<HearthBoundaryParticlePolicy.ParticleCue> cues) {
        private boolean matches(ReturnedHearthSavedData.HearthRecord hearth) {
            return layoutSeed == hearth.layoutSeed()
                    && stage == hearth.structureStageApplied()
                    && structurePlaced == hearth.structurePlaced();
        }
    }
}
