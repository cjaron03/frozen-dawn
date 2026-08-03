package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.network.PostMaeveWorldStatePayload;
import com.frozendawn.network.BloomStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Authoritative irreversible switch for the post-Maeve world. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class PostMaeveWorldState {
    private PostMaeveWorldState() {
    }

    public static boolean isErased(Level level) {
        return level != null && level.getServer() != null
                && isErased(level.getServer());
    }

    public static boolean isErased(MinecraftServer server) {
        return server != null && (FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get()
                || ReturnedHearthSavedData.get(server).maeveErased());
    }

    public static boolean isUndoneSpawningReleased(MinecraftServer server) {
        if (server == null || !isErased(server)) {
            return false;
        }
        return FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get()
                || ReturnedHearthSavedData.get(server).undoneSpawningReleased();
    }

    public static boolean markErased(ServerLevel level) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        if (!data.markMaeveErased(level.getGameTime())) {
            return false;
        }
        CognitiveLoadManager.clearForHeartErasure(level.getServer().overworld());
        HearthTransmissionManager.reset();
        HearthDarkeningManager.begin(level.getServer().overworld());
        syncAll(level.getServer());
        return true;
    }

    public static void tick(ServerLevel overworld) {
        MinecraftServer server = overworld.getServer();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(server);
        if (!isErased(server) || data.undoneSpawningReleased()) {
            return;
        }
        ReturnedHearthSavedData.HearthRecord major = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (major == null || !major.heartMaeveBiologicalWarningPlayed()) {
            return;
        }
        BlockPos center = major.heartAnchor().orElse(major.center());
        double radiusSqr = HeartFormationPolicy.AURA_RADIUS
                * HeartFormationPolicy.AURA_RADIUS;
        boolean someoneHasMovedOn = server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isSpectator())
                .anyMatch(player -> player.serverLevel().dimension() != Level.OVERWORLD
                        || player.distanceToSqr(
                        center.getX() + 0.5D,
                        center.getY() + 0.5D,
                        center.getZ() + 0.5D) > radiusSqr);
        if (someoneHasMovedOn && data.markUndoneSpawningReleased()) {
            syncAll(server);
        }
    }

    public static void sync(ServerPlayer player) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(player.getServer());
        boolean erased = isErased(player.getServer());
        PacketDistributor.sendToPlayer(player, new PostMaeveWorldStatePayload(
                erased,
                erased && (FrozenDawnConfig.DEBUG_FORCE_MAEVE_ERASED.get()
                        || data.undoneSpawningReleased())));
        if (!erased || player.serverLevel().dimension() != Level.OVERWORLD) {
            PacketDistributor.sendToPlayer(player, new BloomStatePayload(0.0F, 0));
        }
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static void setForDebug(MinecraftServer server, boolean erased) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(server);
        data.setMaeveErasedForDebug(erased, server.overworld().getGameTime());
        if (erased) {
            CognitiveLoadManager.clearForHeartErasure(server.overworld());
            HearthTransmissionManager.reset();
            HearthDarkeningManager.begin(server.overworld());
        } else {
            HearthDarkeningManager.reset();
        }
        syncAll(server);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
