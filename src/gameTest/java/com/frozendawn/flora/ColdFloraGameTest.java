package com.frozendawn.flora;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColdFloraGameTest {
    @BeforeBatch(batch="cold_flora") public static void report(ServerLevel l){GameTestReporting.installReporter(l);}

    private static void floor(GameTestHelper h,int size,net.minecraft.world.level.block.Block block){
        for(int x=0;x<size;x++)for(int z=0;z<size;z++)h.setBlock(new BlockPos(x,1,z),block);
    }
    private static int count(ServerLevel l,BlockPos origin,net.minecraft.world.level.block.Block block){
        int n=0;for(var p:BlockPos.betweenClosed(origin.offset(-4,0,-4),origin.offset(4,10,4)))if(l.getBlockState(p).is(block))n++;return n;
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cold_flora",timeoutTicks=100)
    public static void rimewoodTreeIsASquatConiferWithAboutTenLogs(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();floor(h,GameTestTemplates.EMPTY_LARGE_WIDTH,Blocks.DIRT);
        int total=0,trees=0;
        for(int[] at:new int[][]{{4,4},{4,15},{15,4},{15,15}}){
            var origin=h.absolutePos(new BlockPos(at[0],2,at[1]));
            h.assertTrue(RimewoodTreeFeature.grow(l,origin,RandomSource.create(at[0]*31L+at[1])),"Tree grows on dirt at "+origin);
            int logs=count(l,origin,ModBlocks.RIMEWOOD_LOG.get()),leaves=count(l,origin,ModBlocks.RIMEWOOD_LEAVES.get());
            h.assertTrue(logs>=7&&logs<=16,"About 8-12 logs per tree: "+logs);
            h.assertTrue(leaves>=18&&leaves<=50,"A narrow, tiered canopy of needles: "+leaves);
            int trunk=0;while(l.getBlockState(origin.above(trunk)).is(ModBlocks.RIMEWOOD_LOG.get()))trunk++;
            h.assertTrue(trunk>=5&&trunk<=7,"Squat trunk 5-7 tall: "+trunk);
            for(var p:BlockPos.betweenClosed(origin.offset(-3,0,-3),origin.offset(3,9,3))){var s=l.getBlockState(p);
                if(s.is(ModBlocks.RIMEWOOD_LEAVES.get()))h.assertTrue(s.getValue(LeavesBlock.DISTANCE)<LeavesBlock.DECAY_DISTANCE,"Needles are attached and will not decay at "+p);}
            total+=logs;trees++;
        }
        h.assertTrue(total>=8*trees&&total<=13*trees,"Trees average about ten logs: "+total+"/"+trees);
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cold_flora",timeoutTicks=100)
    public static void rimewoodSaplingGrowsTheConfiguredTree(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();floor(h,GameTestTemplates.EMPTY_LARGE_WIDTH,ModBlocks.FROZEN_DIRT.get());
        var at=h.absolutePos(new BlockPos(10,2,10));
        h.assertTrue(ModBlocks.RIMEWOOD_SAPLING.get().defaultBlockState().canSurvive(l,at),"Saplings take root in frozen dirt");
        l.setBlockAndUpdate(at,ModBlocks.RIMEWOOD_SAPLING.get().defaultBlockState());
        var sapling=ModBlocks.RIMEWOOD_SAPLING.get();var random=RandomSource.create(7);
        sapling.advanceTree(l,at,l.getBlockState(at),random);
        h.assertTrue(l.getBlockState(at).is(sapling),"First stage: still a sapling");
        sapling.advanceTree(l,at,l.getBlockState(at),random);
        h.assertTrue(l.getBlockState(at).is(ModBlocks.RIMEWOOD_LOG.get()),"Second stage grows a Rimewood: "+l.getBlockState(at));
        h.assertTrue(ModBlocks.RIMEWOOD_SAPLING.get().defaultBlockState().is(BlockTags.SAPLINGS),"Counts as a sapling");
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY,batch="cold_flora",timeoutTicks=100)
    public static void frostcapStallsDiesRipensAndSpreadsUnderACap(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();floor(h,GameTestTemplates.EMPTY_WIDTH,Blocks.SNOW_BLOCK);
        var at=h.absolutePos(new BlockPos(4,2,4));var cap=ModBlocks.FROSTCAP.get();var random=RandomSource.create(3);
        l.setBlockAndUpdate(at,cap.defaultBlockState());
        for(int i=0;i<200;i++)FrostcapBlock.grow(l,at,l.getBlockState(at),random,-10f);
        h.assertTrue(l.getBlockState(at).getValue(FrostcapBlock.AGE)==0,"Stalls between -20C and 0C");
        for(int i=0;i<4000&&l.getBlockState(at).is(cap);i++)FrostcapBlock.grow(l,at,l.getBlockState(at),random,-60f);
        h.assertTrue(l.getBlockState(at).getValue(FrostcapBlock.AGE)==2,"Ripens in the cold");
        int caps=0;for(var p:BlockPos.betweenClosed(at.offset(-4,-1,-4),at.offset(4,1,4)))if(l.getBlockState(p).is(cap))caps++;
        h.assertTrue(caps>1&&caps<=ColdFloraPolicy.FROSTCAP_AREA_CAP,"Spreads, but no more than five close together: "+caps);
        FrostcapBlock.grow(l,at,l.getBlockState(at),random,2f);
        h.assertTrue(l.getBlockState(at).isAir(),"Dies above 0C");
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY,batch="cold_flora",timeoutTicks=100)
    public static void frostcapShelvesHangOnRimewoodAndFallWithIt(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var log=h.absolutePos(new BlockPos(4,2,4));var shelf=log.north();
        l.setBlockAndUpdate(log,ModBlocks.RIMEWOOD_LOG.get().defaultBlockState());
        var state=ModBlocks.FROSTCAP.get().defaultBlockState().setValue(FrostcapBlock.FACING,Direction.NORTH);
        h.assertTrue(state.canSurvive(l,shelf),"A shelf hangs on the log's north face");
        h.assertTrue(!ModBlocks.FROSTCAP.get().defaultBlockState().canSurvive(l,h.absolutePos(new BlockPos(1,2,1))),"Not on bare stone floor");
        l.setBlockAndUpdate(shelf,state);
        l.setBlockAndUpdate(log,Blocks.AIR.defaultBlockState());
        h.assertTrue(l.getBlockState(shelf).isAir(),"The shelf falls when its log is gone");
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cold_flora",timeoutTicks=100)
    public static void rimewoodGrovesCarryFrostcapAndSeedingFindsTrunks(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();floor(h,GameTestTemplates.EMPTY_LARGE_WIDTH,ModBlocks.DEAD_GRASS_BLOCK.get());
        var center=h.absolutePos(new BlockPos(10,2,10));
        h.assertTrue(ModBlocks.RIMEWOOD_SAPLING.get().defaultBlockState().canSurvive(l,center),"Saplings root in dead grass, the Phase 3 ground");
        var trunks=ColdFloraSpawner.plantGrove(l,center,RandomSource.create(11),false);
        h.assertTrue(trunks.size()>=2&&trunks.size()<=4,"A grove is two to four trees on dead grass: "+trunks);
        var trunk=trunks.getFirst();
        h.assertTrue(ColdFloraSpawner.shelve(l,trunk,RandomSource.create(5),3)==3,"Three Frostcap shelves fit on a trunk");
        int shelves=0;for(var p:BlockPos.betweenClosed(trunk.offset(-1,0,-1),trunk.offset(1,8,1))){var st=l.getBlockState(p);
            if(st.is(ModBlocks.FROSTCAP.get())){shelves++;h.assertTrue(st.getValue(FrostcapBlock.FACING)!=Direction.UP&&st.canSurvive(l,p),"Each shelf hangs on the trunk at "+p);}}
        h.assertTrue(shelves==3,"Shelves placed: "+shelves);
        var found=ColdFloraSpawner.findTrunk(l,trunk.offset(3,0,2),8);
        h.assertTrue(found!=null&&trunks.contains(found),"Seeding finds a trunk base nearby: "+found);
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY,batch="cold_flora",timeoutTicks=100)
    public static void frostcapSpreadsMostlyOntoRimewood(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();floor(h,GameTestTemplates.EMPTY_WIDTH,Blocks.SNOW_BLOCK);
        var base=h.absolutePos(new BlockPos(4,2,4));
        for(int y=0;y<3;y++)l.setBlockAndUpdate(base.above(y),ModBlocks.RIMEWOOD_LOG.get().defaultBlockState());
        var source=base.above().north();
        l.setBlockAndUpdate(source,ModBlocks.FROSTCAP.get().defaultBlockState().setValue(FrostcapBlock.FACING,Direction.NORTH).setValue(FrostcapBlock.AGE,2));
        int onLog=0,onGround=0;var random=RandomSource.create(9);
        for(int i=0;i<300;i++){
            FrostcapBlock.spread(l,source,random);
            for(var p:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(3,3,3))){
                if(p.equals(source)||!l.getBlockState(p).is(ModBlocks.FROSTCAP.get()))continue;
                if(l.getBlockState(p).getValue(FrostcapBlock.FACING)==Direction.UP)onGround++;else onLog++;
                l.removeBlock(p,false);
            }
        }
        h.assertTrue(onLog>0&&onLog>onGround*3,"Spreading heavily prefers Rimewood: log "+onLog+", ground "+onGround);
        h.assertTrue(ColdFloraPolicy.RIMEWOOD_GROWTH_BONUS>1,"Shelves grow faster than ground clusters");
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY,batch="cold_flora",timeoutTicks=100)
    public static void strippingRimewoodGivesOneResin(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var at=h.absolutePos(new BlockPos(4,2,4));
        l.setBlockAndUpdate(at,ModBlocks.RIMEWOOD_LOG.get().defaultBlockState());
        var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"rimewood_axe"));player.setPos(at.south(2).getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_AXE));
        var result=player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(at.getCenter(),Direction.SOUTH,at,false)));
        h.assertTrue(result.consumesAction()&&l.getBlockState(at).is(ModBlocks.STRIPPED_RIMEWOOD_LOG.get()),"Axe strips the log: "+result+" "+l.getBlockState(at));
        var drops=l.getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(2));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(ModItems.RIMEWOOD_RESIN.get())).mapToInt(e->e.getItem().getCount()).sum()==1,"One resin per log");
        player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(at.getCenter(),Direction.SOUTH,at,false)));
        h.assertTrue(l.getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(2)).stream().filter(e->e.getItem().is(ModItems.RIMEWOOD_RESIN.get())).count()==1,"A stripped log yields no more");
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY,batch="cold_flora",timeoutTicks=100)
    public static void frostcapThawsOneWayFrozenThawedMush(GameTestHelper h){
        var stack=new ItemStack(ModItems.FROZEN_FROSTCAP.get(),5);ItemStack next=null;
        for(int s=1;s<ColdFloraPolicy.THAW_SECONDS;s++)h.assertTrue(ColdFloraEvents.warm(stack)==null,"Still frozen at "+s+" s");
        h.assertTrue(stack.getOrDefault(ModDataComponents.FROSTCAP_WARMTH.get(),0)==ColdFloraPolicy.THAW_SECONDS-1,"Warmth is counted on the stack");
        next=ColdFloraEvents.warm(stack);
        h.assertTrue(next!=null&&next.is(ModItems.THAWED_FROSTCAP.get())&&next.getCount()==5&&!next.has(ModDataComponents.FROSTCAP_WARMTH.get()),"Five warm minutes thaw the whole stack: "+next);
        var thawed=next;
        for(int s=1;s<ColdFloraPolicy.THAW_SECONDS;s++)ColdFloraEvents.warm(thawed);
        next=ColdFloraEvents.warm(thawed);
        h.assertTrue(next!=null&&next.is(ModItems.FROSTCAP_MUSH.get())&&next.getCount()==5,"Five more turn it to mush");
        h.assertTrue(ColdFloraEvents.warm(next)==null&&ColdFloraEvents.warm(new ItemStack(Items.BREAD))==null,"Mush and other food are untouched");
        h.assertTrue(new ItemStack(ModItems.FROZEN_FROSTCAP.get()).is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"frost_resistant"))),"General food frost leaves caps to their own chain");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="cold_flora",timeoutTicks=100)
    public static void coldFloraTagsRecipesAndSigns(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();
        for(var b:new net.minecraft.world.level.block.Block[]{ModBlocks.RIMEWOOD_LOG.get(),ModBlocks.RIMEWOOD_LEAVES.get(),ModBlocks.RIMEWOOD_SAPLING.get(),ModBlocks.FROSTCAP.get()})
            h.assertTrue(b.defaultBlockState().is(ColdFlora.COLD_FLORA),"Decay and catch-up spare "+b);
        h.assertTrue(!ModBlocks.RIMEWOOD_PLANKS.get().defaultBlockState().is(ColdFlora.COLD_FLORA)&&ModBlocks.RIMEWOOD_PLANKS.get().defaultBlockState().is(BlockTags.PLANKS),"Planks are ordinary planks");
        h.assertTrue(new ItemStack(ModItems.RIMEWOOD_LOG.get()).is(ItemTags.LOGS_THAT_BURN)&&new ItemStack(ModItems.RIMEWOOD_PLANKS.get()).is(ItemTags.PLANKS),"Charcoal, fuel and wood recipes accept Rimewood");
        var rm=l.getRecipeManager();
        for(var id:new String[]{"thermal_core","fabricator/rocket_engine","reinforced_helmet","hot_wings"}){
            var recipe=rm.byKey(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,id)).orElseThrow().value();
            h.assertTrue(recipe.getIngredients().stream().anyMatch(i->i.test(new ItemStack(ModItems.SULFUR_IGNITER.get())))
                    &&recipe.getIngredients().stream().anyMatch(i->i.test(new ItemStack(Items.BLAZE_POWDER))),id+" takes blaze powder or a Sulfur Igniter");
        }
        var core=rm.byKey(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"thermal_core")).orElseThrow().value();
        h.assertTrue(core.getIngredients().stream().anyMatch(i->i.test(new ItemStack(ModItems.THERMAL_BINDER.get()))&&i.test(new ItemStack(Items.MAGMA_CREAM))),"Thermal Core takes magma cream or a Thermal Binder");
        BlockPos sign=h.absolutePos(new BlockPos(2,2,2)),hanging=h.absolutePos(new BlockPos(6,3,6));
        l.setBlockAndUpdate(sign,ModBlocks.RIMEWOOD_SIGN.get().defaultBlockState());
        l.setBlockAndUpdate(hanging,ModBlocks.RIMEWOOD_HANGING_SIGN.get().defaultBlockState());
        h.assertTrue(l.getBlockEntity(sign) instanceof SignBlockEntity&&l.getBlockEntity(hanging) instanceof HangingSignBlockEntity,"Rimewood signs get sign block entities");
        h.succeed();
    });}
}
