package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.oxygen.*;
import com.frozendawn.thermal.RoomHeatMath;
import com.frozendawn.world.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PixieGameTest {
    @BeforeBatch(batch="pixie") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="pixie",timeoutTicks=100)
    public static void pixieSharedBudgetThrottleAndConservation(GameTestHelper h){scene(h,s->{
        var offline=s.pixie(3,3);PowerNetworks.clear(s.l,offline.getBlockPos());var a=s.pixie(4,4);var b=s.pixie(6,4);var c=s.pixie(8,4);s.flames();var x=s.tank(5,8,0);var y=s.tank(8,8,0);s.warm(24);s.next();
        h.assertTrue(offline.outputWatts()==0&&offline.waterMicro()==0&&offline.getItem(0).getCount()==2,"Unlinked intake consumes no resources and cannot block powered siblings");
        int watts=a.outputWatts()+b.outputWatts()+c.outputWatts();
        h.assertTrue(watts==10000,"Three physical PIXIEs have no per-room limit:2 room demand plus8 tank fill: "+watts);
        h.assertTrue(a.activity()==PixieBlockEntity.ROOM_AND_STORAGE&&b.activity()==PixieBlockEntity.ROOM_AND_STORAGE,"Actual shared room production and accepted storage filling are identified");
        h.assertTrue(x.storedMicro()+y.storedMicro()==80000,"Only surplus fills actual tank contents once");
        h.assertTrue(a.waterMicro()+b.waterMicro()+c.waterMicro()==36_000_000-100000,"Ice debit exactly equals produced breathing plus tank oxygen");
        var r=RoomOxygenManager.readingAt(s.l,s.anchor);h.assertTrue(r.demand()==2&&r.supply()==10&&r.sources()==4,"Shared gauge counts physical PIXIE production once; unpowered neighbor does not reserve demand");
        long water=a.waterMicro()+b.waterMicro()+c.waterMicro(),stored=x.storedMicro()+y.storedMicro();
        for(int i=0;i<100;i++){RoomOxygenManager.tickLevel(s.l);RoomOxygenManager.readingAt(s.l,s.anchor);PowerNetworks.monitorReading(s.l,a.getBlockPos());}
        h.assertTrue(water==a.waterMicro()+b.waterMicro()+c.waterMicro()&&stored==x.storedMicro()+y.storedMicro(),"Repeated reads/account requests cannot duplicate oxygen or ice consumption");
        s.tankContents(x,OxygenReservoirBlockEntity.capacityMicro());s.tankContents(y,OxygenReservoirBlockEntity.capacityMicro());
        s.power(25);s.next();r=RoomOxygenManager.readingAt(s.l,s.anchor);
        h.assertTrue(r.supply()==2&&r.demand()==2&&r.mode()==OxygenStatus.Mode.SUPPLIED&&a.outputWatts()==2000&&a.activity()==PixieBlockEntity.ROOM,"Full storage throttles to real room demand without a false near-capacity warning");
        s.power(10);s.next();h.assertTrue(RoomOxygenManager.readingAt(s.l,s.anchor).mode()==OxygenStatus.Mode.LOW,"No spare electrical headroom retains the real near-capacity warning");
        s.power(100);water=a.waterMicro()+b.waterMicro()+c.waterMicro();
        for(var p:s.campfires)s.l.setBlock(p,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,false),3);s.next();
        h.assertTrue(a.outputWatts()+b.outputWatts()+c.outputWatts()==0&&a.status()==PixieBlockEntity.IDLE&&a.activity()==PixieBlockEntity.INACTIVE,"Demand met/full tanks throttle to zero");
        h.assertTrue(water==a.waterMicro()+b.waterMicro()+c.waterMicro(),"Idle spends no water");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="pixie",timeoutTicks=100)
    public static void pixiePriorityPartialPowerAndFiniteBackup(GameTestHelper h){scene(h,s->{
        var p=s.pixie(10,4);s.flames();var bank=s.place(6,3,3,ModBlocks.BATTERY_BANK.get());var store=(PowerStorageBlockEntity)s.l.getBlockEntity(bank);s.link(s.core,bank);s.link(bank,p.getBlockPos());
        var f=s.place(4,3,3,ModBlocks.INDUCTION_FURNACE.get());((InductionFurnaceBlockEntity)s.l.getBlockEntity(f)).setItem(0,new ItemStack(Items.RAW_IRON,64));s.link(bank,f);
        var lamp=s.place(4,3,5,ModBlocks.PANEL_LIGHT.get());s.link(bank,lamp);s.warm(24);
        s.power(9);s.next();h.assertTrue(p.outputWatts()==1800&&!PowerNetworks.powered(s.l,f)&&!PowerNetworks.powered(s.l,lamp),"Life support gets all1.8kW despite its later position");
        h.assertTrue(PowerNetworks.monitorReading(s.l,p.getBlockPos()).shed().stream().anyMatch(load->load.kind()==3&&load.pos().equals(p.getBlockPos())),"Monitor identifies partially supplied PIXIE by its device kind");
        h.assertTrue(RoomOxygenManager.readingAt(s.l,s.anchor).mode()==OxygenStatus.Mode.STRAINED,"Unused nameplate capacity cannot hide an actual oxygen shortage");
        h.assertTrue(RoomOxygenManager.readingAt(s.l,s.anchor).supply()==1.8,"O2 capacity is actual paid partial output");
        s.power(20);s.next();h.assertTrue(p.outputWatts()==2000&&PowerNetworks.powered(s.l,f)&&!PowerNetworks.powered(s.l,lamp),"4kW serves2kW O2 before furnace; lamp waits");
        s.power(21);s.next();h.assertTrue(PowerNetworks.powered(s.l,lamp),"4.2kW serves all loads");
        var liveLinks=PowerNetworks.nearby(s.l,p.getBlockPos());s.l.setBlock(s.abs(13,3,4),ModBlocks.FROZEN_STONE_BRICKS.get().defaultBlockState(),3);
        h.assertTrue(PowerNetworks.nearby(s.l,p.getBlockPos()).equals(liveLinks),"Solid material conversion cannot erase live power beams or restart unrelated validation");
        store.setItem(0,PowerCellItem.charged(4100));s.power(0);s.next();h.assertTrue(p.outputWatts()==2000&&store.storedJoules()==2000,"Finite cell prepays2000J O2 and100J lamp, leaving2000J for actual furnace work");
        for(int i=0;i<20;i++){((net.minecraft.world.level.storage.ServerLevelData)s.l.getLevelData()).setGameTime(s.tick+i);((InductionFurnaceBlockEntity)s.l.getBlockEntity(f)).serverTick();}h.assertTrue(store.storedJoules()==0,"Twenty actual paid furnace ticks exhaust its2000J reservation");
        long retained=p.waterMicro();s.next();h.assertTrue(p.status()==PixieBlockEntity.NO_POWER&&p.outputWatts()==0&&p.waterMicro()==retained,"Empty backup stops oxygen with no water debit");
        h.assertTrue(RoomAtmosphere.hasAir(s.l,s.anchor),"Power loss does not evacuate retained sealed gas");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="pixie",timeoutTicks=100)
    public static void pixieFreezeThawWaterRatiosAndPaidHeat(GameTestHelper h){scene(h,s->{
        h.assertTrue(PixieBlockEntity.iceWater(new ItemStack(Items.SNOW_BLOCK))*4==PixieBlockEntity.iceWater(new ItemStack(Items.ICE))&&PixieBlockEntity.iceWater(new ItemStack(Items.PACKED_ICE))==9*PixieBlockEntity.iceWater(new ItemStack(Items.ICE))&&PixieBlockEntity.iceWater(new ItemStack(Items.BLUE_ICE))==81*PixieBlockEntity.iceWater(new ItemStack(Items.ICE)),"Snow/ice/packed/blue yield quarter/1/9/81 days");
        var p=s.pixie(4,4);var tank=s.tank(7,4,OxygenReservoirBlockEntity.capacityMicro()-20000);s.warm(.9);s.next();h.assertTrue(p.status()==PixieBlockEntity.FROZEN&&p.getItem(0).getCount()==2&&p.waterMicro()==0,"Below1C no thawing, ice loss or production");
        s.warm(24);double before=RoomThermalState.get(s.l).ledger.passive;s.next();RoomThermalManager.tickLevel(s.l);
        h.assertTrue(p.activity()==PixieBlockEntity.STORAGE,"Only actual tank filling is labeled Filling storage");
        h.assertTrue(p.outputWatts()==2000&&p.getItem(0).getCount()==1&&p.waterMicro()==11980000,"Warm room melts one ice and spends exactly2 breathing seconds");
        h.assertTrue(Math.abs(RoomThermalState.get(s.l).ledger.passive-before-500)<1e-6,"25percent of2kW is exactly500 raw waste heat: "+(RoomThermalState.get(s.l).ledger.passive-before));
        long water=p.waterMicro();s.warm(.9);s.next();h.assertTrue(p.outputWatts()==0&&p.waterMicro()==water,"Freezing preserves remaining water");
        var t=new CompoundTag();p.loadWithComponents(t,s.l.registryAccess());s.warm(24);s.next();h.assertTrue(p.status()==PixieBlockEntity.NO_WATER&&p.outputWatts()==0,"No ice/water cannot draw power or produce oxygen");
        p.setItem(0,new ItemStack(Items.SNOW_BLOCK));s.tankContents(tank,OxygenReservoirBlockEntity.capacityMicro()-20000);s.next();h.assertTrue(p.waterMicro()==2980000&&p.outputWatts()==2000,"Restocking automatically resumes the finite reservoir");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="pixie",timeoutTicks=100)
    public static void pixieUnsealedExplicitTankFillAndIsolation(GameTestHelper h){scene(h,s->{
        var p=s.pixie(4,4);var tank=s.tank(7,4,0);s.link(p.getBlockPos(),tank.getBlockPos());s.warm(24);
        var opening=s.abs(1,3,6);s.l.setBlock(opening,Blocks.AIR.defaultBlockState(),3);RoomAtmosphere.view(s.l,s.anchor);RoomAtmosphere.view(s.l,p.getBlockPos().above());s.next();
        h.assertTrue(p.status()==PixieBlockEntity.UNSEALED&&p.outputWatts()==4000&&tank.storedMicro()==40000,"Known unsealed warm intake fills only explicitly linked closed tank: status="+p.status()+", watts="+p.outputWatts()+", tank="+tank.storedMicro());
        h.assertTrue(RoomOxygenManager.readingAt(s.l,s.anchor).supply()==0&&RoomAtmosphere.inspect(s.l,s.anchor).seal()==RoomAtmosphere.Seal.OPEN&&RoomAtmosphere.cachedSealAt(s.l,p.getBlockPos().above())==RoomAtmosphere.Seal.OPEN,"Electrical/oxygen links do not supply or pressurize open room air");
        PowerNetworks.clear(s.l,tank.getBlockPos());long water=p.waterMicro(),stored=tank.storedMicro();s.next();h.assertTrue(p.outputWatts()==0&&p.waterMicro()==water&&tank.storedMicro()==stored,"Unlinked open intake neither vents nor wastes water");
        s.tankContents(tank,0);var remainder=new CompoundTag();remainder.putLong("Water",500);p.loadWithComponents(remainder,s.l.registryAccess());p.setItem(0,new ItemStack(Items.BLUE_ICE));s.link(p.getBlockPos(),tank.getBlockPos());s.next();
        h.assertTrue(tank.storedMicro()==40000&&p.waterMicro()==PixieBlockEntity.BLUE_ICE_WATER+500-40000&&p.getItem(0).isEmpty(),"Fractional remainder plus blue ice refills without a tank-only deadlock, discarded water or duplicate item");
        var absent=new BlockPos(1234567,200,1234567);PowerNetworks.linkedOxygenTanks(s.l,absent);h.assertTrue(!s.l.isLoaded(absent),"Tank link query never loads absent chunks");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="pixie",timeoutTicks=100)
    public static void pixieSavedContentsHarvestMenuAndRecipe(GameTestHelper h){scene(h,s->{
        var p=s.pixie(4,4);s.flames();s.warm(24);s.next();var saved=p.saveWithFullMetadata(s.l.registryAccess());long water=p.waterMicro();int ice=p.getItem(0).getCount();
        var copy=new PixieBlockEntity(p.getBlockPos(),p.getBlockState());copy.loadWithComponents(saved,s.l.registryAccess());h.assertTrue(copy.waterMicro()==water&&copy.getItem(0).getCount()==ice&&copy.outputWatts()==0&&copy.activity()==PixieBlockEntity.INACTIVE,"Save keeps exact water/ice but no fake production grants");
        var player=new FakePlayer(s.l,new GameProfile(UUID.randomUUID(),"pixie_menu"));player.setPos(p.getBlockPos().getCenter());var menu=new PixieMenu(1,player.getInventory(),p,p.getBlockPos());h.assertTrue(menu.slots.size()==37&&!menu.slots.getFirst().mayPlace(new ItemStack(Items.STONE))&&menu.slots.getFirst().mayPlace(new ItemStack(Items.BLUE_ICE)),"Only ice accepted in one input slot");
        var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());buf.writeBlockPos(p.getBlockPos());var client=new PixieMenu(1,player.getInventory(),buf);for(int i=0;i<9;i++){buf.clear();buf.writeShort(menu.data().get(i));client.data().set(i,buf.readShort());}buf.release();h.assertTrue(client.water()==water&&client.data().get(6)==p.activity()&&client.data().get(7)==menu.data().get(7)&&client.data().get(8)==menu.data().get(8),"Water, actual activity and temperatures survive menu transport");
        player.setPos(p.getBlockPos().east(12).getCenter());h.assertTrue(!menu.stillValid(player)&&menu.quickMoveStack(player,-1).isEmpty(),"Remote menu and invalid slot refused");
        p.loadWithComponents(saved,s.l.registryAccess());long energy=water;s.next(false);RoomOxygenManager.tickLevel(s.l);h.assertTrue(p.waterMicro()==energy,"Same-second save reconstruction cannot debit a second batch");
        var drops=Block.getDrops(p.getBlockState(),s.l,p.getBlockPos(),p);h.assertTrue(drops.size()==1&&drops.getFirst().getOrDefault(ModDataComponents.PIXIE_WATER.get(),0L)==water,"Harvest copies exact remaining water once");
        s.l.destroyBlock(p.getBlockPos(),false);h.assertTrue(!menu.stillValid(player),"Removed machine invalidates its menu");
        var target=s.abs(9,3,9);player.setPos(target.getCenter());var context=new net.minecraft.world.item.context.BlockPlaceContext(s.l,player,net.minecraft.world.InteractionHand.MAIN_HAND,drops.getFirst(),new net.minecraft.world.phys.BlockHitResult(target.below().getCenter().add(0,.5,0),Direction.UP,target.below(),false));
        h.assertTrue(((BlockItem)drops.getFirst().getItem()).place(context).consumesAction()&&((PixieBlockEntity)s.l.getBlockEntity(target)).waterMicro()==water&&((PixieBlockEntity)s.l.getBlockEntity(target)).getItem(0).isEmpty(),"Actual harvested-item placement restores exact water without cloned ice");
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);h.assertTrue(!menu.stillValid(player),"Spectator access refused");player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var dock=s.place(11,3,9,ModBlocks.CELL_DOCK.get());s.link(dock,target);var vessel=s.tank(9,11,0);s.link(target,vessel.getBlockPos());
        h.assertTrue(PowerLinkRules.validate(s.l,target,s.core)==PowerLinkRules.Result.DOCK_CONNECTION&&PowerLinkRules.validate(s.l,vessel.getBlockPos(),s.core)==PowerLinkRules.Result.DOCK_CONNECTION,"Neither PIXIE nor passive oxygen tank can bridge dock to infrastructure");
        var recipe=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.COPPER_INGOT),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.GLASS),new ItemStack(Items.BUCKET),new ItemStack(Items.REDSTONE_BLOCK),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.COPPER_INGOT),new ItemStack(Items.IRON_INGOT)));
        var out=s.l.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,recipe,s.l).orElseThrow().value().assemble(recipe,s.l.registryAccess());h.assertTrue(out.is(ModItems.PIXIE)&&out.getOrDefault(ModDataComponents.PIXIE_WATER.get(),0L)==0,"Native four iron/two copper/bucket/glass/redstone recipe crafts empty PIXIE");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="pixie",timeoutTicks=100)
    public static void pixieVacuumOxygenLossRecoveryAndPressure(GameTestHelper h){scene(h,62-h.absolutePos(BlockPos.ZERO).getY(),s->{
        var p=s.pixie(4,4);var second=s.pixie(6,4);s.warm(24);var phase=ApocalypseState.get(s.l.getServer());phase.setApocalypseTicks((long)(phase.getTotalDays()*24000L*.9),s.l.getServer());
        var cow=new Cow(EntityType.COW,s.l);cow.setNoAi(true);cow.setPos(s.abs(8,3,8).getCenter());s.l.addFreshEntity(cow);
        try{s.power(0);for(int i=0;i<13;i++)s.next();h.assertTrue(RoomOxygenState.get(s.l).unavailableAt(s.anchor)&&RoomAtmosphere.hasAir(s.l,s.anchor),"No-power O2 grace ends in NO O2 with gas/pressure retained");
            s.power(100);for(int i=0;i<4;i++)s.next();h.assertTrue(!RoomOxygenState.get(s.l).unavailableAt(s.anchor)&&p.outputWatts()+second.outputWatts()==5000,"Stable paid five-unit production recovers breathing automatically");
            RoomAirState.get(s.l).evacuate(RoomAtmosphere.view(s.l,s.anchor).geometry().cells());RoomThermalManager.tickLevel(s.l);s.warm(.9);
            long frozenWater=p.waterMicro()+second.waterMicro();s.next();
            h.assertTrue(p.status()==PixieBlockEntity.FROZEN&&second.status()==PixieBlockEntity.FROZEN&&p.waterMicro()+second.waterMicro()==frozenWater,"Physically cold airless reservoirs still freeze and consume nothing");
            s.warm(24);
            h.assertTrue(RoomThermalManager.temperatureAt(s.l,s.anchor).orElseThrow()<1&&RoomThermalManager.machineryTemperatureAt(s.l,s.anchor).orElseThrow()>1,"Real depleted room has warm physical structure despite a cold player exposure reading");
            s.next();h.assertTrue(p.outputWatts()+second.outputWatts()==1000&&!RoomAtmosphere.hasAir(s.l,s.anchor),"Warm airless machinery pays one shared priming unit without instantly fabricating pressure");
            h.assertTrue(frozenWater-p.waterMicro()-second.waterMicro()==10000,"Priming spends exact water even while player exposure remains cold");
            var viewer=new FakePlayer(s.l,new GameProfile(UUID.randomUUID(),"pixie_temperature"));var menu=new PixieMenu(2,viewer.getInventory(),p,p.getBlockPos());var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());buf.writeBlockPos(p.getBlockPos());var client=new PixieMenu(2,viewer.getInventory(),buf);
            for(int i=0;i<9;i++){buf.clear();buf.writeShort(menu.data().get(i));client.data().set(i,buf.readShort());}buf.release();
            h.assertTrue(client.data().get(7)>10&&client.data().get(8)<0&&client.data().get(8)!=Short.MIN_VALUE,"UI distinguishes warm machine from cold room feel through actual signed-short transport");
            for(int i=0;i<9;i++){s.next();RoomThermalManager.tickLevel(s.l);RoomAtmosphere.hasAir(s.l,s.anchor);}h.assertTrue(RoomAtmosphere.hasAir(s.l,s.anchor),"Paid warm-wall priming restores pressure through the normal delay with the actual thermal tick");
        }finally{cow.discard();}
    });}
    @GameTest(template=GameTestTemplates.EMPTY,batch="pixie",timeoutTicks=100)
    public static void pixieReplayFunctionsParse(GameTestHelper h){for(var name:List.of("load","tick","setup","controls","basic","backup","brownout","cold","warm","breach","seal","tank"))h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("pixie_check",name)).isPresent(),"Native parser loads PIXIE helper "+name);h.succeed();}
    private static final class Scene {
        final GameTestHelper h;final ServerLevel l;final BlockPos core,anchor;final int yOffset;final List<BlockPos> campfires=new ArrayList<>();long tick;
        Scene(GameTestHelper h,int yOffset){this.h=h;this.yOffset=yOffset;l=h.getLevel();tick=l.getGameTime();core=abs(1,3,4);anchor=abs(5,4,6);}
        BlockPos abs(int x,int y,int z){return h.absolutePos(new BlockPos(x,y+yOffset,z));}
        BlockPos place(int x,int y,int z,Block b){var p=abs(x,y,z);l.setBlock(p,b.defaultBlockState(),3);if(l.getBlockEntity(p)!=null)l.getBlockEntity(p).onLoad();return p;}
        void power(int percent){var c=(GeothermalCoreBlockEntity)l.getBlockEntity(core);c.setPowerShare(percent);c.setHeatShare(100-percent);}
        void link(BlockPos a,BlockPos b){var result=PowerNetworks.link(l,a,b);h.assertTrue(result==PowerLinkRules.Result.OK,"Fixture Binder link must pass: "+a+" -> "+b+" result="+result);}
        PixieBlockEntity pixie(int x,int z){var p=place(x,3,z,ModBlocks.PIXIE.get());var be=(PixieBlockEntity)l.getBlockEntity(p);be.setItem(0,new ItemStack(Items.ICE,2));link(core,p);return be;}
        OxygenReservoirBlockEntity tank(int x,int z,long stored){var p=place(x,3,z,ModBlocks.OXYGEN_RESERVOIR.get());var t=(OxygenReservoirBlockEntity)l.getBlockEntity(p);tankContents(t,stored);return t;}
        void tankContents(OxygenReservoirBlockEntity t,long n){var tag=new CompoundTag();tag.putLong("StoredOxygen",n);t.loadWithComponents(tag,l.registryAccess());}
        void flames(){for(int x=7;x<=8;x++){var p=place(x,3,6,Blocks.CAMPFIRE);campfires.add(p);}}
        void warm(double c){RoomAtmosphere.view(l,anchor);RoomThermalManager.temperatureAt(l,anchor);var heat=RoomThermalState.get(l);for(var r:heat.rooms.values())if(r.cells.contains(anchor)){r.airEnergy=RoomHeatMath.energy(r.airCapacity(),c);for(var m:r.structure){var v=heat.materials.get(m);if(v!=null)v.energy=RoomHeatMath.energy(v.capacity,c);}}}
        void next(){next(true);}
        void next(boolean advance){if(advance)tick+=20;((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(tick);RoomAtmosphere.tickLevel(l);RoomAtmosphere.view(l,anchor);RoomAtmosphere.view(l,abs(4,4,4));for(int i=0;i<3;i++)RoomOxygenManager.tickLevel(l);}
    }
    private static void scene(GameTestHelper h,java.util.function.Consumer<Scene> test){scene(h,0,test);}
    private static void scene(GameTestHelper h,int yOffset,java.util.function.Consumer<Scene> test){
        var s=new Scene(h,yOffset);var l=s.l;var phase=ApocalypseState.get(l.getServer());long oldPhase=phase.getApocalypseTicks(),clock=l.getGameTime();
        var identity=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());var air=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());var oxygen=RoomOxygenState.get(l).save(new CompoundTag(),l.registryAccess());var heat=RoomThermalState.get(l).save(new CompoundTag(),l.registryAccess());var transfer=OxygenTransferState.get(l).save(new CompoundTag(),l.registryAccess());var links=PowerNetworkSavedData.get(l).save(new CompoundTag(),l.registryAccess());
        try{phase.setApocalypseTicks(0,l.getServer());RoomAtmosphere.reset();RoomOxygenManager.reset();RoomThermalManager.reset();CombustionAtmosphere.reset();PowerNetworks.stopped(null);
            l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());l.getDataStorage().set(RoomAirState.NAME,new RoomAirState());l.getDataStorage().set(RoomOxygenState.NAME,new RoomOxygenState());l.getDataStorage().set(RoomThermalState.NAME,new RoomThermalState());l.getDataStorage().set(OxygenTransferState.NAME,new OxygenTransferState());l.getDataStorage().set(PowerNetworkSavedData.NAME,new PowerNetworkSavedData());
            for(int x=1;x<=13;x++)for(int y=2;y<=6;y++)for(int z=1;z<=13;z++)l.setBlock(s.abs(x,y,z),(x==1||x==13||y==2||y==6||z==1||z==13?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
            s.place(1,3,4,ModBlocks.GEOTHERMAL_CORE.get());s.power(100);test.accept(s);h.succeed();
        }finally{
            for(int x=1;x<=13;x++)for(int y=2;y<=6;y++)for(int z=1;z<=13;z++)l.setBlock(s.abs(x,y,z),Blocks.AIR.defaultBlockState(),2);
            phase.setApocalypseTicks(oldPhase,l.getServer());((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);RoomAtmosphere.reset();RoomOxygenManager.reset();RoomThermalManager.reset();CombustionAtmosphere.reset();AtmosphericBreach.reset();PowerNetworks.stopped(null);
            l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(identity,l.registryAccess()));l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(air,l.registryAccess()));l.getDataStorage().set(RoomOxygenState.NAME,RoomOxygenState.load(oxygen,l.registryAccess()));l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(heat,l.registryAccess()));l.getDataStorage().set(OxygenTransferState.NAME,OxygenTransferState.load(transfer,l.registryAccess()));l.getDataStorage().set(PowerNetworkSavedData.NAME,PowerNetworkSavedData.load(links,l.registryAccess()));
        }
    }
}
