package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.GeothermalCoreBlockEntity;
import com.frozendawn.block.GeothermalCoreMenu;
import com.frozendawn.block.PanelLightBlock;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerFlowGameTest {
    @BeforeBatch(batch="power_flow")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch="power_flow_cadence")
    public static void reportCadence(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_flow",timeoutTicks=100)
    public static void corePowerSplitPreservesLegacySavesAndMenuSecurity(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var pos=place(h,3,2,3,ModBlocks.GEOTHERMAL_CORE.get());
            var core=(GeothermalCoreBlockEntity)level.getBlockEntity(pos);
            var old=core.saveWithFullMetadata(level.registryAccess());old.remove("PowerShare");old.remove("HeatBalance");old.putInt("HeatShare",80);
            old.putInt("RangeLevel",12);old.putInt("TempLevel",6);old.putInt("O2Level",2);
            core.loadWithComponents(old,level.registryAccess());
            h.assertTrue(core.getHeatShare()==80&&core.getOxygenShare()==20&&core.getPowerShare()==0,
                    "A legacy two-way split keeps its exact settings and receives no free electricity");
            core.getItems().set(0,new ItemStack(Items.OBSIDIAN,3));
            core.resetAllocation();double defaultHeat=core.getHeatOutput();core.setPowerShare(25);
            h.assertTrue(core.getHeatShare()==45&&core.getOxygenShare()==30&&core.getPowerShare()==25,
                    "25 percent power preserves the 60/40 heat/O2 balance as 45/30/25");
            near(h,core.getHeatOutput(),defaultHeat*.75,"Power allocation really reduces heat input");
            near(h,core.getOxygenSupply(),6,"Power allocation really reduces shared oxygen supply");
            near(h,core.getPowerOutputKw(),5,"25 percent produces 5 kW");
            core.resetAllocation();for(int share=1;share<=75;share++)core.setPowerShare(share);
            h.assertTrue(core.getHeatShare()==15&&core.getOxygenShare()==10,"75 percent follows the specified 15/10/75 split");
            var saved=core.saveWithFullMetadata(level.registryAccess());var copy=new GeothermalCoreBlockEntity(pos,core.getBlockState());copy.loadWithComponents(saved,level.registryAccess());
            h.assertTrue(copy.getPowerShare()==75&&copy.getHeatShare()==15&&copy.getItems().get(0).getCount()==3
                    &&copy.getEffectiveRange()==24&&copy.getEffectiveTemp()==80&&copy.getEffectiveO2Range()==26,
                    "Three-way share, existing upgrades and inventory all survive serialization");
            copy.loadWithComponents(core.getUpdateTag(level.registryAccess()),level.registryAccess());
            h.assertTrue(copy.getPowerShare()==75&&core.getData().getCount()==9&&core.getData().get(7)==1500,
                    "Client update tag and menu report actual kW");
            var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"power_core_menu"));player.setPos(pos.east(2).getCenter());
            var menu=new GeothermalCoreMenu(1,player.getInventory(),core);
            h.assertTrue(menu.clickMenuButton(player,225)&&core.getPowerShare()==25,"Real menu sets power server-side");
            h.assertTrue(!menu.clickMenuButton(player,101)&&!menu.clickMenuButton(player,301)&&!menu.clickMenuButton(player,80),
                    "Invalid buttons and heat beyond the remaining budget are rejected");
            h.assertTrue(menu.clickMenuButton(player,400)&&core.getHeatShare()==60&&core.getOxygenShare()==40&&core.getPowerShare()==0,
                    "Default resets all three shares");
            player.setPos(pos.east(12).getCenter());h.assertTrue(!menu.clickMenuButton(player,225),"Remote player cannot allocate power");
            player.setPos(pos.east(2).getCenter());h.setBlock(new BlockPos(3,2,3),Blocks.STONE);h.setBlock(new BlockPos(3,2,3),ModBlocks.GEOTHERMAL_CORE.get());
            h.assertTrue(!menu.clickMenuButton(player,225),"Stale menu cannot change a replacement Core");
            saved.putInt("PowerShare",999);saved.putInt("HeatShare",999);copy.loadWithComponents(saved,level.registryAccess());
            h.assertTrue(copy.getPowerShare()==100&&copy.getHeatShare()==0&&copy.getOxygenShare()==0,"Corrupt saved shares cannot exceed the fixed budget");
            near(h,copy.getPowerOutputKw(),20,"Full power has a fixed 20 kW rating");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_flow_cadence",timeoutTicks=200)
    public static void coreRelayPanelRespondsOnRealServerTicks(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var level=h.getLevel();var source=place(h,1,2,2,ModBlocks.GEOTHERMAL_CORE.get());
            var a=place(h,7,2,2,ModBlocks.POWER_RELAY.get());var b=place(h,13,2,2,ModBlocks.POWER_RELAY.get());
            var lamp=place(h,19,2,2,ModBlocks.PANEL_LIGHT.get());var core=(GeothermalCoreBlockEntity)level.getBlockEntity(source);
            link(h,source,a);link(h,a,b);link(h,b,lamp);
            h.runAfterDelay(25, () -> {
                h.assertTrue(!lit(level,lamp)&&core.getPowerShare()==0,"Default allocation powers no light");core.setPowerShare(25);
                h.runAfterDelay(25, () -> {
                    h.assertTrue(lit(level,lamp)&&level.getBlockState(lamp).getLightEmission(level,lamp)==15,
                            "Ordinary server accounting lights the panel at level 15; state="+level.getBlockState(lamp)+" reading="+PowerNetworks.reading(level,lamp)+" power="+core.getPowerShare()+" rules="+List.of(PowerLinkRules.validate(level,source,a),PowerLinkRules.validate(level,a,b),PowerLinkRules.validate(level,b,lamp)));
                    var r=PowerNetworks.reading(level,lamp);near(h,r.supplyKw(),5,"Binder supply is real");near(h,r.demandKw(),.1,"Panel draws 0.1 kW");
                    h.assertTrue(r.nodes()==4&&r.storedKj()==0,"Accounted chain counts each node and has no invented storage");core.setPowerShare(0);
                    h.runAfterDelay(25, () -> {
                        h.assertTrue(!lit(level,lamp),"Zeroing Core power turns the light off");core.setPowerShare(25);
                        h.runAfterDelay(25, () -> {
                            h.assertTrue(lit(level,lamp),"Restored power automatically relights the panel");h.setBlock(new BlockPos(13,2,2),Blocks.AIR);
                            h.runAfterDelay(25, () -> {
                                h.assertTrue(!lit(level,lamp)&&PowerNetworkSavedData.get(level).degree(b)==0,
                                        "Breaking a real relay cuts delivery and erases its incident links");
                                h.setBlock(new BlockPos(13,2,2),ModBlocks.POWER_RELAY.get());link(h,a,b);link(h,b,lamp);
                                h.runAfterDelay(25, () -> {h.assertTrue(lit(level,lamp),"Rebinding the replacement restores delivery");h.succeed();});
                            });
                        });
                    });
                });
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_flow",timeoutTicks=100)
    public static void powerBrownoutCyclesAndMultipleSourcesDoNotDuplicateSupply(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();var a=place(h,3,2,3,ModBlocks.GEOTHERMAL_CORE.get());var b=place(h,13,2,3,ModBlocks.GEOTHERMAL_CORE.get());
            var relay=place(h,8,2,3,ModBlocks.POWER_RELAY.get());var lamps=new ArrayList<BlockPos>();
            for(int z:List.of(6,9,12)) {var lamp=place(h,8,2,z,ModBlocks.PANEL_LIGHT.get());lamps.add(lamp);link(h,relay,lamp);}
            link(h,a,relay);link(h,b,relay);link(h,a,b);
            var first=(GeothermalCoreBlockEntity)level.getBlockEntity(a);var second=(GeothermalCoreBlockEntity)level.getBlockEntity(b);
            first.setPowerShare(1);allocate(level);
            var reading=PowerNetworks.reading(level,relay);near(h,reading.supplyKw(),.2,"Cycle cannot multiply source power");near(h,reading.demandKw(),.3,"All demand remains visible even when a lamp is shed");
            h.assertTrue(lamps.stream().filter(p->lit(level,p)).count()==2&&!lit(level,lamps.get(2)),"0.2 kW supports exactly two 0.1 kW lights in stable order");
            var balance=List.of(first.getHeatShare(),first.getOxygenShare(),first.getPowerShare());allocate(level);
            h.assertTrue(balance.equals(List.of(first.getHeatShare(),first.getOxygenShare(),first.getPowerShare())),"Brownout never silently reallocates the Core");
            second.setPowerShare(1);allocate(level);near(h,PowerNetworks.reading(level,relay).supplyKw(),.4,"Independent sources add once each");
            h.assertTrue(lamps.stream().allMatch(p->lit(level,p)),"Sufficient restored supply relights all shed loads");
            PowerNetworks.clear(level,a);allocate(level);
            near(h,PowerNetworks.reading(level,relay).supplyKw(),.2,"Disconnected source contributes no power through its removed edges");
            h.assertTrue(lamps.stream().filter(p->lit(level,p)).count()==2,"Remaining source controls the same shared budget");
            var alone=place(h,20,2,17,ModBlocks.PANEL_LIGHT.get());allocate(level);
            h.assertTrue(!lit(level,alone)&&!PowerNetworks.powered(level,alone),"A nearby unlinked panel receives no wireless power");
            near(h,PowerNetworks.reading(level,alone).demandKw(),.1,"Unlinked device shows its own demand");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_flow",timeoutTicks=100)
    public static void poweredPanelSuspendsAtWallAndReconnectsSavedLink(GameTestHelper h) {
        scene(h, () -> {
            var level=h.getLevel();
            var state=com.frozendawn.data.ApocalypseState.get(level.getServer());long previous=state.getApocalypseTicks();
            try {
            state.setApocalypseTicks((long)(state.getTotalDays()*24000L*.90),level.getServer());
            h.assertTrue(com.frozendawn.world.CombustionAtmosphere.isVacuum(level),"Fixture exercises actual late-vacuum conditions");
            for(int x=2;x<=7;x++)for(int y=1;y<=6;y++)for(int z=2;z<=8;z++)
                h.setBlock(new BlockPos(x,y,z),x==2||x==7||y==1||y==6||z==2||z==8?Blocks.GLASS:Blocks.AIR);
            var source=place(h,4,3,5,ModBlocks.GEOTHERMAL_CORE.get());var lamp=place(h,10,3,5,ModBlocks.PANEL_LIGHT.get());
            var core=(GeothermalCoreBlockEntity)level.getBlockEntity(source);core.setPowerShare(25);
            h.assertTrue(PowerNetworks.link(level,source,lamp)==PowerLinkRules.Result.FEEDTHROUGH,"Electricity obeys the sealed wall rule");
            h.setBlock(new BlockPos(7,3,5),ModBlocks.BULKHEAD_FEEDTHROUGH.get());link(h,source,lamp);allocate(level);
            h.assertTrue(lit(level,lamp),"Light gets electricity through the real feedthrough path");
            h.assertTrue(!com.frozendawn.world.RoomAtmosphere.hasAir(level,lamp.above())
                    &&!com.frozendawn.world.VacuumFlames.isOrdinaryFlame(level.getBlockState(lamp)),
                    "Powered light works outside the seal in vacuum without a combustion/O2 consumer");
            var data=PowerNetworkSavedData.get(level);var restored=PowerNetworkSavedData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            h.assertTrue(restored.links().equals(data.links()),"Saved link serialization retains the powered topology");
            var saved=core.saveWithFullMetadata(level.registryAccess());core.resetAllocation();allocate(level);h.assertTrue(!lit(level,lamp),"Default leaves no electrical output");
            core.loadWithComponents(saved,level.registryAccess());allocate(level);h.assertTrue(lit(level,lamp),"Saved power share reconstructs delivery without reallocating it");
            h.setBlock(new BlockPos(7,3,5),Blocks.GLASS);allocate(level);
            h.assertTrue(!lit(level,lamp)&&data.links().contains(new PowerLink(source,lamp)),"Blocked wall cuts power while retaining the saved edge");
            h.setBlock(new BlockPos(7,3,5),ModBlocks.BULKHEAD_FEEDTHROUGH.get());allocate(level);
            h.assertTrue(lit(level,lamp),"Wall repair restores the original saved link and light");
            h.assertTrue(com.frozendawn.world.RoomAtmosphere.inspect(level,source.east()).seal()==com.frozendawn.world.RoomAtmosphere.Seal.SEALED,
                    "Electrical delivery leaves the pressure seal intact");
            var absent=new BlockPos(1234567,200,1234567);PowerNetworks.reading(level,absent);PowerNetworks.powered(level,absent);
            h.assertTrue(!level.isLoaded(absent),"Electrical diagnostics never load an absent chunk");
            } finally {state.setApocalypseTicks(previous,level.getServer());}
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_flow_cadence",timeoutTicks=80)
    public static void orphanedSavedLitPanelTurnsOffWithoutFuelOrAir(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var level=h.getLevel();var lamp=place(h,3,2,3,ModBlocks.PANEL_LIGHT.get());
            level.setBlock(lamp,level.getBlockState(lamp).setValue(PanelLightBlock.LIT,true),3);
            h.runAfterDelay(25, () -> {
                h.assertTrue(!lit(level,lamp)&&level.getBlockState(lamp).getLightEmission(level,lamp)==0,
                        "Unlinked saved lit state is reconciled and emits no phantom light");
                h.assertTrue(level.getBlockEntity(lamp)==null,"Panel requires no fuel inventory or stored energy");h.succeed();
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="power_flow",timeoutTicks=100)
    public static void powerFlowReplayFunctionsParse(GameTestHelper h) {
        for(var name:List.of("load","tick","setup","controls","basic","sealed","sealed_outside","brownout"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("power_flow_check",name)).isPresent(),
                    "Native parser loads Power Flow Check function "+name);
        h.assertTrue(ModItems.PANEL_LIGHT.get().getDefaultInstance().getItem() instanceof net.minecraft.world.item.BlockItem,"Panel Light can be placed from its registered item");h.succeed();
    }
    private static BlockPos place(GameTestHelper h,int x,int y,int z,net.minecraft.world.level.block.Block block) {
        var pos=new BlockPos(x,y,z);h.setBlock(pos,block);return h.absolutePos(pos);
    }
    private static void link(GameTestHelper h,BlockPos a,BlockPos b) {
        h.assertTrue(PowerNetworks.link(h.getLevel(),a,b)==PowerLinkRules.Result.OK,"Expected link "+a+" -> "+b);
    }
    private static void allocate(ServerLevel level) {for(int i=0;i<128;i++)PowerNetworks.update(level);PowerNetworks.account(level);}
    private static boolean lit(ServerLevel level,BlockPos p) {return level.getBlockState(p).getValue(PanelLightBlock.LIT);}
    private static void near(GameTestHelper h,double actual,double expected,String reason) {h.assertTrue(Math.abs(actual-expected)<1e-6,reason+": "+actual+" != "+expected);}
    private static void scene(GameTestHelper h,Runnable body) {h.runAfterDelay(1,()->{body.run();h.succeed();});}
}
