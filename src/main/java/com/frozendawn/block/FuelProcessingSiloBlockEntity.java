package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.recipe.FuelProcessingSiloRecipes;
import com.frozendawn.recipe.FuelProcessingSiloRecipes.FuelProcessingSiloRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FuelProcessingSiloBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOTS = 4;
    public static final int OUTPUT_SLOT = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    private int progressUnits = 0;
    private int progressTargetUnits = 0;
    private int processingFuelDebtUnits = 0;
    private boolean structureValid = false;
    private boolean heaterPresent = false;
    private boolean heaterLit = false;
    private int heaterSpeedUnits = 0;
    private int heaterEtaMinutes = 0;
    private int heaterTierCode = 0;
    private boolean heaterHasCapacitor = false;
    private boolean processing = false;
    private String activeRecipeId = "";
    private boolean initializedStructureState = false;
    private boolean lastStructureValid = false;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getProgressPercent();
                case 1 -> processing ? 1 : 0;
                case 2 -> structureValid ? 1 : 0;
                case 3 -> heaterPresent ? 1 : 0;
                case 4 -> heaterLit ? 1 : 0;
                case 5 -> heaterSpeedUnits;
                case 6 -> heaterEtaMinutes;
                case 7 -> heaterTierCode;
                case 8 -> heaterHasCapacitor ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progressUnits = value;
                case 1 -> processing = value != 0;
                case 2 -> structureValid = value != 0;
                case 3 -> heaterPresent = value != 0;
                case 4 -> heaterLit = value != 0;
                case 5 -> heaterSpeedUnits = value;
                case 6 -> heaterEtaMinutes = value;
                case 7 -> heaterTierCode = value;
                case 8 -> heaterHasCapacitor = value != 0;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 9;
        }
    };

    public FuelProcessingSiloBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_PROCESSING_SILO.get(), pos, state);
    }

    public void serverTick() {
        if (level == null) {
            return;
        }

        refreshMultiblockState();

        if (!initializedStructureState) {
            initializedStructureState = true;
            lastStructureValid = structureValid;
        } else if (structureValid && !lastStructureValid) {
            spawnStructureFormedParticles();
        }
        lastStructureValid = structureValid;

        FuelProcessingSiloRecipe recipe = FuelProcessingSiloRecipes.findMatch(items.subList(0, INPUT_SLOTS), items.get(OUTPUT_SLOT));
        boolean recipeChanged = recipe == null ? !activeRecipeId.isEmpty() : !recipe.id().equals(activeRecipeId);
        if (recipeChanged) {
            progressUnits = 0;
            progressTargetUnits = 0;
            processingFuelDebtUnits = 0;
            activeRecipeId = recipe == null ? "" : recipe.id();
        }

        if (!structureValid || !heaterLit || recipe == null) {
            processing = false;
            updateControllerLit(false);
            return;
        }

        if (progressTargetUnits <= 0) {
            progressTargetUnits = recipe.baseTicks() * FuelProcessingSiloRecipes.PROGRESS_UNITS_PER_TICK;
        }

        FuelProcessingSiloMultiblock.AttachedHeater attachedHeater = FuelProcessingSiloMultiblock.findAttachedHeater(
                level,
                worldPosition,
                getBlockState().getValue(FuelProcessingSiloControllerBlock.FACING)
        );
        ThermalHeaterBlockEntity heater = attachedHeater.heater();
        if (heater == null || !drainHeaterForProcessing(heater, heaterSpeedUnits)) {
            heaterLit = false;
            processing = false;
            updateControllerLit(false);
            return;
        }

        progressUnits += heaterSpeedUnits;
        processing = true;
        updateControllerLit(true);

        if (progressUnits >= progressTargetUnits) {
            recipe.consumeInputs(items, INPUT_SLOTS, level, worldPosition);
            ItemStack produced = recipe.createOutput();
            ItemStack outputSlot = items.get(OUTPUT_SLOT);
            if (outputSlot.isEmpty()) {
                items.set(OUTPUT_SLOT, produced);
            } else {
                outputSlot.grow(produced.getCount());
            }
            progressUnits = 0;
            progressTargetUnits = 0;
            processingFuelDebtUnits = 0;
            activeRecipeId = "";
            setChanged();
        } else if (level.getServer() != null && level.getServer().getTickCount() % 40 == 0) {
            setChanged();
        }
    }

    private void refreshMultiblockState() {
        if (level == null) {
            return;
        }
        Direction facing = getBlockState().getValue(FuelProcessingSiloControllerBlock.FACING);
        structureValid = FuelProcessingSiloMultiblock.isValid(level, worldPosition, facing);

        FuelProcessingSiloMultiblock.AttachedHeater attached = structureValid
                ? FuelProcessingSiloMultiblock.findAttachedHeater(level, worldPosition, facing)
                : FuelProcessingSiloMultiblock.AttachedHeater.none();

        heaterPresent = attached.pos() != null;
        heaterLit = attached.lit();
        heaterSpeedUnits = attached.speedUnits();
        heaterEtaMinutes = attached.heater() != null ? attached.heater().getBurnEtaMinutes() : 0;
        heaterTierCode = attached.tierCode();
        heaterHasCapacitor = attached.hasCapacitor();
    }

    private void spawnStructureFormedParticles() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 0.5D;
        double z = worldPosition.getZ() + 0.5D;
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 10, 0.22D, 0.22D, 0.22D, 0.01D);
        serverLevel.sendParticles(ParticleTypes.END_ROD, x, y + 0.1D, z, 6, 0.16D, 0.18D, 0.16D, 0.0D);
    }

    private boolean drainHeaterForProcessing(ThermalHeaterBlockEntity heater, int speedUnits) {
        processingFuelDebtUnits += speedUnits;
        int extraFuelTicks = processingFuelDebtUnits / FuelProcessingSiloRecipes.PROGRESS_UNITS_PER_TICK;
        processingFuelDebtUnits %= FuelProcessingSiloRecipes.PROGRESS_UNITS_PER_TICK;
        return extraFuelTicks <= 0 || heater.consumeIndustrialFuel(extraFuelTicks);
    }

    private void updateControllerLit(boolean active) {
        if (level == null) {
            return;
        }
        BlockState current = getBlockState();
        if (current.getValue(FuelProcessingSiloControllerBlock.LIT) != active) {
            level.setBlock(worldPosition, current.setValue(FuelProcessingSiloControllerBlock.LIT, active), 3);
        }
    }

    private int getProgressPercent() {
        if (progressTargetUnits <= 0) {
            return 0;
        }
        return Math.min(100, progressUnits * 100 / progressTargetUnits);
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public ContainerData getData() {
        return data;
    }

    public boolean isStructureValid() {
        return structureValid;
    }

    public boolean hasAttachedHeater() {
        return heaterPresent;
    }

    public boolean isHeaterLit() {
        return heaterLit;
    }

    public int getHeaterSpeedUnits() {
        return heaterSpeedUnits;
    }

    public int getHeaterEtaMinutes() {
        return heaterEtaMinutes;
    }

    public int getHeaterTierCode() {
        return heaterTierCode;
    }

    public boolean heaterHasCapacitor() {
        return heaterHasCapacitor;
    }

    public int getProgressPercentPublic() {
        return getProgressPercent();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.frozendawn.fuel_processing_silo");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new FuelProcessingSiloMenu(containerId, playerInv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("ProgressUnits", progressUnits);
        tag.putInt("ProgressTargetUnits", progressTargetUnits);
        tag.putInt("ProcessingFuelDebtUnits", processingFuelDebtUnits);
        tag.putString("ActiveRecipeId", activeRecipeId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        progressUnits = tag.getInt("ProgressUnits");
        progressTargetUnits = tag.getInt("ProgressTargetUnits");
        processingFuelDebtUnits = tag.getInt("ProcessingFuelDebtUnits");
        activeRecipeId = tag.getString("ActiveRecipeId");
    }
}
