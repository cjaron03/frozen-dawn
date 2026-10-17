package com.frozendawn.uplink;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.MonitoringStationState;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** Listen mode: automated ORSA carrier traffic. Rotates phase advisories, relay census and ambient traffic. */
public final class UplinkBroadcasts {
    public static final int AMBIENT = 8;
    private UplinkBroadcasts() { }

    /** A translation key and its arguments, so the console log stays localizable and can be saved. */
    public record Line(String key, List<String> args) {
        public Line { args = List.copyOf(args); }
    }

    public static Line next(ServerLevel level, int index) {
        var server = level.getServer();
        return switch (Math.floorMod(index, 3)) {
            case 0 -> new Line("uplink.frozendawn.listen.phase." + Math.clamp(ApocalypseState.get(server).getPhase(), 0, 6), List.of());
            case 1 -> {
                var stations = MonitoringStationState.get(server);
                int built = 0, answering = 0;
                for (var center : stations.getBuiltStationCenters()) {
                    built++;
                    if (stations.isStationUnlocked(center.getX() >> 4, center.getZ() >> 4)) answering++;
                }
                yield built == 0 ? new Line("uplink.frozendawn.listen.census.none", List.of())
                        : new Line("uplink.frozendawn.listen.census", List.of(Integer.toString(answering), Integer.toString(built)));
            }
            default -> new Line("uplink.frozendawn.listen.ambient." + (Math.floorMod(index / 3, AMBIENT) + 1), List.of());
        };
    }
}
