package com.frozendawn.power;

import com.frozendawn.power.IcarusPhysics.Readout;
import com.frozendawn.power.IcarusPhysics.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** The AMR-1 Icarus advancements, all hidden and granted from code. The running ones are decided once per reactor second
 * and go to crew near the core; the two detonation ones are granted by IcarusDetonation. */
public final class IcarusAdvancements {
    public static final String E_EQUALS_MC2 = "e_equals_mc2", TOO_CLOSE = "too_close_to_the_sun", CLIPPED_WINGS = "clipped_wings",
            WAKE_ME = "wake_me_when_you_need_me", CONTINUITY = "continuity_beyond", FALL = "the_fall_of_icarus", DONT_LOOK_UP = "dont_look_up";
    public static final List<String> ALL = List.of(E_EQUALS_MC2, TOO_CLOSE, CLIPPED_WINGS, WAKE_ME, CONTINUITY, FALL, DONT_LOOK_UP);
    /** Crew this close to the core share its running advancements. */
    public static final double RADIUS = 64;
    /** Full output, allowing for the readout's floating-point ramp landing a hair under the cap. */
    private static final double FULL_MW = IcarusPhysics.MAX_OUTPUT_MW - 1e-3;
    private IcarusAdvancements() {}

    /** restartedAfterCole: a full Cole Protocol completed since this reactor last ignited. */
    public static List<String> earned(@Nullable Readout before, Readout after, boolean restartedAfterCole) {
        var out = new ArrayList<String>();
        if (before == null) return out;
        if (before.state() != State.IGNITION && after.state() == State.IGNITION) {
            out.add(E_EQUALS_MC2);
            if (restartedAfterCole) out.add(WAKE_ME);
        }
        if (after.grossMw() >= FULL_MW && before.grossMw() < FULL_MW) out.add(TOO_CLOSE);
        if (before.state() != State.CLIPPED && after.state() == State.CLIPPED) out.add(CLIPPED_WINGS);
        return out;
    }

    /** A producing Geothermal Core, CFU-7 Hearthstone and SFR-1 Solstice on the network of this node. */
    public static boolean continuity(ServerLevel l, BlockPos node) {
        boolean geothermal = false, fission = false, fusion = false;
        for (var cell : PowerNetworks.component(l, node)) {
            if (!l.isLoaded(cell)) continue;
            var be = l.getBlockEntity(cell);
            if (be instanceof com.frozendawn.block.GeothermalCoreBlockEntity core && core.getPowerShare() > 0) geothermal = true;
            else if (be instanceof com.frozendawn.block.ReactorControllerBlockEntity cfu && cfu.powerWatts() > 0) fission = true;
            // The Solstice joins a grid by its power port, not its Controller.
            else if (be instanceof com.frozendawn.block.SfrPowerPortBlockEntity sfr && sfr.supplyWatts() > 0) fusion = true;
            if (geothermal && fission && fusion) return true;
        }
        return false;
    }

    public static void grantCrewNear(ServerLevel l, BlockPos core, Collection<UUID> crew, List<String> names) {
        if (names.isEmpty()) return;
        for (var id : crew)
            if (l.getPlayerByUUID(id) instanceof net.minecraft.server.level.ServerPlayer p && !p.isSpectator() && p.blockPosition().closerThan(core, RADIUS))
                for (var name : names) com.frozendawn.radiation.ReactorAdvancements.grant(p, name);
    }
}
