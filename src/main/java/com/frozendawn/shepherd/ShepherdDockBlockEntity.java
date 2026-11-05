package com.frozendawn.shepherd;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.shepherd.ShepherdDockBlock.DockState;
import com.frozendawn.shepherd.ShepherdSavedData.Stage;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ShepherdDockBlockEntity extends BlockEntity {
    /** How long the green light holds before she lifts off. */
    private static final int BOOT_TICKS = 20;

    private int bootTicks;
    private UUID waker;

    public ShepherdDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CS1_DOCK.get(), pos, state);
    }

    /** The cell is already spent. */
    public void wake(ServerPlayer waker) {
        var data = ShepherdSavedData.get(waker.getServer());
        data.stage(Stage.AWAKE);
        data.flag(ShepherdSavedData.WOKE_IN_VAULT, getBlockState().is(ModBlocks.VAULT_CS1_DOCK.get()));
        data.resumeNode(ShepherdTalk.FIRST_MEETING);
        bootTicks = BOOT_TICKS;
        this.waker = waker.getUUID();
        setState(DockState.BOOT);
        level.playSound(null, worldPosition, ModSounds.CS1_DOCK_BOOT.get(), SoundSource.BLOCKS, 1, 1);
    }

    private void setState(DockState state) {
        level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition).setValue(ShepherdDockBlock.STATE, state));
    }

    private void liftOff() {
        bootTicks = 0;
        ShepherdEntity.spawnFrom((ServerLevel) level, worldPosition.above(), waker);
        // A waker who has left finds the first meeting waiting on their next right-click.
        if (level.getPlayerByUUID(waker) instanceof ServerPlayer player) ShepherdTalk.start(player, ShepherdTalk.FIRST_MEETING);
    }

    /** The dock is going away: she is already awake and has to be somewhere. */
    void removed() {
        if (bootTicks > 0) liftOff();
    }

    void serverTick() {
        if (bootTicks > 0) {
            setChanged();
            if (--bootTicks == 0) {
                liftOff();
                setState(DockState.EMPTY);
            }
            return;
        }
        if (level.getGameTime() % 20 != 0) return;
        // A found dock still shows her at rest, and she cannot be in two places.
        boolean found = level.getBlockState(worldPosition).getValue(ShepherdDockBlock.STATE) == DockState.FOUND;
        if (found && ShepherdSavedData.get(level.getServer()).stage() == Stage.AWAKE) setState(DockState.DARK);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (bootTicks > 0) {
            tag.putInt("BootTicks", bootTicks);
            tag.putUUID("Waker", waker);
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bootTicks = tag.getInt("BootTicks");
        waker = tag.hasUUID("Waker") ? tag.getUUID("Waker") : null;
    }
}
