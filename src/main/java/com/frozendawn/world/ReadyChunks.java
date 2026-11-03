package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Non-blocking "is this block readable now" checks for background work.
 *
 * {@link Level#isLoaded} is true as soon as a chunk holder reaches FULL ticket level, which includes chunks that are
 * still being generated or read from disk. Reading such a chunk makes the server thread wait for it (or generate it),
 * which stalled epoch catch-up for over a second at a time. These checks only accept chunks whose full data is
 * already present, never request one, and return false off the server thread.
 */
public final class ReadyChunks {
    private ReadyChunks() { }

    public static boolean ready(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    public static boolean ready(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        if (level instanceof ServerLevel server) return ready(server, pos.getX() >> 4, pos.getZ() >> 4);
        return level.isLoaded(pos);
    }
}
