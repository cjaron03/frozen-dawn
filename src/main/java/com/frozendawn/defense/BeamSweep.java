package com.frozendawn.defense;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** The directions a beam passes through between last tick's aim and this tick's, by spherical interpolation. */
public final class BeamSweep {
    /** Widest gap between neighbouring rays at full reach, in blocks. */
    public static final double SPACING = 0.25;
    public static final int MAX_RAYS = 64;
    private BeamSweep() {}

    public static List<Vec3> rays(Vec3 from, Vec3 to, double reach) {
        var end = to.normalize();
        if (from == null) return List.of(end);
        var start = from.normalize();
        double cos = Math.clamp(start.dot(end), -1, 1);
        double angle = Math.acos(cos);
        // Opposite aims have no unique arc; a flick that wide is a new aim, not a sweep.
        if (angle < 1e-6 || Math.PI - angle < 1e-3) return List.of(end);
        // Arc length bounds every neighbouring chord.
        int steps = Math.min(MAX_RAYS - 1, (int) Math.ceil(reach * angle / SPACING));
        double sin = Math.sin(angle);
        var rays = new ArrayList<Vec3>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double a = Math.sin((1 - t) * angle) / sin, b = Math.sin(t * angle) / sin;
            rays.add(start.scale(a).add(end.scale(b)));
        }
        return rays;
    }
}
