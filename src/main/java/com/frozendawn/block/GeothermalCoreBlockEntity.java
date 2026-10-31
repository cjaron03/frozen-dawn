package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.world.GeothermalCoreRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Geothermal Core. Stores upgrade levels for range, temperature, and O2 production.
 * Upgrade materials placed in slots are consumed on the next server tick.
 */
public class GeothermalCoreBlockEntity extends BlockEntity implements MenuProvider {

    public static final float SURFACE_WARMTH_MULTIPLIER = 0.5f;
    public static final int BASE_RANGE = 12;
    public static final int MAX_RANGE = 32;
    public static final float BASE_TEMP = 50.0f;
    public static final float MAX_TEMP = 100.0f;
    public static final int BASE_O2_RANGE = 16;
    public static final int MAX_O2_RANGE = 32;

    public static final int MAX_RANGE_LEVEL = MAX_RANGE - BASE_RANGE; // 20
    public static final int MAX_TEMP_LEVEL = (int) ((MAX_TEMP - BASE_TEMP) / 5.0f); // 10
    public static final int MAX_O2_LEVEL = 3;

    private int rangeLevel = 0;
    private int tempLevel = 0;
    private int o2Level = 0;
    public static final int DEFAULT_HEAT_SHARE = 60;
    private int heatShare = DEFAULT_HEAT_SHARE;
    private int powerShare = 0;
    private double heatBalance = 0.6;


