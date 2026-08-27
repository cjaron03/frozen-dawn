package com.frozendawn.world;

import com.frozendawn.data.PostMaeveEncounterSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Server authority for cooldowns, pity pressure, and encounter diagnostics. */
public final class PostMaeveEncounterDirector {
    private static final String PLAYER_PREFIX = "player:";
    private static final String REGION_PREFIX = "region:";

    private PostMaeveEncounterDirector() {
    }

    public static boolean rollPlayer(ServerLevel level, ServerPlayer player,
                                     PostMaeveEncounterType type,
                                     double baseChance) {
        return roll(level, playerKey(player), type, baseChance);
    }

    public static boolean rollRegion(ServerLevel level, long region,
                                     PostMaeveEncounterType type,
                                     double baseChance) {
        return roll(level, regionKey(region), type, baseChance);
    }

    public static void blockedPlayer(ServerLevel level, ServerPlayer player,
                                     PostMaeveEncounterType type, String reason) {
        blocked(level, playerKey(player), type, reason);
    }

    public static void blockedRegion(ServerLevel level, long region,
                                     PostMaeveEncounterType type, String reason) {
        blocked(level, regionKey(region), type, reason);
    }

    public static void successPlayer(ServerLevel level, ServerPlayer player,
                                     PostMaeveEncounterType type) {
        success(level, playerKey(player), type);
    }

    public static void successRegion(ServerLevel level, long region,
                                     PostMaeveEncounterType type) {
        success(level, regionKey(region), type);
    }

    public static String playerStatus(ServerLevel level, ServerPlayer player,
                                      PostMaeveEncounterType type) {
        return status(level, playerKey(player), type);
    }

    public static String regionStatus(ServerLevel level, long region,
                                      PostMaeveEncounterType type) {
        return status(level, regionKey(region), type);
    }

    public static void debugReadyPlayer(ServerLevel level, ServerPlayer player,
                                        PostMaeveEncounterType type) {
        debugReady(level, playerKey(player), type);
    }

    public static void debugReadyRegion(ServerLevel level, long region,
                                        PostMaeveEncounterType type) {
        debugReady(level, regionKey(region), type);
    }

    public static void debugResetPlayer(ServerLevel level, ServerPlayer player,
                                        PostMaeveEncounterType type) {
        debugReset(level, playerKey(player), type);
    }

    public static void debugResetRegion(ServerLevel level, long region,
                                        PostMaeveEncounterType type) {
        debugReset(level, regionKey(region), type);
    }

    public static boolean isDebugReadyPlayer(ServerLevel level, ServerPlayer player,
                                             PostMaeveEncounterType type) {
        return isDebugReady(level, playerKey(player), type);
    }

    private static boolean roll(ServerLevel level, String ownerKey,
                                PostMaeveEncounterType type,
                                double baseChance) {
        if (baseChance <= 0.0D) return false;
        long now = level.getGameTime();
        PostMaeveEncounterSavedData data =
                PostMaeveEncounterSavedData.get(level.getServer());
        PostMaeveEncounterSavedData.OwnerRecord owner = data.owner(ownerKey);
        PostMaeveEncounterSavedData.Entry entry = owner.entry(type);
        entry.begin(now);
        boolean debugReady = entry.debugReady();
        boolean guaranteed = debugReady
                || PostMaeveEncounterPolicy.isGuaranteed(
                type, now, entry.windowStartTick());
        boolean repeatedRecently = owner.lastType() == type
                && owner.lastEncounterTick() >= 0L
                && now - owner.lastEncounterTick()
                < PostMaeveEncounterPolicy.REPEAT_DAMPING_TICKS;
        double chance = PostMaeveEncounterPolicy.effectiveChance(
                type, baseChance, now, entry.windowStartTick(),
                entry.failedAttempts(), repeatedRecently && !guaranteed);
        if (!debugReady && !PostMaeveEncounterPolicy.typeCooldownReady(
                type, now, entry.lastSuccessTick())) {
            entry.recordCooldown("type cooldown", chance);
            data.changed();
            return false;
        }
        if (!debugReady && !PostMaeveEncounterPolicy.globalCooldownReady(
                now, owner.lastEncounterTick())) {
            entry.recordCooldown("shared encounter cooldown", chance);
            data.changed();
            return false;
        }
        boolean selected = guaranteed || level.random.nextDouble() < chance;
        entry.recordRoll(chance, selected);
        data.changed();
        return selected;
    }

    private static void blocked(ServerLevel level, String ownerKey,
                                PostMaeveEncounterType type, String reason) {
        PostMaeveEncounterSavedData data =
                PostMaeveEncounterSavedData.get(level.getServer());
        PostMaeveEncounterSavedData.Entry entry = data.owner(ownerKey).entry(type);
        entry.begin(level.getGameTime());
        entry.recordBlocked(reason);
        data.changed();
    }

    private static void success(ServerLevel level, String ownerKey,
                                PostMaeveEncounterType type) {
        long now = level.getGameTime();
        PostMaeveEncounterSavedData data =
                PostMaeveEncounterSavedData.get(level.getServer());
        PostMaeveEncounterSavedData.OwnerRecord owner = data.owner(ownerKey);
        owner.entry(type).recordSuccess(now);
        owner.recordSuccess(type, now);
        data.changed();
    }

    private static String status(ServerLevel level, String ownerKey,
                                 PostMaeveEncounterType type) {
        long now = level.getGameTime();
        PostMaeveEncounterSavedData.OwnerRecord owner =
                PostMaeveEncounterSavedData.get(level.getServer()).owner(ownerKey);
        PostMaeveEncounterSavedData.Entry entry = owner.entry(type);
        long age = entry.windowStartTick() < 0L ? 0L
                : Math.max(0L, now - entry.windowStartTick());
        long remaining = Math.max(0L, type.guaranteedIntervalTicks() - age);
        boolean eligible = PostMaeveEncounterPolicy.typeCooldownReady(
                type, now, entry.lastSuccessTick())
                && PostMaeveEncounterPolicy.globalCooldownReady(
                now, owner.lastEncounterTick());
        return String.format(Locale.ROOT,
                "eligible=%s chance=%.1f%% failures=%d guarantee=%ds last=%s",
                eligible ? "yes" : "no",
                entry.lastChance() * 100.0D, entry.failedAttempts(),
                remaining / 20L, entry.lastReason());
    }

    private static void debugReady(ServerLevel level, String ownerKey,
                                   PostMaeveEncounterType type) {
        PostMaeveEncounterSavedData data =
                PostMaeveEncounterSavedData.get(level.getServer());
        PostMaeveEncounterSavedData.OwnerRecord owner = data.owner(ownerKey);
        owner.clearSharedCooldown();
        owner.entry(type).markDebugReady(
                level.getGameTime(), type.guaranteedIntervalTicks());
        data.changed();
    }

    private static void debugReset(ServerLevel level, String ownerKey,
                                   PostMaeveEncounterType type) {
        PostMaeveEncounterSavedData data =
                PostMaeveEncounterSavedData.get(level.getServer());
        data.owner(ownerKey).reset(type);
        data.changed();
    }

    private static boolean isDebugReady(ServerLevel level, String ownerKey,
                                        PostMaeveEncounterType type) {
        return PostMaeveEncounterSavedData.get(level.getServer())
                .owner(ownerKey).entry(type).debugReady();
    }

    private static String playerKey(ServerPlayer player) {
        return PLAYER_PREFIX + player.getUUID();
    }

    private static String regionKey(long region) {
        return REGION_PREFIX + region;
    }
}
