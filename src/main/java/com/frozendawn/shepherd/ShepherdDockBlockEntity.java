package com.frozendawn.shepherd;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.shepherd.ShepherdDockBlock.DockState;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ShepherdDockBlockEntity extends BlockEntity {
    public ShepherdDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CS1_DOCK.get(), pos, state);
    }

    /** The cell is already spent. */
    public void wake(ServerPlayer waker) {
        ShepherdSavedData.get(waker.getServer()).stage(Stage.AWAKE);
        setState(DockState.BOOT);
        level.playSound(null, worldPosition, ModSounds.CS1_DOCK_BOOT.get(), SoundSource.BLOCKS, 1, 1);
    }

    private void setState(DockState state) {
        level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition).setValue(ShepherdDockBlock.STATE, state));
    }

    void serverTick() {
        if (level.getGameTime() % 20 != 0) return;
        // A found dock still shows her at rest, and she cannot be in two places.
        boolean found = level.getBlockState(worldPosition).getValue(ShepherdDockBlock.STATE) == DockState.FOUND;
        if (found && ShepherdSavedData.get(level.getServer()).stage() == Stage.AWAKE) setState(DockState.DARK);
    }
}
