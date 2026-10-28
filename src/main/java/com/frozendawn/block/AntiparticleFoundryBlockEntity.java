package com.frozendawn.block;

import com.frozendawn.init.*;
import com.frozendawn.item.AntimatterCapsuleItem;
import com.frozendawn.power.IcarusPhysics;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.world.RoomAtmosphere;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** ORSA Antiparticle Foundry: fills one empty capsule per paid hour at 400 kW (1.44 GJ a capsule). Losing power pauses the
 * fill and keeps its progress. The energy goes into the trap, not the room, so it sheds no waste heat. */
public final class AntiparticleFoundryBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider,com.frozendawn.power.PoweredMachine {
    public static final int INPUT=0,OUTPUT=1,DEMAND_WATTS=400_000,SECONDS_PER_CAPSULE=3600,TOTAL_TICKS=SECONDS_PER_CAPSULE*20;
    private final NonNullList<ItemStack> items=NonNullList.withSize(2,ItemStack.EMPTY);
    private int progress;
    public AntiparticleFoundryBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.ANTIPARTICLE_FOUNDRY.get(),p,s);}
    public static boolean accepts(ItemStack s){return s.is(ModItems.EMPTY_ANTIMATTER_CAPSULE);}
    private boolean fits(){var out=items.get(OUTPUT);return out.isEmpty()||ItemStack.isSameItemSameComponents(out,AntimatterCapsuleItem.sealed(IcarusPhysics.CAPSULE))&&out.getCount()<out.getMaxStackSize();}
    public boolean wantsPower(){return accepts(items.get(INPUT))&&fits();}
    public int demandWatts(){return wantsPower()?DEMAND_WATTS:0;}
    /** Paid ticks toward the current capsule. */
    public int progress(){return progress;}
    public void setProgressForTest(int seconds){progress=Math.clamp(seconds*20L,0,TOTAL_TICKS-1);setChanged();}
    public boolean working(){return getBlockState().getValue(AntiparticleFoundryBlock.LIT);}
    /** 0 idle, 1 waiting for power, 2 filling, 4 output full. */
    public int status(){if(!accepts(items.get(INPUT)))return 0;if(!fits())return 4;return working()?2:1;}
    public ContainerData data(){return new ContainerData(){public int get(int i){return switch(i){case 0->progress/20;case 1->SECONDS_PER_CAPSULE;case 2->status();case 3->DEMAND_WATTS/1000;default->0;};}public void set(int i,int v){}public int getCount(){return 4;}};}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerNetworks.machineLoaded(l,worldPosition);}
    @Override public void setRemoved(){if(level instanceof ServerLevel l)PowerNetworks.machineRemoved(l,worldPosition);super.setRemoved();}
    private void working(boolean value){if(working()!=value&&level!=null)level.setBlock(worldPosition,getBlockState().setValue(AntiparticleFoundryBlock.LIT,value),3);}
    public void serverTick(){
        if(!(level instanceof ServerLevel l))return;
        if(!wantsPower()||!PowerNetworks.consumeMachineTick(l,worldPosition)){working(false);return;}
        working(true);progress++;
        if(l.getGameTime()%20==0)RoomAtmosphere.keepAlive(this);
        if(progress>=TOTAL_TICKS){
            items.get(INPUT).shrink(1);
            if(items.get(OUTPUT).isEmpty())items.set(OUTPUT,AntimatterCapsuleItem.sealed(IcarusPhysics.CAPSULE));else items.get(OUTPUT).grow(1);
            progress=0;if(!wantsPower())working(false);
        }
        setChanged();
    }
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.antiparticle_foundry");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new AntiparticleFoundryMenu(id,inv,this,data(),worldPosition);}
    @Override public int getContainerSize(){return 2;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int i){return items.get(i);}
    @Override public ItemStack removeItem(int i,int n){var s=ContainerHelper.removeItem(items,i,n);if(!s.isEmpty())setChanged();return s;}
    @Override public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(items,i);}
    @Override public void setItem(int i,ItemStack s){s.setCount(Math.min(s.getCount(),s.getMaxStackSize()));items.set(i,s);setChanged();}
    @Override public boolean canPlaceItem(int i,ItemStack s){return i==INPUT&&accepts(s);}
    @Override public boolean stillValid(Player p){return !p.isSpectator()&&level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public void clearContent(){items.clear();progress=0;setChanged();}
    @Override public int[] getSlotsForFace(Direction d){return d==Direction.DOWN?new int[]{OUTPUT}:new int[]{INPUT};}
    @Override public boolean canPlaceItemThroughFace(int i,ItemStack s,Direction d){return canPlaceItem(i,s);}
    @Override public boolean canTakeItemThroughFace(int i,ItemStack s,Direction d){return i==OUTPUT;}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){super.saveAdditional(t,r);ContainerHelper.saveAllItems(t,items,r);t.putInt("Progress",progress);}
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){super.loadAdditional(t,r);items.clear();ContainerHelper.loadAllItems(t,items,r);progress=Math.clamp(t.getInt("Progress"),0,TOTAL_TICKS-1);}
}
