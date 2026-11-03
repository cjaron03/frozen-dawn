package com.frozendawn.gpr;

import com.frozendawn.gpr.PlumbLineScan.Return;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Triangulation memory: ore veins seen in the last ten minutes, with the scanner that saw them. A later pulse from a
 * scanner at least eight blocks away that hits an overlapping vein of the same type resolves it exactly. Held in
 * memory only (a session-length aid, not world state) and bounded.
 */
public final class PlumbLineMemory {
    private record Seen(Return vein, BlockPos scanner, long time) { }
    private static final int MAX = 512;
    private static final Map<ServerLevel, ArrayDeque<Seen>> LEVELS = new WeakHashMap<>();
    private PlumbLineMemory() { }

    /** True when an earlier sighting from a separate scanner overlaps this vein; always records this sighting. */
    public static boolean triangulate(ServerLevel level, Return vein, BlockPos scanner, long now) {
        var seen = LEVELS.computeIfAbsent(level, l -> new ArrayDeque<>());
        seen.removeIf(s -> now - s.time > PlumbLinePolicy.MEMORY_TICKS);
        boolean match = false;
        for (var s : seen) {
            if (!s.vein.label().equals(vein.label()) || !overlaps(s.vein, vein)) continue;
            if (PlumbLinePolicy.baseline(s.scanner.getX(), s.scanner.getY(), s.scanner.getZ(), scanner.getX(), scanner.getY(), scanner.getZ())) { match = true; break; }
        }
        if (seen.size() >= MAX) seen.removeFirst();
        seen.addLast(new Seen(vein, scanner.immutable(), now));
        return match;
    }

    static boolean overlaps(Return a, Return b) {
        return a.minX() <= b.maxX() + 1 && b.minX() <= a.maxX() + 1 && a.minY() <= b.maxY() + 1 && b.minY() <= a.maxY() + 1
                && a.minZ() <= b.maxZ() + 1 && b.minZ() <= a.maxZ() + 1;
    }

    public static void clear() { LEVELS.clear(); }
}
