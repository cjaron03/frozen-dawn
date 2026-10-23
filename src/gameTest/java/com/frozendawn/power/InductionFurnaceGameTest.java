package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.thermal.RoomHeatMath;
import com.frozendawn.world.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InductionFurnaceGameTest {
    @BeforeBatch(batch="induction") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}
    @BeforeBatch(batch="induction_live") public static void reportLive(ServerLevel l){GameTestReporting.installReporter(l);}
    @BeforeBatch(batch="induction_heat") public static void reportHeat(ServerLevel l){GameTestReporting.installReporter(l);}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="induction",timeoutTicks=100)
    public static void inductionVanillaCatalogueInventoryAndMenuSecurity(GameTestHelper h){
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var p=place(h,3,2,3,ModBlocks.INDUCTION_FURNACE.get());var f=furnace(l,p);int checked=0;
            for(var holder:l.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)){
                if(!holder.id().getNamespace().equals("minecraft"))continue;
                var choices=holder.value().getIngredients().getFirst().getItems();if(choices.length==0)continue;
                f.setItem(0,choices[0].copy());f.setItem(1,ItemStack.EMPTY);
                h.assertTrue(f.wantsPower(),"Vanilla smelting input is accepted: "+holder.id());checked++;
            }
            h.assertTrue(checked>=50,"Tests the native vanilla furnace recipe catalogue");
            f.setItem(0,new ItemStack(Items.RAW_IRON,3));f.setItem(1,new ItemStack(Items.IRON_INGOT,64));
            h.assertTrue(!f.wantsPower()&&f.status()==4,"Full output draws no power");
            f.setItem(1,new ItemStack(Items.DIAMOND));h.assertTrue(!f.wantsPower(),"Incompatible output is never overwritten");f.setItem(1,ItemStack.EMPTY);
            var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"induction_menu"));player.setPos(p.east().getCenter());
            var menu=new InductionFurnaceMenu(1,player.getInventory(),f,f.data(),p);
            h.assertTrue(menu.stillValid(player)&&f.getContainerSize()==2&&menu.slots.size()==38&&!menu.slots.get(1).mayPlace(new ItemStack(Items.DIAMOND)),"Two-slot menu has no fuel slot and refuses output insertion");
            player.getInventory().setItem(0,new ItemStack(Items.RAW_IRON,2));menu.quickMoveStack(player,29);h.assertTrue(f.getItem(0).getCount()==5,"Shift-click inserts smelting inputs");
            h.assertTrue(Arrays.equals(f.getSlotsForFace(Direction.DOWN),new int[]{1})&&f.canPlaceItemThroughFace(0,new ItemStack(Items.RAW_IRON),Direction.UP)&&!f.canPlaceItemThroughFace(1,new ItemStack(Items.DIAMOND),Direction.UP),"Hoppers expose input above and output below");
            var core=place(h,7,2,3,ModBlocks.GEOTHERMAL_CORE.get());var source=(GeothermalCoreBlockEntity)l.getBlockEntity(core);source.setPowerShare(9);link(h,core,p);account(l);f.serverTick();
            h.assertTrue(f.progress()==0&&!f.working(),"1.8 kW cannot run a 2 kW furnace partially");source.setPowerShare(10);account(l);f.serverTick();
            h.assertTrue(f.progress()==1&&f.working(),"Exactly 2 kW admits a full processing load");
            player.setPos(p.east(12).getCenter());h.assertTrue(!menu.stillValid(player),"Remote menu invalidates");
            player.setPos(p.getCenter());l.destroyBlock(p,false);h.assertTrue(!menu.stillValid(player),"Removed furnace invalidates stale menu");
            h.assertTrue(l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(2)).stream().filter(e->e.getItem().is(Items.RAW_IRON)).mapToInt(e->e.getItem().getCount()).sum()==5,"Breaking the furnace preserves unfinished inputs");h.succeed();
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="induction_live",timeoutTicks=180)
    public static void inductionRealTicksTwiceSpeedOutputsAndExperience(GameTestHelper h){
        h.runAfterDelay(1,()->{
            // Own the pressure geometry: links must not depend on the surrounding batch's terrain or scans.
            for(int x=0;x<=16;x++)for(int y=0;y<=4;y++)for(int z=1;z<=7;z++)
                h.setBlock(new BlockPos(x,y,z),x==0||x==16||y==0||y==4||z==1||z==7?Blocks.GLASS:Blocks.AIR);
            var l=h.getLevel();var core=place(h,2,2,4,ModBlocks.GEOTHERMAL_CORE.get());((GeothermalCoreBlockEntity)l.getBlockEntity(core)).setPowerShare(100);
            var p=place(h,6,2,4,ModBlocks.INDUCTION_FURNACE.get());var q=place(h,10,2,4,ModBlocks.INDUCTION_FURNACE.get());var s=place(h,14,2,4,ModBlocks.INDUCTION_FURNACE.get());
            furnace(l,p).setItem(0,new ItemStack(Items.RAW_IRON));furnace(l,q).setItem(0,new ItemStack(Items.WET_SPONGE));furnace(l,s).setItem(0,new ItemStack(Items.DIAMOND_ORE));link(h,core,p);link(h,p,q);link(h,q,s);
            h.runAfterDelay(90,()->{h.assertTrue(furnace(l,p).getItem(1).isEmpty()&&furnace(l,p).total()==100,"A 200-tick vanilla recipe requires exactly 100 processing ticks");});
            h.runAfterDelay(130,()->{
                h.assertTrue(furnace(l,p).getItem(1).is(Items.IRON_INGOT)&&furnace(l,q).getItem(1).is(Items.SPONGE)&&furnace(l,s).getItem(1).is(Items.DIAMOND),"Ordinary server ticks smelt ore and sponge without fuel");
                h.assertTrue(!furnace(l,p).wantsPower()&&PowerNetworks.reading(l,p).demandKw()==0,"Finished empty machines draw zero power");
                var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"induction_xp"));player.setPos(s.getCenter());var menu=new InductionFurnaceMenu(1,player.getInventory(),furnace(l,s),furnace(l,s).data(),s);
                menu.quickMoveStack(player,1);int xp=l.getEntitiesOfClass(ExperienceOrb.class,new AABB(s).inflate(2)).stream().mapToInt(ExperienceOrb::getValue).sum();h.assertTrue(xp==1,"Taking a native diamond recipe output grants its exact vanilla XP");
                furnace(l,s).awardRecipesAndExperience(player);h.assertTrue(l.getEntitiesOfClass(ExperienceOrb.class,new AABB(s).inflate(2)).stream().mapToInt(ExperienceOrb::getValue).sum()==1,"Recipe XP is awarded once");
                l.destroyBlock(p,false);h.assertTrue(l.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(2)).stream().filter(e->e.getItem().is(Items.IRON_INGOT)).mapToInt(e->e.getItem().getCount()).sum()==1,"Breaking a finished furnace drops its output without duplication");h.succeed();
            });
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="induction_live",timeoutTicks=300)
    public static void inductionBatteryDebitTracksWorkAndBlockedIdle(GameTestHelper h){
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var bank=place(h,2,2,3,ModBlocks.BATTERY_BANK.get());var p=place(h,6,2,3,ModBlocks.INDUCTION_FURNACE.get());var store=(PowerStorageBlockEntity)l.getBlockEntity(bank);var f=furnace(l,p);
            store.setItem(0,PowerCellItem.charged(4000));f.setItem(0,new ItemStack(Items.RAW_IRON));link(h,bank,p);
            h.runAfterDelay(65,()->{
                h.assertTrue(store.storedJoules()==0&&f.progress()==40&&f.getItem(1).isEmpty(),"4 kJ buys exactly forty 100 J processing ticks, then pauses without producing an item");
                store.setItem(0,PowerCellItem.charged(10000));
                h.runAfterDelay(100,()->{
                    h.assertTrue(f.getItem(1).is(Items.IRON_INGOT)&&store.storedJoules()==4000,"Resumed job spends only its remaining sixty processing ticks");
                    f.setItem(0,new ItemStack(Items.RAW_IRON));f.setItem(1,new ItemStack(Items.IRON_INGOT,64));
                    h.runAfterDelay(25,()->{h.assertTrue(store.storedJoules()==4000&&!f.working()&&PowerNetworks.reading(l,p).demandKw()==0,"Blocked output consumes neither cell charge nor active demand");
                        f.setItem(0,ItemStack.EMPTY);h.runAfterDelay(25,()->{h.assertTrue(store.storedJoules()==4000,"Idle furnace leaks no battery charge");h.succeed();});});
                });
            });
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="induction_live",timeoutTicks=360)
    public static void inductionPowerPauseSaveAndRelayRebindPreserveProgress(GameTestHelper h){
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var core=place(h,2,2,4,ModBlocks.GEOTHERMAL_CORE.get());var relay=place(h,7,2,4,ModBlocks.POWER_RELAY.get());var p=place(h,12,2,4,ModBlocks.INDUCTION_FURNACE.get());var f=furnace(l,p);var source=(GeothermalCoreBlockEntity)l.getBlockEntity(core);source.setPowerShare(25);f.setItem(0,new ItemStack(Items.RAW_IRON,3));link(h,core,relay);link(h,relay,p);
            h.runAfterDelay(35,()->{h.assertTrue(f.progress()>0&&f.getItem(1).isEmpty(),"Live job is partially complete");source.setPowerShare(0);
                h.runAfterDelay(25,()->{int paused=f.progress();var saved=f.saveWithFullMetadata(l.registryAccess());f.setItem(0,new ItemStack(Items.SAND));f.loadWithComponents(saved,l.registryAccess());
                    h.assertTrue(f.progress()==paused&&f.getItem(0).is(Items.RAW_IRON)&&f.getItem(0).getCount()==3,"Saved partial job retains input and progress");
                    h.runAfterDelay(25,()->{h.assertTrue(f.progress()==paused&&!f.working()&&f.getItem(1).isEmpty(),"Power loss preserves progress without free smelting or stored grants");source.setPowerShare(25);
                        h.runAfterDelay(120,()->{h.assertTrue(f.getItem(1).is(Items.IRON_INGOT),"Power restoration resumes the saved job");l.setBlock(relay,Blocks.AIR.defaultBlockState(),3);
                            h.runAfterDelay(25,()->{int hold=f.progress(),out=f.getItem(1).getCount();h.runAfterDelay(25,()->{h.assertTrue(f.progress()==hold&&f.getItem(1).getCount()==out&&!f.working(),"Broken relay stops further work");
                                l.setBlock(relay,ModBlocks.POWER_RELAY.get().defaultBlockState(),3);link(h,core,relay);link(h,relay,p);
                                h.runAfterDelay(25,()->{h.assertTrue(f.progress()>hold||f.getItem(1).getCount()>out,"Rebinding restores work without resetting inventory");h.succeed();});});});});});});});
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="induction_heat",timeoutTicks=100)
    public static void inductionVacuumPaidWasteHeatAndDuplicateTick(GameTestHelper h){
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var apoc=ApocalypseState.get(l.getServer());long previous=apoc.getApocalypseTicks();
            try{
                apoc.setApocalypseTicks((long)(apoc.getTotalDays()*24000L*.90),l.getServer());h.assertTrue(CombustionAtmosphere.isVacuum(l),"Fixture is actual late vacuum");RoomAtmosphere.reset();RoomThermalManager.reset();
                for(int x=2;x<=7;x++)for(int y=1;y<=6;y++)for(int z=2;z<=8;z++)h.setBlock(new BlockPos(x,y,z),x==2||x==7||y==1||y==6||z==2||z==8?Blocks.GLASS:Blocks.AIR);
                var p=place(h,7,3,5,ModBlocks.INDUCTION_FURNACE.get());var core=place(h,10,3,5,ModBlocks.GEOTHERMAL_CORE.get());var source=(GeothermalCoreBlockEntity)l.getBlockEntity(core);source.setPowerShare(100);var f=furnace(l,p);f.setItem(0,new ItemStack(Items.RAW_IRON));
                var room=RoomAtmosphere.view(l,h.absolutePos(new BlockPos(4,3,5)));h.assertTrue(room!=null,"Known sealed thermal room");RoomAirState.get(l).evacuate(room.geometry().cells());h.assertTrue(!RoomAtmosphere.hasAir(l,h.absolutePos(new BlockPos(4,3,5))),"No room oxygen remains");
                link(h,core,p);account(l);double before=RoomThermalState.get(l).ledger.passive;f.serverTick();int progress=f.progress();f.serverTick();h.assertTrue(progress==1&&f.progress()==1,"Only one paid processing tick can occur at a given server tick");
                RoomThermalManager.tickLevel(l);double heat=RoomThermalState.get(l).ledger.passive-before;
                h.assertTrue(Math.abs(heat-25*RoomHeatMath.VACUUM_HEATER_EFFICIENCY)<1e-6,"Exactly 0.5 kW raw waste heat is admitted for one paid tick using existing vacuum efficiency: "+heat);
                h.assertTrue(!VacuumFlames.isOrdinaryFlame(f.getBlockState())&&f.getContainerSize()==2,"Electric furnace is not combustion and has no fuel/O2 slot");
                f.setItem(1,new ItemStack(Items.IRON_INGOT,64));h.assertTrue(!f.wantsPower(),"Blocked furnace has no heat-producing work");
                var absent=new BlockPos(1234567,200,1234567);PowerNetworks.reading(l,absent);h.assertTrue(!l.isLoaded(absent),"Furnace diagnostics never force chunks");h.succeed();
            }finally{apoc.setApocalypseTicks(previous,l.getServer());}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY,batch="induction",timeoutTicks=100)
    public static void inductionReplayFunctionsParse(GameTestHelper h){for(var name:List.of("load","tick","setup","controls","basic","backup","brownout"))h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("induction_furnace_check",name)).isPresent(),"Native parser loads induction helper "+name);h.succeed();}
    private static InductionFurnaceBlockEntity furnace(ServerLevel l,BlockPos p){return (InductionFurnaceBlockEntity)l.getBlockEntity(p);}
    private static BlockPos place(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block b){var p=new BlockPos(x,y,z);h.setBlock(p,b);return h.absolutePos(p);}
    private static void link(GameTestHelper h,BlockPos a,BlockPos b){var result=PowerNetworks.link(h.getLevel(),a,b);h.assertTrue(result==PowerLinkRules.Result.OK,"Expected induction link "+a+" -> "+b+", got "+result);}
    private static void account(ServerLevel l){for(int i=0;i<128;i++)PowerNetworks.update(l);PowerNetworks.account(l);}
}
