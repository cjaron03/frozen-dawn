package com.frozendawn.vault;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.defense.Terminus;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.vault.ArgusFight.Phase;
import com.mojang.authlib.GameProfile;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** The Watch Floor, built whole in the air over each test, and played the way the server judges a player. Its shell shuts
 * out the neighbouring tests' rays, so these need no batch of their own. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerminusWatchFloorGameTest {
    private static final float NORTH=180,SOUTH=0,EAST=-90,WEST=90;
    private static final int LOWER=TerminusVaultBuilder.LOWER_FLOOR+1,UPPER=TerminusVaultBuilder.FLOOR+1;

    @BeforeBatch(batch="terminus_floor") public static void report(ServerLevel level) {GameTestReporting.installReporter(level);}

    private record Floor(GameTestHelper h,BlockPos hall,VaultArgusBlockEntity argus) {
        ServerLevel level() {return h.getLevel();}
        ArgusFight fight() {return argus.fight();}
        CustodianEntity boss() {return argus.custodian();}
        Vec3 at(double x,double y,double z) {return Vec3.atLowerCornerOf(hall).add(x,y,z);}
        ServerPlayer player(String name,double x,double y,double z,float yaw) {
            var profile=new GameProfile(UUID.randomUUID(),name);
            var player=new ServerPlayer(level().getServer(),level(),profile,ClientInformation.createDefault());
            player.connection=new net.neoforged.neoforge.common.util.FakePlayer(level(),profile).connection;
            player.setPos(at(x,y,z));player.setGameMode(GameType.SURVIVAL);GameTestPlayers.join(h,player);
            look(player,yaw);
            return player;
        }
        /** Someone on the far catwalk, in the fight, facing nothing but wall. */
        ServerPlayer reader() {return player("floor_reader",30.5,UPPER,10.5,SOUTH);}
    }
    private static void look(ServerPlayer p,float yaw) {p.setYRot(yaw);p.setYHeadRot(yaw);p.setXRot(0);}

    private static Floor floor(GameTestHelper h) {
        var hall=h.absolutePos(BlockPos.ZERO).offset(-17,0,0).atY(150);
        TerminusVaultBuilder.watchFloor(h.getLevel(),hall);
        // Floating, the hall's roof would be the ground: a thermal vent whose region lands here dug rock into it. A sheet over it takes that instead.
        for(int x=17;x<=40;x++) for(int z=-12;z<=12;z++) h.getLevel().setBlock(hall.offset(x,-2,z),Blocks.STONE.defaultBlockState(),2);
        return new Floor(h,hall,(VaultArgusBlockEntity)h.getLevel().getBlockEntity(hall.offset(TerminusVaultBuilder.ARGUS)));
    }
    /** A test can start before its chunks tick block entities, and these count ticks: wait, and for the Custodian too. */
    private static GameTestSequence begin(Floor f) {
        return f.h.startSequence().thenWaitUntil(()->f.h.assertTrue(ticking(f)&&f.boss()!=null,"Chunks are ticking and the Custodian stands"));
    }
    private static boolean ticking(Floor f) {
        var level=f.level();var at=f.argus.getBlockPos();
        return level.shouldTickBlocksAt(at)&&level.getChunkAt(at).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)&&level.areEntitiesLoaded(ChunkPos.asLong(at));
    }
    private static int slotOf(Floor f,int glyph) {return IntStream.range(0,6).filter(s->f.fight().fuses()[s]==glyph).findFirst().orElseThrow();}
    private static int wrongSlot(Floor f) {return IntStream.range(0,6).filter(s->Arrays.stream(f.fight().wanted()).noneMatch(g->g==f.fight().fuses()[s])).findFirst().orElseThrow();}
    /** Two seconds of held use, as a client repeats it. */
    private static void pull(Floor f,ServerPlayer p,int slot) {
        long start=f.level().getGameTime();
        for(int i=0;i<ArgusFight.HOLD_TICKS/4;i++) f.fight().hold(slot,start-ArgusFight.HOLD_TICKS-4+i*4);
        f.argus.holdFuse(p,slot);
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void theFloorStandsAsDrawn(GameTestHelper h) {
        var f=floor(h);var level=f.level();var hall=f.hall;
        for(var at:new BlockPos[] {new BlockPos(18,TerminusVaultBuilder.FLOOR,0),new BlockPos(19,TerminusVaultBuilder.FLOOR,5),new BlockPos(39,TerminusVaultBuilder.FLOOR,0),new BlockPos(28,TerminusVaultBuilder.FLOOR,-11),new BlockPos(28,TerminusVaultBuilder.FLOOR,10)})
            h.assertTrue(level.getBlockState(hall.offset(at)).is(ModBlocks.VAULT_WALKWAY.get()),"The catwalk ring at "+at);
        h.assertTrue(level.getBlockState(hall.offset(28,TerminusVaultBuilder.FLOOR,0)).isAir(),"The middle is open down");
        h.assertTrue(level.getBlockState(hall.offset(28,TerminusVaultBuilder.LOWER_FLOOR,0)).is(ModBlocks.VAULT_FLOOR.get()),"The lower floor");
        for(int slot=0;slot<6;slot++) h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.FUSES[slot])).is(ModBlocks.VAULT_FUSE.get()),"Fuse "+slot);
        for(int glyph=0;glyph<6;glyph++) h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.BOARD[glyph])).is(ModBlocks.VAULT_BOARD.get()),"Board "+glyph);
        h.assertTrue(TerminusVaultBuilder.FUSES[0].getZ()<0&&TerminusVaultBuilder.FUSES[5].getZ()>0,"Fuses north and south");
        for(var ladder:TerminusVaultBuilder.LADDERS) for(int y=LOWER;y<=TerminusVaultBuilder.FLOOR;y++)
            h.assertTrue(level.getBlockState(hall.offset(ladder.getX(),y,ladder.getZ())).is(Blocks.LADDER),"An unbroken ladder at "+ladder+", "+y);
        for(var clock:TerminusVaultBuilder.CLOCKS) h.assertTrue(level.getBlockEntity(hall.offset(clock)) instanceof VaultFightClockBlockEntity,"A clock at "+clock);
        h.assertTrue(TerminusVaultBuilder.LIGHTS.size()==24&&TerminusVaultBuilder.LIGHTS.stream().allMatch(l->level.getBlockState(hall.offset(l)).getValue(VaultLightBlock.LIT)),"The red lights, all burning");
        h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.WEST_DOOR).below(3)).isAir(),"The way in stands open");
        h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.EAST_DOOR).below(3)).is(ModBlocks.VAULT_DOOR_CELL.get()),"The way on is shut");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void theWestCatwalkIsSafeAndWalkingInSealsTheDoor(GameTestHelper h) {
        var f=floor(h);var op=f.player("floor_guest",18.5,UPPER,5.5,NORTH);
        begin(f).thenExecuteAfter(5,()->{
                    h.assertTrue(f.fight().phase()==Phase.DORMANT,"The west catwalk starts nothing");
                    h.assertTrue(f.argus.announcing(),"The recording plays");
                })
                .thenExecute(()->op.setPos(f.at(24.5,UPPER,-10.5)))
                .thenExecuteAfter(3,()->{
                    h.assertTrue(f.fight().running(),"Past it, the fight is on");
                    h.assertTrue(f.level().getBlockState(f.hall.offset(TerminusVaultBuilder.WEST_DOOR).below(3)).is(ModBlocks.VAULT_DOOR_CELL.get()),"The door seals behind them");
                    h.assertTrue(!f.argus.announcing(),"The recording is cut off");
                }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void theCustodianMovesOnlyWhileNoMindFacesIt(GameTestHelper h) {
        var f=floor(h);var op=f.player("floor_stare",28.5,LOWER,-9.5,SOUTH);
        double[] was=new double[1];
        begin(f).thenExecuteAfter(30,()->{
                    was[0]=f.boss().position().distanceTo(op.position());
                    h.assertTrue(was[0]>8.5,"Faced, it has not stirred: "+was[0]);
                })
                .thenExecute(()->look(op,NORTH))
                .thenExecuteAfter(15,()->h.assertTrue(f.boss().position().distanceTo(op.position())<was[0]-1,"Back turned, it comes"))
                .thenExecute(()->look(op,SOUTH))
                .thenExecuteAfter(3,()->was[0]=f.boss().position().distanceTo(op.position()))
                .thenExecuteAfter(20,()->h.assertTrue(Math.abs(f.boss().position().distanceTo(op.position())-was[0])<.05,"Faced again, it stops dead"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void itsTouchTakesHeartsAndAFacedOneTakesNone(GameTestHelper h) {
        var f=floor(h);
        var op=f.player("floor_touched",28.5,LOWER,3.5,NORTH);
        var watcher=f.player("floor_watcher",28.5,LOWER,-9.5,SOUTH);
        begin(f).thenExecuteAfter(30,()->h.assertTrue(op.getHealth()==op.getMaxHealth(),"Someone stares: it holds, however near"))
                .thenExecute(()->look(watcher,NORTH))
                .thenExecute(()->look(op,SOUTH))
                .thenWaitUntil(()->h.assertTrue(op.getHealth()<=op.getMaxHealth()-CustodianEntity.TOUCH*.5f,"Unwatched, it reaches them: "+op.getHealth()))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void aHeldFuseCountsAndAWrongOneCostsTwentySeconds(GameTestHelper h) {
        var f=floor(h);var op=f.reader();
        begin(f).thenWaitUntil(()->h.assertTrue(f.fight().phase()==Phase.READ,"Someone up top: the board lights"))
                .thenExecute(()->{
                    for(int glyph=0;glyph<6;glyph++) {
                        var lamp=f.level().getBlockState(f.hall.offset(TerminusVaultBuilder.BOARD[glyph])).getValue(VaultBoardBlock.LAMP);
                        int g=glyph;
                        h.assertTrue((lamp==VaultBoardBlock.Lamp.LIT)==Arrays.stream(f.fight().wanted()).anyMatch(w->w==g),"The board shows the wanted glyphs");
                    }
                })
                .thenWaitUntil(()->h.assertTrue(f.fight().phase()==Phase.PULL,"Then it goes dark"))
                .thenExecuteAfter(2,()->{
                    for(int slot=0;slot<6;slot++) {
                        var fuse=f.level().getBlockState(f.hall.offset(TerminusVaultBuilder.FUSES[slot]));
                        h.assertTrue(fuse.getValue(VaultFuseBlock.GLYPH)==f.fight().fuses()[slot],"Each fuse shows its shuffled glyph");
                        h.assertTrue(fuse.getValue(VaultBoardBlock.LAMP)==VaultBoardBlock.Lamp.LIT,"And is live");
                    }
                    int right=slotOf(f,f.fight().wanted()[0]),other=slotOf(f,f.fight().wanted()[1]),clock=f.fight().clock();
                    f.argus.holdFuse(op,other);
                    h.assertTrue(!f.fight().pulled(other),"One press is no pull");
                    pull(f,op,right);
                    h.assertTrue(f.fight().pulled(right),"Two seconds held is");
                    h.assertTrue(f.level().getBlockState(f.hall.offset(TerminusVaultBuilder.FUSES[right])).getValue(VaultBoardBlock.LAMP)==VaultBoardBlock.Lamp.PULLED,"And it shows");
                    pull(f,op,wrongSlot(f));
                    h.assertTrue(f.fight().clock()<=clock-ArgusFight.PENALTY_TICKS,"A wrong fuse takes twenty seconds: "+f.fight().clock());
                    h.assertTrue(!f.fight().clean(),"And spoils the clean run");
                }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void theShellGuardsItAndAnOpeningTakesAThirdAtMost(GameTestHelper h) {
        var f=floor(h);var op=f.reader();
        begin(f).thenExecute(()->{
                    h.assertTrue(!f.boss().hurt(f.level().damageSources().playerAttack(op),50),"Shell shut, nothing hurts it");
                    h.assertTrue(f.boss().getHealth()==CustodianEntity.HEALTH,"Not a mark");
                    f.fight().start();
                    f.fight().jump(0,Phase.BURN,ArgusFight.CLOCK_TICKS);
                })
                .thenExecuteAfter(2,()->{
                    h.assertTrue(f.boss().shellOpen(),"Shell open");
                    f.boss().hurt(f.level().damageSources().playerAttack(op),CustodianEntity.HEALTH);
                    h.assertTrue(Math.abs(f.boss().getHealth()-CustodianEntity.HEALTH*2/3)<.01,"A third and no more: "+f.boss().getHealth());
                    h.assertTrue(f.boss().isAlive(),"It stands");
                })
                .thenExecuteAfter(2,()->{
                    h.assertTrue(Math.abs(f.argus.barProgress()-2/3f)<.01,"The bar shows it: "+f.argus.barProgress());
                    h.assertTrue(!f.boss().shellOpen()&&f.fight().phase()!=Phase.BURN,"With nothing left to give it shuts at once");
                })
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void theCustodianFallingOpensTheEastDoorAndACleanRunEarnsTheBadge(GameTestHelper h) {
        var f=floor(h);var op=f.reader();
        var east=f.hall.offset(TerminusVaultBuilder.EAST_DOOR).below(3);
        var fallen=new CustodianEntity[1];
        begin(f).thenExecute(()->{f.fight().start();f.fight().jump(2,Phase.BURN,ArgusFight.CLOCK_TICKS);})
                .thenExecuteAfter(2,()->{fallen[0]=f.boss();fallen[0].hurt(f.level().damageSources().playerAttack(op),CustodianEntity.HEALTH*2);})
                .thenExecuteAfter(2,()->{
                    h.assertTrue(f.fight().phase()==Phase.WON,"It is over");
                    h.assertTrue(f.level().getBlockState(f.hall.offset(TerminusVaultBuilder.WEST_DOOR).below(3)).isAir(),"The way back");
                    h.assertTrue(badge(f,op),"No wrong fuse, no purge: Last Badge-Out");
                    h.assertTrue(!fallen[0].isRemoved(),"It takes its time");
                    h.assertTrue(!f.level().getBlockState(east).isAir(),"And the way on waits for it");
                })
                .thenExecuteAfter(CustodianEntity.DEATH_TICKS-12,()->{
                    h.assertTrue(!fallen[0].isRemoved(),"Still kneeling");
                    h.assertTrue(!f.level().getBlockState(east).isAir(),"Still shut");
                })
                .thenWaitUntil(()->{
                    h.assertTrue(fallen[0].isRemoved(),"Gone");
                    h.assertTrue(f.level().getBlockState(east).isAir(),"The way on stands open");
                }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=600)
    public static void theLampsOverTheEastGateGoOutFuseByFuse(GameTestHelper h) {
        var f=floor(h);var op=f.reader();
        begin(f).thenExecute(()->{
                    h.assertTrue(TerminusVaultBuilder.TALLY.length==ArgusFight.LAMPS,"A lamp a fuse");
                    h.assertTrue(lit(f)==ArgusFight.LAMPS,"All burning before the fight");
                    f.fight().start();f.fight().jump(0,Phase.PULL,ArgusFight.CLOCK_TICKS);
                })
                .thenExecuteAfter(1,()->pull(f,op,wrongSlot(f)))
                .thenExecute(()->h.assertTrue(lit(f)==ArgusFight.LAMPS,"A wrong fuse puts none out"))
                .thenExecute(()->pull(f,op,slotOf(f,f.fight().wanted()[0])))
                .thenExecute(()->{
                    h.assertTrue(lit(f)==ArgusFight.LAMPS-1,"One out");
                    h.assertTrue(!f.level().getBlockState(f.hall.offset(TerminusVaultBuilder.TALLY[0])).getValue(VaultTallyBlock.LIT),"From the left as you face the gate");
                }).thenSucceed();
    }
    private static long lit(Floor f) {return Arrays.stream(TerminusVaultBuilder.TALLY).filter(at->f.level().getBlockState(f.hall.offset(at)).getValue(VaultTallyBlock.LIT)).count();}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void aWrongFuseCostsTheBadgeNotTheWin(GameTestHelper h) {
        var f=floor(h);var op=f.reader();
        begin(f).thenExecute(()->{f.fight().start();f.fight().jump(0,Phase.PULL,ArgusFight.CLOCK_TICKS);})
                .thenExecuteAfter(1,()->pull(f,op,wrongSlot(f)))
                .thenExecute(()->f.fight().jump(2,Phase.BURN,ArgusFight.CLOCK_TICKS))
                .thenExecuteAfter(2,()->f.boss().hurt(f.level().damageSources().playerAttack(op),CustodianEntity.HEALTH*2))
                .thenExecuteAfter(2,()->{
                    h.assertTrue(f.fight().phase()==Phase.WON,"Won all the same");
                    h.assertTrue(!badge(f,op),"But no badge");
                }).thenSucceed();
    }
    private static boolean badge(Floor f,ServerPlayer op) {
        var holder=f.level().getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"last_badge_out"));
        return holder!=null&&op.getAdvancements().getOrStartProgress(holder).isDone();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void thePurgeThrowsEveryoneBackAliveAndStartsOver(GameTestHelper h) {
        var f=floor(h);var op=f.reader();var low=f.player("floor_low",28.5,LOWER,-9.5,SOUTH);
        begin(f).thenExecute(()->{
                    f.fight().start();f.fight().jump(1,Phase.WAITING,10);
                    low.setHealth(3);
                })
                .thenExecuteAfter(15,()->{
                    h.assertTrue(f.fight().phase()==Phase.DORMANT&&f.fight().clock()==ArgusFight.CLOCK_TICKS,"Over, and the clock full again");
                    h.assertTrue(op.position().distanceTo(f.at(16.5,UPPER,.5))<1,"Thrown back to the ledge: "+op.position());
                    h.assertTrue(low.position().distanceTo(f.at(16.5,UPPER,.5))<1,"Everyone");
                    h.assertTrue(op.getHealth()==op.getMaxHealth()/2,"Half their hearts: "+op.getHealth());
                    h.assertTrue(low.isAlive()&&low.getHealth()==3,"It kills nobody: "+low.getHealth());
                    h.assertTrue(f.boss().getHealth()==CustodianEntity.HEALTH&&f.boss().position().distanceTo(f.at(28.5,LOWER,.5))<1,"The Custodian back on its mark, whole");
                    h.assertTrue(f.level().getBlockState(f.hall.offset(TerminusVaultBuilder.WEST_DOOR).below(3)).isAir(),"The way in stands open");
                    h.assertTrue(!f.fight().clean(),"No badge after a purge");
                }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void theLightsGoOutWithTheClockAndTheDisplaysFollowIt(GameTestHelper h) {
        var f=floor(h);f.reader();
        begin(f).thenExecuteAfter(5,()->f.fight().setClock(60*20))
                .thenExecuteAfter(25,()->{
                    long lit=TerminusVaultBuilder.LIGHTS.stream().filter(l->f.level().getBlockState(f.hall.offset(l)).getOptionalValue(VaultLightBlock.LIT).orElse(false)).count();
                    h.assertTrue(lit==2,"The last minute is nearly dark: "+lit);
                    for(var clock:TerminusVaultBuilder.CLOCKS) {
                        var be=(VaultFightClockBlockEntity)f.level().getBlockEntity(f.hall.offset(clock));
                        h.assertTrue(be.seconds()<=60&&be.seconds()>=57,"Every clock agrees: "+be.seconds());
                    }
                }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void aFightInProgressSurvivesAReload(GameTestHelper h) {
        var f=floor(h);f.reader();
        begin(f).thenExecute(()->{f.fight().start();f.fight().jump(1,Phase.PULL,3000);})
                .thenExecuteAfter(2,()->{
                    var tag=f.argus.saveWithFullMetadata(f.level().registryAccess());
                    var again=(VaultArgusBlockEntity)BlockEntity.loadStatic(f.argus.getBlockPos(),f.argus.getBlockState(),tag,f.level().registryAccess());
                    h.assertTrue(again!=null&&again.fight().phase()==Phase.PULL&&again.fight().won()==1,"Round and phase come back");
                    h.assertTrue(Arrays.equals(again.fight().fuses(),f.fight().fuses())&&again.fight().clock()==f.fight().clock(),"Fuses and clock come back");
                    var boss=f.boss().saveWithoutId(new net.minecraft.nbt.CompoundTag());
                    h.assertTrue(boss.getLong("Argus")==f.argus.getBlockPos().asLong(),"The Custodian remembers its post");
                }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void aFightWithNobodyLeftInsideEnds(GameTestHelper h) {
        var f=floor(h);var op=f.reader();
        begin(f).thenExecuteAfter(3,()->h.assertTrue(f.fight().running(),"On"))
                .thenExecute(()->op.setPos(f.at(16.5,UPPER,.5)))
                .thenExecuteAfter(VaultArgusBlockEntity.ABANDON_TICKS+5,()->{
                    h.assertTrue(f.fight().phase()==Phase.DORMANT,"Dead or gone, it stands down");
                    h.assertTrue(f.level().getBlockState(f.hall.offset(TerminusVaultBuilder.WEST_DOOR).below(3)).isAir(),"The door opens for the walk back");
                    h.assertTrue(f.fight().clean(),"A death is not a purge");
                }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void nothingIsPlacedOnTheFloorUntilItFalls(GameTestHelper h) {
        var f=floor(h);var state=HelionSiteState.get(f.level().getServer());var was=state.vault();var inside=f.hall.offset(28,LOWER,3);
        try {
            state.setVaultForTest(f.hall);
            h.assertTrue(VaultArgusBlockEntity.refuses(f.level(),inside),"No torches, no walls");
            for(var gap:new BlockPos[]{f.hall.offset(6,UPPER,-2),f.hall.offset(10,UPPER-1,0),f.hall.offset(14,TerminusVaultBuilder.CEILING-1,2),f.hall.offset(10,TerminusVaultBuilder.FLOOR-8,0)})
                h.assertTrue(VaultArgusBlockEntity.refuses(f.level(),gap),"Nothing built over the bridge's gap, at "+gap);
            h.assertTrue(!VaultArgusBlockEntity.refuses(f.level(),f.hall.offset(5,UPPER,0))&&!VaultArgusBlockEntity.refuses(f.level(),f.hall.offset(15,UPPER,0)),"The ledges are free");
            f.fight().start();f.fight().fell();
            h.assertTrue(!VaultArgusBlockEntity.refuses(f.level(),inside),"Once it falls the floor is theirs");
        } finally {state.setVaultForTest(was);}
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_floor",timeoutTicks=2000)
    public static void theCustodianIsNoMindAndNoTargetForTheWand(GameTestHelper h) {
        var type=ModEntities.CUSTODIAN.get();
        h.assertTrue(!type.is(Terminus.SUSCEPTIBLE),"No mind: it freezes no door");
        h.assertTrue(type.is(Terminus.IMMUNE),"And the wand has nothing to stop");
        h.succeed();
    }
}
