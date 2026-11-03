package com.frozendawn.block;

import com.frozendawn.event.ResonanceEventHooks;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.power.PoweredMachine;
import com.frozendawn.world.AcheronForgeRegistry;
import com.frozendawn.world.GeothermalCoreRegistry;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.RoomThermalManager;
import com.frozendawn.world.RoomAtmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
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

/** Existing forges accept grid power; nearby heat still works below Y=0. */
public class AcheronForgeBlockEntity extends BlockEntity implements MenuProvider, PoweredMachine {
    public static final int DEMAND_WATTS = 25_000;
    public static final double WASTE_HEAT_WATTS = 6_250;
    // Half-tick work units preserve the same completion fraction when modes change.
    public static final int TOTAL_WORK = 400, DATA_COUNT = 6;
    private static final int HEAT_CHECK_RADIUS_SQ = 64;

    public enum Mode {
        AUTO, ELECTRIC, HEAT;
        public static Mode fromId(int id) { return id >= 0 && id < values().length ? values()[id] : AUTO; }
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int processingProgress;
    private String activeRecipe = "";
    private Mode mode = Mode.AUTO;
    private boolean hasHeat;
    private boolean isDeepEnough;
    private int workingSource;
    private long heatCheckedAt = Long.MIN_VALUE, workedAt = Long.MIN_VALUE;

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> processingProgress;
                case 1 -> hasHeat ? 1 : 0;
                case 2 -> isDeepEnough ? 1 : 0;
                case 3 -> mode.ordinal();
                case 4 -> workingSource;
                case 5 -> status();
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };

    public AcheronForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACHERON_FORGE.get(), pos, state);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null) AcheronForgeRegistry.register(level, worldPosition);
        if (level instanceof ServerLevel server) PowerNetworks.machineLoaded(server, worldPosition);
    }

    @Override public void setRemoved() {
        if (level != null) AcheronForgeRegistry.unregister(level, worldPosition);
        if (level instanceof ServerLevel server) PowerNetworks.machineRemoved(server, worldPosition);
        super.setRemoved();
    }

    public Mode mode() { return mode; }
    public void setMode(Mode value) {
        if (mode != value) { mode = value; workingSource = 0; setChanged(); }
    }
    public int progress() { return processingProgress; }
    @Override public int demandWatts() { return DEMAND_WATTS; }
    @Override public boolean wantsPower() { return mode != Mode.HEAT && canProcess(); }

    private boolean canProcess() {
        ForgeRecipe recipe = recipeFor(items.get(0));
        ItemStack output = items.get(1);
        return recipe != null && items.get(0).getCount() >= recipe.inputCount
                && (output.isEmpty() || output.is(recipe.output) && output.getCount() < output.getMaxStackSize());
    }

    /** 0 ready, 1 electric, 2 heat, 3 waiting for power, 4 depth, 5 heat, 6 blocked, 7 input. */
    public int status() {
        if (items.get(0).isEmpty()) return 0;
        ForgeRecipe recipe = recipeFor(items.get(0));
        if (recipe == null || items.get(0).getCount() < recipe.inputCount) return 7;
        if (!canProcess()) return 6;
        if (workingSource != 0) return workingSource;
        if (mode == Mode.ELECTRIC) return 3;
        if (!isDeepEnough) return mode == Mode.AUTO ? 3 : 4;
        if (!hasHeat) return mode == Mode.AUTO ? 3 : 5;
        return 0;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || workedAt == server.getGameTime()) return;
        workingSource = 0;
        isDeepEnough = worldPosition.getY() < 0;
        if (heatCheckedAt == Long.MIN_VALUE || server.getGameTime() - heatCheckedAt >= 20) {
            hasHeat = checkNearbyHeat();
            heatCheckedAt = server.getGameTime();
        }

        ForgeRecipe recipe = recipeFor(items.get(0));
        String recipeId = recipe == null ? "" : BuiltInRegistries.ITEM.getKey(recipe.output).toString();
        if (!recipeId.equals(activeRecipe)) {
            processingProgress = 0;
            activeRecipe = recipeId;
            setChanged();
        }
        if (!canProcess()) return;

        boolean electric = wantsPower() && PowerNetworks.consumeMachineTick(server, worldPosition);
        if (!electric && (mode == Mode.ELECTRIC || !isDeepEnough || !hasHeat)) return;
        workedAt = server.getGameTime();
        workingSource = electric ? 1 : 2;
        processingProgress += electric ? 3 : 2;
        if (electric) {
            if (server.getGameTime() % 20L == 0L) RoomAtmosphere.keepAlive(this);
            RoomThermalManager.addMachineHeat(server, worldPosition, WASTE_HEAT_WATTS / 20.0);
        }
        com.frozendawn.event.BlindArchitectEvents.machine(server, worldPosition);
        if (server.getGameTime() % 20L == 0L) ResonanceEventHooks.emitMachinery(server, worldPosition);

        if (processingProgress >= TOTAL_WORK) {
            items.get(0).shrink(recipe.inputCount);
            if (items.get(1).isEmpty()) items.set(1, new ItemStack(recipe.output));
            else items.get(1).grow(1);
            processingProgress = 0;
        }
        setChanged();
    }

    private static ForgeRecipe recipeFor(ItemStack input) {
        if (input.is(ModItems.ACHERONITE_SHARD.get())) return new ForgeRecipe(2, ModItems.REFINED_ACHERONITE.get());
        if (input.is(ModItems.SPENT_LATTICE.get())) return new ForgeRecipe(4, ModItems.SEALED_LATTICE.get());
        return null;
    }
    private record ForgeRecipe(int inputCount, net.minecraft.world.item.Item output) {}

    private boolean checkNearbyHeat() {
        if (level == null) return false;
        for (BlockPos pos : HeaterRegistry.getHeaters(level)) {
            if (level.isLoaded(pos) && worldPosition.distSqr(pos) <= HEAT_CHECK_RADIUS_SQ) return true;
        }
        for (BlockPos pos : GeothermalCoreRegistry.getCores(level)) {
            if (level.isLoaded(pos) && worldPosition.distSqr(pos) <= HEAT_CHECK_RADIUS_SQ) return true;
        }
        return false;
    }

    public NonNullList<ItemStack> getItems() { return items; }
    public ContainerData getData() { return data; }
    public boolean stillValid(Player player) {
        return !player.isSpectator() && level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }
    @Override public Component getDisplayName() { return Component.translatable("container.frozendawn.acheron_forge"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AcheronForgeMenu(id, inv, this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ProgressUnits", processingProgress);
        tag.putString("ActiveRecipe", activeRecipe);
        tag.putInt("Mode", mode.ordinal());
        ContainerHelper.saveAllItems(tag, items, registries);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        processingProgress = Math.clamp(tag.contains("ProgressUnits") ? tag.getInt("ProgressUnits")
                : tag.getInt("Progress") * 2, 0, TOTAL_WORK - 1);
        ForgeRecipe recipe = recipeFor(items.get(0));
        activeRecipe = tag.contains("ActiveRecipe") ? tag.getString("ActiveRecipe")
                : recipe == null ? "" : BuiltInRegistries.ITEM.getKey(recipe.output).toString();
        mode = Mode.fromId(tag.getInt("Mode"));
        workingSource = 0;
        hasHeat = false;
        isDeepEnough = false;
        heatCheckedAt = workedAt = Long.MIN_VALUE;
    }
}
