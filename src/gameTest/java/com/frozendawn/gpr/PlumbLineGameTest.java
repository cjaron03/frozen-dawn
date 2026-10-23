package com.frozendawn.gpr;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.gametest.*;
import com.frozendawn.gpr.PlumbLinePolicy.Setting;
import com.frozendawn.gpr.PlumbLineScan.Kind;
import com.frozendawn.gpr.PlumbLineScan.Return;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.network.PlumbLineReturnsPayload;
import com.frozendawn.power.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlumbLineGameTest {
    @BeforeBatch(batch="plumb_line") public static void report(ServerLevel l){GameTestReporting.installReporter(l);PlumbLineMemory.clear();}
    @BeforeBatch(batch="plumb_line_power") public static void reportPower(ServerLevel l){GameTestReporting.installReporter(l);}

    /** A stone block with a coal vein, a diamond sealed in water, and an iron block, scanned from above. */
    private static BlockPos geology(GameTestHelper h){
        for(int x=1;x<=19;x++)for(int y=1;y<=12;y++)for(int z=1;z<=19;z++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        for(int x=6;x<=7;x++)for(int y=4;y<=5;y++)h.setBlock(new BlockPos(x,y,10),Blocks.COAL_ORE);
        for(int x=13;x<=15;x++)for(int y=4;y<=6;y++)for(int z=9;z<=11;z++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);
        h.setBlock(new BlockPos(14,5,10),Blocks.DIAMOND_ORE);
        h.setBlock(new BlockPos(10,3,15),Blocks.IRON_BLOCK);
        return h.absolutePos(new BlockPos(10,13,10));
    }
    private static List<Return> scan(ServerLevel l,BlockPos at,Setting s,java.util.function.Predicate<Return> tri){return scan(l,at,s,PlumbLineOres.Target.ALL,tri);}
    private static List<Return> scan(ServerLevel l,BlockPos at,Setting s,PlumbLineOres.Target target,java.util.function.Predicate<Return> tri){
        var scan=new PlumbLineScan(l,at,s);while(!scan.finished())scan.advance(65_536);return scan.results(target,tri);
    }
    private static boolean contains(Return r,BlockPos p){return p.getX()>=r.minX()&&p.getX()<=r.maxX()&&p.getY()>=r.minY()&&p.getY()<=r.maxY()&&p.getZ()>=r.minZ()&&p.getZ()<=r.maxZ();}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="plumb_line",timeoutTicks=100)
    public static void plumbLineFindsOreByTypeButNotThroughWater(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var scanner=geology(h);
        BlockPos coal=h.absolutePos(new BlockPos(6,4,10)),diamond=h.absolutePos(new BlockPos(14,5,10)),iron=h.absolutePos(new BlockPos(10,3,15));
        var returns=scan(l,scanner,Setting.SURVEY,v->false);
        var ore=returns.stream().filter(r->r.kind()==Kind.ORE&&r.label().equals("minecraft:coal_ore")&&contains(r,coal)).findFirst().orElse(null);
        h.assertTrue(ore!=null,"Buried coal vein returns, labelled by exact type: "+returns.size()+" returns");
        h.assertTrue(!ore.precise()&&ore.maxX()-ore.minX()>=1+2*2,"A single pulse gives a padded blob, not exact blocks: "+ore);
        h.assertTrue(returns.stream().noneMatch(r->r.label().equals("minecraft:diamond_ore")&&contains(r,diamond)),"Water stops the signal: the sealed diamond stays hidden");
        h.assertTrue(returns.stream().anyMatch(r->r.kind()==Kind.METAL&&contains(r,iron)),"Metal reflects as a bright return");
        h.assertTrue(returns.size()<=PlumbLinePolicy.MAX_RETURNS,"Returns are bounded");
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="plumb_line",timeoutTicks=100)
    public static void plumbLineTriangulatesFromASecondPosition(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var a=geology(h);var b=a.offset(9,0,0);var coal=h.absolutePos(new BlockPos(6,4,10));
        var near=scan(l,a.offset(0,0,1),Setting.SURVEY,v->PlumbLineMemory.triangulate(l,v,a.offset(0,0,1),l.getGameTime()));
        h.assertTrue(near.stream().noneMatch(r->r.precise()&&contains(r,coal)),"A nearby second scanner does not triangulate");
        var first=scan(l,a,Setting.SURVEY,v->PlumbLineMemory.triangulate(l,v,a,l.getGameTime()));
        var second=scan(l,b,Setting.SURVEY,v->PlumbLineMemory.triangulate(l,v,b,l.getGameTime()));
        var tight=second.stream().filter(r->r.kind()==Kind.ORE&&r.precise()&&contains(r,coal)).findFirst().orElse(null);
        h.assertTrue(first.stream().anyMatch(r->r.kind()==Kind.ORE&&contains(r,coal)),"First pulse sees the vein");
        h.assertTrue(tight!=null&&tight.maxX()-tight.minX()<=1&&tight.maxY()-tight.minY()<=1,"A pulse 9 blocks away tightens it to the vein's own box: "+tight);
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="plumb_line",timeoutTicks=100)
    public static void plumbLineFiltersOreByTarget(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();var scanner=geology(h);
        for(int x=14;x<=16;x++)h.setBlock(new BlockPos(x,7,4),Blocks.IRON_ORE);
        BlockPos iron=h.absolutePos(new BlockPos(15,7,4)),coal=h.absolutePos(new BlockPos(6,4,10));
        var all=scan(l,scanner,Setting.SURVEY,v->false);
        // Test areas are roofed with barriers, so the surface skip can only be checked in a real world.
        h.assertTrue(all.stream().anyMatch(r->r.kind()==Kind.ORE&&contains(r,coal))&&all.stream().anyMatch(r->r.kind()==Kind.ORE&&contains(r,iron)),"Buried coal and iron both return on All");
        var metals=scan(l,scanner,Setting.SURVEY,PlumbLineOres.Target.METALS,v->false);
        h.assertTrue(metals.stream().anyMatch(r->r.kind()==Kind.ORE&&contains(r,iron)),"Metals target keeps iron");
        h.assertTrue(metals.stream().noneMatch(r->r.kind()==Kind.ORE&&r.label().equals("minecraft:coal_ore")),"Metals target drops coal");
        h.assertTrue(metals.stream().noneMatch(r->r.kind()==Kind.VOID),"Caves are shown only on All");
        h.succeed();
    });}

    @GameTest(template=GameTestTemplates.EMPTY,batch="plumb_line",timeoutTicks=100)
    public static void plumbLineReturnsSurviveTheWire(GameTestHelper h){
        var list=List.of(new Return(Kind.ORE,"frozendawn:frozen_coal_ore",-5,-60,3,1,-55,9,false),new Return(Kind.STRUCTURE,"orsa.reactor_site",100,60,-40,144,72,4,true));
        var p=new PlumbLineReturnsPayload("minecraft:overworld",new BlockPos(7,8,9),list);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        PlumbLineReturnsPayload.STREAM_CODEC.encode(buf,p);var back=PlumbLineReturnsPayload.STREAM_CODEC.decode(buf);buf.release();
        h.assertTrue(back.equals(p),"Returns round-trip, including negative coordinates");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="plumb_line_power",timeoutTicks=400)
    public static void plumbLinePulseDrawsPowerCompletesRechargesAndPrints(GameTestHelper h){h.runAfterDelay(1,()->{
        var l=h.getLevel();h.setBlock(new BlockPos(2,3,2),ModBlocks.BATTERY_BANK.get());h.setBlock(new BlockPos(6,3,2),ModBlocks.PLUMB_LINE.get());
        BlockPos bank=h.absolutePos(new BlockPos(2,3,2)),gpr=h.absolutePos(new BlockPos(6,3,2));
        var scanner=(PlumbLineBlockEntity)l.getBlockEntity(gpr);var store=(PowerStorageBlockEntity)l.getBlockEntity(bank);
        var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"gpr_user"));player.setPos(gpr.getCenter());
        h.assertTrue(PowerNetworks.link(l,bank,gpr)==PowerLinkRules.Result.OK,"Scanner is a Binder node");
        for(int i=0;i<260;i++)PowerNetworks.update(l);PowerNetworks.account(l);
        scanner.press(player,2);h.assertTrue(!scanner.pulsing()&&scanner.result()==PlumbLineBlockEntity.RESULT_NO_POWER,"No stored energy: refused, nothing spent");
        for(int i=0;i<4;i++)store.setItem(i,PowerCellItem.charged(500000));
        // The bank pays once per accounting tick; let the next real pass see the new cells.
        h.runAfterDelay(25,()->{
            scanner.press(player,1);scanner.press(player,2);
            h.assertTrue(scanner.pulsing()&&scanner.demandWatts()==50_000&&scanner.setting()==Setting.DEEP,"Deep pulse draws 50 kW: available="+scanner.availableWatts()+" result="+scanner.result());
            h.assertTrue(!scanner.press(player,0),"Setting is locked while pulsing");
            h.succeedWhen(()->{
                h.assertTrue(!scanner.pulsing()&&scanner.result()==PlumbLineBlockEntity.RESULT_DONE,"Waiting for the 10 s pulse: elapsed="+scanner.elapsedTicks()+" result="+scanner.result());
                h.assertTrue(scanner.cooldownTicks()>PlumbLinePolicy.COOLDOWN_TICKS-40&&scanner.demandWatts()==0,"60 s recharge, no draw at rest");
                scanner.press(player,2);h.assertTrue(scanner.result()==PlumbLineBlockEntity.RESULT_COOLING&&!scanner.pulsing(),"Second pulse refused while recharging");
                scanner.press(player,4);h.assertTrue(scanner.printNote()==PlumbLineBlockEntity.PRINT_NO_MATERIALS,"A Survey Sheet costs paper and ink");
                player.getInventory().clearContent();
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER));
                player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHARCOAL));
                scanner.press(player,4);
                var sheet=player.getInventory().items.stream().filter(st->st.is(ModItems.PLUMB_LINE_SURVEY.get())).findFirst().orElse(null);
                h.assertTrue(scanner.printNote()==PlumbLineBlockEntity.PRINT_DONE&&sheet!=null,"Printed with paper and charcoal");
                var survey=sheet.get(ModDataComponents.PLUMB_LINE_SURVEY.get());
                h.assertTrue(survey!=null&&survey.radius()==Setting.DEEP.radius&&survey.scanner().equals(gpr),"The sheet carries the Deep pulse: "+survey);
                h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.PAPER)==0&&player.getInventory().countItem(net.minecraft.world.item.Items.CHARCOAL)==0,"One paper and one charcoal spent");
            });
        });
    });}
}