    private final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> rangeLevel;
                case 1 -> tempLevel;
                case 2 -> o2Level;
                case 3 -> heatShare;
                case 4 -> (int)Math.round(getHeatOutput());
                case 5 -> (int)Math.round(getOxygenSupply()*100);
                case 6 -> powerShare;
                case 7 -> (int)Math.round(getPowerOutputKw()*100);
                case 8 -> (int)Math.round(heatBalance*10000);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> rangeLevel = value;
                case 1 -> tempLevel = value;
                case 2 -> o2Level = value;
                case 3 -> heatShare = Math.clamp(value, 0, 100 - powerShare);
                case 6 -> powerShare = Math.clamp(value, 0, 100 - heatShare);
                case 8 -> heatBalance = Math.clamp(value, 0, 10000) / 10000.0;
            }
        }

        @Override
        public int getCount() {
            return 9;
        }
    };

    public GeothermalCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GEOTHERMAL_CORE.get(), pos, state);
    }

    public void serverTick() {
        if (level instanceof net.minecraft.server.level.ServerLevel server) com.frozendawn.event.BlindArchitectEvents.machine(server, worldPosition);
        if(level!=null&&level.getGameTime()%20==0) com.frozendawn.world.RoomAtmosphere.keepAlive(this);
        boolean changed = false;

        // Slot 0: Range upgrade — obsidian (+1), diamond block (+4)
        ItemStack rangeStack = items.get(0);
        if (!rangeStack.isEmpty() && rangeLevel < MAX_RANGE_LEVEL) {
            int gain = 0;
            if (rangeStack.is(Items.OBSIDIAN)) gain = 1;
            else if (rangeStack.is(Items.DIAMOND_BLOCK)) gain = 4;

            if (gain > 0) {
                rangeLevel = Math.min(rangeLevel + gain, MAX_RANGE_LEVEL);
                rangeStack.shrink(1);
                changed = true;
            }
        }

        // Slot 1: Temp upgrade — blaze powder (+1), thermal core (+2)
        ItemStack tempStack = items.get(1);
        if (!tempStack.isEmpty() && tempLevel < MAX_TEMP_LEVEL) {
            int gain = 0;
            if (tempStack.is(Items.BLAZE_POWDER)) gain = 1;
            else if (tempStack.is(ModItems.THERMAL_CORE.get())) gain = 2;

            if (gain > 0) {
                tempLevel = Math.min(tempLevel + gain, MAX_TEMP_LEVEL);
                tempStack.shrink(1);
                changed = true;
            }
        }

        // Slot 2: O2 upgrade — nether star (+1 level)
        ItemStack o2Stack = items.get(2);
        if (!o2Stack.isEmpty() && o2Level < MAX_O2_LEVEL) {
            if (o2Stack.is(Items.NETHER_STAR)) {
                o2Level++;
                o2Stack.shrink(1);
                changed = true;
            }
        }

        // Slot 3 is replenished by the shared RoomOxygenManager allocation.

        if (changed) {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public int getEffectiveRange() {
        return BASE_RANGE + rangeLevel;
    }

    public float getEffectiveTemp() {
        return BASE_TEMP + tempLevel * 5.0f;
    }

    public int getHeatShare() { return heatShare; }
    public int getOxygenShare() { return 100 - heatShare - powerShare; }
    public int getPowerShare() { return powerShare; }
    /** Fixed electrical rating; upgrades and the surface warmth penalty affect heat, not electricity. */
    public double getPowerOutputKw() { return powerShare * 0.2; }
    public double getHeatMultiplier() { return heatShare/(double)DEFAULT_HEAT_SHARE; }
    public double getOxygenSupply() { return 8.0*getOxygenShare()/40.0; }
    /** Calibrated game heat units/s, preserving every upgrade and surface penalty at 60/40. */
    public double getHeatOutput() {
        double temperature=applySurfaceWarmthPenalty(getEffectiveTemp(),worldPosition);
        return com.frozendawn.world.RoomThermalManager.BASE_HEATER_POWER*.25*temperature/BASE_TEMP
                *com.frozendawn.config.FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get()*getHeatMultiplier();
    }
    /** Adjusts heat within the remaining budget; oxygen receives the remainder. */
    public void setHeatShare(int value) {
        int remaining = 100 - powerShare; int next = Math.clamp(value, 0, remaining);
        setAllocation(next, powerShare, remaining > 0 ? next / (double)remaining : heatBalance);
    }

    /** Preserve the player's heat/O2 balance when reserving or releasing electrical output. */
    public void setPowerShare(int value) {
        int next = Math.clamp(value, 0, 100);
        // Retain the chosen balance independently of rounded integer shares. A dragged slider
        // sends intermediate values; repeatedly deriving a ratio from rounded shares would drift.
        setAllocation((int)Math.round((100 - next) * heatBalance), next, heatBalance);
    }

    public void resetAllocation() { setAllocation(DEFAULT_HEAT_SHARE, 0, 0.6); }

    private void setAllocation(int heat, int power, double balance) {
        if (heat == heatShare && power == powerShare && balance == heatBalance) return;
        heatShare = heat; powerShare = power; heatBalance = balance; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public int getEffectiveO2Range() {
        return switch (o2Level) {
            case 1 -> 20;
            case 2 -> 26;
            case 3 -> MAX_O2_RANGE;
            default -> BASE_O2_RANGE;
        };
    }

    public static boolean hasSurfaceWarmthPenalty(BlockPos pos) {
        return pos.getY() >= 0;
    }

    public static float getWarmthMultiplier(BlockPos pos) {
        return hasSurfaceWarmthPenalty(pos) ? SURFACE_WARMTH_MULTIPLIER : 1.0f;
    }

    public static float applySurfaceWarmthPenalty(float value, BlockPos pos) {
        return value * getWarmthMultiplier(pos);
    }

    public int oxygenChargeRequest(){var stack=items.get(3);return stack.getItem() instanceof O2TankItem t?Math.clamp(t.getMaxO2()-stack.getOrDefault(ModDataComponents.O2_LEVEL.get(),0),0,200):0;}
    public int chargeOxygen(int amount){int n=Math.min(Math.max(0,amount),oxygenChargeRequest());if(n>0){var stack=items.get(3);stack.set(ModDataComponents.O2_LEVEL.get(),stack.getOrDefault(ModDataComponents.O2_LEVEL.get(),0)+n);setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}return n;}
    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public ContainerData getData() {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.frozendawn.geothermal_core");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new GeothermalCoreMenu(containerId, playerInv, this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            GeothermalCoreRegistry.register(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            GeothermalCoreRegistry.unregister(level, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("RangeLevel", rangeLevel);
        tag.putInt("TempLevel", tempLevel);
        tag.putInt("O2Level", o2Level);
        tag.putInt("HeatShare", heatShare);
        tag.putInt("PowerShare", powerShare);
        tag.putDouble("HeatBalance", heatBalance);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rangeLevel = tag.getInt("RangeLevel");
        tempLevel = tag.getInt("TempLevel");
        o2Level = tag.getInt("O2Level");
        powerShare = tag.contains("PowerShare", net.minecraft.nbt.Tag.TAG_INT)
                ? Math.clamp(tag.getInt("PowerShare"), 0, 100) : 0;
        heatShare = tag.contains("HeatShare",net.minecraft.nbt.Tag.TAG_INT)
                ? Math.clamp(tag.getInt("HeatShare"), 0, 100 - powerShare)
                : Math.min(DEFAULT_HEAT_SHARE, 100 - powerShare);
        double storedBalance = tag.contains("HeatBalance", net.minecraft.nbt.Tag.TAG_DOUBLE)
                ? tag.getDouble("HeatBalance") : powerShare < 100 ? heatShare / (double)(100 - powerShare) : 0.6;
        heatBalance = Double.isFinite(storedBalance) ? Math.clamp(storedBalance, 0.0, 1.0) : 0.6;
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("RangeLevel", rangeLevel);
        tag.putInt("TempLevel", tempLevel);
        tag.putInt("O2Level", o2Level);
        tag.putInt("HeatShare", heatShare);
        tag.putInt("PowerShare", powerShare);
        tag.putDouble("HeatBalance", heatBalance);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
