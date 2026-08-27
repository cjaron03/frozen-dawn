package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ReturnedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Records player contact with loaded Hearths and applies hive-wide conduct memory.
 */
public final class HearthMemoryManager {
    private static final long CONTACT_SCAN_INTERVAL_TICKS = 20L;
    private static final double CONTACT_RADIUS = 32.0D;
    private static final double CONTACT_RADIUS_SQUARED = CONTACT_RADIUS * CONTACT_RADIUS;

    private static long contactsRecorded;
    private static long firstContactsRecorded;
    private static long violationsRecorded;

    private HearthMemoryManager() {
    }

    public static void tick(ServerLevel level) {
        if (PostMaeveWorldState.isErased(level)
                || level.dimension() != ServerLevel.OVERWORLD
                || level.getGameTime() % CONTACT_SCAN_INTERVAL_TICKS != 0L) {
            return;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        long gameTime = level.getGameTime();
        for (ReturnedHearthSavedData.HearthRecord hearth : data.hearths()) {
            if (!hearth.surfaceResolved() || !hearth.structurePlaced()
                    || !level.isLoaded(hearth.center())) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (!player.isAlive() || player.isSpectator()
                        || player.distanceToSqr(hearth.center().getCenter())
                        > CONTACT_RADIUS_SQUARED) {
                    continue;
                }
                ReturnedHearthSavedData.ContactResult result = data.recordPlayerContact(
                        player.getUUID(), hearth.id(), gameTime);
                if (result.changed()) {
                    contactsRecorded++;
                }
                if (result.firstHearthContact()) {
                    firstContactsRecorded++;
                    FrozenDawn.LOGGER.info(
                            "Homo reliquus recorded first contact with player {} at {} Hearth {}",
                            player.getGameProfile().getName(),
                            hearth.type().name().toLowerCase(),
                            shortId(hearth.id()));
                }
            }
        }
    }

    public static boolean recordWatcherAttack(ServerLevel level, ReturnedEntity watcher,
                                              ServerPlayer attacker) {
        UUID hearthId = watcher.getHearthId().orElse(null);
        if (hearthId == null) {
            return false;
        }
        return recordHearthEntityAttack(level, hearthId, attacker, "watcher");
    }

    public static boolean recordHearthEntityAttack(ServerLevel level, UUID hearthId,
                                                   ServerPlayer attacker, String role) {
        if (PostMaeveWorldState.isErased(level)) {
            return false;
        }
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HiveRelationship before = data.relationship(attacker.getUUID());
        boolean changed = data.markPlayerOrsathae(
                attacker.getUUID(), hearthId, level.getGameTime());
        if (changed) {
            violationsRecorded++;
            FrozenDawn.LOGGER.info(
                    "Homo reliquus permanently classified player {} as Orsathae after {} attack at Hearth {}",
                    attacker.getGameProfile().getName(), role, shortId(hearthId));
        }
        if (before != ReturnedHearthSavedData.HiveRelationship.ORSATHAE
                && data.relationship(attacker.getUUID())
                == ReturnedHearthSavedData.HiveRelationship.ORSATHAE) {
            HearthBoundaryManager.triggerOrsathaeEffect(level, hearthId, attacker);
        }
        return changed;
    }

    public static boolean recordProtectedViolation(
            ServerLevel level, UUID hearthId, ServerPlayer player,
            ReturnedHearthSavedData.HearthViolationReason reason) {
        if (PostMaeveWorldState.isErased(level)) {
            return false;
        }
        ReturnedHearthSavedData.ViolationResult result =
                ReturnedHearthSavedData.get(level.getServer()).recordHearthViolation(
                        player.getUUID(), hearthId, level.getGameTime(), reason);
        if (result.localReasonRecorded()) {
            violationsRecorded++;
            FrozenDawn.LOGGER.info(
                    "Homo reliquus recorded {} by player {} at Hearth {}; classification={}",
                    reason.name().toLowerCase(), player.getGameProfile().getName(),
                    shortId(hearthId), result.currentRelationship().name().toLowerCase());
        }
        return result.localReasonRecorded();
    }

    public static ReturnedHearthSavedData.HiveRelationship relationship(
            ServerLevel level, UUID playerId) {
        return ReturnedHearthSavedData.get(level.getServer()).relationship(playerId);
    }

    public static boolean isPermanentOrsathae(ServerLevel level, UUID playerId) {
        return relationship(level, playerId) == ReturnedHearthSavedData.HiveRelationship.ORSATHAE;
    }

    public static String statusLine() {
        return "contacts=" + contactsRecorded
                + " first=" + firstContactsRecorded
                + " violations=" + violationsRecorded;
    }

    public static void reset() {
        contactsRecorded = 0L;
        firstContactsRecorded = 0L;
        violationsRecorded = 0L;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
