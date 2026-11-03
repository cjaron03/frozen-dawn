package com.frozendawn.defense;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.TerminusItem;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerminusGameTest {
    private static final double EYE_X = 10.5, FEET_Y = 4, EYE_Z = 4.5;

    private static boolean ledgerBefore;

    /** These tests run before the Heart falls: pin the world's log there, whatever an earlier batch left. */
    @BeforeBatch(batch="terminus") public static void report(ServerLevel level) {
        GameTestReporting.installReporter(level);
        ledgerBefore=TerminusLedger.turned(level.getServer());
        TerminusLedger.setForTest(level.getServer(),false);
    }
    @AfterBatch(batch="terminus") public static void restore(ServerLevel level) {TerminusLedger.setForTest(level.getServer(),ledgerBefore);}
    @BeforeBatch(batch="terminus_terminal") public static void reportTerminal(ServerLevel level) {GameTestReporting.installReporter(level);}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusComesAsFoundWithThreeDischargesAndOneTransfer(GameTestHelper h) {
        var wand=new ItemStack(ModItems.TERMINUS.get());
        var log=wand.get(ModDataComponents.TERMINUS_LOG.get());
        h.assertTrue(log!=null&&log.discharges()==3&&log.logged()==1,"A found wand: three discharges, one transfer already logged: "+log);
        h.assertTrue(wand.getMaxStackSize()==1,"Stack of one");
        h.assertTrue(!wand.isDamageableItem(),"It has no durability to wear");
        var lines=TerminusItem.lines(log,false);
        h.assertTrue(key(lines.get(0)).equals("item.frozendawn.terminus.discharges")&&arg(lines.get(0)).equals(3),"Discharges remaining: 3");
        h.assertTrue(key(lines.get(1)).equals("item.frozendawn.terminus.transfers")&&arg(lines.get(1)).equals(1),"Transfers logged: 1");
        h.assertTrue(key(lines.get(2)).equals("item.frozendawn.terminus.exempt"),"Operator exempt. Protocol active.");
        h.assertTrue(key(lines.get(3)).equals("item.frozendawn.terminus.warning"),"NOT FOR USE IN OCCUPIED HABITATS.");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusCannotBeBurnedOrLeftToDespawn(GameTestHelper h) {
        var wand=new ItemStack(ModItems.TERMINUS.get());
        var level=h.getLevel();
        h.assertTrue(!wand.canBeHurtBy(level.damageSources().lava())&&!wand.canBeHurtBy(level.damageSources().cactus())
                &&!wand.canBeHurtBy(level.damageSources().explosion(null,null)),"Lava, fire, cactus and blasts leave it");
        var dropped=new ItemEntity(level,0,0,0,wand);
        h.assertTrue(dropped.lifespan>=Short.MAX_VALUE-1,"A dropped wand never despawns: "+dropped.lifespan);
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusStopsInsideTheConeAndMissesOutside(GameTestHelper h) {
        var op=operator(h);var wand=op.getMainHandItem();
        var ahead=target(h,EntityType.HUSK,0,6);
        var edge=target(h,EntityType.HUSK,13,10);
        var wide=target(h,EntityType.HUSK,22,6);
        var far=target(h,EntityType.HUSK,0,13.5);
        Terminus.discharge(op,wand,InteractionHand.MAIN_HAND);
        h.assertTrue(ahead.isDeadOrDying()&&edge.isDeadOrDying(),"Minds inside 12 blocks and 15 degrees are stopped");
        h.assertTrue(full(wide)&&wide.isAlive(),"Outside 30 degrees is untouched");
        h.assertTrue(full(far)&&far.isAlive(),"Beyond 12 blocks is untouched");
        var log=wand.get(ModDataComponents.TERMINUS_LOG.get());
        h.assertTrue(log.discharges()==2&&log.logged()==3,"One discharge spent, two transfers added: "+log);
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusPassesThroughWalls(GameTestHelper h) {
        var op=operator(h);
        for(int y=3;y<=8;y++)for(int x=8;x<=13;x++)h.setBlock(new BlockPos(x,y,8),Blocks.OBSIDIAN);
        var hidden=target(h,EntityType.ZOMBIE,0,8);
        Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);
        h.assertTrue(hidden.isDeadOrDying(),"No line of sight needed: obsidian stops nothing");
        h.succeed();
    }

    /** The spec's table: minds are stopped; animals, post-Maeve life, the Undone, the Heart and the Aggregate are not. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusFollowsTheImmunityTable(GameTestHelper h) {
        var op=operator(h);var level=h.getLevel();
        for(var type:List.<EntityType<?>>of(EntityType.PLAYER,EntityType.ZOMBIE,EntityType.HUSK,EntityType.DROWNED,
                EntityType.ZOMBIE_VILLAGER,EntityType.VILLAGER,EntityType.WANDERING_TRADER,EntityType.PILLAGER,
                EntityType.WITCH,ModEntities.RETURNED.get(),ModEntities.ARCHITECT.get(),ModEntities.UNDONE_ARCHITECT.get(),
                ModEntities.FROSTBITTEN.get(),ModEntities.HOLLOW.get(),ModEntities.MIMIC.get(),ModEntities.ARCHIVIST.get()))
            h.assertTrue(type.is(Terminus.SUSCEPTIBLE),"Susceptible: "+type);
        for(var type:List.<EntityType<?>>of(EntityType.PIG,EntityType.WOLF,EntityType.SKELETON,ModEntities.FROSTMITE.get(),
                ModEntities.FROSTWRITHE.get(),ModEntities.RIMEBOUND.get(),ModEntities.RESONANT.get(),ModEntities.REMNANT.get(),
                ModEntities.UNDONE.get(),ModEntities.BLOOMBOUND_UNDONE.get(),ModEntities.THAE_IVEN_HEART.get(),
                ModEntities.HEART_SUCCESSOR.get(),ModEntities.AGGREGATE.get(),ModEntities.AGGREGATE_FRAGMENT.get()))
            h.assertTrue(!type.is(Terminus.SUSCEPTIBLE),"Not susceptible: "+type);
        for(var type:List.<EntityType<?>>of(ModEntities.THAE_IVEN_HEART.get(),ModEntities.HEART_SUCCESSOR.get(),
                ModEntities.AGGREGATE.get(),ModEntities.AGGREGATE_FRAGMENT.get()))
            h.assertTrue(type.is(Terminus.IMMUNE),"Never touched, even if tagged a boss elsewhere: "+type);
        var pig=target(h,EntityType.PIG,0,5);var mite=target(h,ModEntities.FROSTMITE.get(),5,7);
        var rimebound=target(h,ModEntities.RIMEBOUND.get(),-5,9);
        Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);
        h.assertTrue(full(pig)&&full(mite)&&full(rimebound)&&!pig.isRemoved()&&!mite.isRemoved()&&!rimebound.isRemoved(),
                "Animals, Frostmites and post-Maeve life are untouched");
        var log=op.getMainHandItem().get(ModDataComponents.TERMINUS_LOG.get());
        h.assertTrue(log.logged()==1,"Nothing was logged: "+log);
        var held=target(h,EntityType.HUSK,0,8);held.setInvulnerable(true);op.setGameMode(GameType.CREATIVE);
        Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);
        h.assertTrue(held.isAlive(),"An invulnerable mob is spared, even by a creative operator");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusTakesFortyPercentFromBossesAndNeverKills(GameTestHelper h) {
        var op=operator(h);var wand=op.getMainHandItem();
        var wither=target(h,EntityType.WITHER,0,7);float max=wither.getMaxHealth();
        Terminus.discharge(op,wand,InteractionHand.MAIN_HAND);
        h.assertTrue(Math.abs(wither.getHealth()-max*.6f)<1e-3,"A boss loses 40% of its maximum: "+wither.getHealth());
        Terminus.discharge(op,wand,InteractionHand.MAIN_HAND);Terminus.discharge(op,wand,InteractionHand.MAIN_HAND);
        h.assertTrue(wither.isAlive()&&wither.getHealth()>=1,"Never a kill: "+wither.getHealth());
        h.assertTrue(wand.get(ModDataComponents.TERMINUS_LOG.get()).logged()==1,"A wounded boss is not a transfer");
        wither.discard();
        h.succeed();
    }

    /** The Master watches the wand, not the operator, then goes back to what it was doing. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=200)
    public static void terminusMasterArchitectLooksAtTheWandNotThePlayer(GameTestHelper h) {
        var op=operator(h);var level=h.getLevel();
        var master=ModEntities.ARCHITECT.get().create(level);
        master.setPos(h.absoluteVec(new Vec3(EYE_X,FEET_Y,EYE_Z+6)));master.setNoAi(true);master.setNoGravity(true);
        master.bindToHearthMasterArchitect(UUID.randomUUID(),master.blockPosition(),0);
        master.setYRot(90);master.setYHeadRot(90);master.setXRot(0);
        level.addFreshEntity(master);
        float health=master.getHealth();
        Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);
        h.runAfterDelay(3,()->{
            try {
                h.assertTrue(master.isAlive()&&!master.isRemoved()&&master.getHealth()==health,"The Master is immune");
                var wand=Terminus.wandPosition(op,InteractionHand.MAIN_HAND);
                var gaze=Vec3.directionFromRotation(master.getXRot(),master.getYHeadRot());
                double toWand=angle(gaze,wand.subtract(master.getEyePosition()));
                double toEyes=angle(gaze,op.getEyePosition().subtract(master.getEyePosition()));
                h.assertTrue(toWand<2,"Its head turns to the wand: "+toWand+" degrees off");
                h.assertTrue(toEyes>3,"Not to the operator's face: "+toEyes+" degrees off");
            } catch(RuntimeException e) {master.discard();throw e;}
            h.runAfterDelay(Terminus.GAZE_TICKS,()->{
                h.assertTrue(!TerminusGaze.watching(master),"Then it resumes");
                master.discard();h.succeed();
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusSparesItsOperatorButNotOtherPlayers(GameTestHelper h) {
        var server=h.getLevel().getServer();boolean pvp=server.isPvpAllowed();
        var op=operator(h);
        var other=player(h,"terminus_other",new Vec3(EYE_X,FEET_Y,EYE_Z+5));
        var creative=player(h,"terminus_creative",new Vec3(EYE_X+1,FEET_Y,EYE_Z+7));creative.setGameMode(GameType.CREATIVE);
        try {
            server.setPvpAllowed(false);
            Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);
            h.assertTrue(other.isAlive(),"A server with PvP off spares other players");
            server.setPvpAllowed(true);
            Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);
            h.assertTrue(other.isDeadOrDying(),"Otherwise another player in the cone is stopped");
            h.assertTrue(!creative.isDeadOrDying(),"A creative player cannot be");
            var board=server.getScoreboard();var team=board.addPlayerTeam("terminus_crew_"+UUID.randomUUID().toString().substring(0,8));
            team.setAllowFriendlyFire(false);
            var mate=player(h,"terminus_mate",new Vec3(EYE_X-1,FEET_Y,EYE_Z+6));
            board.addPlayerToTeam(op.getScoreboardName(),team);board.addPlayerToTeam(mate.getScoreboardName(),team);
            try {
                Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);
                h.assertTrue(mate.isAlive(),"A teammate is spared where friendly fire is off");
            } finally {board.removePlayerTeam(team);}
            h.assertTrue(op.isAlive()&&op.getHealth()==op.getMaxHealth(),"The operator takes nothing from the cone");
            h.assertTrue(op.getMainHandItem().get(ModDataComponents.TERMINUS_LOG.get()).logged()==2,"The other player is a transfer");
        } finally {server.setPvpAllowed(pvp);}
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusCostsFourMaxHealthForGoodThroughRespawn(GameTestHelper h) {
        var op=operator(h);var wand=op.getMainHandItem();
        Terminus.discharge(op,wand,InteractionHand.MAIN_HAND);
        h.assertTrue(op.getMaxHealth()==16,"Two hearts gone: "+op.getMaxHealth());
        h.assertTrue(op.getHealth()<=16,"Health cannot sit above the new maximum");
        var dark=op.getEffect(MobEffects.DARKNESS);var sick=op.getEffect(MobEffects.CONFUSION);
        h.assertTrue(dark!=null&&dark.getDuration()==Terminus.BACKLASH_TICKS&&sick!=null&&sick.getDuration()==Terminus.BACKLASH_TICKS,
                "Ten seconds of Darkness and Nausea");
        h.assertTrue(earned(op,"deathwand"),"Deathwand, on discharge");
        Terminus.discharge(op,wand,InteractionHand.MAIN_HAND);
        h.assertTrue(op.getMaxHealth()==12,"Four more: "+op.getMaxHealth());
        // Respawn, as PlayerList does it: a fresh player restored from the dead one.
        var reborn=new ServerPlayer(op.server,h.getLevel(),op.getGameProfile(),ClientInformation.createDefault());
        reborn.connection=op.connection;
        h.getLevel().removePlayerImmediately(op,Entity.RemovalReason.KILLED);
        reborn.restoreFrom(op,false);
        reborn.setPos(op.position());GameTestPlayers.join(h,reborn);
        h.assertTrue(reborn.getMaxHealth()==12,"The loss survives death and respawn: "+reborn.getMaxHealth());
        h.assertTrue(reborn.getData(ModAttachments.TERMINUS_TOLL.get())==2,"Two discharges on record");
        // Back through the End portal, as PlayerList does it: a fresh player that keeps everything.
        var returned=new ServerPlayer(op.server,h.getLevel(),op.getGameProfile(),ClientInformation.createDefault());
        returned.connection=op.connection;
        h.getLevel().removePlayerImmediately(reborn,Entity.RemovalReason.CHANGED_DIMENSION);
        returned.restoreFrom(reborn,true);
        returned.setPos(reborn.position());GameTestPlayers.join(h,returned);
        h.assertTrue(returned.getMaxHealth()==12,"And the End portal's return: "+returned.getMaxHealth());
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusWindsUpForTwoSeconds(GameTestHelper h) {
        var op=operator(h);var wand=op.getMainHandItem();
        h.assertTrue(wand.getUseDuration(op)==Terminus.WINDUP_TICKS&&Terminus.WINDUP_TICKS==40,"Two seconds of wind-up");
        h.assertTrue(ModSounds.TERMINUS_WINDUP.get().getRange(1f)==24f,"The tone carries 24 blocks");
        var husk=target(h,EntityType.HUSK,0,6);
        wand.use(h.getLevel(),op,InteractionHand.MAIN_HAND);
        h.assertTrue(op.isUsingItem(),"Holding use winds it up");
        wand.finishUsingItem(h.getLevel(),op);
        h.assertTrue(husk.isDeadOrDying(),"At the end of the wind-up it discharges");
        // Use held down is retried every four ticks; a cooldown keeps it from winding straight into another discharge.
        h.assertTrue(op.getCooldowns().isOnCooldown(ModItems.TERMINUS.get()),"A discharge leaves it on cooldown");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusDamageDuringTheWindUpSpendsTheDischarge(GameTestHelper h) {
        var op=operator(h);var wand=op.getMainHandItem();
        var husk=target(h,EntityType.HUSK,0,6);
        op.startUsingItem(InteractionHand.MAIN_HAND);op.releaseUsingItem();
        h.assertTrue(wand.get(ModDataComponents.TERMINUS_LOG.get()).discharges()==3,"Letting go early costs nothing");
        op.startUsingItem(InteractionHand.MAIN_HAND);
        op.hurt(h.getLevel().damageSources().generic(),1);
        h.assertTrue(!op.isUsingItem(),"A hit cancels the wind-up");
        h.assertTrue(op.getCooldowns().isOnCooldown(ModItems.TERMINUS.get()),"And leaves it on cooldown, so held use cannot burn the rest");
        var log=wand.get(ModDataComponents.TERMINUS_LOG.get());
        h.assertTrue(log.discharges()==2&&log.logged()==1,"The discharge is still spent: "+log);
        h.assertTrue(husk.isAlive()&&op.getMaxHealth()==20,"Nothing fired and nothing more was taken from the operator");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusIsAnInertTrophyAtZero(GameTestHelper h) {
        var op=operator(h);var wand=op.getMainHandItem();
        wand.set(ModDataComponents.TERMINUS_LOG.get(),new TerminusLog(0,4));
        var husk=target(h,EntityType.HUSK,0,6);
        var result=wand.use(h.getLevel(),op,InteractionHand.MAIN_HAND);
        h.assertTrue(!result.getResult().consumesAction()&&!op.isUsingItem(),"It will not wind up");
        Terminus.discharge(op,wand,InteractionHand.MAIN_HAND);
        h.assertTrue(husk.isAlive()&&op.getMaxHealth()==20,"And nothing happens");
        h.succeed();
    }

    /** Before the Heart falls, the stopped leave nothing behind, though the death event still fires for the Hearth. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusLeavesNoDropsBeforeTheHeartFalls(GameTestHelper h) {
        var op=operator(h);
        var zombie=target(h,EntityType.ZOMBIE,0,6);
        zombie.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));zombie.setDropChance(EquipmentSlot.MAINHAND,2f);
        // A tick old, so a kill credit would count (see terminusKillsNormallyAfterTheHeartFalls).
        h.runAfterDelay(1,()->{
            var deaths=new ArrayList<LivingDeathEvent>();
            Consumer<LivingDeathEvent> seen=deaths::add;
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL,false,LivingDeathEvent.class,seen);
            try {Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);}
            finally {NeoForge.EVENT_BUS.unregister(seen);}
            // Its own die() runs, so whatever keeps a roll of the living (Hearth residents, villager beds, Maeve's
            // roster) hears of it; it drops where it stands and is gone with the ordinary death clock.
            h.assertTrue(zombie.isDeadOrDying()&&!zombie.isRemoved(),"It dies where it stands");
            h.assertTrue(deaths.stream().filter(e->e.getEntity()==zombie&&e.getSource().getEntity()==op).count()==1,"One death event, naming the operator");
            h.assertTrue(around(h,ExperienceOrb.class).isEmpty(),"No experience");
            h.assertTrue(op.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.ZOMBIE))==1,"The operator answers for it");
            // Vanilla takes a body away at 20 ticks; a stopped one stands for the crumble, then goes without a poof.
            h.runAfterDelay(25,()->h.assertTrue(!zombie.isRemoved(),"It stands while it crumbles"));
            h.runAfterDelay(Terminus.DEATH_TICKS+2,()->{
                h.assertTrue(zombie.isRemoved(),"Then it is gone");
                h.assertTrue(around(h,ItemEntity.class).isEmpty(),"No drops, not even guaranteed equipment");
                h.assertTrue(around(h,ExperienceOrb.class).isEmpty()&&op.totalExperience==0,"Still no experience");
                h.succeed();
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus",timeoutTicks=100)
    public static void terminusLabelDoesNotTurnWhileMaeveStands(GameTestHelper h) {
        var server=h.getLevel().getServer();
        h.assertTrue(!com.frozendawn.homo.PostMaeveWorldState.isErased(server),"The test server's Maeve stands");
        var dying=player(h,"terminus_dying",new Vec3(EYE_X,FEET_Y,EYE_Z));
        try {
            dying.hurt(h.getLevel().damageSources().genericKill(),Float.MAX_VALUE);
            h.assertTrue(dying.isDeadOrDying()&&!TerminusLedger.turned(server),"A death before the Heart falls changes nothing");
        } finally {TerminusLedger.setForTest(server,false);}
        h.succeed();
    }

    /** After the CH-18 trigger the wand kills as anything else does, credited to its operator. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_terminal",timeoutTicks=100)
    public static void terminusKillsNormallyAfterTheHeartFalls(GameTestHelper h) {
        var server=h.getLevel().getServer();boolean was=TerminusLedger.turned(server);
        var op=operator(h);
        var zombie=target(h,EntityType.ZOMBIE,0,6);
        zombie.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));zombie.setDropChance(EquipmentSlot.MAINHAND,2f);
        // Credit lasts as many ticks as the mob has lived, as vanilla's setLastHurtByPlayer has it: give it one.
        h.runAfterDelay(1,()->{
            TerminusLedger.setForTest(server,true);
            try {Terminus.discharge(op,op.getMainHandItem(),InteractionHand.MAIN_HAND);}
            finally {TerminusLedger.setForTest(server,was);}
            h.assertTrue(zombie.isDeadOrDying()&&!zombie.isRemoved(),"It dies the ordinary way");
            h.assertTrue(!around(h,ExperienceOrb.class).isEmpty(),"Its experience drops");
            h.runAfterDelay(25,()->h.assertTrue(!zombie.isRemoved(),"After the Heart it crumbles too"));
            h.runAfterDelay(Terminus.DEATH_TICKS+2,()->{
                h.assertTrue(zombie.isRemoved(),"Then it is gone");
                h.assertTrue(around(h,ItemEntity.class).stream().anyMatch(i->i.getItem().is(Items.IRON_SWORD)),"Its drops fall");
                h.assertTrue(op.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.ZOMBIE))==1,"The kill is the operator's");
                h.succeed();
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_terminal",timeoutTicks=100)
    public static void terminusLabelReadsTerminationsOnceTurned(GameTestHelper h) {
        var log=new TerminusLog(2,5);
        var before=TerminusItem.lines(log,false).get(1);var after=TerminusItem.lines(log,true).get(1);
        h.assertTrue(key(before).equals("item.frozendawn.terminus.transfers")&&arg(before).equals(5),"Transfers logged: 5");
        h.assertTrue(key(after).equals("item.frozendawn.terminus.terminations")&&arg(after).equals(5),"Terminations logged: 5, the count carried over");
        var server=h.getLevel().getServer();boolean was=TerminusLedger.turned(server);
        try {
            TerminusLedger.setForTest(server,true);
            h.assertTrue(TerminusLedger.turned(server),"The world remembers it turned");
        } finally {TerminusLedger.setForTest(server,was);}
        h.succeed();
    }

    private static ServerPlayer operator(GameTestHelper h) {
        var p=player(h,"terminus_operator",new Vec3(EYE_X,FEET_Y,EYE_Z));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.TERMINUS.get()));
        return p;
    }
    private static ServerPlayer player(GameTestHelper h,String name,Vec3 at) {
        var level=h.getLevel();var profile=new GameProfile(UUID.randomUUID(),name);
        var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        player.connection=new net.neoforged.neoforge.common.util.FakePlayer(level,profile).connection;
        player.setPos(h.absoluteVec(at));player.setGameMode(GameType.SURVIVAL);GameTestPlayers.join(h,player);
        try {var field=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(player,0);}
        catch(ReflectiveOperationException e) {throw new IllegalStateException("Test players must lose spawn protection",e);}
        player.setYRot(0);player.setYHeadRot(0);player.setXRot(0);
        return player;
    }
    /** A still, floating target centred on the operator's eye line, {@code distance} blocks out at {@code yaw}. */
    private static <T extends Mob> T target(GameTestHelper h,EntityType<T> type,double yaw,double distance) {
        double r=Math.toRadians(yaw);double eye=FEET_Y+EntityType.PLAYER.getDimensions().eyeHeight();
        var at=new Vec3(EYE_X-Math.sin(r)*distance,eye-type.getHeight()/2,EYE_Z+Math.cos(r)*distance);
        var mob=h.spawnWithNoFreeWill(type,at);mob.setNoGravity(true);mob.setDeltaMovement(Vec3.ZERO);return mob;
    }
    private static <E extends Entity> List<E> around(GameTestHelper h,Class<E> type) {
        return h.getLevel().getEntitiesOfClass(type,new AABB(h.absolutePos(BlockPos.ZERO)).inflate(24));
    }
    private static double angle(Vec3 a,Vec3 b) {return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,a.normalize().dot(b.normalize())))));}
    private static boolean full(LivingEntity e) {return e.getHealth()==e.getMaxHealth();}
    private static String key(Component c) {return ((TranslatableContents)c.getContents()).getKey();}
    private static Object arg(Component c) {return ((TranslatableContents)c.getContents()).getArgs()[0];}
    private static boolean earned(ServerPlayer p,String name) {
        var holder=p.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,name));
        return holder!=null&&p.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
