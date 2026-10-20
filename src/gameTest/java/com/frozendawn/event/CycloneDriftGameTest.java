package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.cyclone.*;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.world.BlastPitWarmZoneRegistry;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CycloneDriftGameTest {
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone_drifts",timeoutTicks=100)
    public static void cycloneDriftsTaperRotateCapAndRemainAfterStorm(GameTestHelper h) {
        scene(h,p->{
            var l=p.serverLevel();var c=p.blockPosition();wall(p,c.west(),3);force(p,0);
            for(int i=0;i<80;i++) for(int x=0;x<=5;x++) CycloneDrift.tryColumn(l,c.getX()+x,c.getZ());
            layers(h,c,5);layers(h,c.east(2),3);layers(h,c.east(4),1);
            h.assertTrue(l.getBlockState(c.east(5)).isAir(),"No deposit outside nearest obstacle shadow");
            var tall=c.south(4);wall(p,tall.west(),6);
            for(int i=0;i<80;i++) CycloneDrift.tryColumn(l,tall.getX(),tall.getZ());
            layers(h,tall,8);h.assertTrue(l.getBlockState(tall.above()).isAir(),"Cyclone never starts a second snow block");
            var rotated=c.east(4).north(4);wall(p,rotated.north(),3);force(p,(float)Math.PI/2);
            for(int i=0;i<80;i++) CycloneDrift.tryColumn(l,rotated.getX(),rotated.getZ());layers(h,rotated,5);
            CycloneSavedData.get(l.getServer()).end();
            h.assertTrue(!CycloneDrift.tryColumn(l,tall.getX(),tall.getZ()),"No storm means no column mutation");layers(h,tall,8);
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone_drifts",timeoutTicks=100)
    public static void cycloneDriftsProtectObjectsRoofsWaterWarmZonesAndUnloadedChunks(GameTestHelper h) {
        scene(h,p->{
            var l=p.serverLevel();var c=p.blockPosition();force(p,0);
            for(int z=-4;z<=4;z++) wall(p,c.west().offset(0,0,z),3);
            var torch=c.north(3);var flower=c.north();var chest=c.south();var machine=c.south(3);
            l.setBlock(torch,Blocks.TORCH.defaultBlockState(),3);l.setBlock(flower.below(),Blocks.DIRT.defaultBlockState(),3);
            l.setBlock(flower,Blocks.DANDELION.defaultBlockState(),3);l.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);
            ((ChestBlockEntity)l.getBlockEntity(chest)).setItem(0,new ItemStack(Items.DIAMOND,7));
            l.setBlock(machine,Blocks.FURNACE.defaultBlockState(),3);l.setBlock(c.south(2),Blocks.WATER.defaultBlockState(),3);
            l.setBlock(c.above(4),Blocks.GLASS.defaultBlockState(),3);
            for(int i=0;i<600;i++) for(int z=-4;z<=4;z++) CycloneDrift.tryColumn(l,c.getX(),c.getZ()+z);
            h.assertTrue(l.getBlockState(torch).is(Blocks.TORCH)&&l.getBlockState(flower).is(Blocks.DANDELION),"Torch and flower preserved");
            h.assertTrue(l.getBlockState(chest).is(Blocks.CHEST)&&((ChestBlockEntity)l.getBlockEntity(chest)).getItem(0).getCount()==7,"Container and contents preserved");
            h.assertTrue(l.getBlockState(machine).is(Blocks.FURNACE)&&l.getBlockState(machine.above()).isAir(),"No drift on top of machines");
            h.assertTrue(l.getBlockState(c).isAir(),"Glass roof protects even with open sky beside it");
            h.assertTrue(l.getBlockState(c.south(2)).is(Blocks.WATER),"Water not displaced");
            var open=c.north(4);layers(h,open,5);
            BlastPitWarmZoneRegistry.register(l,open);
            try {h.assertTrue(!CycloneDrift.tryColumn(l,c.east().getX(),open.getZ()),"Warm zone cannot receive drifts");layers(h,open,5);}
            finally {BlastPitWarmZoneRegistry.unregister(l,open);}
            boolean checked=false;
            for(int dx=240;dx<=512;dx+=16) {
                var absent=c.east(dx);
                if(l.hasChunkAt(absent))continue;
                h.assertTrue(!CycloneDrift.tryColumn(l,absent.getX(),absent.getZ())&&!l.hasChunkAt(absent),"Attempt never loads an absent chunk");checked=true;break;
            }
            h.assertTrue(checked,"Fixture found an unloaded active-track column");
            force(p,0);var d=CycloneSavedData.get(l.getServer());d.replaceActive(d.active().orElseThrow().atStage(CycloneStage.FALSE_CALM,l.getGameTime(),.5f));
            h.assertTrue(!CycloneDrift.tryColumn(l,c.east().getX(),open.getZ()),"False calm never deposits");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone_drifts",timeoutTicks=100)
    public static void cycloneDriftReplayFunctionsLoadWithNativeParser(GameTestHelper h) {
        for(var name:java.util.List.of("load","bootstrap","tick","setup","controls","start","end","walls","objects","roof","read"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cyclone_drift",name)).isPresent(),"Native parser loaded "+name);
        h.succeed();
    }
    private static void layers(GameTestHelper h,BlockPos pos,int expected) {
        var at=h.getLevel().getBlockState(pos);h.assertTrue(at.is(Blocks.SNOW)&&at.getValue(SnowLayerBlock.LAYERS)==expected,"Expected "+expected+" layers at "+pos+", got "+at);
    }
    private static void wall(ServerPlayer p,BlockPos pos,int height) {
        for(int y=0;y<height;y++)p.serverLevel().setBlock(pos.above(y),Blocks.STONE.defaultBlockState(),3);
    }
    private static void force(ServerPlayer p,float heading) {
        var l=p.serverLevel();l.getDataStorage().set("frozendawn_cyclones",new CycloneSavedData());
        CycloneSavedData.get(l.getServer()).startForced(CycloneStorm.roll(321,CycloneSeverity.CATASTROPHIC,p.getX(),p.getZ(),l.getGameTime(),p.getUUID(),heading).atStage(CycloneStage.PEAK,l.getGameTime(),.5f));
    }
    private static void scene(GameTestHelper h,Consumer<ServerPlayer> body) {
        var l=h.getLevel();var original=h.absolutePos(new BlockPos(5,0,5));var center=new BlockPos(original.getX(),80,original.getZ());
        for(int x=-8;x<=10;x++)for(int z=-8;z<=8;z++)for(int y=79;y<l.getMaxBuildHeight();y++)
            l.setBlock(center.offset(x,y-80,z),(y==79?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        h.runAfterDelay(20,()->EmergencyEvaGameTest.scene(h,p->{
            long time=l.getGameTime();var saved=CycloneSavedData.get(l.getServer()).save(new CompoundTag(),l.registryAccess());
            try {EmergencyEvaGameTest.setProgress(p,.5f);p.setPos(center.getX()+.5,80,center.getZ()+.5);((ServerLevelData)l.getLevelData()).setGameTime(40000);body.accept(p);}
            finally {((ServerLevelData)l.getLevelData()).setGameTime(time);l.getDataStorage().set("frozendawn_cyclones",CycloneSavedData.load(saved,l.registryAccess()));}
        }));
    }
}
