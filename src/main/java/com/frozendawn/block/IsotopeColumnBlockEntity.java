package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.power.IsotopeColumnLogic;
import com.frozendawn.power.IsotopeColumnLogic.Job;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.power.PoweredMachine;
import com.frozendawn.world.RoomAtmosphere;
import com.frozendawn.world.RoomThermalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Slot 0 takes ice, snow or Lithium Carbonate; slot 1 holds the product. Ice is taken into a unit buffer, and 8 units
 * make one Deuterium Canister in 120 s. Lithium Carbonate refines one to one in 60 s. A paid tick advances the job by
 * one tick and puts 25% of the 10 kW draw into the room as waste heat; an unpaid tick pauses it. */
public final class IsotopeColumnBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, PoweredMachine {
    public static final int INPUT = 0, OUTPUT = 1;
    public static final int IDLE = 0, NO_POWER = 1, WORKING = 2, OUTPUT_FULL = 3;
    public static final int DATA = 5;
    public static final double WASTE_HEAT_WATTS = IsotopeColumnLogic.DRAW_WATTS * 0.25;
    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int buffer, progress;
    private Job job = Job.NONE;

    public IsotopeColumnBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.ISOTOPE_COLUMN.get(), p, s); }

    public static boolean accepts(ItemStack s) { return IsotopeColumnLogic.iceUnits(s.getItem()) > 0 || s.is(ModItems.LITHIUM_CARBONATE.get()); }
    public int buffer() { return buffer; }
    public int progress() { return progress; }
    public Job job() { return job; }
    public int total() { return total(job); }
    private static int total(Job job) { return job == Job.LITHIUM ? IsotopeColumnLogic.LITHIUM_TICKS : IsotopeColumnLogic.DEUTERIUM_TICKS; }

    /** The job the contents call for, counting ice still waiting in the input slot. */
    private Job wanted() {
        var in = items.get(INPUT); int units = IsotopeColumnLogic.iceUnits(in.getItem());
        if (buffer >= IsotopeColumnLogic.ICE_PER_CANISTER || !in.isEmpty() && IsotopeColumnLogic.pullIce(buffer, units) && buffer + units * in.getCount() >= IsotopeColumnLogic.ICE_PER_CANISTER) return Job.DEUTERIUM;
        return in.is(ModItems.LITHIUM_CARBONATE.get()) ? Job.LITHIUM : Job.NONE;
    }
    private static ItemStack product(Job job) {
        return switch (job) { case DEUTERIUM -> new ItemStack(ModItems.DEUTERIUM_CANISTER.get()); case LITHIUM -> new ItemStack(ModItems.LITHIUM.get()); default -> ItemStack.EMPTY; };
    }
    private boolean fits(ItemStack product) {
        var out = items.get(OUTPUT);
        return !product.isEmpty() && (out.isEmpty() || ItemStack.isSameItemSameComponents(out, product) && out.getCount() < out.getMaxStackSize());
    }
    @Override public boolean wantsPower() { return fits(product(wanted())); }
    @Override public int demandWatts() { return IsotopeColumnLogic.DRAW_WATTS; }
    public boolean working() { return getBlockState().getValue(IsotopeColumnBlock.LIT); }
    public int status() {
        var job = wanted();
        if (job == Job.NONE) return IDLE;
        if (!fits(product(job))) return OUTPUT_FULL;
        return working() ? WORKING : NO_POWER;
    }
    public ContainerData data() {
        return new ContainerData() {
            @Override public int get(int i) { return switch (i) { case 0 -> progress; case 1 -> total(); case 2 -> status(); case 3 -> buffer; case 4 -> job.ordinal(); default -> 0; }; }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA; }
        };
    }
    @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel l) PowerNetworks.machineLoaded(l, worldPosition); }
    @Override public void setRemoved() { if (level instanceof ServerLevel l) PowerNetworks.machineRemoved(l, worldPosition); super.setRemoved(); }
    private void working(boolean value) { if (working() != value && level != null) level.setBlock(worldPosition, getBlockState().setValue(IsotopeColumnBlock.LIT, value), 3); }

    public void serverTick() {
        if (!(level instanceof ServerLevel l)) return;
        var in = items.get(INPUT); boolean changed = false;
        while (!in.isEmpty() && IsotopeColumnLogic.pullIce(buffer, IsotopeColumnLogic.iceUnits(in.getItem()))) {
            buffer += IsotopeColumnLogic.iceUnits(in.getItem()); in.shrink(1); changed = true;
        }
        var next = wanted();
        if (next != job) { job = next; progress = 0; changed = true; }
        var out = product(job);
        if (job == Job.NONE || !fits(out) || !PowerNetworks.consumeMachineTick(l, worldPosition)) { working(false); if (changed) setChanged(); return; }
        working(true); progress++;
        RoomThermalManager.addMachineHeat(l, worldPosition, WASTE_HEAT_WATTS / 20.0);
        if (l.getGameTime() % 20 == 0) RoomAtmosphere.keepAlive(this);
        if (progress >= total()) {
            if (items.get(OUTPUT).isEmpty()) items.set(OUTPUT, out); else items.get(OUTPUT).grow(1);
            if (job == Job.DEUTERIUM) buffer -= IsotopeColumnLogic.ICE_PER_CANISTER; else items.get(INPUT).shrink(1);
            progress = 0;
            if (!wantsPower()) working(false);
        }
        setChanged();
    }

    @Override public Component getDisplayName() { return Component.translatable("block.frozendawn.isotope_column"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new IsotopeColumnMenu(id, inv, this); }
    public NonNullList<ItemStack> items() { return items; }
    @Override public int getContainerSize() { return 2; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int i) { return items.get(i); }
    @Override public ItemStack removeItem(int i, int n) { var s = ContainerHelper.removeItem(items, i, n); if (!s.isEmpty()) setChanged(); return s; }
    @Override public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items, i); }
    @Override public void setItem(int i, ItemStack s) { s.setCount(Math.min(s.getCount(), s.getMaxStackSize())); items.set(i, s); setChanged(); }
    @Override public boolean canPlaceItem(int i, ItemStack s) { return i == INPUT && accepts(s); }
    @Override public boolean stillValid(Player p) { return !p.isSpectator() && level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); buffer = 0; progress = 0; job = Job.NONE; setChanged(); }
    @Override public int[] getSlotsForFace(Direction d) { return d == Direction.DOWN ? new int[]{OUTPUT} : new int[]{INPUT}; }
    @Override public boolean canPlaceItemThroughFace(int i, ItemStack s, Direction d) { return canPlaceItem(i, s); }
    @Override public boolean canTakeItemThroughFace(int i, ItemStack s, Direction d) { return i == OUTPUT; }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.saveAdditional(t, r); ContainerHelper.saveAllItems(t, items, r);
        t.putInt("Buffer", buffer); t.putInt("Progress", progress); t.putString("Job", job.name());
    }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.loadAdditional(t, r); items.clear(); ContainerHelper.loadAllItems(t, items, r);
        buffer = Math.clamp(t.getInt("Buffer"), 0, IsotopeColumnLogic.MAX_BUFFER);
        try { job = Job.valueOf(t.getString("Job")); } catch (IllegalArgumentException e) { job = Job.NONE; }
        progress = job == Job.NONE ? 0 : Math.clamp(t.getInt("Progress"), 0, total(job) - 1);
    }
}
