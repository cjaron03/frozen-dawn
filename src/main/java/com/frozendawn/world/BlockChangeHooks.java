package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** What Frozen Dawn's systems must hear when a block in a loaded chunk changes: power links, rooms, room heat, open flames
 * and airlocks. LevelChunk.setBlockState calls it through VacuumLevelChunkMixin; code that writes chunk sections directly,
 * such as the Icarus crater, calls it for each block it changes. */
public final class BlockChangeHooks {
    private BlockChangeHooks() {}

    public record Change(BlockPos pos, BlockState before, BlockState after) {}

    /** The same for a batch, such as one tick's carve of a crater chunk: power links and the room scan caches are
     * invalidated once for the whole batch, not once per block. */
    public static void changed(ServerLevel level, List<Change> changes) {
        if (changes.isEmpty()) return;
        com.frozendawn.power.PowerNetworks.blocksChanged(level, changes);
        if (level.dimension() != Level.OVERWORLD) return;
        RoomAtmosphere.blocksChanged(level, changes);
        // An airlock only reacts to a change at one of its chambers or doors.
        boolean airlocks = !com.frozendawn.airlock.AirlockSavedState.get(level).chambers().isEmpty();
        for (var c : changes) {
            RoomThermalManager.blockChanged(level, c.pos(), c.before(), c.after());
            VacuumFlames.blockChanged(level, c.pos(), c.after());
            if (airlocks) com.frozendawn.airlock.AirlockManager.blockChanged(level, c.pos(), c.before(), c.after());
        }
    }

    public static void changed(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        com.frozendawn.power.PowerNetworks.blockChanged(level, pos, before, after);
        if (level.dimension() != Level.OVERWORLD) return;
        RoomAtmosphere.blockChanged(level, pos, before, after);
        RoomThermalManager.blockChanged(level, pos, before, after);
        VacuumFlames.blockChanged(level, pos, after);
        com.frozendawn.airlock.AirlockManager.blockChanged(level, pos, before, after);
    }
}
