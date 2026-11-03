package com.frozendawn.block;

import com.frozendawn.init.*;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A paid processing tick consumes 100 J and emits 25 units of waste heat. No fuel or air consumer. */
public final class InductionFurnaceBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider,com.frozendawn.power.PoweredMachine {
    public static final int DEMAND_WATTS=2000;
    public static final double WASTE_HEAT_WATTS=500;
    private final NonNullList<ItemStack> items=NonNullList.withSize(2,ItemStack.EMPTY);
    private final RecipeManager.CachedCheck<SingleRecipeInput,SmeltingRecipe> recipes=RecipeManager.createCheck(RecipeType.SMELTING);
    private final Map<ResourceLocation,Integer> used=new HashMap<>();
    private ResourceLocation activeRecipe;
    private int progress,total=100;
    public InductionFurnaceBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.INDUCTION_FURNACE.get(),p,s);}
    public static boolean smeltable(net.minecraft.world.level.Level l,ItemStack s){return !s.isEmpty()&&(com.frozendawn.recipe.InductionRecipes.find(s).isPresent()||l.getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(s),l).isPresent());}
    /** Induction-only refining first, then ordinary smelting. */
    private RecipeHolder<SmeltingRecipe> recipe(){if(level==null||items.get(0).isEmpty())return null;var own=com.frozendawn.recipe.InductionRecipes.find(items.get(0));return own.isPresent()?own.get():recipes.getRecipeFor(new SingleRecipeInput(items.get(0)),level).orElse(null);}
    private ItemStack result(RecipeHolder<SmeltingRecipe> recipe){return recipe==null?ItemStack.EMPTY:recipe.value().assemble(new SingleRecipeInput(items.get(0)),level.registryAccess());}
    private boolean fits(ItemStack result){var out=items.get(1);return !result.isEmpty()&&(out.isEmpty()?result.getCount()<=result.getMaxStackSize():ItemStack.isSameItemSameComponents(out,result)&&out.getCount()+result.getCount()<=Math.min(out.getMaxStackSize(),getMaxStackSize()));}
    public boolean wantsPower(){return fits(result(recipe()));}
    /** 2 kW smelting; induction-only refining runs hotter and draws more. */
    public int demandWatts(){return com.frozendawn.recipe.InductionRecipes.watts(recipe());}
    public int progress(){return progress;}
    public int total(){return total;}
    public boolean working(){return getBlockState().getValue(InductionFurnaceBlock.LIT);}
    public int status(){if(items.get(0).isEmpty())return 0;var recipe=recipe();if(recipe==null)return 3;if(!fits(result(recipe)))return 4;return working()?2:1;}
    public ContainerData data(){return new ContainerData(){public int get(int i){return switch(i){case 0->progress;case 1->total;case 2->status();case 3->demandWatts()/100;default->0;};}public void set(int i,int v){}public int getCount(){return 4;}};}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerNetworks.machineLoaded(l,worldPosition);}
    @Override public void setRemoved(){if(level instanceof ServerLevel l)PowerNetworks.machineRemoved(l,worldPosition);super.setRemoved();}
    private void working(boolean value){if(working()!=value&&level!=null)level.setBlock(worldPosition,getBlockState().setValue(InductionFurnaceBlock.LIT,value),3);}
    public void serverTick(){
        if(!(level instanceof ServerLevel l))return;
        var recipe=recipe();
        if(recipe==null){if(progress!=0||activeRecipe!=null){progress=0;activeRecipe=null;setChanged();}working(false);return;}
        int duration=Math.clamp((recipe.value().getCookingTime()+1)/2,1,32000);
        if(!recipe.id().equals(activeRecipe)||duration!=total){progress=0;total=duration;activeRecipe=recipe.id();setChanged();}
        var out=result(recipe);
        if(!fits(out)||!PowerNetworks.consumeMachineTick(l,worldPosition)){working(false);return;}
        working(true);progress++;
        RoomThermalManager.addMachineHeat(l,worldPosition,WASTE_HEAT_WATTS*demandWatts()/DEMAND_WATTS/20.0);
        if(l.getGameTime()%20==0)RoomAtmosphere.keepAlive(this);
        if(progress>=total){
            if(items.get(1).isEmpty())items.set(1,out.copy());else items.get(1).grow(out.getCount());
            items.get(0).shrink(1);used.merge(recipe.id(),1,(a,b)->Math.min(1_000_000,a+b));progress=0;
            if(!wantsPower())working(false);
        }
        setChanged();
    }
    public void awardRecipesAndExperience(ServerPlayer player){
        if(!(level instanceof ServerLevel l))return;var awarded=new ArrayList<RecipeHolder<?>>();
        for(var entry:used.entrySet())l.getRecipeManager().byKey(entry.getKey()).or(()->com.frozendawn.recipe.InductionRecipes.byId(entry.getKey())).ifPresent(holder->{
            if(holder.value() instanceof SmeltingRecipe recipe){
                float value=entry.getValue()*recipe.getExperience();int xp=Mth.floor(value);if(l.random.nextFloat()<value-xp)xp++;
                ExperienceOrb.award(l,player==null?worldPosition.getCenter():player.position(),xp);awarded.add(holder);
            }
        });
        awarded.removeIf(h->com.frozendawn.recipe.InductionRecipes.byId(h.id()).isPresent());
        if(player!=null)player.awardRecipes(awarded);used.clear();setChanged();
    }
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.induction_furnace");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new InductionFurnaceMenu(id,inv,this,data(),worldPosition);}
    @Override public int getContainerSize(){return 2;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int i){return items.get(i);}
    @Override public ItemStack removeItem(int i,int n){var s=ContainerHelper.removeItem(items,i,n);if(!s.isEmpty())setChanged();return s;}
    @Override public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(items,i);}
    @Override public void setItem(int i,ItemStack s){if(i==0&&!ItemStack.isSameItemSameComponents(items.get(0),s)){progress=0;activeRecipe=null;}s.setCount(Math.min(s.getCount(),s.getMaxStackSize()));items.set(i,s);setChanged();}
    @Override public boolean canPlaceItem(int i,ItemStack s){return i==0;}
    @Override public boolean stillValid(Player p){return !p.isSpectator()&&level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public void clearContent(){items.clear();progress=0;activeRecipe=null;used.clear();setChanged();}
    @Override public int[] getSlotsForFace(Direction d){return d==Direction.DOWN?new int[]{1}:new int[]{0};}
    @Override public boolean canPlaceItemThroughFace(int i,ItemStack s,Direction d){return i==0;}
    @Override public boolean canTakeItemThroughFace(int i,ItemStack s,Direction d){return i==1;}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){super.saveAdditional(t,r);ContainerHelper.saveAllItems(t,items,r);t.putInt("Progress",progress);t.putInt("Total",total);if(activeRecipe!=null)t.putString("ActiveRecipe",activeRecipe.toString());var u=new CompoundTag();used.forEach((id,count)->u.putInt(id.toString(),count));t.put("RecipesUsed",u);}
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){super.loadAdditional(t,r);items.clear();ContainerHelper.loadAllItems(t,items,r);total=Math.clamp(t.getInt("Total"),1,32000);progress=Math.clamp(t.getInt("Progress"),0,total-1);activeRecipe=ResourceLocation.tryParse(t.getString("ActiveRecipe"));used.clear();var u=t.getCompound("RecipesUsed");for(var key:u.getAllKeys()){var id=ResourceLocation.tryParse(key);if(id!=null)used.put(id,Math.clamp(u.getInt(key),0,1_000_000));}}
}
