package com.frozendawn.client;

import com.frozendawn.block.HalseyCoreBlockEntity;
import com.frozendawn.power.IcarusLook;
import com.frozendawn.power.IcarusPhysics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The running core's chance events, shared by the renderer (which draws them) and the client ticker (which sounds them):
 * stray annihilations striking the shield, and the stabilizer that fails as the shield runs low. Both are seeded from the
 * game clock and the core's position, so every viewer sees the same events at the same moments. Cosmetic only. */
public final class IcarusFx {
    /** Hits are rolled in slots of this many ticks and each glows on the shield for HIT_LIFE ticks. */
    public static final int HIT_SLOT = 2, HIT_LIFE = 8;
    /** The drawn shield's radius in blocks, about the core's centre. */
    public static final float SHIELD_RADIUS = 4.5f;
    /** A failing stabilizer's beam may cut out in each slot of this many ticks. */
    public static final int SPUTTER_SLOT = 2;
    private IcarusFx() {}

    public record Hit(Vec3 dir, float age, long slot) {}

    /** Stray annihilations reaching the shield per second: about one every two seconds at a gentle 2900 C, three a second
     * at full output, eight at the red line and a storm of 25 near critical, rising exponentially, ten-fold every 3150 C. */
    public static double hitRate(int coreC) {
        return Math.min(25, 0.6 * Math.exp((coreC - 2900) / 1366.0));
    }

    /** The hits alive at time, newest last; none unless the plasma burns inside a standing shield. */
    public static List<Hit> hits(HalseyCoreBlockEntity core, float time, boolean burning) {
        var v = core.visual();
        if (!burning || IcarusLook.shieldAlpha(v.state(), v.shieldPct()) < 0.1) return List.of();
        double perSlot = hitRate(v.coreC()) * HIT_SLOT / 20.0;
        int tries = (int) Math.ceil(perSlot), salt = core.getBlockPos().hashCode() * 17;
        var hits = new ArrayList<Hit>();
        long now = Mth.floor(time / HIT_SLOT);
        for (long slot = now - HIT_LIFE / HIT_SLOT; slot <= now; slot++) {
            float age = (time - slot * HIT_SLOT) / HIT_LIFE;
            if (age < 0 || age >= 1) continue;
            for (int m = 0; m < tries; m++) {
                int seed = (int) (slot * 7919) + m * 104729 + salt;
                if (hash01(seed) >= perSlot - m) continue;
                double lat = Math.asin(2 * hash01(seed + 1) - 1) * 0.9, lon = 2 * Math.PI * hash01(seed + 2);
                hits.add(new Hit(IcarusDraw.onSphere(1, lat, lon), age, slot));
            }
        }
        return hits;
    }

    /** Which stabilizer fails for this core, as a 2D direction index. */
    public static int sputterIndex(HalseyCoreBlockEntity core) { return Math.floorMod(core.getBlockPos().hashCode() * 31 + 7, IcarusLook.STABILIZERS); }

    /** How badly this core's failing stabilizer misbehaves (IcarusLook.stabilizerFailing), or 0 while its beam is dark. */
    public static double failing(HalseyCoreBlockEntity core) {
        var v = core.visual();
        return IcarusLook.beamsLit(v.state(), v.stepLeft()) <= sputterIndex(core) ? 0 : IcarusLook.stabilizerFailing(v.state(), v.shieldPct());
    }

    /** Whether the failing beam is out during this slot: more often the worse it fails, never for long. */
    public static boolean cutOut(HalseyCoreBlockEntity core, long slot, double failing) {
        return failing > 0 && hash01((int) (slot * 6151) + core.getBlockPos().hashCode()) < 0.2 + 0.45 * failing;
    }

    public static double hash01(int seed) { return (Mth.murmurHash3Mixer(seed) & 0xFFFFFF) / (double) 0x1000000; }
}
