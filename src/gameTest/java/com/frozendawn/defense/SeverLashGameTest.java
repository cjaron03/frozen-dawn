package com.frozendawn.defense;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeverLashGameTest {
    private static final double EYE_X = 10.5, FEET_Y = 4, EYE_Z = 4.5;

    @BeforeBatch(batch="sever_lash") public static void report(ServerLevel level) {GameTestReporting.installReporter(level);}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashPaysTwentyFiveKilowattsFromLooseCells(GameTestHelper h) {
        var p=shooter(h,0);var lash=p.getMainHandItem();
        p.getInventory().setItem(1,PowerCellItem.charged(1000));
        p.getInventory().setItem(9,PowerCellItem.charged(500000));
        lash.getItem().onUseTick(h.getLevel(),p,lash,71999);
        h.assertTrue(energy(p,1)==0&&energy(p,9)==499750,"Holding use spends 1250 J a tick, hotbar cells first");
        h.assertTrue(PowerCellItem.CAPACITY_JOULES/SeverLash.JOULES_PER_TICK==400,"One full cell is twenty seconds of beam");
        p.getInventory().setItem(9,ItemStack.EMPTY);p.getInventory().setItem(2,PowerCellItem.charged(1249));
        h.assertTrue(!SeverLash.fire(p,lash,h.getLevel().getGameTime()+1)&&energy(p,2)==1249,"A cell short of one tick neither fires nor drains");
        h.assertTrue(lash.getOrDefault(ModDataComponents.LASH_HEAT.get(),LashHeat.COOL).heat()==LashHeat.FIRE_HEAT,"An unpowered trigger adds no heat");
        p.discard();h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashCutsTheSweptArcOnly(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        for(int y=4;y<=7;y++)for(int x=11;x<=12;x++)h.setBlock(new BlockPos(x,y,8),Blocks.STONE);
        var inside=target(h,EntityType.PIG,0,6);
        var outside=target(h,EntityType.PIG,70,6);
        var beyond=target(h,EntityType.PIG,0,14);
        var walled=target(h,EntityType.PIG,-20,8);
        aim(p,-45);h.assertTrue(SeverLash.fire(p,lash,1000),"First tick fires");
        aim(p,45);h.assertTrue(SeverLash.fire(p,lash,1001),"Second tick fires");
        h.assertTrue(Math.abs(inside.getHealth()-(inside.getMaxHealth()-SeverLash.DAMAGE_PER_TICK))<1e-4,"A target between last tick's aim and this one is cut once");
        h.assertTrue(full(outside),"A target outside the swept arc is untouched");
        h.assertTrue(full(beyond),"Twelve blocks is the reach");
        h.assertTrue(full(walled),"Stone stops the beam");
        p.discard();h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashIgnoresHurtCooldownWithoutKnockback(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        var pig=target(h,EntityType.PIG,0,5);aim(p,0);
        for(int t=0;t<5;t++)SeverLash.fire(p,lash,1000+t);
        h.assertTrue(Math.abs(pig.getHealth()-(pig.getMaxHealth()-5*SeverLash.DAMAGE_PER_TICK))<1e-4,"Every tick of contact lands: six damage a second");
        h.assertTrue(pig.getDeltaMovement().horizontalDistance()<1e-6,"A cutter does not shove");
        var stale=target(h,EntityType.PIG,30,5);
        aim(p,60);h.assertTrue(SeverLash.fire(p,lash,1010),"Fires again after a gap");
        h.assertTrue(full(stale),"A broken sweep resumes as a single ray, not an arc from the stale aim");
        p.discard();h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashCutsThroughArmor(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        var husk=target(h,EntityType.HUSK,0,5);
        husk.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));
        husk.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
        husk.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));
        husk.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
        // Worn armor becomes attributes on the husk's next tick.
        h.runAfterDelay(2,()->{
            h.assertTrue(husk.getArmorValue()>=20,"Full diamond is on: "+husk.getArmorValue());
            float before=husk.getHealth();aim(p,0);SeverLash.fire(p,lash,1000);
            h.assertTrue(Math.abs((before-husk.getHealth())-SeverLash.DAMAGE_PER_TICK)<1e-4,"Full diamond stops none of the cut");
            p.discard();h.succeed();
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashLeavesPlayerArmorUnworn(GameTestHelper h) {
        var server=h.getLevel().getServer();boolean pvp=server.isPvpAllowed();
        // The GameTest server runs with PvP off; ordinary worlds allow it.
        server.setPvpAllowed(true);
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        var level=h.getLevel();var profile=new GameProfile(UUID.randomUUID(),"sever_lash_target");
        var victim=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        victim.connection=new net.neoforged.neoforge.common.util.FakePlayer(level,profile).connection;
        victim.setPos(h.absoluteVec(new Vec3(EYE_X,FEET_Y,EYE_Z+5)));victim.setGameMode(GameType.SURVIVAL);level.addNewPlayer(victim);
        try {var field=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(victim,0);}
        catch(ReflectiveOperationException e) {throw new IllegalStateException("The target player must lose spawn protection",e);}
        var helmet=new ItemStack(Items.IRON_HELMET);victim.setItemSlot(EquipmentSlot.HEAD,helmet);
        try {
            aim(p,0);p.setXRot(10);
            for(int t=0;t<40;t++)SeverLash.fire(p,lash,1000+t);
            h.assertTrue(victim.getHealth()<victim.getMaxHealth(),"The beam reached the armored player");
            h.assertTrue(helmet.getDamageValue()==0,"Two seconds of beam leave the helmet unworn: "+helmet.getDamageValue());
        } finally {server.setPvpAllowed(pvp);victim.discard();p.discard();}
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashOverheatLockoutSpendsNothing(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();aim(p,0);
        for(int t=0;t<LashHeat.FIRE_TICKS;t++)h.assertTrue(SeverLash.fire(p,lash,1000+t),"Six seconds of continuous fire");
        int charge=energy(p,1);long last=1000+LashHeat.FIRE_TICKS-1;
        h.assertTrue(!SeverLash.fire(p,lash,last+1)&&energy(p,1)==charge,"Overheated, it neither fires nor drains");
        h.assertTrue(SeverLash.fire(p,lash,last+LashHeat.LOCKOUT_TICKS)&&energy(p,1)==charge-SeverLash.JOULES_PER_TICK,"Four seconds later it cuts again");
        p.discard();h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashHellwhipNeedsThreeHostilesInOneSweep(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        target(h,EntityType.HUSK,-30,6);target(h,EntityType.HUSK,0,6);target(h,EntityType.PIG,30,6);
        aim(p,-40);SeverLash.fire(p,lash,1000);aim(p,40);SeverLash.fire(p,lash,1001);
        h.assertTrue(!earned(p,"hellwhip"),"Two hostiles and a pig is not a Hellwhip");
        target(h,EntityType.HUSK,20,7);
        aim(p,20);SeverLash.fire(p,lash,1010);
        h.assertTrue(!earned(p,"hellwhip"),"A new beam starts a new count, even with three hostiles cut across two");
        aim(p,0);SeverLash.fire(p,lash,1011);
        h.assertTrue(!earned(p,"hellwhip"),"Two hostiles so far in this beam");
        aim(p,-30);SeverLash.fire(p,lash,1012);
        h.assertTrue(earned(p,"hellwhip"),"Three hostiles cut in one continuous beam earns Hellwhip, no single tick needed");
        p.discard();h.succeed();
    }

    /** A real survival player in the template, Lash in hand, one charged cell in slot 1. */
    private static ServerPlayer shooter(GameTestHelper h,int joules) {
        var level=h.getLevel();var profile=new GameProfile(UUID.randomUUID(),"sever_lash");
        var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        player.connection=new net.neoforged.neoforge.common.util.FakePlayer(level,profile).connection;
        player.setPos(h.absoluteVec(new Vec3(EYE_X,FEET_Y,EYE_Z)));player.setGameMode(GameType.SURVIVAL);level.addNewPlayer(player);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.SEVER_LASH.get()));
        if(joules>0)player.getInventory().setItem(1,PowerCellItem.charged(joules));
        aim(player,0);return player;
    }
    private static void aim(ServerPlayer p,double yaw) {p.setYRot((float)yaw);p.setYHeadRot((float)yaw);p.setXRot(0);}
    /** A still, floating target centred on the shooter's eye line, {@code distance} blocks out at {@code yaw}. */
    private static <T extends Mob> T target(GameTestHelper h,EntityType<T> type,double yaw,double distance) {
        double r=Math.toRadians(yaw);double eye=FEET_Y+EntityType.PLAYER.getDimensions().eyeHeight();
        var at=new Vec3(EYE_X-Math.sin(r)*distance,eye-type.getHeight()/2,EYE_Z+Math.cos(r)*distance);
        var mob=h.spawnWithNoFreeWill(type,at);mob.setNoGravity(true);mob.setDeltaMovement(Vec3.ZERO);return mob;
    }
    private static boolean full(LivingEntity e) {return e.getHealth()==e.getMaxHealth();}
    private static int energy(ServerPlayer p,int slot) {return PowerCellItem.energy(p.getInventory().getItem(slot));}
    private static boolean earned(ServerPlayer p,String name) {
        var holder=p.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,name));
        return holder!=null&&p.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
