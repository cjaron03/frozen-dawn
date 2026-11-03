package com.frozendawn.block;

import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Standalone physical cell consumer. Only the loaded server block ticker spends energy. */
public final class BatteryLanternBlockEntity extends BlockEntity {
    public static final int DRAW_WATTS = 50;
    private ItemStack cell = ItemStack.EMPTY;
    private boolean enabled;
    private int credit;
    public BatteryLanternBlockEntity(BlockPos pos,BlockState state) {
        super(ModBlockEntities.BATTERY_LANTERN.get(),pos,state);
        enabled=state.getValue(BatteryLanternBlock.ENABLED);
    }
    public ItemStack cell() { return cell.copy(); }
    public int storedJoules() { return PowerCellItem.energy(cell); }
    public int prepaidHalfJoules() { return credit; }
    public boolean enabled() { return enabled; }
    public ItemStack swapCell(ItemStack incoming) {
        if (!incoming.isEmpty()&&!incoming.is(ModItems.POWER_CELL)) throw new IllegalArgumentException("Expected Power Cell");
        var old=cell;cell=incoming.isEmpty()?ItemStack.EMPTY:incoming.copyWithCount(1);
        setChanged();refreshLight(false);return old;
    }
    public void toggle() { enabled=!enabled;setChanged();refreshLight(false); }
    public void serverTick() {
        boolean paid=false;
        if(enabled&&storedJoules()*2+credit>=5) {
            // 50 W / 20 ticks = 2.5 J: pay 3 J then 2 J, retaining the half-joule.
            int debit=(5-credit+1)/2;
            credit+=debit*2-5;
            PowerCellItem.setEnergy(cell,storedJoules()-debit);
            paid=true;setChanged();
        }
        refreshLight(paid);
    }
    public void refreshLight(boolean paid) {
        if(level==null||level.isClientSide())return;
        var state=getBlockState();
        boolean lit=enabled&&(paid||storedJoules()*2+credit>=5);
        if(state.getValue(BatteryLanternBlock.LIT)!=lit||state.getValue(BatteryLanternBlock.ENABLED)!=enabled)
            level.setBlock(worldPosition,state.setValue(BatteryLanternBlock.LIT,lit).setValue(BatteryLanternBlock.ENABLED,enabled),3);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        if(!cell.isEmpty())tag.put("Cell",cell.save(registries));
        tag.putBoolean("Enabled",enabled);tag.putInt("Credit",credit);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        cell=sanitize(ItemStack.parseOptional(registries,tag.getCompound("Cell")));
        enabled=tag.contains("Enabled")?tag.getBoolean("Enabled"):getBlockState().getValue(BatteryLanternBlock.ENABLED);
        credit=Math.clamp(tag.getInt("Credit"),0,1);
    }
    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        cell=sanitize(input.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyOne());
        enabled=input.getOrDefault(ModDataComponents.LANTERN_ENABLED.get(),true);
        credit=Math.clamp(input.getOrDefault(ModDataComponents.LANTERN_CREDIT.get(),0),0,1);
        input.get(DataComponents.MAX_STACK_SIZE);
    }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder output) {
        super.collectImplicitComponents(output);
        output.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(cell)));
        output.set(ModDataComponents.LANTERN_ENABLED.get(),enabled);
        output.set(ModDataComponents.LANTERN_CREDIT.get(),credit);
        output.set(DataComponents.MAX_STACK_SIZE,cell.isEmpty()?16:1);
    }
    private static ItemStack sanitize(ItemStack value) {return value.is(ModItems.POWER_CELL)?value.copyWithCount(1):ItemStack.EMPTY;}
}
