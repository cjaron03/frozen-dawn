package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.GeothermalCoreRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> rangeLevel;
                case 1 -> tempLevel;
                case 2 -> o2Level;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> rangeLevel = value;
                case 1 -> tempLevel = value;
                case 2 -> o2Level = value;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public GeothermalCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GEOTHERMAL_CORE.get(), pos, state);
    }

    public void serverTick() {
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

        if (changed) setChanged();
    }

    public int getEffectiveRange() {
        return BASE_RANGE + rangeLevel;
    }

    public float getEffectiveTemp() {
        return BASE_TEMP + tempLevel * 5.0f;
    }

    public int getEffectiveO2Range() {
        return switch (o2Level) {
            case 1 -> 20;
            case 2 -> 26;
            case 3 -> MAX_O2_RANGE;
            default -> BASE_O2_RANGE;
        };
    }

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
        if (level != null && !level.isClientSide()) {
            GeothermalCoreRegistry.register(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
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
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rangeLevel = tag.getInt("RangeLevel");
        tempLevel = tag.getInt("TempLevel");
        o2Level = tag.getInt("O2Level");
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
