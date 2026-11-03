package com.frozendawn.aurora;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.network.AuroraStatePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The saved world seed and world clock reconstruct natural events after a reload. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class AuroraEvents {
    private static ServerLevel previewLevel;
    private static AuroraStatePayload preview;
    private AuroraEvents() { }

    public static AuroraStatePayload snapshot(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || PostMaeveWorldState.isErased(level)) {
            return AuroraStatePayload.NONE;
        }
        ApocalypseState state = ApocalypseState.get(level.getServer());
        if (state.getPhase() > 3) return AuroraStatePayload.NONE;
        if (previewLevel == level && preview != null
                && level.getGameTime() < preview.startTick() + preview.durationTicks()) {
            return new AuroraStatePayload(preview.seed(), preview.startTick(), preview.durationTicks(),
                    preview.strength() * AuroraPolicy.phaseStrength(state.getPhase(), state.getPreciseProgress()), true);
        }
        long visualSeed = AuroraPolicy.mix(level.getSeed() ^ 0x46444155524f5241L);
        var display = AuroraPolicy.nightly(visualSeed, level.getDayTime(), state.getPhase(), state.getPreciseProgress());
        return new AuroraStatePayload(display.seed(), display.startTick(), display.durationTicks(), display.strength(), false);
    }

    /** Called only by the separate, exact-world guarded lab source set. */
    public static void preview(ServerLevel level, boolean enabled) {
        previewLevel = enabled ? level : null;
        preview = enabled ? new AuroraStatePayload(0x4155524f5241L,
                level.getGameTime() - AuroraPolicy.FADE_TICKS, 24_000, 0.90F, true) : null;
        send(level);
    }

    private static void send(ServerLevel level) {
        var payload = snapshot(level);
        for (ServerPlayer player : level.players()) PacketDistributor.sendToPlayer(player, payload);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % 20L == 0) send(level);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { sync(event); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { sync(event); }
    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, snapshot(player.serverLevel()));
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { previewLevel = null; preview = null; }
}
