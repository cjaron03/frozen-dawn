package com.frozendawn.block;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.entity.FrostmiteEntity;
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

    private static final int MAX_GLOW_STAGE = 4;
    private static final int FROSTMITE_HEAT_DRAIN_STEP_INTERVAL = 10;
    private static final float MAX_FROSTMITE_HEAT_PENALTY = 50.0f;
    private static final float FROSTMITE_HEAT_PENALTY_PER_MITE_PER_STEP = 1.5f;
    private static final float FROSTMITE_HEAT_RECOVERY_PER_STEP = 2.0f;
    private int burnTimeRemaining = 0;
    private boolean cachedSheltered = false;
    private boolean shelterValid = false;
    private boolean hasCapacitor = false;
    private boolean clientRegistryLit = false;
    private float frostmiteHeatPenalty = 0.0f;

    public ThermalHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THERMAL_HEATER.get(), pos, state);
    }

    public void addFuel(int ticks) {
        burnTimeRemaining += ticks;
        updateLitState();
        setChanged();
    }

    /** Extinguish the heater by setting burn time to 0. Used by Returned AI. */
    public void extinguish() {
        burnTimeRemaining = 0;
        updateLitState();
        setChanged();
    }

    public void serverTick() {
        tickFrostmiteHeatPenalty();
        if (burnTimeRemaining > 0) {
            int totalDrain = getPhaseConsumption() + getFrostmiteFuelDrain();
            burnTimeRemaining = Math.max(0, burnTimeRemaining - totalDrain);
            if (burnTimeRemaining == 0) {
                setChanged();
            } else if (level != null && level.getServer() != null
                    && level.getServer().getTickCount() % 200 == 0) {
                setChanged(); // periodic save, not every tick
            }
        }
        updateLitState();
    }

    private void tickFrostmiteHeatPenalty() {
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return;
        }
        if (level.getServer().getTickCount() % FROSTMITE_HEAT_DRAIN_STEP_INTERVAL != 0) {
            return;
        }

        int attached = FrostmiteEntity.countLatchedToHeater(level, worldPosition);
        float previous = frostmiteHeatPenalty;
        if (isLit() && attached > 0) {
            frostmiteHeatPenalty = Math.min(MAX_FROSTMITE_HEAT_PENALTY,
                    frostmiteHeatPenalty + attached * FROSTMITE_HEAT_PENALTY_PER_MITE_PER_STEP);
        } else {
            frostmiteHeatPenalty = Math.max(0.0f, frostmiteHeatPenalty - FROSTMITE_HEAT_RECOVERY_PER_STEP);
        }

        if (Math.abs(frostmiteHeatPenalty - previous) > 0.001f) {
            setChanged();
        }
    }

    public void clientTick() {
        if (level == null || !level.isClientSide()) return;
        boolean shouldBeRegistered = getBlockState().getValue(ThermalHeaterBlock.LIT);
        if (shouldBeRegistered != clientRegistryLit) {
            if (shouldBeRegistered) {
                HeaterRegistry.register(level, worldPosition);
            } else {
                HeaterRegistry.unregister(level, worldPosition);
            }
            clientRegistryLit = shouldBeRegistered;
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
        int desiredGlowStage = getDesiredGlowStage(shouldBeLit);
        boolean litChanged = current.getValue(ThermalHeaterBlock.LIT) != shouldBeLit;
        boolean glowChanged = current.getValue(ThermalHeaterBlock.GLOW_STAGE) != desiredGlowStage;
        if (litChanged || glowChanged) {
            level.setBlock(worldPosition,
                    current.setValue(ThermalHeaterBlock.LIT, shouldBeLit)
                            .setValue(ThermalHeaterBlock.GLOW_STAGE, desiredGlowStage),
                    3);
            if (litChanged) {
                if (shouldBeLit) {
                    HeaterRegistry.register(level, worldPosition);
                } else {
                    HeaterRegistry.unregister(level, worldPosition);
                }
            }
        }
    }

    private int getDesiredGlowStage(boolean shouldBeLit) {
        if (!shouldBeLit || level == null) return 0;

        int attached = FrostmiteEntity.countLatchedToHeater(level, worldPosition);
        if (attached <= 0) return MAX_GLOW_STAGE;
        if (attached <= 2) return 3;
        if (attached <= 4) return 2;
        if (attached <= 7) return 1;
        return 0;
    }

    private int getFrostmiteFuelDrain() {
        if (level == null || !isLit()) {
            return 0;
        }
        return FrostmiteEntity.getHeaterFuelDrain(level, worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            clientRegistryLit = getBlockState().getValue(ThermalHeaterBlock.LIT);
            if (clientRegistryLit) {
                HeaterRegistry.register(level, worldPosition);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            HeaterRegistry.unregister(level, worldPosition);
        }
        super.setRemoved();
    }

    public boolean hasCapacitor() {
        return hasCapacitor;
    }

    public void installCapacitor() {
        this.hasCapacitor = true;
        setChanged();
    }

    private int getHeatOutput() {
        Block block = getBlockState().getBlock();
        int base;
        if (block == ModBlocks.DIAMOND_THERMAL_HEATER.get()) base = 80;
        else if (block == ModBlocks.GOLD_THERMAL_HEATER.get()) base = 65;
        else if (block == ModBlocks.IRON_THERMAL_HEATER.get()) base = 50;
        else base = 35;
        return hasCapacitor ? (int) (base * 1.5f) : base;
    }

    private int getBaseRadius() {
        Block block = getBlockState().getBlock();
        int base;
        if (block == ModBlocks.DIAMOND_THERMAL_HEATER.get()) base = 14;
        else if (block == ModBlocks.GOLD_THERMAL_HEATER.get()) base = 11;
        else if (block == ModBlocks.IRON_THERMAL_HEATER.get()) base = 9;
        else base = 7;
        return hasCapacitor ? base * 2 : base;
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
    public float getFrostmiteHeatPenalty() { return frostmiteHeatPenalty; }

    public int getEffectiveRadius() {
        int base = getBaseRadius();
        boolean exposed = !getCachedSheltered() && getPhase() >= 5;
        int effective = exposed ? (int) (base * 0.6f) : base;
        if (level != null) {
            effective -= FrostmiteEntity.getHeaterRadiusPenalty(level, worldPosition);
        }
        return Math.max(2, effective);
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
        tag.putBoolean("HasCapacitor", hasCapacitor);
        tag.putFloat("FrostmiteHeatPenalty", frostmiteHeatPenalty);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTimeRemaining = tag.getInt("BurnTime");
        hasCapacitor = tag.getBoolean("HasCapacitor");
        frostmiteHeatPenalty = tag.getFloat("FrostmiteHeatPenalty");
    }
}
