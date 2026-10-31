package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only there so the client can draw the injector's nozzle. No state of its own: it reads the Halsey Core above it. */
public final class IcarusInjectorBlockEntity extends BlockEntity {
    /** The injector sits this far below the core. */
    public static final int CORE_DISTANCE = 5;
    public IcarusInjectorBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.ICARUS_INJECTOR.get(), p, s); }

    public @Nullable HalseyCoreBlockEntity core() {
        return level != null && level.getBlockEntity(worldPosition.above(CORE_DISTANCE)) instanceof HalseyCoreBlockEntity core ? core : null;
    }
}
