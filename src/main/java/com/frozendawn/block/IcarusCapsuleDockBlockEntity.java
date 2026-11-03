package com.frozendawn.block;

import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.AntimatterCapsuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Eight slots of sealed and empty antimatter capsules. Injection takes whole capsules in slot order and leaves their empty
 * casings; the Cole Protocol seals the core's charge back into empties. */
public final class IcarusCapsuleDockBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int SLOTS = 8;
    private static final int[] FACE_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7 };
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public IcarusCapsuleDockBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.ICARUS_CAPSULE_DOCK.get(), p, s); }
    public static boolean accepts(ItemStack s) { return s.is(ModItems.ANTIMATTER_CAPSULE.get()) || s.is(ModItems.EMPTY_ANTIMATTER_CAPSULE.get()); }
    public NonNullList<ItemStack> items() { return items; }

    /** Sealed charge of the whole capsules that fit in room, taken in slot order; a capsule that would overfill it is skipped. */
    public long injectable(long room) {
        long total = 0;
        for (var s : items) {
            long c = AntimatterCapsuleItem.charge(s);
            for (int n = 0; n < s.getCount() && c > 0 && total + c <= room; n++) total += c;
        }
        return total;
    }
    /** Empties the same capsules injectable(charge) counted. */
    public void consume(long charge) {
        if (charge <= 0) return;
        long left = charge; int casings = 0;
        for (var s : items) {
            long c = AntimatterCapsuleItem.charge(s);
            int n = 0;
            while (n < s.getCount() && c > 0 && c <= left) { left -= c; n++; }
            s.shrink(n); casings += n;
        }
        insert(new ItemStack(ModItems.EMPTY_ANTIMATTER_CAPSULE.get(), casings));
        setChanged();
    }
    public int empties() { return items.stream().filter(s -> s.is(ModItems.EMPTY_ANTIMATTER_CAPSULE.get())).mapToInt(ItemStack::getCount).sum(); }
    /** Total sealed charge held, in micro-capsules. */
    public long charge() { return items.stream().mapToLong(s -> AntimatterCapsuleItem.charge(s) * s.getCount()).sum(); }
    /** Seals charge into empties: full capsules first, then one partial. */
    public void recover(long charge) {
        if (charge <= 0) return;
        long full = charge / com.frozendawn.power.IcarusPhysics.CAPSULE, rest = charge % com.frozendawn.power.IcarusPhysics.CAPSULE;
        int casings = (int) full + (rest > 0 ? 1 : 0), taken = 0;
        for (var s : items) if (s.is(ModItems.EMPTY_ANTIMATTER_CAPSULE.get())) { int n = Math.min(casings - taken, s.getCount()); s.shrink(n); taken += n; }
        if (full > 0) insert(AntimatterCapsuleItem.sealed(com.frozendawn.power.IcarusPhysics.CAPSULE).copyWithCount((int) full));
        if (rest > 0) insert(AntimatterCapsuleItem.sealed(rest));
        setChanged();
    }
    /** Merges into matching stacks, then empty slots. What no slot holds drops beside the dock rather than vanish. */
    private void insert(ItemStack stack) {
        for (int i = 0; i < SLOTS && !stack.isEmpty(); i++) {
            var s = items.get(i);
            if (s.isEmpty()) { int n = Math.min(stack.getCount(), stack.getMaxStackSize()); items.set(i, stack.split(n)); }
            else if (ItemStack.isSameItemSameComponents(s, stack)) { int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount()); s.grow(n); stack.shrink(n); }
        }
        while (!stack.isEmpty() && level != null)
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, stack.split(stack.getMaxStackSize()));
    }

    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int i) { return items.get(i); }
    @Override public ItemStack removeItem(int i, int n) { var s = ContainerHelper.removeItem(items, i, n); if (!s.isEmpty()) setChanged(); return s; }
    @Override public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items, i); }
    @Override public void setItem(int i, ItemStack s) { s.limitSize(getMaxStackSize(s)); items.set(i, s); setChanged(); }
    @Override public boolean canPlaceItem(int i, ItemStack s) { return accepts(s); }
    @Override public boolean stillValid(Player p) { return level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction d) { return FACE_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int i, ItemStack s, Direction d) { return canPlaceItem(i, s); }
    @Override public boolean canTakeItemThroughFace(int i, ItemStack s, Direction d) { return false; }
    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) { super.saveAdditional(t, r); ContainerHelper.saveAllItems(t, items, r); }
    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) { super.loadAdditional(t, r); items.clear(); ContainerHelper.loadAllItems(t, items, r); }
}
