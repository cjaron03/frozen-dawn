package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Once a second: note where she is, and if she is meant to be there and is not, build her again. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdWatch {
    private ShepherdWatch() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        var data = ShepherdSavedData.get(server);
        // No dimension yet means the dock has not finished booting her.
        if (data.stage() != Stage.AWAKE || data.dimension() == null || data.lastPos() == null) return;
        var level = server.getLevel(data.dimension());
        if (level == null) return;
        if (level.getEntity(data.entity()) instanceof ShepherdEntity she && !she.isRemoved()) {
            if (!she.blockPosition().equals(data.lastPos())) data.lastPos(she.blockPosition());
            return;
        }
        // Entities load after their chunk: a chunk that is only half up would look like she had gone.
        if (level.areEntitiesLoaded(ChunkPos.asLong(data.lastPos()))) {
            ShepherdEntity.spawnFrom(level, data.lastPos(), null);
            return;
        }
        // Her body is out of reach of everything, so her own goal cannot fetch her. It discards itself if it ever loads.
        var player = data.waiting() ? null : ShepherdEntity.followerIn(level);
        double line = ShepherdPolicy.TELEPORT_BLOCKS;
        if (player != null && player.blockPosition().distSqr(data.lastPos()) > line * line) {
            ShepherdEntity.spawnAt(level, ShepherdEntity.roomBeside(player), null);
        }
    }
}
