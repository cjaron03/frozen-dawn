package com.frozendawn.block;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.PlayerEndStats;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.event.ResonanceEventHooks;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
    private double fuelFraction = 0;
    private double burnFraction = 1;
    private double controlledTemperature = Double.NaN;
    private int controlMode = 0; // 0 open camp, 1 room air, 2 depleted room structure

    private boolean cachedSheltered = false;
    private boolean shelterValid = false;
    private boolean hasCapacitor = false;
    private boolean clientRegistryLit = false;
    private float frostmiteHeatPenalty = 0.0f;
    private int industrialDrainWarningTicks = 0;

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
        fuelFraction = 0;
        burnFraction = 0;
        updateLitState();
        setChanged();
    }

    public void serverTick() {
        tickFrostmiteHeatPenalty();
        if (industrialDrainWarningTicks > 0) {
            industrialDrainWarningTicks--;
        }
        boolean managed = level instanceof ServerLevel serverLevel
                && com.frozendawn.world.RoomThermalManager.managesHeater(serverLevel, worldPosition);
        if (hasFuel() && !isRedstoneDisabled()) {
            if (level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 20L == 0L && burnFraction > 0)
                ResonanceEventHooks.emitMachinery(serverLevel, worldPosition);
            // Modeled rooms debit their actual shared heat grant once per second in the thermal solver.
            // Unknown/unloaded room geometry suspends that debit together with heat integration.
            if (!managed) {
                controlMode = 0;
                var phase = ApocalypseState.get(level.getServer());
                double background = com.frozendawn.world.TemperatureManager.getBackgroundTemperature(
                        worldPosition.getY(), phase.getCurrentDay(), phase.getTotalDays())
                        + (getCachedSheltered() ? 5 : 0);
                controlledTemperature = Double.NaN;
                burnFraction = com.frozendawn.thermal.HeaterControl.openCampFraction(background,
                        Math.max(0, getPublicHeatOutput() - frostmiteHeatPenalty)
                                * FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get());
                debitFuel(getPhaseConsumption() * burnFraction);
            }
            // Parasites retain their separate fuel cost even when ordinary room demand is low.
            debitFuel(getFrostmiteFuelDrain());
        } else burnFraction = 0;

        updateLitState();
        if (hasFuel() && level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 20L == 0L)
            com.frozendawn.world.RoomAtmosphere.keepAlive(this);
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

    public boolean hasFuel() { return burnTimeRemaining > 0; }
    public boolean isRedstoneDisabled() { return level != null && level.hasNeighborSignal(worldPosition); }
    public boolean isLit() { return hasFuel() && !isRedstoneDisabled(); }
    public double getBurnFraction() { return isLit() ? burnFraction : 0; }
    public double availableRoomFraction() {
        return isLit() ? Math.clamp((burnTimeRemaining - fuelFraction) / (20.0 * getPhaseConsumption()), 0, 1) : 0;
    }
    public void consumeRoomHeating(double fraction, double temperature, boolean airPresent) {
        burnFraction = isLit() ? Math.clamp(fraction, 0, 1) : 0;
        controlledTemperature = temperature;
        controlMode = airPresent ? 1 : 2;
        debitFuel(20.0 * getPhaseConsumption() * burnFraction);
        updateLitState();
    }
    private void debitFuel(double units) {
        if (units <= 0 || burnTimeRemaining <= 0) return;
        double accumulated = fuelFraction + units;
        int drain = Math.min(burnTimeRemaining, (int)Math.floor(accumulated + 1e-9));
        fuelFraction = accumulated - drain;
        burnTimeRemaining -= drain;
        if (burnTimeRemaining == 0) fuelFraction = 0;
        if (level instanceof ServerLevel serverLevel)
            PlayerEndStats.addFuelBurnedNearby(serverLevel, worldPosition, drain);
        setChanged(); // Fractional consumption is part of persistent fuel, including save/reload.
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
        boolean shouldBeLit = isLit();
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

    /**
     * Lets adjacent infrastructure drain heater burn time directly.
     * Returns true when the requested drain was fully covered by available fuel.
     * A drain that takes the heater exactly to zero still powers the current work tick.
     */
    public boolean consumeIndustrialFuel(int ticks) {
        if (ticks <= 0) {
            return isLit();
        }
        if (!isLit()) {
            updateLitState();
            return false;
        }
        if (burnTimeRemaining < ticks) {
            return false;
        }
        burnTimeRemaining -= ticks;
        if (level instanceof ServerLevel serverLevel) {
            PlayerEndStats.addFuelBurnedNearby(serverLevel, worldPosition, ticks);
        }
        industrialDrainWarningTicks = 40;
        if (burnTimeRemaining == 0
                || (level != null && !level.isClientSide() && level.getServer() != null
                && level.getServer().getTickCount() % 200 == 0)) {
            setChanged();
        }
        updateLitState();
        return true;
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
            private final com.frozendawn.thermal.HeaterDemandDisplay display = new com.frozendawn.thermal.HeaterDemandDisplay();

            private double displayedFraction() {
                return display.sample(level == null ? 0 : level.getGameTime(), getBurnFraction(), isLit(), controlMode);
            }
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> estimateMinutes(displayedFraction());
                    case 1 -> isLit() ? 1 : 0;
                    case 2 -> getCachedSheltered() ? 1 : 0;
                    case 3 -> industrialDrainWarningTicks > 0 ? 1 : 0;
                    case 4 -> displayedFraction() > 1e-6 ? Math.max(1, (int)Math.round(displayedFraction() * 100)) : 0;
                    case 5 -> isRedstoneDisabled() ? 1 : 0;
                    case 6 -> controlMode;
                    case 7 -> Double.isFinite(controlledTemperature) ? (int)Math.clamp(Math.round(controlledTemperature * 10), -32767, 32767) : -32768;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() { return 8; }
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
        return estimateMinutes(getBurnFraction());
    }

    private int estimateMinutes(double fraction) {
        double consumption = getPhaseConsumption() * fraction;
        return consumption > 1e-6 ? (int)Math.min(9999, Math.max(0, burnTimeRemaining - fuelFraction) / (consumption * 1200)) : 9999;
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
        tag.putDouble("FuelFraction", fuelFraction);
        tag.putBoolean("HasCapacitor", hasCapacitor);
        tag.putFloat("FrostmiteHeatPenalty", frostmiteHeatPenalty);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTimeRemaining = Math.max(0, tag.getInt("BurnTime"));
        double fraction = tag.getDouble("FuelFraction");
        fuelFraction = Double.isFinite(fraction) ? Math.clamp(fraction, 0, Math.nextDown(1.0)) : 0;
        burnFraction = 1;
        hasCapacitor = tag.getBoolean("HasCapacitor");
        frostmiteHeatPenalty = tag.getFloat("FrostmiteHeatPenalty");
    }
}
