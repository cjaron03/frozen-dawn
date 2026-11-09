package com.frozendawn.lore;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.network.PayoffDeathLinePayload;
import com.frozendawn.network.PayoffStatePayload;
import com.frozendawn.network.RespawnCuePayload;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The world's payoff flag (CH-18). It fires on a player's first death after the Heart falls with Maeve erased, never
 * in an E11 world (CH-21), and never reverts. Only a world new enough to have had this flag from its start is eligible;
 * an older world keeps the rest of its story as it was. The same death turns the Terminus log in every world.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class PayoffState extends SavedData {
    public enum RespawnCue { BOOT, REPLAY }

    public static final ResourceLocation ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "payoff_fired");
    /** A world whose clock is below this when the flag is first created is new. */
    static final long NEW_SAVE_TICKS = 1200;
    private static final String NAME = FrozenDawn.MOD_ID + "_payoff";
    private static final String LEGACY_TERMINUS = FrozenDawn.MOD_ID + "_terminus";

    private static volatile boolean clientFired, clientTerminusTurned;

    private boolean eligible, fired, terminusTurned, replayPending;
    private UUID firedBy;

    /** The one-way trigger, given the saved erased flag, whether Maeve escaped (E11), and whether it already fired. */
    public static boolean firesOnDeath(boolean savedErased, boolean maeveEscaped, boolean fired) {
        return savedErased && !maeveEscaped && !fired;
    }

    public static boolean eligibleAt(long gameTime) { return gameTime < NEW_SAVE_TICKS; }

    /** The state a world gets the first time it loads with this flag. */
    public static PayoffState create(long gameTime, boolean legacyTurned) {
        var data = new PayoffState();
        data.eligible = eligibleAt(gameTime);
        data.terminusTurned = legacyTurned;
        return data;
    }

    public boolean isEligible() { return eligible; }
    public boolean isFired() { return fired; }
    public boolean isTerminusTurned() { return terminusTurned; }

    public static boolean fired(MinecraftServer server) { return get(server).fired; }
    public static boolean terminusTurned(MinecraftServer server) { return get(server).terminusTurned; }
    static UUID firedBy(MinecraftServer server) { return get(server).firedBy; }

    public static boolean clientFired() { return clientFired; }
    public static boolean clientTerminusTurned() { return clientTerminusTurned; }
    public static void receive(PayoffStatePayload payload) {
        clientFired = payload.fired();
        clientTerminusTurned = payload.terminusTurned();
    }
    public static void forgetClient() { clientFired = clientTerminusTurned = false; }

    /** The replay belongs to the player whose death fired the flag, once; every other respawn is a boot. */
    public static RespawnCue cueFor(ServerPlayer player) {
        var data = get(player.getServer());
        return data.fired && data.replayPending && player.getUUID().equals(data.firedBy) ? RespawnCue.REPLAY : RespawnCue.BOOT;
    }

    /**
     * The cue a respawn sends, spending the replay once it is sent. Leaving the End raises the same event without a
     * death; it sends nothing.
     */
    static Optional<RespawnCue> cueOnRespawn(ServerPlayer player, boolean endConquered) {
        if (endConquered) return Optional.empty();
        var cue = cueFor(player);
        if (cue == RespawnCue.REPLAY) {
            var data = get(player.getServer());
            data.replayPending = false;
            data.setDirty();
        }
        return Optional.of(cue);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        cueOnRespawn(player, event.isEndConquered()).ifPresent(cue -> {
            if (cue == RespawnCue.REPLAY) WorldTickHandler.freezeForAwakening(player);
            PacketDistributor.sendToPlayer(player, new RespawnCuePayload(cue == RespawnCue.REPLAY));
        });
    }

    /** E11 is not built. Its escape flag plugs in here (CH-21). */
    public static boolean maeveEscaped(MinecraftServer server) { return false; }

    /** GameTests and the dev command only: in play the flag never reverts. */
    public static void setForTest(MinecraftServer server, boolean fired) {
        var data = get(server);
        data.eligible = true;
        data.fired = fired;
        data.terminusTurned = fired;
        data.firedBy = null;
        data.replayPending = false;
        data.setDirty();
        if (!fired) {
            var holder = server.getAdvancements().get(ADVANCEMENT);
            if (holder != null) for (var player : server.getPlayerList().getPlayers()) {
                var progress = player.getAdvancements().getOrStartProgress(holder);
                for (var criterion : progress.getCompletedCriteria()) player.getAdvancements().revoke(holder, criterion);
            }
        }
        syncAll(server);
    }

    /** GameTests and the Terminus playtest command only: turns the Terminus log without touching the payoff. */
    public static void setTerminusTurnedForTest(MinecraftServer server, boolean turned) {
        var data = get(server);
        data.terminusTurned = turned;
        data.setDirty();
        syncAll(server);
    }

    /** GameTests only: stands in for a world from before the payoff. */
    static void setEligibleForTest(MinecraftServer server, boolean eligible) {
        var data = get(server);
        data.eligible = eligible;
        data.setDirty();
    }

    static boolean eligible(MinecraftServer server) { return get(server).eligible; }

    /** The dev status line. */
    public static String describe(MinecraftServer server) {
        var data = get(server);
        return "Payoff: " + (data.eligible ? "eligible" : "not eligible (an older world)") + ", fired " + data.fired
                + (data.firedBy == null ? "" : " by " + data.firedBy) + ", replay pending " + data.replayPending
                + ", Terminus turned " + data.terminusTurned + ", saved erased " + ReturnedHearthSavedData.get(server).maeveErased();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) { get(event.getServer()); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var server = player.getServer();
        boolean savedErased = ReturnedHearthSavedData.get(server).maeveErased();
        boolean escaped = maeveEscaped(server);
        if (!savedErased || escaped) return;
        var data = get(server);
        boolean fires = data.eligible && firesOnDeath(savedErased, escaped, data.fired);
        if (data.terminusTurned && !fires) return;
        data.terminusTurned = true;
        if (fires) {
            data.fired = true;
            data.firedBy = player.getUUID();
            data.replayPending = true;
            // Ahead of vanilla's kill packet, which opens the death screen.
            PacketDistributor.sendToPlayer(player, new PayoffDeathLinePayload());
        }
        data.setDirty();
        sync(player, fires && grant(player));
        for (var online : server.getPlayerList().getPlayers()) if (online != player) sync(online, fires && grant(online));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) login(player);
    }

    static void login(ServerPlayer player) {
        var data = get(player.getServer());
        sync(player, data.fired && grant(player));
        if (deathLineOwed(player)) PacketDistributor.sendToPlayer(player, new PayoffDeathLinePayload());
    }

    /** A player who fired the flag and has not yet respawned: their death screen still owes the line. */
    public static boolean deathLineOwed(ServerPlayer player) {
        var data = get(player.getServer());
        return data.fired && data.replayPending && player.isDeadOrDying() && player.getUUID().equals(data.firedBy);
    }

    /** Grants the payoff pages; true if this player did not hold them yet. */
    private static boolean grant(ServerPlayer player) {
        var holder = player.getServer().getAdvancements().get(ADVANCEMENT);
        if (holder == null) return false;
        var progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) return false;
        for (var criterion : progress.getRemainingCriteria()) player.getAdvancements().award(holder, criterion);
        return true;
    }

    private static void sync(ServerPlayer player, boolean markNew) {
        var data = get(player.getServer());
        PacketDistributor.sendToPlayer(player, new PayoffStatePayload(data.fired, data.terminusTurned, markNew));
    }

    private static void syncAll(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) sync(player, false);
    }

    private static PayoffState get(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(() -> {
            var data = create(server.overworld().getGameTime(), legacyTerminusTurned(server));
            data.setDirty();
            return data;
        }, PayoffState::load), NAME);
    }

    /** The Terminus log kept its own flag before the payoff existed; a world that has one keeps its turn. */
    private static boolean legacyTerminusTurned(MinecraftServer server) {
        var legacy = server.overworld().getDataStorage().get(new SavedData.Factory<>(LegacyTerminus::new, LegacyTerminus::load), LEGACY_TERMINUS);
        return legacy != null && legacy.turned;
    }

    private static PayoffState load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new PayoffState();
        data.eligible = tag.getBoolean("eligible");
        data.fired = tag.getBoolean("fired");
        data.terminusTurned = tag.getBoolean("terminusTurned");
        data.replayPending = tag.getBoolean("replayPending");
        if (tag.hasUUID("firedBy")) data.firedBy = tag.getUUID("firedBy");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("eligible", eligible);
        tag.putBoolean("fired", fired);
        tag.putBoolean("terminusTurned", terminusTurned);
        tag.putBoolean("replayPending", replayPending);
        if (firedBy != null) tag.putUUID("firedBy", firedBy);
        return tag;
    }

    /** Read once, to seed a world's Terminus turn; never written. */
    private static final class LegacyTerminus extends SavedData {
        private boolean turned;
        private static LegacyTerminus load(CompoundTag tag, HolderLookup.Provider registries) {
            var data = new LegacyTerminus();
            data.turned = tag.getBoolean("turned");
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("turned", turned);
            return tag;
        }
    }
}
