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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ShepherdDockBlockEntity extends BlockEntity {
    private int bootTicks;
    private UUID waker;
    /** Only real while the block is SEATED: a vault reset puts FOUND back and leaves this behind. */
    private ItemStack cell = ItemStack.EMPTY;

    public ShepherdDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CS1_DOCK.get(), pos, state);
    }

    /** The cell goes in and nothing wakes: that is the player's choice, and the cell is only spent on Boot. */
    public void seat(ItemStack cell) {
        this.cell = cell;
        setChanged();
        setState(DockState.SEATED);
    }

    public ItemStack seated() {
        return getBlockState().getValue(ShepherdDockBlock.STATE) == DockState.SEATED ? cell : ItemStack.EMPTY;
    }

    /** Boot: the seated cell goes into her, for good. */
    void wake(ServerPlayer waker) {
        var data = ShepherdSavedData.get(waker.getServer());
        data.stage(Stage.AWAKE);
        data.wokeAt(waker.getServer().overworld().getGameTime());
        data.flag(ShepherdSavedData.WOKE_IN_VAULT, getBlockState().is(ModBlocks.VAULT_CS1_DOCK.get()));
        data.resumeNode(ShepherdTalk.FIRST_MEETING);
        cell = ItemStack.EMPTY;
        bootTicks = ShepherdBoot.BIOS_TICKS;
        this.waker = waker.getUUID();
        setState(DockState.BOOT);
        level.playSound(null, worldPosition, ModSounds.CS1_DOCK_BOOT.get(), SoundSource.BLOCKS, 1, 1);
    }

    private void setState(DockState state) {
        level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition).setValue(ShepherdDockBlock.STATE, state));
    }

    private void liftOff() {
        bootTicks = 0;
        // There is one of her: a boot that outlived her waking, or the world's reset, builds nothing.
        if (ShepherdSavedData.get(level.getServer()).stage() != Stage.AWAKE || ShepherdReactions.loaded(level.getServer()) != null) return;
        ShepherdEntity.spawnFrom((ServerLevel) level, worldPosition.above(), waker);
        // A waker who has left, or is in trouble, finds the first meeting waiting on their next right-click.
        ShepherdBoot.liftedOff(level.getPlayerByUUID(waker));
    }

    /** The dock is going away: she is already awake and has to be somewhere, and a seated cell has to go somewhere too. */
    void removed() {
        if (bootTicks > 0) liftOff();
        giveBackCell();
    }

    private void giveBackCell() {
        var back = seated();
        cell = ItemStack.EMPTY;
        if (!back.isEmpty()) Block.popResource(level, worldPosition, back);
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
        // A found or seated dock still shows her at rest, and she cannot be in two places.
        var state = level.getBlockState(worldPosition).getValue(ShepherdDockBlock.STATE);
        if ((state != DockState.FOUND && state != DockState.SEATED) || ShepherdSavedData.get(level.getServer()).stage() != Stage.AWAKE) return;
        giveBackCell();
        setState(DockState.DARK);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (bootTicks > 0) {
            tag.putInt("BootTicks", bootTicks);
            tag.putUUID("Waker", waker);
        }
        if (!cell.isEmpty()) tag.put("Cell", cell.save(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bootTicks = tag.getInt("BootTicks");
        waker = tag.hasUUID("Waker") ? tag.getUUID("Waker") : null;
        cell = ItemStack.parseOptional(registries, tag.getCompound("Cell"));
    }
}
