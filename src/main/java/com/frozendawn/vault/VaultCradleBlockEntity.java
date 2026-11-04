package com.frozendawn.vault;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Holds the one Terminus of a world, as found: 3 discharges, one transfer logged. */
public final class VaultCradleBlockEntity extends BlockEntity {
    private boolean holding = true;

    public VaultCradleBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.VAULT_CRADLE.get(), pos, state); }
    public boolean holding() { return holding; }
    public boolean take(Player player) {
        if (!holding) return false;
        var wand = new ItemStack(ModItems.TERMINUS.get());
        if (!player.getInventory().add(wand)) player.drop(wand, false);
        set(false);
        return true;
    }
    /** Playtest only. */
    public void reset() { set(true); }
    private void set(boolean value) {
        holding = value;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); tag.putBoolean("holding", holding); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); holding = tag.getBoolean("holding"); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
