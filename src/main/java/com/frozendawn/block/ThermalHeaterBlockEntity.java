package com.frozendawn.block;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
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
            burnTimeRemaining = Math.max(0, burnTimeRemaining - getPhaseConsumption());
            if (burnTimeRemaining == 0) {
                updateLitState();
                setChanged();
            } else if (level != null && level.getServer() != null
                    && level.getServer().getTickCount() % 200 == 0) {
                setChanged(); // periodic save, not every tick
            }
        }
    }

    /**
     * Phase-based fuel consumption multiplier.
     * Phases 1-3: 1x, Phase 4: 2x, Phase 5: 4x, Phase 6: 8x.
     * Disabled when FUEL_PHASE_SCALING config is false.
     */
    private int getPhaseConsumption() {
        if (!FrozenDawnConfig.ENABLE_FUEL_PHASE_SCALING.get()) return 1;
        if (level == null || level.isClientSide()) return 1;
        MinecraftServer server = level.getServer();
        if (server == null) return 1;
        int phase = ApocalypseState.get(server).getPhase();
        return switch (phase) {
            case 4 -> 2;
            case 5 -> 4;
            case 6 -> 8;
            default -> 1;
        };
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
