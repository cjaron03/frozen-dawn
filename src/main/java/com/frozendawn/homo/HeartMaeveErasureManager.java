package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server authority for the sustained, non-accidental Maeve erasure choice. */
public final class HeartMaeveErasureManager {
    private static final long MAX_PULSE_GAP_TICKS = 2L;
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    private HeartMaeveErasureManager() {
    }

    public static void handlePulse(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (!validTarget(level, player, hearth)) {
            CHANNELS.remove(player.getUUID());
            return;
        }

        long now = level.getGameTime();
        Channel previous = CHANNELS.get(player.getUUID());
        if (previous != null && previous.lastPulseTick() == now) {
            return;
        }
        int ticks = previous != null
                && now - previous.lastPulseTick() <= MAX_PULSE_GAP_TICKS
                ? previous.ticks() + 1 : 1;
        CHANNELS.put(player.getUUID(), new Channel(now, ticks));
        if (ticks < HeartMaeveErasurePolicy.CHANNEL_TICKS) {
            return;
        }

        CHANNELS.remove(player.getUUID());
        if (data.startHeartMaeveErasure(
                hearth.id(), now, player.getUUID())) {
            PostMaeveWorldState.markErased(level);
            BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
            level.playSound(null, BlockPos.containing(HeartLattice.maevePosition(anchor)),
                    ModSounds.THAE_IVEN_HEART_COLLAPSE.get(),
                    SoundSource.AMBIENT, 2.8F, 0.42F);
        }
    }

    public static void reset() {
        CHANNELS.clear();
    }

    private static boolean validTarget(
            ServerLevel level,
            ServerPlayer player,
            ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth == null || !hearth.heartCollapseComplete()
                || !hearth.heartMaeveExposed()
                || hearth.heartMaeveErasureStartGameTime() >= 0L
                || hearth.heartMaeveErasureComplete()
                || player.isSpectator()) {
            return false;
        }
        BlockPos anchor = hearth.heartAnchor().orElse(hearth.center());
        Vec3 maeve = HeartLattice.maevePosition(anchor);
        Vec3 eye = player.getEyePosition();
        if (eye.distanceToSqr(maeve)
                > HeartLattice.MAX_MAEVE_INTERACTION_DISTANCE
                * HeartLattice.MAX_MAEVE_INTERACTION_DISTANCE
                || !HeartLattice.raySelectsMaeve(
                eye, player.getViewVector(1.0F), maeve)) {
            return false;
        }
        HitResult hit = level.clip(new ClipContext(
                eye, maeve, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private record Channel(long lastPulseTick, int ticks) {
    }
}
