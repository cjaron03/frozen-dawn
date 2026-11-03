package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeadlampGameTest {
    @BeforeBatch(batch="headlamp") public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="headlamp",timeoutTicks=100)
    public static void headlampDirectionFalloffWallsAndGlass(GameTestHelper h) {
        var l=h.getLevel();var eye=h.absolutePos(new BlockPos(4,6,10)).getCenter();
        var beam=HeadlampBeam.project(l,eye,new Vec3(1,0,0));
        h.assertTrue(beam.size()<=HeadlampBeam.MAX_SOURCES&&beam.containsValue(15)&&beam.containsValue(9),"Bounded forward projection includes peak 15 and distant 9");
        h.assertTrue(beam.keySet().stream().anyMatch(p->p.getX()>=Math.floor(eye.x)+16)&&beam.keySet().stream().allMatch(p->p.getX()>=Math.floor(eye.x)),"Direction reaches 16 blocks forward rather than behind");
        h.assertTrue(beam.keySet().stream().allMatch(p->p.getX()>=Math.floor(eye.x)+4&&p.getY()==Math.floor(eye.y)&&p.getZ()==Math.floor(eye.z)),"Sources stay on the aim line with no local halo or side rays");
        for(int y=1;y<15;y++)for(int z=1;z<20;z++)h.setBlock(new BlockPos(9,y,z),Blocks.STONE);
        int wall=h.absolutePos(new BlockPos(9,6,10)).getX();
        var blocked=HeadlampBeam.project(l,eye,new Vec3(1,0,0));
        h.assertTrue(blocked.keySet().stream().allMatch(p->p.getX()<wall),"Opaque partition stops every ray and source center");
        for(int y=4;y<=9;y++)for(int z=7;z<=13;z++)h.setBlock(new BlockPos(9,y,z),Blocks.GLASS);
        h.assertTrue(HeadlampBeam.project(l,eye,new Vec3(1,0,0)).keySet().stream().anyMatch(p->p.getX()>wall),"Transparent glass transmits projection without replacement");
        h.assertTrue(l.getBlockState(h.absolutePos(new BlockPos(9,6,10))).is(Blocks.GLASS),"Projection is read-only");
        for(int y=1;y<15;y++)for(int z=1;z<20;z++)h.setBlock(new BlockPos(6,y,z),Blocks.STONE);
        int closeWall=h.absolutePos(new BlockPos(6,6,10)).getX();
        for(int offset=0;offset<=3;offset++) {
            var close=HeadlampBeam.project(l,eye.add(-offset,0,0),new Vec3(1,0,0));
            h.assertTrue(!close.isEmpty()&&close.keySet().stream().allMatch(p->p.getX()<closeWall)&&close.values().stream().allMatch(v->v==15),"Approaching the wall from 4.5 to 1.5 blocks keeps peak brightness and cannot project through it");
        }
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="headlamp",timeoutTicks=100)
    public static void headlampVerticalAimAndUnloadedBoundary(GameTestHelper h) {
        var l=h.getLevel();var eye=h.absolutePos(new BlockPos(10,7,10)).getCenter();
        for(int sign:List.of(-1,1)) {
            var beam=HeadlampBeam.project(l,eye,new Vec3(0,sign,0));
            h.assertTrue(!beam.isEmpty()&&beam.size()<=HeadlampBeam.MAX_SOURCES,"Straight vertical aim has a finite bounded basis");
            h.assertTrue(beam.keySet().stream().anyMatch(p->sign*(p.getY()-eye.y)>3),"Vertical ray illuminates in the chosen direction");
        }
        var absent=new BlockPos(1234567,200,1234567);
        h.assertTrue(!l.isLoaded(absent)&&HeadlampBeam.project(l,absent.getCenter(),new Vec3(1,0,0)).isEmpty()&&!l.isLoaded(absent),"Unloaded projection never requests or replaces a chunk");
        h.assertTrue(HeadlampBeam.project(l,eye,Vec3.ZERO).isEmpty(),"Zero direction cannot create a light");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="headlamp",timeoutTicks=100)
    public static void headlampLanternOverlapBrightnessAndTurning(GameTestHelper h) {
        var l=h.getLevel();var a=player(l,"headlamp_a");var b=player(l,"headlamp_b");
        var p=h.absolutePos(new BlockPos(4,5,10));a.setPos(p.getCenter());a.setYRot(-90);a.setXRot(0);
        HeadlampPrototype.setEnabled(a,true);var beam=HeadlampBeam.project(l,a.getEyePosition(),a.getLookAngle());
        var far=beam.entrySet().stream().filter(e->e.getValue()==9).findFirst().orElseThrow().getKey();
        b.setPos(far.getX()+.5,far.getY()+.5-b.getEyeHeight(),far.getZ()+.5);
        var lantern=new ItemStack(ModItems.BATTERY_LANTERN.get());
        lantern.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(PowerCellItem.charged(1000))));lantern.set(DataComponents.MAX_STACK_SIZE,1);
        b.setItemInHand(InteractionHand.OFF_HAND,lantern);
        HeldLanternLights.tickLevel(l,List.of(a,b));
        h.assertTrue(l.getBlockState(far).getValue(LightBlock.LEVEL)==14,"Overlapping Lantern wins max brightness over dim beam");
        b.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);HeldLanternLights.tickLevel(l,List.of(a,b));
        h.assertTrue(l.getBlockState(far).is(ModBlocks.HELD_LANTERN_LIGHT)&&l.getBlockState(far).getValue(LightBlock.LEVEL)==9,"Removing Lantern restores remaining beam brightness");
        a.setYRot(90);HeldLanternLights.tickLevel(l,List.of(a,b));
        h.assertTrue(l.isEmptyBlock(far),"Turning removes the old forward footprint");
        b.setPos(a.position());b.setItemInHand(InteractionHand.OFF_HAND,lantern);
        HeadlampPrototype.setEnabled(a,false);HeldLanternLights.tickLevel(l,List.of(a,b));
        var head=BlockPos.containing(b.getEyePosition());
        h.assertTrue(l.getBlockState(head).is(ModBlocks.HELD_LANTERN_LIGHT),"Headlamp OFF cannot erase another player's Lantern");
        HeldLanternLights.tickLevel(l,List.of());h.assertTrue(l.isEmptyBlock(head),"Departure clears all owned sources");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="headlamp",timeoutTicks=100)
    public static void headlampWaterForeignLightsAndDeathCleanup(GameTestHelper h) {
        var l=h.getLevel();var a=player(l,"headlamp_water");var p=h.absolutePos(new BlockPos(4,5,10));a.setPos(p.getCenter());a.setYRot(-90);
        for(int x=2;x<19;x++)for(int y=4;y<9;y++)for(int z=7;z<14;z++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);
        // Keep a real water sample at x=8; the protected foreign light lies between samples.
        var foreign=h.absolutePos(new BlockPos(9,7,10));l.setBlock(foreign,Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,7),3);
        var solid=h.absolutePos(new BlockPos(10,7,10));l.setBlock(solid,Blocks.STONE.defaultBlockState(),3);
        HeadlampPrototype.setEnabled(a,true);var beam=HeadlampBeam.project(l,a.getEyePosition(),a.getLookAngle());HeldLanternLights.tickLevel(l,List.of(a));
        var source=beam.keySet().stream().filter(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)&&l.getBlockState(pos).getValue(LightBlock.WATERLOGGED)).findFirst().orElseThrow();
        h.assertTrue(l.getBlockState(source).getValue(LightBlock.WATERLOGGED),"Projected light preserves source water");
        a.setHealth(0);HeldLanternLights.tickLevel(l,List.of(a));
        h.assertTrue(beam.keySet().stream().noneMatch(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)),"Death leaves no projected markers");
        h.assertTrue(l.getBlockState(source).is(Blocks.WATER)&&l.getBlockState(solid).is(Blocks.STONE)
                &&l.getBlockState(foreign).is(Blocks.LIGHT)&&l.getBlockState(foreign).getValue(LightBlock.LEVEL)==7,"Cleanup restores water and preserves terrain and foreign light");
        l.setBlock(source,ModBlocks.HELD_LANTERN_LIGHT.get().defaultBlockState().setValue(LightBlock.LEVEL,8).setValue(LightBlock.WATERLOGGED,true),3);
        l.getBlockState(source).tick(l,source,l.random);
        h.assertTrue(l.getBlockState(source).is(Blocks.WATER),"A persisted low-brightness orphan self-cleans without a live owner");
        HeldLanternLights.tickLevel(l,List.of());h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="headlamp",timeoutTicks=100)
    public static void headlampPlaytestFunctionsParseAndWorldGuard(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        for(var name:List.of("load","bootstrap","tick","setup","controls","on","off","read","tunnel","wall","behind","corner","vertical","water"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("headlamp_check",name)).isPresent(),"Native parser loads Headlamp function "+name);
        var a=player(h.getLevel(),"headlamp_guard");
        for(var action:List.of("on","toggle")) {
            int result=h.getLevel().getServer().getCommands().getDispatcher().execute("headlamp_prototype "+action,a.createCommandSourceStack().withPermission(2));
            h.assertTrue(result==0&&!HeadlampPrototype.enabled(a),"Player "+action+" command cannot enable prototype outside the exact lab world");
        }
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="headlamp",timeoutTicks=100)
    public static void headlampWaterFixturePhaseIsolation(GameTestHelper h) {
        var l=h.getLevel();var positions=new ArrayList<BlockPos>();
        for(int x=9;x<=11;x++)for(int y=5;y<=7;y++)for(int z=9;z<=11;z++) {
            var p=new BlockPos(x,y,z);h.setBlock(p,Blocks.WATER);positions.add(h.absolutePos(p));
        }
        var centre=h.absolutePos(new BlockPos(10,6,10));
        var random=net.minecraft.util.RandomSource.create(42);
        for(int i=0;i<32;i++)BlockFreezer.tickRegion(l,0,0,random,centre,1);
        h.assertTrue(positions.stream().allMatch(p->l.getBlockState(p).is(Blocks.WATER)),"Early-phase water fixture remains liquid under real freezing ticks");
        for(int i=0;i<32;i++)BlockFreezer.tickRegion(l,6,.9f,random,centre,1);
        h.assertTrue(positions.stream().anyMatch(p->{var s=l.getBlockState(p);return s.is(Blocks.ICE)||s.is(Blocks.PACKED_ICE)||s.is(Blocks.BLUE_ICE);}),"Late-phase control still freezes water; the production freezing rules were not disabled");
        h.succeed();
    }
    private static FakePlayer player(ServerLevel level,String name) { return new FakePlayer(level,new GameProfile(UUID.randomUUID(),name)); }
}
