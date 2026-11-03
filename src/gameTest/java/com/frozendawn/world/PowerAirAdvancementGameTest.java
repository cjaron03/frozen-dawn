package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.airlock.AirlockSavedState;
import com.frozendawn.block.*;
import com.frozendawn.data.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.oxygen.OxygenControlledFurnace;
import com.frozendawn.power.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerAirAdvancementGameTest {
    @BeforeBatch(batch="power_air_advancements") public static void report(ServerLevel level){GameTestReporting.installReporter(level);}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_air_advancements",timeoutTicks=100)
    public static void powerAirDefinitionsLoadAsFourHiddenJokes(GameTestHelper h){withScene(h,s->{
        for(var id:List.of(PowerAirAdvancements.BROWNOUT,PowerAirAdvancements.UNLIMITED,PowerAirAdvancements.CANARY,PowerAirAdvancements.COW)){
            var holder=s.level.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,id));
            h.assertTrue(holder!=null&&holder.value().display().orElseThrow().isHidden(),"Hidden joke loads: "+id);
        }
        h.assertTrue(s.level.getServer().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"orsa_emergency_light")).isPresent(),"Emergency Light is craftable");
        h.assertTrue(ModBlocks.EMERGENCY_LIGHT.get().defaultBlockState().hasProperty(EmergencyLightBlock.POWER_STAGE)
                &&!ModBlocks.ORSA_EMERGENCY_LIGHT.get().equals(ModBlocks.EMERGENCY_LIGHT.get()),"Legacy ruin light keeps its ID and phase-driven behavior");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_air_advancements",timeoutTicks=100)
    public static void compactEmergencyLightMountsWithoutSealingRooms(GameTestHelper h){withScene(h,s->{
        var player=s.player(s.center.south(2));var support=s.center;var block=ModBlocks.ORSA_EMERGENCY_LIGHT.get();
        for(var face:Direction.values()){
            var pos=support.relative(face);s.block(support,Blocks.STONE);s.block(pos,Blocks.AIR);
            var item=new ItemStack(block.asItem());
            var hit=new net.minecraft.world.phys.BlockHitResult(support.getCenter().relative(face,.5),face,support,false);
            var context=new net.minecraft.world.item.context.BlockPlaceContext(s.level,player,net.minecraft.world.InteractionHand.MAIN_HAND,item,hit);
            h.assertTrue(((BlockItem)item.getItem()).place(context).consumesAction(),"Actual compact lamp placement succeeds on "+face);
            var state=s.level.getBlockState(pos);var bounds=state.getShape(s.level,pos).bounds();
            h.assertTrue(state.is(block)&&state.getValue(OrsaEmergencyLightBlock.FACING)==face,"Lens faces away from clicked surface: "+face);
            h.assertTrue(bounds.getXsize()<=.5&&bounds.getYsize()<=.5&&bounds.getZsize()<=.5,"Selection stays within the compact housing on "+face);
            h.assertTrue(state.getCollisionShape(s.level,pos).isEmpty()&&RoomAtmosphere.isPassage(s.level,pos,state),"Small fixture cannot seal a breached wall: "+face);
            var saved=net.minecraft.nbt.NbtUtils.writeBlockState(state);
            h.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(s.level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),saved).equals(state),"Mounted orientation survives saved-state codec");
            s.block(pos,Blocks.AIR);
        }
        var old=net.minecraft.nbt.NbtUtils.writeBlockState(block.defaultBlockState().setValue(OrsaEmergencyLightBlock.LIT,true));
        old.getCompound("Properties").remove("facing");
        var loaded=net.minecraft.nbt.NbtUtils.readBlockState(s.level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),old);
        h.assertTrue(loaded.getValue(OrsaEmergencyLightBlock.FACING)==Direction.UP&&loaded.getValue(OrsaEmergencyLightBlock.LIT),"Existing palettes without facing keep their light state and default upright");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_air_advancements",timeoutTicks=100)
    public static void unlimitedPowerRequiresValidCoreMenuAction(GameTestHelper h){withScene(h,s->{
        var core=s.core();var player=s.player(s.center);var menu=new GeothermalCoreMenu(1,player.getInventory(),core);
        core.setPowerShare(100);h.assertTrue(!earned(player,PowerAirAdvancements.UNLIMITED),"Load/automation cannot award a player");
        core.resetAllocation();h.assertTrue(menu.clickMenuButton(player,299)&&!earned(player,PowerAirAdvancements.UNLIMITED),"99 percent is not unlimited");
        player.setPos(s.center.east(14).getCenter());h.assertTrue(!menu.clickMenuButton(player,300)&&!earned(player,PowerAirAdvancements.UNLIMITED),"Out-of-range packet cannot award");
        player.setPos(s.center.getCenter());player.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!menu.clickMenuButton(player,300)&&!earned(player,PowerAirAdvancements.UNLIMITED),"Spectators cannot allocate or earn");
        player.setGameMode(GameType.SURVIVAL);h.assertTrue(menu.clickMenuButton(player,300)&&core.getPowerShare()==100&&earned(player,PowerAirAdvancements.UNLIMITED),"Actual valid full-power action awards its actor");
        var other=s.player(s.center.east());h.assertTrue(!earned(other,PowerAirAdvancements.UNLIMITED),"Nearby bystander is not the Core actor");
        s.block(core.getBlockPos(),Blocks.STONE);s.block(core.getBlockPos(),ModBlocks.GEOTHERMAL_CORE.get());
        h.assertTrue(!menu.clickMenuButton(other,300)&&!earned(other,PowerAirAdvancements.UNLIMITED),"Stale menu cannot grant against replacement Core");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_air_advancements",timeoutTicks=100)
    public static void emergencyLightBacksUpForTenMinutesAndKeepsItsReserve(GameTestHelper h){withScene(h,s->{
        var core=s.core();core.setPowerShare(100);var panel=s.center.east(2);var pos=s.center.north(2);
        s.block(panel,ModBlocks.PANEL_LIGHT.get());s.block(pos,ModBlocks.ORSA_EMERGENCY_LIGHT.get());var lamp=(OrsaEmergencyLightBlockEntity)s.level.getBlockEntity(pos);
        h.assertTrue(lamp.backupTicks()==0,"Fresh placement cannot invent charged backup");link(h,s,core.getBlockPos(),panel);link(h,s,core.getBlockPos(),pos);PowerNetworks.update(s.level);PowerNetworks.account(s.level);s.step();lamp.serverTick();
        h.assertTrue(lamp.backupTicks()==12000&&!s.level.getBlockState(pos).getValue(OrsaEmergencyLightBlock.LIT),"Healthy grid arms ten minutes, normally dark");
        h.assertTrue(PowerNetworks.monitorReading(s.level,pos).demandWatts()==100,"Reserve lamp adds zero grid draw");
        core.setPowerShare(0);PowerNetworks.account(s.level);s.step();lamp.serverTick();
        h.assertTrue(s.level.getBlockState(pos).getValue(OrsaEmergencyLightBlock.LIT)&&s.level.getBlockState(pos).getLightEmission(s.level,pos)==6
                &&!s.level.getBlockState(panel).getValue(PanelLightBlock.LIT),"Paid panel drops out while dim red backup illuminates");
        int before=lamp.backupTicks();lamp.serverTick();h.assertTrue(lamp.backupTicks()==before,"Duplicate callback cannot double-spend backup time");
        for(int i=0;i<399;i++){s.step();lamp.serverTick();}
        var copy=new OrsaEmergencyLightBlockEntity(pos,s.level.getBlockState(pos));copy.loadWithComponents(lamp.saveWithFullMetadata(s.level.registryAccess()),s.level.registryAccess());s.level.setBlockEntity(copy);
        h.assertTrue(copy.backupTicks()==11600,"Actual saved codec retains spent reserve across replacement/load");
        for(int i=0;i<11600;i++){s.step();copy.serverTick();}
        h.assertTrue(copy.backupTicks()==0&&!s.level.getBlockState(pos).getValue(OrsaEmergencyLightBlock.LIT),"Exactly ten minutes of loaded outage exhausts the light");
        s.step();copy.serverTick();h.assertTrue(copy.backupTicks()==0,"Unpowered empty light cannot recharge itself");
        core.setPowerShare(100);PowerNetworks.account(s.level);s.step();copy.serverTick();h.assertTrue(copy.backupTicks()==12000&&!s.level.getBlockState(pos).getValue(OrsaEmergencyLightBlock.LIT),"Recovered supply re-arms without phantom light demand");
        var bad=new CompoundTag();bad.putInt("BackupTicks",Integer.MAX_VALUE);copy.loadWithComponents(bad,s.level.registryAccess());h.assertTrue(copy.backupTicks()==12000,"Corrupt reserve cannot exceed its cap");
        bad.putInt("BackupTicks",-10);copy.loadWithComponents(bad,s.level.registryAccess());h.assertTrue(copy.backupTicks()==0,"Negative reserve is empty");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_air_advancements",timeoutTicks=100)
    public static void emergencyBrownoutAwardsOnlyAffectedBasePlayers(GameTestHelper h){withScene(h,s->{
        var core=s.core();var panel=s.center.east(2);var pos=s.center.north(2);
        s.block(panel,ModBlocks.PANEL_LIGHT.get());s.block(pos,ModBlocks.ORSA_EMERGENCY_LIGHT.get());
        var inside=s.player(s.center);var outside=s.player(s.center.east(5));var observer=s.player(s.center.south());observer.setGameMode(GameType.SPECTATOR);
        RoomAtmosphere.view(s.level,s.center);link(h,s,core.getBlockPos(),panel);link(h,s,core.getBlockPos(),pos);PowerNetworks.update(s.level);PowerNetworks.account(s.level);
        var lamp=(OrsaEmergencyLightBlockEntity)s.level.getBlockEntity(pos);s.step();lamp.serverTick();
        h.assertTrue(!s.level.getBlockState(pos).getValue(OrsaEmergencyLightBlock.LIT)&&!earned(inside,PowerAirAdvancements.BROWNOUT),"Empty reserve cannot award a fake emergency-light transition");
        core.setPowerShare(100);PowerNetworks.account(s.level);s.step();lamp.serverTick();h.assertTrue(!earned(inside,PowerAirAdvancements.BROWNOUT),"Healthy grid cannot grant brownout");
        var machine=s.center.north();s.block(machine,ModBlocks.INDUCTION_FURNACE.get());
        var furnace=(InductionFurnaceBlockEntity)s.level.getBlockEntity(machine);furnace.onLoad();furnace.setItem(0,new ItemStack(Items.RAW_IRON));
        link(h,s,core.getBlockPos(),machine);core.setPowerShare(10);PowerNetworks.update(s.level);PowerNetworks.account(s.level);s.step();lamp.serverTick();
        h.assertTrue(PowerNetworks.monitorReading(s.level,pos).generationWatts()==2000&&PowerNetworks.powered(s.level,machine)
                &&!s.level.getBlockState(panel).getValue(PanelLightBlock.LIT)&&s.level.getBlockState(pos).getValue(OrsaEmergencyLightBlock.LIT),
                "A real 2 kW brownout powers the furnace but sheds the 100 W panel and activates reserve lighting");
        h.assertTrue(RoomAtmosphere.cachedSealAt(s.level,s.center)==RoomAtmosphere.Seal.UNKNOWN&&!earned(inside,PowerAirAdvancements.BROWNOUT),
                "Adding the furnace invalidates geometry; the advancement hook must not discover or heal the room");
        RoomAtmosphere.view(s.level,s.center);
        ((net.minecraft.world.level.storage.ServerLevelData)s.level.getLevelData()).setGameTime((s.level.getGameTime()/20+1)*20);lamp.serverTick();
        h.assertTrue(earned(inside,PowerAirAdvancements.BROWNOUT)&&!earned(outside,PowerAirAdvancements.BROWNOUT)&&!earned(observer,PowerAirAdvancements.BROWNOUT),"Actual emergency lighting credits this verified base, not through walls or spectator mode: seal="+RoomAtmosphere.cachedSealAt(s.level,s.center)+" inside="+earned(inside,PowerAirAdvancements.BROWNOUT)+" outside="+earned(outside,PowerAirAdvancements.BROWNOUT)+" spectator="+earned(observer,PowerAirAdvancements.BROWNOUT));
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_air_advancements",timeoutTicks=100)
    public static void canaryAwardsOnlyForRealOxygenStarvation(GameTestHelper h){withScene(h,s->{
        s.core();var player=s.player(s.center);var outside=s.player(s.center.east(5));var observer=s.player(s.center.east());observer.setGameMode(GameType.SPECTATOR);
        s.furnace(s.center.south(2));s.startVacuum();s.oxygen();h.assertTrue(!earned(player,PowerAirAdvancements.CANARY),"Supported burning furnace cannot award");
        var idle=s.center.north(2);s.block(idle,Blocks.FURNACE);s.oxygen();h.assertTrue(!earned(player,PowerAirAdvancements.CANARY),"Unlit/empty furnace cannot award");
        s.furnace(s.center.east(2));s.furnace(s.center.north(2));s.ready();
        for(int i=0;i<12;i++)s.oxygen();
        h.assertTrue(s.blocks.stream().filter(p->s.level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity)
                .anyMatch(p->((OxygenControlledFurnace)s.level.getBlockEntity(p)).frozendawn$isOxygenStarved()),"Actual O2 allocator pauses a fueled furnace after grace");
        h.assertTrue(earned(player,PowerAirAdvancements.CANARY)&&!earned(outside,PowerAirAdvancements.CANARY)&&!earned(observer,PowerAirAdvancements.CANARY),"Only affected nonspectator participants receive Canary");
        h.assertTrue(!earned(player,PowerAirAdvancements.COW),"Combustion shortage without cows is not Holy Cow");
    });}
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_air_advancements",timeoutTicks=100)
    public static void holyCowRequiresCowToCauseTheShortage(GameTestHelper h){withScene(h,s->{
        var core=s.core();var player=s.player(s.center);var outside=s.player(s.center.east(5));var observer=s.player(s.center.east());observer.setGameMode(GameType.SPECTATOR);
        var cow=new Cow(EntityType.COW,s.level);cow.setNoAi(true);cow.setPos(s.center.south().getCenter());s.level.addFreshEntity(cow);s.entities.add(cow);
        s.startVacuum();s.oxygen();h.assertTrue(!earned(player,PowerAirAdvancements.COW),"One cow plus one player fit within eight supply");
        s.furnace(s.center.south(2));RoomAtmosphere.view(s.level,s.center);
        VacuumFlames.loaded(new net.neoforged.neoforge.event.level.ChunkEvent.Load(s.level.getChunkAt(s.center),false));s.oxygen();
        h.assertTrue(!earned(player,PowerAirAdvancements.COW),"Pending chunk accounting is not evidence of a cow shortage");
        s.ready();s.oxygen();h.assertTrue(earned(player,PowerAirAdvancements.COW)&&!earned(outside,PowerAirAdvancements.COW)&&!earned(observer,PowerAirAdvancements.COW),"Five-unit cow tips four other demand over eight and credits only this pool");
        revoke(player,PowerAirAdvancements.COW);s.furnace(s.center.east(2));s.furnace(s.center.north(2));s.ready();s.oxygen();
        h.assertTrue(!earned(player,PowerAirAdvancements.COW),"Existing ten-unit noncow overload cannot be blamed on the cow");
        core.setPowerShare(100);s.oxygen();h.assertTrue(!earned(player,PowerAirAdvancements.COW),"Zero source output is not a cow-caused shortage");
    });}
    private static boolean earned(ServerPlayer player,String id){var holder=player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,id));return holder!=null&&player.getAdvancements().getOrStartProgress(holder).isDone();}
    private static void revoke(ServerPlayer player,String id){var holder=player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,id));for(var criterion:holder.value().criteria().keySet())player.getAdvancements().revoke(holder,criterion);}
    private static void link(GameTestHelper h,Scene scene,BlockPos a,BlockPos b){h.assertTrue(PowerNetworks.link(scene.level,a,b)==PowerLinkRules.Result.OK,"Valid fixture link");}
    private static final class Scene {
        final GameTestHelper helper;final ServerLevel level;final BlockPos center;final List<BlockPos> blocks=new ArrayList<>();final List<Entity> entities=new ArrayList<>();
        Scene(GameTestHelper helper){this.helper=helper;level=helper.getLevel();center=helper.absolutePos(new BlockPos(8,3,8));}
        void block(BlockPos pos,Block block){level.setBlock(pos,block.defaultBlockState(),3);if(!blocks.contains(pos))blocks.add(pos);}
        GeothermalCoreBlockEntity core(){var p=center.west(2);block(p,ModBlocks.GEOTHERMAL_CORE.get());var core=(GeothermalCoreBlockEntity)level.getBlockEntity(p);core.onLoad();return core;}
        ServerPlayer player(BlockPos pos){
            var profile=new GameProfile(UUID.randomUUID(),"power_air_"+entities.size());var transport=new net.neoforged.neoforge.common.util.FakePlayer(level,profile);
            var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());player.connection=transport.connection;player.setPos(pos.getCenter());player.setGameMode(GameType.SURVIVAL);level.addNewPlayer(player);entities.add(player);return player;
        }
        void furnace(BlockPos pos){block(pos,Blocks.FURNACE);var furnace=(AbstractFurnaceBlockEntity)level.getBlockEntity(pos);furnace.setItem(0,new ItemStack(Items.RAW_IRON,8));furnace.setItem(1,new ItemStack(Items.COAL,8));level.setBlock(pos,level.getBlockState(pos).setValue(BlockStateProperties.LIT,true),3);}
        void step(){((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);}
        void startVacuum(){RoomAtmosphere.view(level,center);ApocalypseState.get(level.getServer()).setApocalypseTicks((long)(ApocalypseState.get(level.getServer()).getTotalDays()*24000L*.90),level.getServer());CombustionAtmosphere.reset();ready();}
        void ready(){
            var geometry=RoomAtmosphere.view(level,center).geometry();var chunks=new HashSet<Long>();for(var p:geometry.cells())chunks.add(new ChunkPos(p).toLong());for(var p:geometry.walls())chunks.add(new ChunkPos(p).toLong());
            for(int i=0;i<512&&!Collections.disjoint(chunks,VacuumFlames.snapshot(level).pendingChunks());i++)VacuumFlames.tick(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(()->true,level.getServer()));
            helper.assertTrue(Collections.disjoint(chunks,VacuumFlames.snapshot(level).pendingChunks()),"Fixture's cell and wall chunks are indexed");
        }
        void oxygen(){((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+20);RoomAtmosphere.view(level,center);RoomOxygenManager.tickLevel(level);}
    }
    private static void withScene(GameTestHelper helper,java.util.function.Consumer<Scene> action){
        var s=new Scene(helper);var l=s.level;long clock=l.getGameTime();var phase=ApocalypseState.get(l.getServer());long old=phase.getApocalypseTicks();
        var identity=RoomIdentityState.get(l);var air=RoomAirState.get(l);var oxygen=RoomOxygenState.get(l);var transfers=OxygenTransferState.get(l);var heat=RoomThermalState.get(l);var power=PowerNetworkSavedData.get(l);var chambers=AirlockSavedState.get(l);
        try{
            l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());l.getDataStorage().set(RoomAirState.NAME,new RoomAirState());l.getDataStorage().set(RoomOxygenState.NAME,new RoomOxygenState());l.getDataStorage().set(OxygenTransferState.NAME,new OxygenTransferState());l.getDataStorage().set(RoomThermalState.NAME,new RoomThermalState());l.getDataStorage().set(PowerNetworkSavedData.NAME,new PowerNetworkSavedData());l.getDataStorage().set("frozendawn_airlocks",new AirlockSavedState());
            phase.setApocalypseTicks(0,l.getServer());RoomAtmosphere.reset();RoomOxygenManager.reset();RoomThermalManager.reset();CombustionAtmosphere.reset();PowerNetworks.stopped(null);
            for(int x=-3;x<=3;x++)for(int y=-1;y<=3;y++)for(int z=-3;z<=3;z++)l.setBlock(s.center.offset(x,y,z),(Math.abs(x)==3||Math.abs(z)==3||y==-1||y==3?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
            action.accept(s);helper.succeed();
        }finally{
            s.entities.forEach(Entity::discard);for(var p:s.blocks)l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
            phase.setApocalypseTicks(old,l.getServer());((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);RoomAtmosphere.reset();RoomOxygenManager.reset();RoomThermalManager.reset();CombustionAtmosphere.reset();AtmosphericBreach.reset();PowerNetworks.stopped(null);
            l.getDataStorage().set(RoomIdentityState.NAME,identity);l.getDataStorage().set(RoomAirState.NAME,air);l.getDataStorage().set(RoomOxygenState.NAME,oxygen);l.getDataStorage().set(OxygenTransferState.NAME,transfers);l.getDataStorage().set(RoomThermalState.NAME,heat);l.getDataStorage().set(PowerNetworkSavedData.NAME,power);l.getDataStorage().set("frozendawn_airlocks",chambers);
        }
    }
}
