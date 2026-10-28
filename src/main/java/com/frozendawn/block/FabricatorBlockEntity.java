package com.frozendawn.block;

import com.frozendawn.data.FabricatorBlueprints;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.*;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.recipe.FabricatorRecipe;
import com.frozendawn.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** ORSA Fabricator: prints precision parts from blueprints this world has learned. A paid tick draws the recipe's
 * wattage and sheds a quarter of it as waste heat. Losing power pauses the job; ingredients are spent only when it completes. */
public final class FabricatorBlockEntity extends BlockEntity implements WorldlyContainer,MenuProvider,com.frozendawn.power.PoweredMachine {
    public static final int INPUTS=6,OUTPUT=6,STATUS_LOCKED=5;
    public static final double WASTE_HEAT_SHARE=0.25;
    private static final int[] INPUT_SLOTS={0,1,2,3,4,5},OUTPUT_SLOTS={OUTPUT};
    private final NonNullList<ItemStack> items=NonNullList.withSize(INPUTS+1,ItemStack.EMPTY);
    private ResourceLocation activeRecipe;
    private int progress,total=1;
    public FabricatorBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.FABRICATOR.get(),p,s);}
    private FabricatorRecipe.Input input(){return new FabricatorRecipe.Input(items.subList(0,INPUTS));}
    /** Every recipe the inputs satisfy, learned or not; the running job first so a second match never steals it. */
    private List<RecipeHolder<FabricatorRecipe>> matching(){
        if(level==null||items.subList(0,INPUTS).stream().allMatch(ItemStack::isEmpty))return List.of();var in=input();
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.FABRICATING.get()).stream().filter(r->r.value().matches(in,level))
                .sorted(Comparator.comparing((RecipeHolder<FabricatorRecipe> r)->!r.id().equals(activeRecipe)).thenComparing(r->r.id().toString())).toList();
    }
    private RecipeHolder<FabricatorRecipe> recipe(){
        if(!(level instanceof ServerLevel l))return null;
        for(var r:matching())if(FabricatorBlueprints.knows(l.getServer(),r.value().blueprint()))return r;
        return null;
    }
    private boolean fits(ItemStack result){var out=items.get(OUTPUT);return !result.isEmpty()&&(out.isEmpty()?result.getCount()<=result.getMaxStackSize():ItemStack.isSameItemSameComponents(out,result)&&out.getCount()+result.getCount()<=Math.min(out.getMaxStackSize(),getMaxStackSize()));}
    public boolean wantsPower(){var r=recipe();return r!=null&&fits(r.value().result());}
    public int demandWatts(){var r=recipe();return r==null?0:r.value().watts();}
    public int progress(){return progress;}
    public int total(){return total;}
    public boolean working(){return getBlockState().getValue(FabricatorBlock.LIT);}
    /** Induction Furnace codes, plus {@link #STATUS_LOCKED} when the inputs match a blueprint the world has not learned. */
    public int status(){
        if(items.subList(0,INPUTS).stream().allMatch(ItemStack::isEmpty))return 0;
        var r=recipe();if(r==null)return matching().isEmpty()?3:STATUS_LOCKED;
        if(!fits(r.value().result()))return 4;return working()?2:1;
    }
    public ContainerData data(){return new ContainerData(){public int get(int i){return switch(i){case 0->progress;case 1->total;case 2->status();case 3->demandWatts()/100;default->0;};}public void set(int i,int v){}public int getCount(){return 4;}};}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerNetworks.machineLoaded(l,worldPosition);}
    @Override public void setRemoved(){if(level instanceof ServerLevel l)PowerNetworks.machineRemoved(l,worldPosition);super.setRemoved();}
    private void working(boolean value){if(working()!=value&&level!=null)level.setBlock(worldPosition,getBlockState().setValue(FabricatorBlock.LIT,value),3);}
    public void serverTick(){
        if(!(level instanceof ServerLevel l))return;
        var recipe=recipe();
        if(recipe==null){if(progress!=0||activeRecipe!=null){progress=0;activeRecipe=null;setChanged();}working(false);return;}
        int duration=Math.clamp(recipe.value().seconds()*20L,1,32000);
        if(!recipe.id().equals(activeRecipe)||duration!=total){progress=0;total=duration;activeRecipe=recipe.id();setChanged();}
        var out=recipe.value().result();
        if(!fits(out)||!PowerNetworks.consumeMachineTick(l,worldPosition)){working(false);return;}
        working(true);progress++;
        RoomThermalManager.addMachineHeat(l,worldPosition,recipe.value().watts()*WASTE_HEAT_SHARE/20.0);
        if(l.getGameTime()%20==0)RoomAtmosphere.keepAlive(this);
        if(progress>=total){
            var debits=recipe.value().consumption(input());
            if(debits!=null){
                for(int i=0;i<INPUTS;i++)items.get(i).shrink(debits[i]);
                if(items.get(OUTPUT).isEmpty())items.set(OUTPUT,out.copy());else items.get(OUTPUT).grow(out.getCount());
            }
            progress=0;if(!wantsPower())working(false);
        }
        setChanged();
    }
    /** Reads a slate into the world's archive. A newly learned slate is used up; a known, blank or unreadable one stays in hand. */
    public static void insertSlate(ServerLevel level,Player player,ItemStack stack){
        var id=stack.get(ModDataComponents.BLUEPRINT.get());
        if(id==null||!FabricatorBlueprints.SLATES.contains(id)){player.displayClientMessage(Component.translatable("block.frozendawn.fabricator.slate_unreadable"),true);return;}
        var part=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(com.frozendawn.FrozenDawn.MOD_ID,id)).getDescription();
        var archive=FabricatorBlueprints.get(level.getServer());
        if(!archive.learn(id)){player.displayClientMessage(Component.translatable("block.frozendawn.fabricator.slate_known",part),true);return;}
        stack.consume(1,player);
        player.displayClientMessage(Component.translatable("block.frozendawn.fabricator.slate_learned",part),true);
        if(archive.knowsAll()&&player instanceof ServerPlayer server)WorldTickHandler.grantAdvancement(server,"full_archive");
    }
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.fabricator");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new FabricatorMenu(id,inv,this,data(),worldPosition);}
    @Override public int getContainerSize(){return INPUTS+1;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int i){return items.get(i);}
    @Override public ItemStack removeItem(int i,int n){var s=ContainerHelper.removeItem(items,i,n);if(!s.isEmpty())setChanged();return s;}
    @Override public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(items,i);}
    @Override public void setItem(int i,ItemStack s){s.setCount(Math.min(s.getCount(),s.getMaxStackSize()));items.set(i,s);setChanged();}
    /** Slates are learned by hand at the machine, never stored in it. */
    @Override public boolean canPlaceItem(int i,ItemStack s){return i<INPUTS&&!s.is(ModItems.DATA_SLATE);}
    @Override public boolean stillValid(Player p){return !p.isSpectator()&&level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public void clearContent(){items.clear();progress=0;activeRecipe=null;setChanged();}
    @Override public int[] getSlotsForFace(Direction d){return d==Direction.DOWN?OUTPUT_SLOTS.clone():INPUT_SLOTS.clone();}
    @Override public boolean canPlaceItemThroughFace(int i,ItemStack s,Direction d){return canPlaceItem(i,s);}
    @Override public boolean canTakeItemThroughFace(int i,ItemStack s,Direction d){return i==OUTPUT;}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){super.saveAdditional(t,r);ContainerHelper.saveAllItems(t,items,r);t.putInt("Progress",progress);t.putInt("Total",total);if(activeRecipe!=null)t.putString("ActiveRecipe",activeRecipe.toString());}
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){super.loadAdditional(t,r);items.clear();ContainerHelper.loadAllItems(t,items,r);total=Math.clamp(t.getInt("Total"),1,32000);progress=Math.clamp(t.getInt("Progress"),0,total-1);activeRecipe=ResourceLocation.tryParse(t.getString("ActiveRecipe"));}
}
