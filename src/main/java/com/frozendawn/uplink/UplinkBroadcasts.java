package com.frozendawn.uplink;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.MonitoringStationState;
import com.frozendawn.world.MonitoringStationPlacement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Listen mode: automated ORSA carrier traffic. Rotates phase advisories, relay census and ambient traffic. */
public final class UplinkBroadcasts {
    public static final int AMBIENT = 8;
    private UplinkBroadcasts() { }

    /** A translation key and its arguments, so the console log stays localizable and can be saved. */
    public record Line(String key, List<String> args) {
        public Line { args = List.copyOf(args); }
    }

    /** Blocks around the console within which the relay knows its stations: about two station regions. */
    public static final int CENSUS_RADIUS = 1024;

    public static Line next(ServerLevel level, BlockPos console, int index) {
        var server = level.getServer();
        return switch (Math.floorMod(index, 3)) {
            case 0 -> new Line("uplink.frozendawn.listen.phase." + Math.clamp(ApocalypseState.get(server).getPhase(), 0, 6), List.of());
            case 1 -> {
                var census = census(level, console);
                yield census[1] == 0 ? new Line("uplink.frozendawn.listen.census.none", List.of())
                        : new Line("uplink.frozendawn.listen.census", List.of(Integer.toString(census[0]), Integer.toString(census[1])));
            }
            default -> new Line("uplink.frozendawn.listen.ambient." + (Math.floorMod(index / 3, AMBIENT) + 1), List.of());
        };
    }

    /**
     * {answering, known}. The relay knows every station on its network from the seed alone, built or not, within
     * {@link #CENSUS_RADIUS} of the console (overworld only). A candidate worldgen has already rejected was never a
     * station and is not counted. Answering means its terminal has been unlocked. Nothing is loaded or built.
     */
    public static int[] census(ServerLevel level, BlockPos console) {
        if (level.dimension() != ServerLevel.OVERWORLD) return new int[] {0, 0};
        var stations = MonitoringStationState.get(level.getServer());
        int region = 32 * 16, reach = CENSUS_RADIUS / region + 1, known = 0, answering = 0;
        int rx0 = Math.floorDiv(console.getX(), region), rz0 = Math.floorDiv(console.getZ(), region);
        for (int rx = rx0 - reach; rx <= rx0 + reach; rx++) for (int rz = rz0 - reach; rz <= rz0 + reach; rz++) {
            var pos = MonitoringStationPlacement.getStationBlockPos(level.getSeed(), rx, rz);
            if (pos == null || !MonitoringStationPlacement.isOutsideSpawnBuffer(level, pos[0], pos[1])) continue;
            double dx = pos[0] - console.getX(), dz = pos[1] - console.getZ();
            if (dx * dx + dz * dz > (double) CENSUS_RADIUS * CENSUS_RADIUS) continue;
            int cx = pos[0] >> 4, cz = pos[1] >> 4;
            if (stations.isStationEvaluated(cx, cz) && !stations.isStationBuilt(cx, cz)) continue;
            known++;
            if (stations.isStationUnlocked(cx, cz)) answering++;
        }
        return new int[] {answering, known};
    }
}
