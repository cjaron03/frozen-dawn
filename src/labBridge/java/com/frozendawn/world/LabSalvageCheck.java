package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** A fresh, one-use lab with the production loot providers; excluded from release jars. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class LabSalvageCheck {
    @SubscribeEvent public static void register(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("fdlab").requires(s->s.hasPermission(2)).then(Commands.literal("charged_cell_setup").executes(c->setup(c.getSource()))));}
    private static int setup(CommandSourceStack s) {
        if(FMLEnvironment.production||!s.getServer().getWorldData().getLevelName().equals("Charged Cell Salvage Check")||!(s.getEntity() instanceof ServerPlayer p)||!p.isCreative())return 0;
        var receipt=s.getServer().getWorldPath(LevelResource.ROOT).resolve("salvage-setup.json");if(Files.exists(receipt))return 0;
        try {
            Files.writeString(receipt,"{\"state\":\"started\"}",StandardOpenOption.CREATE_NEW);
            var l=s.getLevel();var first=new ArrayList<ItemStack>();var second=new ArrayList<ItemStack>();
            CargoDropStructureBuilder.buildLoot(CargoDropStructureLayout.LootProfile.USEFUL,RandomSource.create(17),first,second,17);first.addAll(second);
            var lists=List.of(first,MonitoringStationStructureBuilder.createMainRoomLoot(29),FrozenEvacVehicleStructureBuilder.buildLoot(BlockPos.ZERO,FrozenEvacVehiclePlacement.VehicleVariant.ROADSIDE_BREAKDOWN,43));
            var records=new ArrayList<String>();
            for(int i=0;i<lists.size();i++) {
                var pos=new BlockPos(-4+4*i,65,0);l.setBlock(pos,ModBlocks.ORSA_SUPPLY_CRATE.get().defaultBlockState(),3);var crate=(BarrelBlockEntity)l.getBlockEntity(pos);var loot=lists.get(i);
                for(int j=0;j<loot.size();j++)crate.setItem(j,loot.get(j));
                var cell=loot.stream().filter(item->item.is(ModItems.POWER_CELL)).findFirst().orElseThrow();records.add("{\"source\":\""+List.of("cargo","station","vehicle").get(i)+"\",\"joules\":"+PowerCellItem.energy(cell)+"}");
            }
            CampStructureBuilder.placeCrateWithLoot(l,new BlockPos(8,65,0),net.minecraft.core.Direction.NORTH,3,false,true);
            l.setBlock(new BlockPos(0,65,6),ModBlocks.CELL_DOCK.get().defaultBlockState(),3);l.setBlock(new BlockPos(4,65,6),ModBlocks.PANEL_LIGHT.get().defaultBlockState(),3);
            l.setBlock(new BlockPos(8,65,6),ModBlocks.INDUCTION_FURNACE.get().defaultBlockState(),3);
            Files.writeString(receipt,"{\"state\":\"ready\",\"fixture\":\"staged production loot, no Core\",\"cells\":["+String.join(",",records)+"]}");
            s.sendSuccess(()->Component.literal("SALVAGE READY: cargo, station, vehicle and surviving camp crates. Each uses production loot; no Core here."),false);return 1;
        } catch(Exception e){FrozenDawn.LOGGER.error("Salvage lab setup failed; preserve this attempt",e);return 0;}
    }
}
