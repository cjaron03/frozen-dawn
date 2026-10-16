package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.loot.SalvagedPowerCells;
import com.frozendawn.power.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerCellSalvageGameTest {
    @BeforeBatch(batch="power_salvage") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_salvage",timeoutTicks=100)
    public static void salvageProductionLootHasBoundedChargeAndPreservesExistingLoot(GameTestHelper h) {
        for(long seed=0;seed<24;seed++) {
            for(var profile:CargoDropStructureLayout.LootProfile.values()) {
                var first=new ArrayList<ItemStack>();var second=new ArrayList<ItemStack>();
                CargoDropStructureBuilder.buildLoot(profile,RandomSource.create(seed),first,second,seed);
                var all=new ArrayList<>(first);all.addAll(second);
                if(profile==CargoDropStructureLayout.LootProfile.BUREAUCRATIC)
                    h.assertTrue(cells(all).isEmpty()&&all.stream().anyMatch(s->s.is(Items.PAPER)),"Paper-only cargo keeps its existing joke/profile");
                else checkCell(h,all);
                h.assertTrue(!first.isEmpty()&&!second.isEmpty(),"Both original cargo inventories remain populated");
                if(profile==CargoDropStructureLayout.LootProfile.JACKPOT)h.assertTrue(first.stream().anyMatch(s->s.is(ModItems.THERMAL_CORE)),"Existing jackpot reward retained");
            }
            var station=MonitoringStationStructureBuilder.createMainRoomLoot(seed);checkCell(h,station);
            h.assertTrue(station.stream().anyMatch(s->s.is(Items.BREAD))&&station.stream().anyMatch(s->s.is(ModItems.ORSA_DOCUMENT)),"Accessible station supplies/documents retained; no locked-room prerequisite");
            for(var variant:FrozenEvacVehiclePlacement.VehicleVariant.values()) {
                var vehicle=FrozenEvacVehicleStructureBuilder.buildLoot(BlockPos.ZERO,variant,seed);checkCell(h,vehicle);
                h.assertTrue(vehicle.stream().anyMatch(s->s.is(Items.BREAD)),"Ruined vehicle keeps existing supplies");
            }
            h.assertTrue(PowerCellItem.energy(SalvagedPowerCells.create(seed))==PowerCellItem.energy(SalvagedPowerCells.create(seed)),"Cache seed determines charge without a new reroll stream");
        }
        for(var name:List.of("load","tick","setup","controls"))h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("charged_cell_check",name)).isPresent(),"Native parser loads salvage replay "+name);
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_salvage",timeoutTicks=100)
    public static void salvageCampCacheKeepsChargeAndCannotRefillAfterRemoval(GameTestHelper h) {
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,2,3));
        CampStructureBuilder.placeCrateWithLoot(l,p,Direction.NORTH,3,false,true);
        var crate=(BarrelBlockEntity)l.getBlockEntity(p);var loot=new ArrayList<ItemStack>();
        for(int i=0;i<crate.getContainerSize();i++)loot.add(crate.getItem(i));checkCell(h,loot);
        h.assertTrue(crate.getItem(0).is(Items.STONE_PICKAXE)&&crate.getItem(22).is(ModItems.EMERGENCY_O2_CARTRIDGE),"Gear and backup O2 slots preserved");
        var cell=crate.removeItem(23,1);int charge=PowerCellItem.energy(cell);
        var saved=crate.saveWithFullMetadata(l.registryAccess());crate.loadWithComponents(saved,l.registryAccess());
        l.setBlock(p.east(),Blocks.COBBLESTONE.defaultBlockState(),3);
        h.assertTrue(crate.getItem(23).isEmpty()&&PowerCellItem.energy(cell)==charge,"Loading/updating a looted crate cannot reroll or replace its cell");
        var food=p.east(3);CampStructureBuilder.placeCrateWithLoot(l,food,Direction.NORTH,3,true,true);
        h.assertTrue(((BarrelBlockEntity)l.getBlockEntity(food)).getItem(23).isEmpty(),"Food crate does not duplicate equipment salvage");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="power_salvage",timeoutTicks=100)
    public static void salvageCellPowersIsolatedDockAndRetainsFiniteChargeOnDropAndReload(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,2,3));var lamp=h.absolutePos(new BlockPos(6,2,3));
            l.setBlock(p,ModBlocks.CELL_DOCK.get().defaultBlockState(),3);l.setBlock(lamp,ModBlocks.PANEL_LIGHT.get().defaultBlockState(),3);
            var dock=(PowerStorageBlockEntity)l.getBlockEntity(p);dock.onLoad();var loot=MonitoringStationStructureBuilder.createMainRoomLoot(71);var cell=cells(loot).getFirst();int initial=PowerCellItem.energy(cell);dock.setItem(0,cell);
            h.assertTrue(PowerNetworks.link(l,p,lamp)==PowerLinkRules.Result.OK,"Salvaged cell's dock links directly to lamp without a Core");
            for(int i=0;i<128;i++)PowerNetworks.update(l);PowerNetworks.account(l);
            h.assertTrue(l.getBlockState(lamp).getValue(PanelLightBlock.LIT)&&dock.storedJoules()==initial-100,"Actual network pays exactly100J to illuminate from finite salvage");
            var dropped=new ItemEntity(l,p.getX()+.5,p.getY()+.5,p.getZ()+.5,dock.removeItem(0,1));l.addFreshEntity(dropped);
            var serialized=dropped.getItem().save(l.registryAccess());var restored=ItemStack.parse(l.registryAccess(),serialized).orElseThrow();dropped.discard();
            h.assertTrue(PowerCellItem.energy(restored)==initial-100,"Dropped item's existing component saves/reloads remaining charge");dock.setItem(0,restored);
            var tag=dock.saveWithFullMetadata(l.registryAccess());dock.loadWithComponents(tag,l.registryAccess());
            h.assertTrue(dock.storedJoules()==initial-100,"Dock NBT keeps exact remaining loot charge");
            h.assertTrue(dock.draw(Integer.MAX_VALUE)==initial-100&&dock.storedJoules()==0,"Store can deliver only its finite remainder");
            h.runAfterDelay(1,()->{for(int i=0;i<128;i++)PowerNetworks.update(l);PowerNetworks.account(l);
                h.assertTrue(!l.getBlockState(lamp).getValue(PanelLightBlock.LIT)&&dock.storedJoules()==0,"Empty salvage stays empty without a generator");h.succeed();});
        });
    }
    private static List<ItemStack> cells(List<ItemStack> loot){return loot.stream().filter(s->s.is(ModItems.POWER_CELL)).toList();}
    private static void checkCell(GameTestHelper h,List<ItemStack> loot){var found=cells(loot);h.assertTrue(found.size()==1,"One cell per eligible equipment cache");var cell=found.getFirst();h.assertTrue(cell.getCount()==1&&cell.getMaxStackSize()==1,"Cell remains unstackable");int n=PowerCellItem.energy(cell);h.assertTrue(n>=SalvagedPowerCells.MIN_JOULES&&n<=SalvagedPowerCells.MAX_JOULES&&n<PowerCellItem.CAPACITY_JOULES,"Charge is positive, partial and bounded");}
}
