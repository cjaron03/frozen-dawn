package com.frozendawn.maeve;

import com.frozendawn.entity.LabSignalActor;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Presentation playback only. Never inserted into Maeve's memory or coordinator. */
public final class LabSignalScene implements AutoCloseable {
    private final ServerLevel level;
    private final ConvergenceGroup cloud;
    private final LabSignalActor actor;
    private final long started;
    public LabSignalScene(ServerLevel level, BlockPos origin, boolean scout) {
        this.level = level; started = level.getServer().overworld().getGameTime();
        if (scout) {
            cloud = null; actor = new LabSignalActor(level, origin.offset(0, 0, -28));
            level.addFreshEntity(actor); actor.begin();
        } else {
            actor = null;
            var hotspot = new Hotspot(UUID.randomUUID(), level.dimension().location().toString(), origin);
            var roster = new LinkedHashMap<UUID, BlockPos>();
            roster.put(UUID.randomUUID(), origin.offset(40, 0, 0));
            roster.put(UUID.randomUUID(), origin.offset(-40, 0, 0));
            cloud = new ConvergenceGroup(UUID.randomUUID(), hotspot, roster, started);
            ConvergencePresentation.cloud(level, cloud, started);
        }
    }
    public void tick() {
        long now = level.getServer().overworld().getGameTime();
        if (cloud != null && now > started && (now - started) % 20 == 0)
            ConvergencePresentation.cloud(level, cloud, now);
    }
    @Override public void close() { if (actor != null) actor.discard(); }
}
