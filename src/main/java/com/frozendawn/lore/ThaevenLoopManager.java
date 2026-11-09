package com.frozendawn.lore;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.data.ThaevenLoreSavedData;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.network.ThaevenLoopPayload;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * CH-37's loop, played live during the Heart. Each beat shows once per world, only to the player who triggered it:
 * the crossing on the world's first two takeovers, the broken lines on the respawn after the first death in the
 * arena, and the returning line when that same player walks back in while the Heart still stands. An erased world
 * shows none of them.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ThaevenLoopManager {
    private ThaevenLoopManager() {
    }

    /** Where CognitiveLoadManager raises the takeover start. */
    public static void onTakeoverStart(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (erased(server)) {
            return;
        }
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(server);
        ThaevenLoopPolicy.takeoverBeat(data.loopShown()).ifPresent(beat -> show(player, data, beat));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            died(player, inArena(player));
        }
    }

    /** The world's first death in the arena owes that player the broken lines on their next respawn. */
    static void died(ServerPlayer player, boolean inArena) {
        MinecraftServer server = player.getServer();
        if (!inArena || erased(server)) {
            return;
        }
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(server);
        if ((data.loopShown() & ThaevenLoopBeat.BODY_BROKE.bit()) != 0 || data.loopBrokenOwed().isPresent()) {
            return;
        }
        data.setLoopBrokenOwed(player.getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer player) {
            respawned(player, heartLive(player.getServer()));
        }
    }

    /** The owed player's respawn shows the broken lines, and owes them the return while the Heart stands. */
    static void respawned(ServerPlayer player, boolean heartLive) {
        MinecraftServer server = player.getServer();
        if (erased(server)) {
            return;
        }
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(server);
        if (!data.loopBrokenOwed().map(player.getUUID()::equals).orElse(false)) {
            return;
        }
        data.setLoopBrokenOwed(null);
        show(player, data, ThaevenLoopBeat.BODY_BROKE);
        if (heartLive) {
            data.setLoopReturningOwed(player.getUUID());
        }
    }

    /** From CognitiveLoadManager.tick's player loop while the Heart is live. */
    public static void tick(ServerPlayer player, BlockPos anchor) {
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(player.getServer());
        if (data.loopReturningOwed().map(player.getUUID()::equals).orElse(false)) {
            tick(player, inArena(player, anchor, true));
        }
    }

    /** The owed player's first step back into the arena shows the returning line, once. */
    static void tick(ServerPlayer player, boolean inArena) {
        MinecraftServer server = player.getServer();
        if (!inArena || !player.isAlive() || erased(server)) {
            return;
        }
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(server);
        if (!data.loopReturningOwed().map(player.getUUID()::equals).orElse(false)) {
            return;
        }
        data.setLoopReturningOwed(null);
        show(player, data, ThaevenLoopBeat.RETURNING);
    }

    /** From CognitiveLoadManager.tick while no Heart is live: a Heart that fell first never shows the return. */
    public static void heartDown(MinecraftServer server) {
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(server);
        if (data.loopReturningOwed().isPresent()) {
            data.setLoopReturningOwed(null);
        }
    }

    private static void show(ServerPlayer player, ThaevenLoreSavedData data, ThaevenLoopBeat beat) {
        if (data.markLoopShown(beat)) {
            PacketDistributor.sendToPlayer(player, new ThaevenLoopPayload(beat.ordinal()));
        }
    }

    private static boolean erased(MinecraftServer server) {
        return PostMaeveWorldState.isErased(server);
    }

    private static Optional<ReturnedHearthSavedData.HearthRecord> liveHeart(MinecraftServer server) {
        return ReturnedHearthSavedData.get(server)
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .filter(ReturnedHearthSavedData.HearthRecord::heartLive);
    }

    private static boolean heartLive(MinecraftServer server) {
        return liveHeart(server).isPresent();
    }

    private static boolean inArena(ServerPlayer player) {
        return liveHeart(player.getServer())
                .map(heart -> inArena(player, heart.heartAnchor().orElse(heart.center()), true))
                .orElse(false);
    }

    /** The Heart lives in the overworld, where CognitiveLoadManager ticks it. */
    private static boolean inArena(ServerPlayer player, BlockPos anchor, boolean heartLive) {
        return ThaevenLoopPolicy.inArena(player.distanceToSqr(Vec3.atCenterOf(anchor)), heartLive,
                player.level().dimension() == Level.OVERWORLD);
    }
}
