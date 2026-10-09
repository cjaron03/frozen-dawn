package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VacuumFlamesGameTest {
    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="vacuum_combustion", timeoutTicks=100)
    public static void vacuumFlamesRejectNewOrdinarySourcesAndPreserveState(GameTestHelper helper) {
        scene(helper,0.90f,pos -> {
            var level=helper.getLevel();
            helper.assertFalse(CombustionAtmosphere.canBurnAt(level,pos),"Fixture must be open vacuum");
            for (var block:new net.minecraft.world.level.block.Block[]{Blocks.FIRE,Blocks.TORCH,Blocks.WALL_TORCH,
                    Blocks.LANTERN,Blocks.CAMPFIRE,Blocks.CANDLE,Blocks.WHITE_CANDLE,Blocks.CANDLE_CAKE,Blocks.FURNACE,Blocks.SMOKER,Blocks.BLAST_FURNACE}) {
                BlockState state=block.defaultBlockState();
                if(state.hasProperty(BlockStateProperties.LIT))state=state.setValue(BlockStateProperties.LIT,true);
                if(state.hasProperty(WallTorchBlock.FACING))state=state.setValue(WallTorchBlock.FACING,Direction.WEST);
                if(state.hasProperty(BlockStateProperties.HANGING))state=state.setValue(BlockStateProperties.HANGING,true);
                level.setBlock(pos,state,2);
                var actual=level.getBlockState(pos);
                helper.assertFalse(VacuumFlames.isOrdinaryFlame(actual),"Real chunk write must extinguish "+block);
                if(block==Blocks.WALL_TORCH)helper.assertTrue(actual.is(ModBlocks.SPENT_WALL_TORCH.get())
                        && actual.getValue(WallTorchBlock.FACING)==Direction.WEST,"Wall facing retained");
                if(block==Blocks.LANTERN)helper.assertTrue(actual.is(ModBlocks.EXTINGUISHED_LANTERN.get())
                        && actual.getValue(BlockStateProperties.HANGING),"Hanging state retained");
                if(block==Blocks.TORCH||block==Blocks.LANTERN||block==Blocks.WALL_TORCH)
                    helper.assertTrue(actual.getLightEmission(level,pos)==0,"Extinguished light emits zero");
                level.removeBlock(pos,false);
            }
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="vacuum_combustion", timeoutTicks=100)
    public static void vacuumFlamesKeepSoulAndEarlierPhaseCombustion(GameTestHelper helper) {
        scene(helper,0.90f,pos -> {
            var level=helper.getLevel();
            for(var block:new net.minecraft.world.level.block.Block[]{Blocks.SOUL_FIRE,Blocks.SOUL_TORCH,Blocks.SOUL_WALL_TORCH,Blocks.SOUL_LANTERN,Blocks.SOUL_CAMPFIRE}) {
                var state=block.defaultBlockState();
                helper.assertTrue(VacuumFlames.normalize(level,pos,state)==state,"Soul flames remain supernatural: "+block);
            }
            var redstone=Blocks.REDSTONE_TORCH.defaultBlockState();
            helper.assertTrue(VacuumFlames.normalize(level,pos,redstone)==redstone,"Redstone component is not combustion");
            helper.assertTrue(VacuumFlames.normalize(level,pos,Blocks.LAVA.defaultBlockState()).is(Blocks.LAVA),"Lava remains geothermal heat");
            var apocalypse=ApocalypseState.get(level.getServer());
            apocalypse.setApocalypseTicks((long)(apocalypse.getTotalDays()*24000L*0.84),level.getServer());
            helper.assertTrue(CombustionAtmosphere.canBurnAt(level,pos),"Mid Phase 6 is not vacuum");
            var fire=Blocks.FIRE.defaultBlockState();
            helper.assertTrue(VacuumFlames.normalize(level,pos,fire)==fire,"Normal ignition before vacuum boundary");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="vacuum_combustion", timeoutTicks=100)
    public static void vacuumFurnacesAndMiteawayPreserveUnspentFuel(GameTestHelper helper) {
        scene(helper,0.90f,pos -> {
            var level=helper.getLevel();
            for(var block:new net.minecraft.world.level.block.Block[]{Blocks.FURNACE,Blocks.BLAST_FURNACE,Blocks.SMOKER}) {
                level.setBlock(pos,block.defaultBlockState(),2);
                var furnace=(AbstractFurnaceBlockEntity)level.getBlockEntity(pos);
                furnace.setItem(0,new ItemStack(block==Blocks.SMOKER?Items.BEEF:Items.RAW_IRON));
                furnace.setItem(1,new ItemStack(Items.COAL,4));
                for(int i=0;i<220;i++)AbstractFurnaceBlockEntity.serverTick(level,pos,level.getBlockState(pos),furnace);
                helper.assertTrue(furnace.getItem(1).getCount()==4&&furnace.getItem(2).isEmpty(),"No vacuum fuel consumption/cooking: "+block);
                helper.assertFalse(level.getBlockState(pos).getValue(BlockStateProperties.LIT),"Furnace stays dark");
                level.removeBlock(pos,false);
            }
            level.setBlock(pos,ModBlocks.MITEAWAY.get().defaultBlockState(),2);
            var mite=(com.frozendawn.block.MiteAwayBlockEntity)level.getBlockEntity(pos);
            var saved=mite.saveWithoutMetadata(level.registryAccess());
            helper.assertFalse(mite.ignite(),"Repellent flame cannot ignite in vacuum");
            for(int i=0;i<200;i++)mite.serverTick();
            helper.assertTrue(saved.equals(mite.saveWithoutMetadata(level.registryAccess())),"Repellent charge is preserved while unlit");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="vacuum_combustion", timeoutTicks=100)
    public static void vacuumFlamesUseSealedRoomAirAndDetectBreach(GameTestHelper helper) {
        var level=helper.getLevel();var center=helper.absolutePos(new BlockPos(10,3,10));
        for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++) {
            boolean wall=Math.abs(x)==2||Math.abs(z)==2||y==-1||y==3;
            level.setBlock(center.offset(x,y,z),(wall?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
        }
        helper.runAfterDelay(20,()->scene(helper,0.90f,ignored->{
            helper.assertTrue(CombustionAtmosphere.canBurnAt(level,center),"Existing sealed-room air allows combustion");
            level.setBlock(center,Blocks.TORCH.defaultBlockState(),2);
            helper.assertTrue(level.getBlockState(center).is(Blocks.TORCH),"Torch remains lit inside breathable room");
            level.setBlock(center.east(2),Blocks.AIR.defaultBlockState(),3);
            CombustionAtmosphere.reset();
            helper.assertFalse(CombustionAtmosphere.canBurnAt(level,center),"Breach connects room to real vacuum");
            level.setBlock(center,VacuumFlames.normalize(level,center,level.getBlockState(center)),2);
            helper.assertTrue(level.getBlockState(center).is(ModBlocks.SPENT_TORCH.get()),"Breached room loses ordinary light");
        }));
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="vacuum_combustion", timeoutTicks=100)
    public static void vacuumFlamesExtinguishExistingLightsAtTransition(GameTestHelper helper) {
        scene(helper,0.84f,pos -> {
            var level=helper.getLevel();
            var torch=pos;var lantern=pos.east(2);var campfire=pos.west(2);
            level.setBlock(torch,Blocks.TORCH.defaultBlockState(),2);
            level.setBlock(lantern,Blocks.LANTERN.defaultBlockState(),2);
            level.setBlock(campfire,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,true),2);
            helper.assertTrue(level.getBlockState(torch).is(Blocks.TORCH),"Before vacuum, existing ordinary torch is lit");
            var apocalypse=ApocalypseState.get(level.getServer());
            apocalypse.setApocalypseTicks((long)(apocalypse.getTotalDays()*24000L*0.90),level.getServer());
            CombustionAtmosphere.reset();
            // Use the actual production index/update loop, not normalize() directly.
            for(int i=0;i<256;i++)VacuumFlames.tick(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(() -> true, level.getServer()));
            helper.assertTrue(level.getBlockState(torch).is(ModBlocks.SPENT_TORCH.get()),"Existing torch goes out at real transition");
            helper.assertTrue(level.getBlockState(lantern).is(ModBlocks.EXTINGUISHED_LANTERN.get()),"Existing lantern goes out at real transition");
            helper.assertFalse(level.getBlockState(campfire).getValue(BlockStateProperties.LIT),"Existing campfire goes out at real transition");
        });
    }

    private static void scene(GameTestHelper helper,float progress,Consumer<BlockPos> test) {
        var level=helper.getLevel();var apocalypse=ApocalypseState.get(level.getServer());
        long original=apocalypse.getApocalypseTicks();
        try {
            apocalypse.setApocalypseTicks((long)(apocalypse.getTotalDays()*24000L*progress),level.getServer());
            CombustionAtmosphere.reset();
            test.accept(helper.absolutePos(new BlockPos(10,2,10)));
            helper.succeed();
        } finally {apocalypse.setApocalypseTicks(original,level.getServer());CombustionAtmosphere.reset();}
    }
}
