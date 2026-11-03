package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only there so the client can draw a stabilizer's moving rings. No state of its own: it reads the Halsey Core it faces. */
public final class IcarusStabilizerBlockEntity extends BlockEntity {
    /** Stabilizers stand this far from the core along their facing. */
    public static final int CORE_DISTANCE = 7;
    public IcarusStabilizerBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.ICARUS_STABILIZER.get(), p, s); }

    /** Its place in the lighting order, going round the ring: south, west, north, east of the core. */
    public int index() { return getBlockState().getValue(IcarusStabilizerBlock.FACING).getOpposite().get2DDataValue(); }

    public @Nullable HalseyCoreBlockEntity core() {
        if (level == null) return null;
        var at = worldPosition.relative(getBlockState().getValue(IcarusStabilizerBlock.FACING), CORE_DISTANCE);
        return level.getBlockEntity(at) instanceof HalseyCoreBlockEntity core ? core : null;
    }
}
