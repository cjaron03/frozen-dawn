package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Whether the technician's head has turned to the cradle. It turns once, while the player rests. */
public final class VaultTechnicianBlockEntity extends BlockEntity {
    private boolean turned;

    public VaultTechnicianBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_TECHNICIAN.get(), pos, state); }
    public boolean turned() { return turned; }
    public void turn() { set(true); }
    /** Playtest only. */
    public void reset() { set(false); }
    private void set(boolean value) {
        turned = value;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); tag.putBoolean("turned", turned); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); turned = tag.getBoolean("turned"); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
