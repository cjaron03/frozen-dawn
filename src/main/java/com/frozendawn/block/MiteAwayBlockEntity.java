package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.MiteAwayRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MiteAwayBlockEntity extends BlockEntity {

    public static final int MAX_BURN_TICKS = 20 * 60 * 10;
    public static final int COVERAGE_RADIUS = 8;

    private int burnTimeRemaining = MAX_BURN_TICKS;
    private boolean registryActive;

    public MiteAwayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MITEAWAY.get(), pos, state);
    }

    public void serverTick() {
        if (burnTimeRemaining > 0 && getBlockState().getValue(MiteAwayBlock.LIT)) {
            burnTimeRemaining = Math.max(0, burnTimeRemaining - 1);
            if (burnTimeRemaining == 0 || (level != null && level.getGameTime() % 200L == 0L)) {
                setChanged();
            }
        }
        updateBurnState();
    }

    public boolean ignite() {
        if (!hasCharge() || isLit()) {
            return false;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return false;
        }
        BlockState current = getBlockState();
        level.setBlock(worldPosition, current.setValue(MiteAwayBlock.LIT, true).setValue(MiteAwayBlock.CHARGED, true), 3);
        syncRegistry(true);
        setChanged();
        return true;
    }

    public boolean extinguish() {
        if (!isLit()) {
            return false;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return false;
        }
        level.setBlock(worldPosition, getBlockState().setValue(MiteAwayBlock.LIT, false), 3);
        syncRegistry(false);
        setChanged();
        return true;
    }

    public boolean refreshToFullCharge() {
        if (burnTimeRemaining >= MAX_BURN_TICKS) {
            return false;
        }
        burnTimeRemaining = MAX_BURN_TICKS;
        updateBurnState();
        setChanged();
        return true;
    }

    public void loadFromItem(ItemStack stack) {
        burnTimeRemaining = Math.max(0, stack.getOrDefault(ModDataComponents.MITEAWAY_BURN_TICKS.get(), MAX_BURN_TICKS));
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            updateBurnState();
        }
    }

    public ItemStack createDropStack() {
        if (!hasCharge()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ModItems.MITEAWAY.get());
        stack.set(ModDataComponents.MITEAWAY_BURN_TICKS.get(), burnTimeRemaining);
        return stack;
    }

    public boolean hasCharge() {
        return burnTimeRemaining > 0;
    }

    public boolean isLit() {
        return getBlockState().getValue(MiteAwayBlock.LIT);
    }

    public int getBurnTimeRemaining() {
        return burnTimeRemaining;
    }

    private void updateBurnState() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockState current = getBlockState();
        boolean shouldBeCharged = hasCharge();
        boolean shouldBeLit = current.getValue(MiteAwayBlock.LIT) && shouldBeCharged;
        boolean litChanged = current.getValue(MiteAwayBlock.LIT) != shouldBeLit;
        boolean chargedChanged = current.getValue(MiteAwayBlock.CHARGED) != shouldBeCharged;
        if (litChanged || chargedChanged) {
            level.setBlock(worldPosition,
                    current.setValue(MiteAwayBlock.LIT, shouldBeLit)
                            .setValue(MiteAwayBlock.CHARGED, shouldBeCharged),
                    3);
        }
        syncRegistry(shouldBeLit);
    }

    private void syncRegistry(boolean active) {
        Level level = getLevel();
        if (level == null || level.isClientSide() || registryActive == active) {
            return;
        }

        registryActive = active;
        if (active) {
            MiteAwayRegistry.register(level, worldPosition);
        } else {
            MiteAwayRegistry.unregister(level, worldPosition);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            updateBurnState();
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            MiteAwayRegistry.unregister(level, worldPosition);
            registryActive = false;
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BurnTimeRemaining", burnTimeRemaining);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTimeRemaining = Math.max(0, tag.getInt("BurnTimeRemaining"));
    }
}
