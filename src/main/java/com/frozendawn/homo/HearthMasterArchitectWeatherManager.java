package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.network.MasterArchitectWeatherPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronizes the living Master Architect's local weather strength per player.
 * Reads SavedData only and never loads or retains Hearth chunks.
 */
public final class HearthMasterArchitectWeatherManager {
    private static final float RESEND_EPSILON = 0.005F;
    private static final long HEARTBEAT_TICKS = 40L;
    private static final Map<UUID, Float> lastSentStrength = new HashMap<>();
    private static final Map<UUID, Long> lastSentGameTime = new HashMap<>();

    private static long packetsSent;

    private HearthMasterArchitectWeatherManager() {
    }

    public static void tick(ServerLevel level, int phase, float progress) {
        if (level.getGameTime()
                % HearthMasterArchitectWeatherPolicy.SYNC_INTERVAL_TICKS != 0L) {
            return;
        }

        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord major = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        Set<UUID> online = new HashSet<>();

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            float strength = strengthFor(player, data, major, phase, progress);
            Float previous = lastSentStrength.get(player.getUUID());
            long previousTick = lastSentGameTime.getOrDefault(player.getUUID(), Long.MIN_VALUE);
            boolean heartbeatDue = previousTick == Long.MIN_VALUE
                    || level.getGameTime() - previousTick >= HEARTBEAT_TICKS;
            if (!heartbeatDue && previous != null
                    && Math.abs(previous - strength) < RESEND_EPSILON) {
                continue;
            }

            PacketDistributor.sendToPlayer(
                    player, new MasterArchitectWeatherPayload(strength));
            lastSentStrength.put(player.getUUID(), strength);
            lastSentGameTime.put(player.getUUID(), level.getGameTime());
            packetsSent++;
        }

        lastSentStrength.keySet().retainAll(online);
        lastSentGameTime.keySet().retainAll(online);
    }

    public static String describe(
            ServerPlayer player, int phase, float progress) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(player.getServer());
        ReturnedHearthSavedData.HearthRecord major = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (major == null) {
            return "hearth=none strength=0.000";
        }

        boolean eligible = HearthMasterArchitectWeatherPolicy.canProject(
                major, data.relationship(player.getUUID()), phase, progress);
        float strength = strengthFor(player, data, major, phase, progress);
        double dx = player.getX() - (major.center().getX() + 0.5D);
        double dz = player.getZ() - (major.center().getZ() + 0.5D);
        return "eligible=" + yesNo(eligible)
                + " relationship="
                + data.relationship(player.getUUID()).name().toLowerCase(Locale.ROOT)
                + " defeated=" + yesNo(major.masterArchitectDefeated())
                + " bound=" + yesNo(major.masterArchitectEntityId().isPresent())
                + " distance=" + String.format(Locale.ROOT, "%.1f", Math.sqrt(dx * dx + dz * dz))
                + " strength=" + String.format(Locale.ROOT, "%.3f", strength);
    }

    public static String statusLine() {
        long active = lastSentStrength.values().stream()
                .filter(strength -> strength > 0.0F)
                .count();
        return "activePlayers=" + active + " packets=" + packetsSent;
    }

    public static void reset() {
        lastSentStrength.clear();
        lastSentGameTime.clear();
        packetsSent = 0L;
    }

    private static float strengthFor(
            ServerPlayer player,
            ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord major,
            int phase,
            float progress) {
        if (player.level().dimension() != Level.OVERWORLD
                || major == null
                || !HearthMasterArchitectWeatherPolicy.canProject(
                        major,
                        data.relationship(player.getUUID()),
                        phase,
                        progress)) {
            return 0.0F;
        }
        return HearthMasterArchitectWeatherPolicy.strength(
                major.center(), player.position());
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
