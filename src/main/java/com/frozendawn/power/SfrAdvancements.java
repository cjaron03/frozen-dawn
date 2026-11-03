package com.frozendawn.power;

import com.frozendawn.power.FusionPhysics.Readout;
import com.frozendawn.power.FusionPhysics.State;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** The SFR-1 advancements from the Notion fusion spec that hook into built systems. Mind the Quench, Helium Voice and
 * Borrowed Daylight ship with quench helium and the Exodus fusion tie. Decided once per reactor second. */
public final class SfrAdvancements {
    public static final String SECOND_SUN = "a_second_sun", TWENTY_YEARS = "twenty_years_away", BREAKEVEN = "breakeven",
            COLD_FUSION = "cold_fusion", LAWSON = "lawson_criterion", FISSION_FUSION_FIRE = "fission_fusion_fire", MASS_FUSION = "mass_fusion";
    public static final List<String> ALL = List.of(SECOND_SUN, TWENTY_YEARS, BREAKEVEN, COLD_FUSION, LAWSON, FISSION_FUSION_FIRE, MASS_FUSION);
    /** One in-game day of unbroken plasma. */
    public static final int LAWSON_SECONDS = 1200;
    public static final double COLD_FUSION_C = -50, MASS_FUSION_KW = 100;
    /** Players this close to the controller share its advancements, as with the CFU-7's. */
    public static final double RADIUS = 32;
    private SfrAdvancements() {}

    /** roomC is the controller's room at ignition, NaN when unknown or not an ignition second. mixedNetwork: a running
     * Geothermal Core and CFU-7 share the Power Port's network. exportedKw is the reactor's share of what that network absorbed. */
    public record Context(double roomC, int burnSeconds, boolean mixedNetwork, double exportedKw) {}

    public static boolean ignited(@Nullable Readout before, Readout after) {
        return before != null && before.state() == State.IGNITION && after.state() == State.BURNING;
    }
    private static boolean plasma(Readout r) { return r.state() == State.BURNING || r.state() == State.RAMP_DOWN; }

    public static List<String> earned(@Nullable Readout before, Readout after, Context c) {
        var out = new ArrayList<String>();
        if (ignited(before, after)) {
            out.add(SECOND_SUN);
            if (c.roomC() < COLD_FUSION_C) out.add(COLD_FUSION);
        } else if (before != null && before.state() == State.IGNITION && after.state() != State.IGNITION) out.add(TWENTY_YEARS);
        if (!plasma(after)) return out;
        if (after.netKw() > 0) out.add(BREAKEVEN);
        if (c.burnSeconds() >= LAWSON_SECONDS) out.add(LAWSON);
        if (c.mixedNetwork()) out.add(FISSION_FUSION_FIRE);
        if (c.exportedKw() >= MASS_FUSION_KW) out.add(MASS_FUSION);
        return out;
    }

    /** Seconds of unbroken plasma, counting this one. */
    public static int burnSeconds(int previous, Readout after) { return plasma(after) ? previous + 1 : 0; }

    /** The reactor's pro rata share of the generation its network's loads and banks absorbed; battery discharge is not counted. */
    public static int exportedWatts(int ownSupply, int networkSupply, int absorbed) {
        if (ownSupply <= 0 || networkSupply <= 0 || absorbed <= 0) return 0;
        return (int) ((long) Math.min(absorbed, networkSupply) * ownSupply / networkSupply);
    }

    /** A running Geothermal Core and a running CFU-7 on the network of this node. */
    public static boolean mixedNetwork(ServerLevel l, BlockPos node) {
        boolean geothermal = false, fission = false;
        for (var cell : PowerNetworks.component(l, node)) {
            if (!l.isLoaded(cell)) continue;
            var be = l.getBlockEntity(cell);
            if (be instanceof com.frozendawn.block.GeothermalCoreBlockEntity core && core.getPowerShare() > 0) geothermal = true;
            else if (be instanceof com.frozendawn.block.ReactorControllerBlockEntity cfu && cfu.powerWatts() > 0) fission = true;
            if (geothermal && fission) return true;
        }
        return false;
    }

    public static void grantNear(ServerLevel l, BlockPos at, List<String> names) {
        if (names.isEmpty()) return;
        for (var p : l.players()) if (!p.isSpectator() && p.blockPosition().closerThan(at, RADIUS))
            for (var name : names) com.frozendawn.radiation.ReactorAdvancements.grant(p, name);
    }
}
