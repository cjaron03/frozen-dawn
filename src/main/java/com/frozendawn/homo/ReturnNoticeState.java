package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The Major Hearth's notice of a death it watched (payoff spec 5b). A witnessed death owes that player one
 * {@link ThaevenTransmissionType#RETURN_NOTICE} on their next contact, at most {@link #MAX_NOTICES} per world, and
 * never once the Heart has fallen (5c). Kept apart from the Hearth ledger so that format does not change.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ReturnNoticeState extends SavedData {
    public static final int MAX_NOTICES = 2;
    static final double WATCH_DISTANCE = 28.0D;
    private static final String NAME = FrozenDawn.MOD_ID + "_return_notice";

    private int shown;
    private final Set<UUID> owed = new LinkedHashSet<>();

    /** Whether a death owes the notice, given the world and player gates and the notices already shown. */
    public static boolean owes(boolean erased, boolean permanentOrsathae, boolean firstTransmissionDone,
                               boolean witnessed, int shown) {
        return !erased && !permanentOrsathae && firstTransmissionDone && witnessed && shown < MAX_NOTICES;
    }

    /** The world count after one notice plays. */
    public static int spend(int shown) {
        return Math.min(MAX_NOTICES, shown + 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            onDeath(player);
        }
    }

    public static void onDeath(ServerPlayer player) {
        onDeath(player, witnessed(player));
    }

    static void onDeath(ServerPlayer player, boolean witnessed) {
        MinecraftServer server = player.getServer();
        ServerLevel level = player.serverLevel();
        ReturnNoticeState state = get(server);
        boolean firstDone = majorHearth(server)
                .flatMap(hearth -> hearth.playerContact(player.getUUID()))
                .map(contact -> contact.architectAssessmentComplete() && contact.firstTransmissionComplete())
                .orElse(false);
        if (!owes(PostMaeveWorldState.isErased(server),
                HearthMemoryManager.isPermanentOrsathae(level, player.getUUID()),
                firstDone, witnessed, state.shown)) {
            return;
        }
        if (state.owed.add(player.getUUID())) {
            state.setDirty();
            FrozenDawn.LOGGER.info("Return notice owed to {} | shown={}",
                    player.getGameProfile().getName(), state.shown);
        }
    }

    public static boolean isOwed(MinecraftServer server, UUID playerId) {
        ReturnNoticeState state = get(server);
        return state.shown < MAX_NOTICES && state.owed.contains(playerId);
    }

    /** One notice played to this player: their debt clears, and at the cap every other debt clears too. */
    public static void spend(MinecraftServer server, UUID playerId) {
        ReturnNoticeState state = get(server);
        state.owed.remove(playerId);
        state.shown = spend(state.shown);
        if (state.shown >= MAX_NOTICES) {
            state.owed.clear();
        }
        state.setDirty();
    }

    public static int shown(MinecraftServer server) {
        return get(server).shown;
    }

    public static Set<UUID> owed(MinecraftServer server) {
        return Set.copyOf(get(server).owed);
    }

    /** Dev command: owes the notice to this player without a death. */
    public static void oweForDebug(MinecraftServer server, UUID playerId) {
        ReturnNoticeState state = get(server);
        state.owed.add(playerId);
        state.setDirty();
    }

    /** Dev command and tests: no notices shown, none owed. */
    public static void resetForDebug(MinecraftServer server) {
        ReturnNoticeState state = get(server);
        state.shown = 0;
        state.owed.clear();
        state.setDirty();
    }

    /** A living population resident of the Major Hearth within watch distance, with line of sight to the player. */
    static boolean witnessed(ServerPlayer player) {
        ReturnedHearthSavedData.HearthRecord hearth = majorHearth(player.getServer()).orElse(null);
        if (hearth == null) {
            return false;
        }
        UUID hearthId = hearth.id();
        double max = WATCH_DISTANCE * WATCH_DISTANCE;
        return !player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(WATCH_DISTANCE),
                mob -> mob.isAlive()
                        && HearthTransmissionManager.isPopulationSource(mob, hearthId)
                        && mob.distanceToSqr(player) <= max
                        && mob.hasLineOfSight(player)).isEmpty();
    }

    private static java.util.Optional<ReturnedHearthSavedData.HearthRecord> majorHearth(MinecraftServer server) {
        return ReturnedHearthSavedData.get(server).hearth(HearthSelectionPolicy.HearthType.MAJOR);
    }

    private static ReturnNoticeState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReturnNoticeState::new, ReturnNoticeState::load), NAME);
    }

    private static ReturnNoticeState load(CompoundTag tag, HolderLookup.Provider registries) {
        ReturnNoticeState state = new ReturnNoticeState();
        state.shown = Math.max(0, Math.min(MAX_NOTICES, tag.getInt("shown")));
        for (Tag entry : tag.getList("owed", Tag.TAG_INT_ARRAY)) {
            state.owed.add(NbtUtils.loadUUID(entry));
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("shown", shown);
        ListTag list = new ListTag();
        for (UUID id : owed) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("owed", list);
        return tag;
    }
}
