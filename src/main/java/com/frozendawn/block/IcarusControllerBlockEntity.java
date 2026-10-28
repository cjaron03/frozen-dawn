package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** AMR-1 Icarus Controller state. For now it holds the build guide; the simulation joins it with the running reactor. */
public final class IcarusControllerBlockEntity extends BlockEntity {
    /** Players shown this Controller's ghost-block blueprint; transient, like the client's own copy. */
    private final Set<UUID> blueprintViewers = new HashSet<>();
    public IcarusControllerBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.ICARUS_CONTROLLER.get(), p, s); }

    public List<GhostCell> blueprint(Level l) {
        return IcarusStructure.wrongCells(l, worldPosition, getBlockState().getValue(IcarusControllerBlock.FACING), Integer.MAX_VALUE);
    }
    /** Shows or hides the blueprint for one player; returns whether it is now shown. */
    public boolean toggleBlueprint(UUID viewer) { return blueprintViewers.remove(viewer) ? false : blueprintViewers.add(viewer); }
    public boolean showsBlueprintTo(UUID viewer) { return blueprintViewers.contains(viewer); }
}
