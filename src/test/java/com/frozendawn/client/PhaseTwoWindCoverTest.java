package com.frozendawn.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTwoWindCoverTest {
    static final BlockPos EYE = new BlockPos(-4, 64, -19);

    static final class Column implements BlockGetter {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        void at(int y, BlockState state) { states.put(EYE.atY(y), state); }
        @Override public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }

    @Test void loadedRoofUsesPacketSupportedBoundAndActualBlocks() {
        assertTrue(Heightmap.Types.MOTION_BLOCKING.sendToClient());
        assertFalse(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.sendToClient(),
                "The old roof map is absent from the client chunk packet");
        var column = new Column();
        for (var roof : new BlockState[]{Blocks.STONE.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState()}) {
            column.at(66, roof);
            assertFalse(PhaseTwoWindCover.isExposed(column, EYE, 67));
            assertTrue(PhaseTwoWindCover.isExposed(column, EYE.atY(68), 67));
        }
        column.at(66, Blocks.AIR.defaultBlockState());
        assertTrue(PhaseTwoWindCover.isExposed(column, EYE, 67),
                "A removed roof must stop sheltering even with a stale upper bound");
    }

    @Test void canopyDoesNotShelterButCannotHideARoofBelowIt() {
        var column = new Column();
        column.at(73, Blocks.OAK_LEAVES.defaultBlockState());
        column.at(74, Blocks.OAK_LEAVES.defaultBlockState());
        assertTrue(PhaseTwoWindCover.isExposed(column, EYE, 75));
        column.at(66, Blocks.GLASS.defaultBlockState());
        assertFalse(PhaseTwoWindCover.isExposed(column, EYE, 75));
        column.at(66, Blocks.AIR.defaultBlockState());
        column.at(70, Blocks.WATER.defaultBlockState());
        assertFalse(PhaseTwoWindCover.isExposed(column, EYE, 75));
    }
}
