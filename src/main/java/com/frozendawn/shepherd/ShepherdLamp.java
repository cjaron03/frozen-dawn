package com.frozendawn.shepherd;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import com.frozendawn.world.ReadyChunks;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Her lamp, as one more source in {@code HeldLanternLights}. It only ever takes air, so a dock is safe under her. */
public final class ShepherdLamp {
    private static final int LEVEL = 14;

    private ShepherdLamp() {}

    public static void collect(ServerLevel level, Map<BlockPos, Integer> desired) {
        var data = ShepherdSavedData.get(level.getServer());
        if (data.stage() != Stage.AWAKE || !data.lamp() || !level.dimension().equals(data.dimension())) return;
        if (!(level.getEntity(data.entity()) instanceof ShepherdEntity she) || she.offline()) return;
        for (var pos : List.of(BlockPos.containing(she.getEyePosition()), she.blockPosition())) {
            if (!ReadyChunks.ready(level, pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(ModBlocks.HELD_LANTERN_LIGHT) || state.isAir() || (state.is(Blocks.WATER) && state.getFluidState().isSource())) {
                desired.merge(pos.immutable(), LEVEL, Math::max);
                return;
            }
        }
    }
}
