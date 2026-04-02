package com.frozendawn.block;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TownPASpeakerBlockEntity extends BlockEntity {

    private static final int ACTIVE_PHASE_MAX = 4;
    private static final long BROADCAST_CYCLE_TICKS = 55L * 20L;
    private static final long CLIP_DURATION_TICKS = 22L * 20L;
    private static final double HEARING_RANGE = 64.0;
    private static final double HEARING_RANGE_SQR = HEARING_RANGE * HEARING_RANGE;
    private static final float PLAYBACK_VOLUME = 4.0f;

    private long lastPlaybackTick = Long.MIN_VALUE;

    public TownPASpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_PA_SPEAKER.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int phase = ApocalypseState.get(serverLevel.getServer()).getPhase();
        if (phase > ACTIVE_PHASE_MAX) {
            stopBroadcast(serverLevel);
            return;
        }

        long gameTime = serverLevel.getGameTime();
        long offset = Math.floorMod(mix64(worldPosition.asLong() ^ 0x5041535045414B52L), BROADCAST_CYCLE_TICKS);
        if (Math.floorMod(gameTime + offset, BROADCAST_CYCLE_TICKS) != 0L || gameTime == lastPlaybackTick) {
            return;
        }

        if (!hasNearbyPlayer(serverLevel)) {
            return;
        }

        SoundEvent sound = phase <= 2 ? ModSounds.TOWN_PA_CLEAR.get() : ModSounds.TOWN_PA_DEGRADED.get();
        serverLevel.playSound(
                null,
                worldPosition,
                sound,
                SoundSource.BLOCKS,
                PLAYBACK_VOLUME,
                1.0f
        );
        lastPlaybackTick = gameTime;
    }

    private void stopBroadcast(ServerLevel level) {
        if (lastPlaybackTick == Long.MIN_VALUE || level.getGameTime() - lastPlaybackTick > CLIP_DURATION_TICKS) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= HEARING_RANGE_SQR) {
                player.connection.send(new ClientboundStopSoundPacket(ModSounds.TOWN_PA_CLEAR.get().getLocation(), SoundSource.BLOCKS));
                player.connection.send(new ClientboundStopSoundPacket(ModSounds.TOWN_PA_DEGRADED.get().getLocation(), SoundSource.BLOCKS));
            }
        }

        lastPlaybackTick = Long.MIN_VALUE;
    }

    private boolean hasNearbyPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= HEARING_RANGE_SQR) {
                return true;
            }
        }
        return false;
    }

    private static long mix64(long value) {
        long x = value + 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
