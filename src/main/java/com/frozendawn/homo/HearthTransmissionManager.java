package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.network.CancelThaevenTransmissionPayload;
import com.frozendawn.network.OpenThaevenTransmissionPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Owns authoritative first-contact transmission sessions without holding chunk tickets.
 */
public final class HearthTransmissionManager {
    private static final int COMPLETION_GRACE_TICKS = 40;
    private static final double MAX_CONTACT_DISTANCE = 34.0D;
    private static final double MAX_CONTACT_DISTANCE_SQUARED =
            MAX_CONTACT_DISTANCE * MAX_CONTACT_DISTANCE;

    private static final Map<UUID, Session> activeSessions = new HashMap<>();
    private static final Map<UUID, RearmContact> awaitingContactExit = new HashMap<>();
    private static int nextSessionId = 1;
    private static long sessionsStarted;
    private static long sessionsCompleted;
    private static long sessionsInterrupted;

    private HearthTransmissionManager() {
    }

    public static boolean tryStart(ServerLevel level, Mob source,
                                   ServerPlayer player, UUID hearthId) {
        return tryStart(level, source, player, hearthId, false);
    }

    public static boolean tryStart(ServerLevel level, Mob source,
                                   ServerPlayer player, UUID hearthId, boolean replay) {
        if (activeSessions.containsKey(player.getUUID())
                || (!replay && awaitingContactExit.containsKey(player.getUUID()))
                || HearthMemoryManager.isPermanentOrsathae(level, player.getUUID())
                || !validHearthSource(source, hearthId)
                || source.distanceToSqr(player) > MAX_CONTACT_DISTANCE_SQUARED
                || !source.hasLineOfSight(player)) {
            return false;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthContactMemory contact = data.hearth(hearthId)
                .flatMap(record -> record.playerContact(player.getUUID()))
                .orElse(null);
        if (contact == null || !contact.architectAssessmentComplete()) {
            return false;
        }

        ReturnedHearthSavedData.HearthRecord hearth = data.hearth(hearthId).orElse(null);
        ThaevenTransmissionType type;
        if (replay || !contact.firstTransmissionComplete()) {
            type = ThaevenTransmissionType.fromAssessment(contact.orsaDetectedAtAssessment());
        } else if (!contact.hearthMythTransmissionComplete()
                && hearth != null
                && hearth.stage().ordinal()
                        >= ReturnedHearthSavedData.HearthStage.FORMED.ordinal()
                && isPopulationSource(source, hearthId)) {
            type = ThaevenTransmissionType.HEARTH_MYTH;
        } else {
            return false;
        }
        int sessionId = nextSessionId();
        Session session = new Session(sessionId, player.getUUID(), source.getUUID(),
                hearthId, type, level.getGameTime(), replay);
        activeSessions.put(player.getUUID(), session);
        sessionsStarted++;

        PacketDistributor.sendToPlayer(player, new OpenThaevenTransmissionPayload(
                sessionId, source.getId(), type.networkId(), type.durationTicks()));
        FrozenDawn.LOGGER.info(
                "Started Thaeven transmission {} for player {} at Hearth {} | type={} replay={}",
                sessionId, player.getGameProfile().getName(), shortId(hearthId),
                type.name().toLowerCase(), replay);
        return true;
    }

    public static void tick(ServerLevel level) {
        updateContactRearming(level);

        Iterator<Map.Entry<UUID, Session>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerId());
            if (player == null || player.level() != level) {
                iterator.remove();
                continue;
            }

            Mob source = resolveSource(level, session.sourceId());
            if (!validContact(level, player, source, session.hearthId())) {
                PacketDistributor.sendToPlayer(player,
                        new CancelThaevenTransmissionPayload(session.sessionId()));
                interrupt(iterator, session);
                continue;
            }

            long elapsed = level.getGameTime() - session.startedGameTime();
            if (elapsed >= session.type().durationTicks() + COMPLETION_GRACE_TICKS) {
                complete(iterator, level, player, session);
            }
        }
    }

    public static void handleResult(ServerPlayer player, int sessionId, boolean completed) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Session session = activeSessions.get(player.getUUID());
        if (session == null || session.sessionId() != sessionId) {
            return;
        }

        Iterator<Map.Entry<UUID, Session>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            if (!entry.getKey().equals(player.getUUID())) {
                continue;
            }
            long elapsed = level.getGameTime() - session.startedGameTime();
            if (completed && elapsed >= session.type().minimumCompletionTicks()) {
                complete(iterator, level, player, session);
            } else {
                interrupt(iterator, session);
            }
            return;
        }
    }

    public static boolean resetForDebug(ServerPlayer player, UUID hearthId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        interruptActive(player, true);
        awaitingContactExit.remove(player.getUUID());
        return ReturnedHearthSavedData.get(level.getServer())
                .clearFirstTransmissionForDebug(player.getUUID(), hearthId);
    }

    public static boolean replayForDebug(ServerPlayer player, UUID hearthId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(level.getServer()).hearth(hearthId).orElse(null);
        if (hearth == null || hearth.architectAssessorEntityId().isEmpty()) {
            return false;
        }
        ArchitectEntity architect = resolveArchitect(
                level, hearth.architectAssessorEntityId().orElseThrow());
        return architect != null && tryStart(level, architect, player, hearthId, true);
    }

    public static boolean isActive(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    public static boolean isAwaitingContactExit(UUID playerId) {
        return awaitingContactExit.containsKey(playerId);
    }

    public static String statusLine() {
        return "active=" + activeSessions.size()
                + " awaitingExit=" + awaitingContactExit.size()
                + " started=" + sessionsStarted
                + " completed=" + sessionsCompleted
                + " interrupted=" + sessionsInterrupted;
    }

    public static void reset() {
        activeSessions.clear();
        awaitingContactExit.clear();
        nextSessionId = 1;
        sessionsStarted = 0L;
        sessionsCompleted = 0L;
        sessionsInterrupted = 0L;
    }

    private static boolean validContact(ServerLevel level, ServerPlayer player,
                                        Mob source, UUID hearthId) {
        return player.isAlive()
                && !player.isSpectator()
                && validHearthSource(source, hearthId)
                && source.distanceToSqr(player) <= MAX_CONTACT_DISTANCE_SQUARED
                && source.hasLineOfSight(player)
                && !HearthMemoryManager.isPermanentOrsathae(level, player.getUUID());
    }

    private static boolean validHearthSource(Mob source, UUID hearthId) {
        if (source == null || !source.isAlive()) {
            return false;
        }
        if (source instanceof ArchitectEntity architect) {
            return architect.isBoundToHearthAssessor(hearthId)
                    || architect.isBoundToHearthPopulation(hearthId);
        }
        if (source instanceof ReturnedEntity returned) {
            return returned.isBoundToHearth(hearthId);
        }
        return source instanceof MimicEntity mimic
                && mimic.isBoundToHearthPopulation(hearthId);
    }

    private static boolean isPopulationSource(Mob source, UUID hearthId) {
        if (source instanceof ArchitectEntity architect) {
            return architect.isBoundToHearthPopulation(hearthId);
        }
        if (source instanceof ReturnedEntity returned) {
            return returned.isHearthPopulationResident()
                    && returned.isBoundToHearth(hearthId);
        }
        return source instanceof MimicEntity mimic
                && mimic.isBoundToHearthPopulation(hearthId);
    }

    private static Mob resolveSource(ServerLevel level, UUID entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof Mob mob ? mob : null;
    }

    private static ArchitectEntity resolveArchitect(ServerLevel level, UUID entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof ArchitectEntity architect ? architect : null;
    }

    private static void complete(Iterator<Map.Entry<UUID, Session>> iterator,
                                 ServerLevel level, ServerPlayer player, Session session) {
        if (!session.replay()) {
            ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
            if (session.type() == ThaevenTransmissionType.HEARTH_MYTH) {
                data.completeHearthMythTransmission(
                        player.getUUID(), session.hearthId(), level.getGameTime());
            } else {
                data.completeFirstTransmission(
                        player.getUUID(), session.hearthId(), level.getGameTime());
            }
        }
        iterator.remove();
        if (!session.replay() && session.type() != ThaevenTransmissionType.HEARTH_MYTH) {
            awaitingContactExit.put(player.getUUID(), new RearmContact(
                    session.sourceId(), session.hearthId()));
        } else {
            awaitingContactExit.remove(player.getUUID());
        }
        sessionsCompleted++;
        FrozenDawn.LOGGER.info(
                "Completed Thaeven transmission {} for player {} at Hearth {} | type={} replay={}",
                session.sessionId(), player.getGameProfile().getName(), shortId(session.hearthId()),
                session.type().name().toLowerCase(), session.replay());
    }

    private static void interrupt(Iterator<Map.Entry<UUID, Session>> iterator,
                                  Session session) {
        iterator.remove();
        awaitingContactExit.put(session.playerId(), new RearmContact(
                session.sourceId(), session.hearthId()));
        sessionsInterrupted++;
        FrozenDawn.LOGGER.info(
                "Interrupted Thaeven transmission {} for player {} at Hearth {}",
                session.sessionId(), shortId(session.playerId()), shortId(session.hearthId()));
    }

    private static void interruptActive(ServerPlayer player, boolean notifyClient) {
        Session session = activeSessions.remove(player.getUUID());
        if (session == null) {
            return;
        }
        if (notifyClient) {
            PacketDistributor.sendToPlayer(player,
                    new CancelThaevenTransmissionPayload(session.sessionId()));
        }
        sessionsInterrupted++;
    }

    private static void updateContactRearming(ServerLevel level) {
        Iterator<Map.Entry<UUID, RearmContact>> iterator =
                awaitingContactExit.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RearmContact> entry = iterator.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != level) {
                iterator.remove();
                continue;
            }

            RearmContact contact = entry.getValue();
            Mob source = resolveSource(level, contact.sourceId());
            if (!validHearthSource(source, contact.hearthId())
                    || source.distanceToSqr(player) > MAX_CONTACT_DISTANCE_SQUARED) {
                iterator.remove();
                FrozenDawn.LOGGER.info(
                        "Re-armed Thaeven contact for player {} after leaving Hearth {} range",
                        player.getGameProfile().getName(), shortId(contact.hearthId()));
            }
        }
    }

    private static int nextSessionId() {
        int result = nextSessionId;
        nextSessionId = nextSessionId == Integer.MAX_VALUE ? 1 : nextSessionId + 1;
        return result;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private record Session(int sessionId, UUID playerId, UUID sourceId, UUID hearthId,
                           ThaevenTransmissionType type, long startedGameTime, boolean replay) {
    }

    private record RearmContact(UUID sourceId, UUID hearthId) {
    }
}
