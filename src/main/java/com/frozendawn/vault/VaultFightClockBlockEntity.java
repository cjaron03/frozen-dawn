package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** What one of the fight's clocks shows. Argus sets it. */
public final class VaultFightClockBlockEntity extends BlockEntity {
    private int seconds = ArgusFight.CLOCK_TICKS / 20;

    public VaultFightClockBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_FIGHT_CLOCK.get(), pos, state); }
    public int seconds() { return seconds; }
    public void show(int seconds) {
        if (seconds == this.seconds) return;
        this.seconds = seconds;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); tag.putInt("seconds", seconds); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); seconds = tag.getInt("seconds"); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
