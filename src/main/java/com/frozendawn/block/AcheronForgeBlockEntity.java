package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.GeothermalCoreRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Acheron Forge block entity. Processes 2 Acheronite Shards into 1 Refined Acheronite.
 * Requires: below Y=0 AND nearby heat source (heater or geothermal core).
 */
public class AcheronForgeBlockEntity extends BlockEntity implements MenuProvider {

    private static final int PROCESS_TIME = 200; // 10 seconds
    private static final int HEAT_CHECK_RADIUS_SQ = 64; // 8 blocks

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int processingProgress = 0;
    private boolean hasHeat = false;
    private boolean isDeepEnough = false;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> processingProgress;
                case 1 -> hasHeat ? 1 : 0;
                case 2 -> isDeepEnough ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> processingProgress = value;
                case 1 -> hasHeat = value != 0;
                case 2 -> isDeepEnough = value != 0;
            }
        }

        @Override
        public int getCount() { return 3; }
    };

    public AcheronForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACHERON_FORGE.get(), pos, state);
    }

    public void serverTick() {
        if (level == null) return;

        isDeepEnough = worldPosition.getY() < 0;

        // Check heat source every 20 ticks
        if (level.getServer() != null && level.getServer().getTickCount() % 20 == 0) {
            hasHeat = checkNearbyHeat();
        }

        if (!isDeepEnough || !hasHeat) {
            if (processingProgress > 0) processingProgress = Math.max(0, processingProgress - 2);
            return;
        }

        ItemStack input = items.get(0);
        ItemStack output = items.get(1);

        boolean canProcess = input.is(ModItems.ACHERONITE_SHARD.get()) && input.getCount() >= 2
                && (output.isEmpty() || (output.is(ModItems.REFINED_ACHERONITE.get()) && output.getCount() < output.getMaxStackSize()));

        if (canProcess) {
            processingProgress++;
            if (processingProgress >= PROCESS_TIME) {
                input.shrink(2);
                if (output.isEmpty()) {
                    items.set(1, new ItemStack(ModItems.REFINED_ACHERONITE.get()));
                } else {
                    output.grow(1);
                }
                processingProgress = 0;
                setChanged();
            }
        } else {
            processingProgress = 0;
        }
    }

    private boolean checkNearbyHeat() {
        if (level == null) return false;
        for (BlockPos heaterPos : HeaterRegistry.getHeaters(level)) {
            if (worldPosition.distSqr(heaterPos) <= HEAT_CHECK_RADIUS_SQ) return true;
        }
        for (BlockPos corePos : GeothermalCoreRegistry.getCores(level)) {
            if (worldPosition.distSqr(corePos) <= HEAT_CHECK_RADIUS_SQ) return true;
        }
        return false;
    }

    public NonNullList<ItemStack> getItems() { return items; }
    public ContainerData getData() { return data; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.frozendawn.acheron_forge");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new AcheronForgeMenu(containerId, playerInv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", processingProgress);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        processingProgress = tag.getInt("Progress");
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
