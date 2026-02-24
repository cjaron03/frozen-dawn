package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the Thermal Heater. Tracks remaining fuel burn ticks.
 * Stops ticking when chunk unloads (vanilla default) — fuel does NOT burn while unloaded.
 */
public class ThermalHeaterBlockEntity extends BlockEntity {

    private int burnTimeRemaining = 0;

    public ThermalHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THERMAL_HEATER.get(), pos, state);
    }

    public void addFuel(int ticks) {
        burnTimeRemaining += ticks;
        updateLitState();
        setChanged();
    }

    public void serverTick() {
        if (burnTimeRemaining > 0) {
            burnTimeRemaining--;
            if (burnTimeRemaining == 0) {
                updateLitState();
            }
            setChanged();
        }
    }

    public boolean isLit() {
        return burnTimeRemaining > 0;
    }

    private void updateLitState() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) return;

        BlockState current = getBlockState();
        boolean shouldBeLit = burnTimeRemaining > 0;
        if (current.getValue(ThermalHeaterBlock.LIT) != shouldBeLit) {
            level.setBlock(worldPosition, current.setValue(ThermalHeaterBlock.LIT, shouldBeLit), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BurnTime", burnTimeRemaining);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTimeRemaining = tag.getInt("BurnTime");
    }
}
