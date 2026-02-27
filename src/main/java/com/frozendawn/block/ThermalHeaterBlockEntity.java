package com.frozendawn.block;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Thermal Heater. Tracks remaining fuel burn ticks.
 * Stops ticking when chunk unloads (vanilla default) — fuel does NOT burn while unloaded.
 */
public class ThermalHeaterBlockEntity extends BlockEntity implements MenuProvider {

    private int burnTimeRemaining = 0;
    private boolean cachedSheltered = false;
    private boolean shelterValid = false;

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

    /** Returns cached shelter status, computing lazily on first access. */
    public boolean getCachedSheltered() {
        if (!shelterValid && level != null) {
            cachedSheltered = com.frozendawn.world.TemperatureManager.isSheltered(level, worldPosition);
            shelterValid = true;
        }
        return cachedSheltered;
    }

    /** Invalidate shelter cache when blocks above change. */
    public void invalidateShelterCache() {
        shelterValid = false;
    }

    private void updateLitState() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) return;

        BlockState current = getBlockState();
        boolean shouldBeLit = burnTimeRemaining > 0;
        if (current.getValue(ThermalHeaterBlock.LIT) != shouldBeLit) {
            level.setBlock(worldPosition, current.setValue(ThermalHeaterBlock.LIT, shouldBeLit), 3);
            if (shouldBeLit) {
                HeaterRegistry.register(level, worldPosition);
            } else {
                HeaterRegistry.unregister(level, worldPosition);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && burnTimeRemaining > 0) {
            HeaterRegistry.register(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            HeaterRegistry.unregister(level, worldPosition);
        }
        super.setRemoved();
    }

    private int getHeatOutput() {
        Block block = getBlockState().getBlock();
        if (block == ModBlocks.DIAMOND_THERMAL_HEATER.get()) return 80;
        if (block == ModBlocks.GOLD_THERMAL_HEATER.get()) return 65;
        if (block == ModBlocks.IRON_THERMAL_HEATER.get()) return 50;
        return 35;
    }

    private int getBaseRadius() {
        Block block = getBlockState().getBlock();
        if (block == ModBlocks.DIAMOND_THERMAL_HEATER.get()) return 14;
        if (block == ModBlocks.GOLD_THERMAL_HEATER.get()) return 11;
        if (block == ModBlocks.IRON_THERMAL_HEATER.get()) return 9;
        return 7;
    }

    /** ContainerData for syncing heater status to the client UI (simplified). */
    public ContainerData getMenuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> Math.min(9999, burnTimeRemaining / (getPhaseConsumption() * 1200));
                    case 1 -> isLit() ? 1 : 0;
                    case 2 -> getCachedSheltered() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return 3; }
        };
    }

    // --- Public getters for ORSA MultiTool diagnostics ---

    public int getPhase() {
        if (level == null || level.isClientSide() || level.getServer() == null) return 0;
        return ApocalypseState.get(level.getServer()).getPhase();
    }

    public int getPublicHeatOutput() { return getHeatOutput(); }
    public int getPublicBaseRadius() { return getBaseRadius(); }
    public int getPublicPhaseConsumption() { return getPhaseConsumption(); }

    public int getEffectiveRadius() {
        int base = getBaseRadius();
        boolean exposed = !getCachedSheltered() && getPhase() >= 5;
        return exposed ? (int) (base * 0.6f) : base;
    }

    public boolean isWindExposed() {
        return !getCachedSheltered() && getPhase() >= 5;
    }

    public int getBurnEtaMinutes() {
        int consumption = getPhaseConsumption();
        return burnTimeRemaining / (consumption * 1200);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ThermalHeaterMenu(containerId, this);
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
