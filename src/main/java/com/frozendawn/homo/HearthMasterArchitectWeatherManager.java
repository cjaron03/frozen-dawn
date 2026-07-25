package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.MasterArchitectLightningEntity;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.network.MasterArchitectAuraEventPayload;
import com.frozendawn.network.MasterArchitectWeatherPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static final Map<UUID, AuraSnapshot> lastSentState = new HashMap<>();
    private static final Map<UUID, Long> lastSentGameTime = new HashMap<>();
    private static final Map<UUID, Long> nextStrikeGameTime = new HashMap<>();
    private static final Map<UUID, Long> nextArcGameTime = new HashMap<>();

    private static long packetsSent;

    private HearthMasterArchitectWeatherManager() {
    }

    public static void tick(ServerLevel level, int phase, float progress) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord major = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        tickAuraEvents(level, major);
        if (level.getGameTime()
                % HearthMasterArchitectWeatherPolicy.SYNC_INTERVAL_TICKS != 0L) {
            return;
        }
        Set<UUID> online = new HashSet<>();

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            AuraSnapshot snapshot = snapshotFor(
                    level, player, data, major, phase, progress);
            AuraSnapshot previous = lastSentState.get(player.getUUID());
            long previousTick = lastSentGameTime.getOrDefault(player.getUUID(), Long.MIN_VALUE);
            boolean heartbeatDue = previousTick == Long.MIN_VALUE
                    || level.getGameTime() - previousTick >= HEARTBEAT_TICKS;
            if (!heartbeatDue && approximatelyEqual(previous, snapshot)) {
                continue;
            }

            PacketDistributor.sendToPlayer(
                    player, snapshot.payload());
            lastSentState.put(player.getUUID(), snapshot);
            lastSentGameTime.put(player.getUUID(), level.getGameTime());
            packetsSent++;
        }

        lastSentState.keySet().retainAll(online);
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
        int auraTier = auraTierFor(player.serverLevel(), major);
        double dx = player.getX() - (major.center().getX() + 0.5D);
        double dz = player.getZ() - (major.center().getZ() + 0.5D);
        return "eligible=" + yesNo(eligible)
                + " relationship="
                + data.relationship(player.getUUID()).name().toLowerCase(Locale.ROOT)
                + " defeated=" + yesNo(major.masterArchitectDefeated())
                + " bound=" + yesNo(major.masterArchitectEntityId().isPresent())
                + " distance=" + String.format(Locale.ROOT, "%.1f", Math.sqrt(dx * dx + dz * dz))
                + " auraTier=" + auraTier
                + " strength=" + String.format(Locale.ROOT, "%.3f", strength);
    }

    public static String statusLine() {
        long active = lastSentState.values().stream()
                .filter(snapshot -> snapshot.tier() > MasterArchitectAuraTier.NONE)
                .count();
        return "activePlayers=" + active + " packets=" + packetsSent;
    }

    public static void reset() {
        lastSentState.clear();
        lastSentGameTime.clear();
        nextStrikeGameTime.clear();
        nextArcGameTime.clear();
        packetsSent = 0L;
    }

    /** Immediately clears the defeated Master's aura for every overworld listener. */
    public static void onMasterDefeated(ServerLevel level, UUID hearthId) {
        nextStrikeGameTime.remove(hearthId);
        nextArcGameTime.remove(hearthId);
        AuraSnapshot inactive = AuraSnapshot.inactive();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() != level.dimension()) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, inactive.payload());
            lastSentState.put(player.getUUID(), inactive);
            lastSentGameTime.put(player.getUUID(), level.getGameTime());
            packetsSent++;
        }
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

    /** Non-damaging local temperature anomaly. This never loads the Hearth chunk. */
    public static float temperatureOffset(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)
                || level.dimension() != Level.OVERWORLD) {
            return 0.0F;
        }
        ReturnedHearthSavedData.HearthRecord major = ReturnedHearthSavedData
                .get(serverLevel.getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .orElse(null);
        int tier = auraTierFor(serverLevel, major);
        if (major == null || tier < MasterArchitectAuraTier.NOTICED) {
            return 0.0F;
        }
        double radius = FrozenDawnConfig.MASTER_AURA_RADIUS.get();
        double dx = pos.getX() + 0.5D - major.center().getX() - 0.5D;
        double dz = pos.getZ() + 0.5D - major.center().getZ() - 0.5D;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance >= radius) {
            return 0.0F;
        }
        float proximity = Mth.clamp((float) (1.0D - distance / radius), 0.0F, 1.0F);
        float smooth = proximity * proximity * (3.0F - 2.0F * proximity);
        int steps = tier - MasterArchitectAuraTier.PASSIVE;
        return FrozenDawnConfig.MASTER_AURA_TEMP_OFFSET_PER_TIER.get().floatValue()
                * steps * smooth;
    }

    public static boolean suppressesVanillaLightning(
            ServerLevel level, BlockPos strikePos) {
        ReturnedHearthSavedData.HearthRecord major = ReturnedHearthSavedData
                .get(level.getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .orElse(null);
        if (major == null || auraTierFor(level, major) <= MasterArchitectAuraTier.NONE) {
            return false;
        }
        double radius = FrozenDawnConfig.MASTER_AURA_RADIUS.get();
        double dx = strikePos.getX() - major.center().getX();
        double dz = strikePos.getZ() - major.center().getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    private static AuraSnapshot snapshotFor(
            ServerLevel level,
            ServerPlayer player,
            ReturnedHearthSavedData data,
            ReturnedHearthSavedData.HearthRecord major,
            int phase,
            float progress) {
        if (player.level().dimension() != Level.OVERWORLD || major == null) {
            return AuraSnapshot.inactive();
        }
        int tier = auraTierFor(level, major);
        float strength = strengthFor(player, data, major, phase, progress);
        return new AuraSnapshot(strength, tier, major.center(), tier > 0);
    }

    private static int auraTierFor(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord major) {
        if (major == null
                || !HearthMasterArchitectPolicy.canHostMasterArchitect(major)
                || major.masterArchitectEntityId().isEmpty()
                || major.masterArchitectDefeated()) {
            return MasterArchitectAuraTier.NONE;
        }
        var loaded = level.getEntity(major.masterArchitectEntityId().orElseThrow());
        if (loaded instanceof ArchitectEntity architect
                && architect.isHearthMasterArchitect()) {
            return MasterArchitectAuraTier.clamp(architect.getMasterAuraTier());
        }
        return MasterArchitectAuraTier.fromMood(major.mood(), false);
    }

    public static void broadcastAuraEvent(
            ServerLevel level,
            int eventType,
            BlockPos origin,
            BlockPos target,
            float intensity) {
        MasterArchitectAuraEventPayload payload = new MasterArchitectAuraEventPayload(
                eventType,
                origin.immutable(),
                target.immutable(),
                level.random.nextLong(),
                Mth.clamp(intensity, 0.0F, 2.0F));
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() == level.dimension()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void tickAuraEvents(
            ServerLevel level, ReturnedHearthSavedData.HearthRecord major) {
        int tier = auraTierFor(level, major);
        if (major == null || tier < MasterArchitectAuraTier.NOTICED) {
            if (major != null) {
                nextStrikeGameTime.remove(major.id());
                nextArcGameTime.remove(major.id());
            }
            return;
        }

        long now = level.getGameTime();
        long nextStrike = nextStrikeGameTime.computeIfAbsent(
                major.id(), ignored -> now + 10L);
        if (now >= nextStrike) {
            emitLightning(level, major, tier);
            nextStrikeGameTime.put(major.id(), now + nextStrikeDelay(level, tier));
        }

        long nextArc = nextArcGameTime.computeIfAbsent(
                major.id(), ignored -> now + 18L);
        if (now >= nextArc) {
            emitArc(level, major, tier);
            int average = tier >= MasterArchitectAuraTier.FIGHT
                    ? FrozenDawnConfig.MASTER_AURA_T3_ARC_SECONDS.get()
                    : FrozenDawnConfig.MASTER_AURA_T2_ARC_SECONDS.get();
            if (isBrutal(level)) {
                average = Math.max(1, Mth.floor(average * 0.72F));
            }
            int jitter = Math.max(1, average / 3);
            int seconds = Math.max(1, average - jitter
                    + level.random.nextInt(jitter * 2 + 1));
            nextArcGameTime.put(major.id(), now + seconds * 20L);
        }
    }

    private static void emitLightning(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord major,
            int tier) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double radius = tier >= MasterArchitectAuraTier.FIGHT
                ? 8.0D + level.random.nextDouble() * 28.0D
                : 22.0D + level.random.nextDouble() * 34.0D;
        int x = Mth.floor(major.center().getX() + Math.cos(angle) * radius);
        int z = Mth.floor(major.center().getZ() + Math.sin(angle) * radius);
        BlockPos probe = new BlockPos(x, major.center().getY(), z);
        int y = level.hasChunkAt(probe)
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                : major.center().getY();
        BlockPos target = new BlockPos(x, y, z);
        BlockPos origin = target.above(tier >= MasterArchitectAuraTier.FIGHT ? 104 : 82);
        float intensity = tier >= MasterArchitectAuraTier.FIGHT ? 1.35F : 0.78F;
        if (level.hasChunkAt(target)) {
            MasterArchitectLightningEntity.spawn(
                    level,
                    target.getX() + 0.5D,
                    target.getY(),
                    target.getZ() + 0.5D,
                    origin.getY() - target.getY(),
                    intensity,
                    level.random.nextLong());
        }
        broadcastAuraEvent(
                level, MasterArchitectAuraEventPayload.BOLT,
                origin, target, intensity);
    }

    private static void emitArc(
            ServerLevel level,
            ReturnedHearthSavedData.HearthRecord major,
            int tier) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        int radius = tier >= MasterArchitectAuraTier.FIGHT ? 18 : 30;
        BlockPos origin = major.center().offset(
                Mth.floor(Math.cos(angle) * radius),
                48 + level.random.nextInt(32),
                Mth.floor(Math.sin(angle) * radius));
        BlockPos target = major.center().offset(
                Mth.floor(Math.cos(angle + 1.2D) * (radius * 0.55D)),
                15 + level.random.nextInt(28),
                Mth.floor(Math.sin(angle + 1.2D) * (radius * 0.55D)));
        broadcastAuraEvent(
                level, MasterArchitectAuraEventPayload.ARC,
                origin, target,
                tier >= MasterArchitectAuraTier.FIGHT ? 1.0F : 0.62F);
    }

    private static long nextStrikeDelay(ServerLevel level, int tier) {
        int minimum = tier >= MasterArchitectAuraTier.FIGHT
                ? FrozenDawnConfig.MASTER_AURA_T3_STRIKE_MIN_SECONDS.get()
                : FrozenDawnConfig.MASTER_AURA_T2_STRIKE_MIN_SECONDS.get();
        int maximum = tier >= MasterArchitectAuraTier.FIGHT
                ? FrozenDawnConfig.MASTER_AURA_T3_STRIKE_MAX_SECONDS.get()
                : FrozenDawnConfig.MASTER_AURA_T2_STRIKE_MAX_SECONDS.get();
        if (isBrutal(level)) {
            minimum = Math.max(1, Mth.floor(minimum * 0.72F));
            maximum = Math.max(minimum, Mth.floor(maximum * 0.78F));
        }
        return (minimum + level.random.nextInt(Math.max(1, maximum - minimum + 1)))
                * 20L;
    }

    private static boolean isBrutal(ServerLevel level) {
        return "brutal".equalsIgnoreCase(
                ApocalypseState.get(level.getServer()).getPresetName());
    }

    private static boolean approximatelyEqual(
            AuraSnapshot previous, AuraSnapshot current) {
        return previous != null
                && Math.abs(previous.strength() - current.strength()) < RESEND_EPSILON
                && previous.tier() == current.tier()
                && previous.anchored() == current.anchored()
                && previous.center().equals(current.center());
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private record AuraSnapshot(
            float strength, int tier, BlockPos center, boolean anchored) {
        private static AuraSnapshot inactive() {
            return new AuraSnapshot(0.0F, 0, BlockPos.ZERO, false);
        }

        private MasterArchitectWeatherPayload payload() {
            return new MasterArchitectWeatherPayload(
                    strength, tier, center, anchored);
        }
    }
}
