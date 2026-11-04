package com.frozendawn.vault;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.defense.Terminus;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.vault.TwoPersonRule.Phase;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** The drill hall and the Morgue, each built whole in the air over its test and played the way the server judges a player. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerminusLastRoomsGameTest {
    private static final int FEET=TerminusVaultBuilder.FLOOR+1;
    private static final BlockPos DRILL=TerminusVaultBuilder.DRILL_DOOR,MORGUE=TerminusVaultBuilder.MORGUE_DOOR;

    @BeforeBatch(batch="terminus_rooms") public static void report(ServerLevel level) {GameTestReporting.installReporter(level);}
    @BeforeBatch(batch="terminus_rooms_alone") public static void reportAlone(ServerLevel level) {GameTestReporting.installReporter(level);}

    private record Room(GameTestHelper h,BlockPos hall) {
        ServerLevel level() {return h.getLevel();}
        Vec3 at(double x,double z) {return Vec3.atLowerCornerOf(hall).add(x,FEET,z);}
        ServerPlayer player(String name,double x,double z) {
            var profile=new GameProfile(UUID.randomUUID(),name);
            var player=new ServerPlayer(level().getServer(),level(),profile,ClientInformation.createDefault());
            player.connection=new net.neoforged.neoforge.common.util.FakePlayer(level(),profile).connection;
            player.setPos(at(x,z));player.setGameMode(GameType.SURVIVAL);GameTestPlayers.join(h,player);
            return player;
        }
        boolean shut(BlockPos door) {return level().getBlockState(hall.offset(door).below(3)).is(ModBlocks.VAULT_DOOR_CELL.get());}
        boolean open(BlockPos door) {return level().getBlockState(hall.offset(door).below(3)).isAir();}
        boolean lampLit(BlockPos door) {return level().getBlockState(hall.offset(door).below()).getValue(VaultTallyBlock.LIT);}
    }
    private static Room room(GameTestHelper h,int west) {return new Room(h,h.absolutePos(BlockPos.ZERO).offset(-west,0,8).atY(150));}
    private static Room drill(GameTestHelper h) {var r=room(h,65);TerminusVaultBuilder.drillHall(r.level(),r.hall);return r;}
    private static Room morgue(GameTestHelper h) {var r=room(h,83);TerminusVaultBuilder.morgue(r.level(),r.hall);return r;}
    private static VaultRecorderBlockEntity recorder(Room r) {return (VaultRecorderBlockEntity)r.level().getBlockEntity(r.hall.offset(TerminusVaultBuilder.RECORDER));}
    private static VaultVitalsBlockEntity vitals(Room r) {return (VaultVitalsBlockEntity)r.level().getBlockEntity(r.hall.offset(TerminusVaultBuilder.VITALS));}
    /** A test can start before its chunks tick block entities, and these count ticks. */
    private static GameTestSequence begin(Room r,BlockPos at) {
        return r.h.startSequence().thenWaitUntil(()->{
            var level=r.level();var pos=r.hall.offset(at);
            r.h.assertTrue(level.shouldTickBlocksAt(pos)&&level.getChunkAt(pos).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)&&level.areEntitiesLoaded(ChunkPos.asLong(pos)),"Chunks are ticking");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=3000)
    public static void aRecordingHoldsOneKeyWhileYouHoldTheOther(GameTestHelper h) {
        var r=drill(h);var rec=recorder(r);var op=r.player("drill_solo",72.5,-5.5);
        begin(r,TerminusVaultBuilder.RECORDER).thenExecute(()->{
                    rec.press(op);
                    h.assertTrue(rec.rule().phase()==Phase.RECORDING,"Recording");
                })
                .thenWaitUntil(()->{
                    rec.holdKey(op,0);
                    h.assertTrue(rec.rule().phase()==Phase.PLAYING,"Twenty seconds on the key");
                })
                .thenExecute(()->{
                    h.assertTrue(rec.ghost()!=null&&rec.ghost().isAlive(),"The figure stands");
                    h.assertTrue(rec.ghost().position().distanceTo(r.at(72.5,-5.5))<.01,"Where you stood");
                    op.setPos(r.at(79.5,5.5));
                })
                .thenExecuteAfter(12,()->{
                    h.assertTrue(rec.rule().held(0,r.level().getGameTime()),"The figure holds key A with nobody's hand on it");
                    h.assertTrue(r.level().getBlockState(r.hall.offset(TerminusVaultBuilder.KEYS[0])).getValue(VaultKeyBlock.HELD),"And the key shows it");
                    h.assertTrue(r.shut(DRILL),"One key opens nothing");
                    rec.holdKey(op,1);
                })
                .thenExecuteAfter(2,()->{
                    h.assertTrue(r.open(DRILL)&&r.lampLit(DRILL),"Both held at once: the door opens and the lamp goes green");
                })
                .thenExecuteAfter(TwoPersonRule.OPEN_TICKS+8,()->h.assertTrue(r.shut(DRILL)&&!r.lampLit(DRILL),"Nobody came: it shuts"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void aSecondPressPlaysTheTakeAndTheProjectorShowsIt(GameTestHelper h) {
        var r=drill(h);var rec=recorder(r);var op=r.player("drill_ready",68.5,2.5);
        begin(r,TerminusVaultBuilder.RECORDER).thenExecute(()->rec.press(op))
                .thenExecuteAfter(2,()->h.assertTrue(mode(r)==VaultRecorderBlock.Mode.RECORDING,"The lens shows it taking"))
                .thenExecuteAfter(TwoPersonRule.MIN_TAKE_TICKS,()->rec.press(op))
                .thenExecuteAfter(2,()->{
                    h.assertTrue(rec.rule().phase()==Phase.PLAYING&&rec.ghost()!=null,"Ready when you say");
                    h.assertTrue(mode(r)==VaultRecorderBlock.Mode.PLAYING,"The lens shows it playing");
                }).thenSucceed();
    }
    private static VaultRecorderBlock.Mode mode(Room r) {return r.level().getBlockState(r.hall.offset(TerminusVaultBuilder.PROJECTOR)).getValue(VaultRecorderBlock.MODE);}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void twoPlayersNeedNoRecorder(GameTestHelper h) {
        var r=drill(h);var rec=recorder(r);var a=r.player("drill_a",72.5,-5.5);var b=r.player("drill_b",79.5,5.5);
        begin(r,TerminusVaultBuilder.RECORDER).thenExecute(()->{rec.holdKey(a,0);rec.holdKey(b,1);})
                .thenExecuteAfter(2,()->h.assertTrue(r.open(DRILL),"Two hands"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void aTakeEndsIfYouLeaveTheRoomAndCostsNothing(GameTestHelper h) {
        var r=drill(h);var rec=recorder(r);var op=r.player("drill_gone",70.5,0.5);
        begin(r,TerminusVaultBuilder.RECORDER).thenExecute(()->rec.press(op))
                .thenExecuteAfter(5,()->op.setPos(r.at(60.5,.5)))
                .thenExecuteAfter(3,()->{
                    h.assertTrue(rec.rule().phase()==Phase.IDLE,"No body, no take");
                    h.assertTrue(rec.ghost()==null,"No figure");
                    op.setPos(r.at(70.5,.5));
                    rec.press(op);
                    h.assertTrue(rec.rule().phase()==Phase.RECORDING,"Press again");
                }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void theDrillDoorNeverShutsOnOrInFrontOfYou(GameTestHelper h) {
        var r=drill(h);var op=r.player("drill_behind",84.5,.5);
        begin(r,TerminusVaultBuilder.RECORDER).thenExecuteAfter(3,()->h.assertTrue(r.open(DRILL),"From the Morgue side it opens"))
                .thenExecute(()->op.setPos(r.at(83.5,.5)))
                .thenExecuteAfter(5,()->h.assertTrue(r.open(DRILL),"In the doorway it stays"))
                .thenExecute(()->op.setPos(r.at(75.5,.5)))
                .thenExecuteAfter(3,()->h.assertTrue(r.shut(DRILL),"Back in the hall it shuts"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void theFigureIsNoMindAndNoHindrance(GameTestHelper h) {
        var type=ModEntities.VAULT_RECORDING.get();
        h.assertTrue(!type.is(Terminus.SUSCEPTIBLE),"No mind: it freezes no door");
        var ghost=type.create(h.getLevel());
        h.assertTrue(ghost!=null&&!ghost.isPushable()&&!ghost.isPickable()&&!ghost.canBeCollidedWith(),"Nothing to bump or hit");
        h.assertTrue(!type.canSerialize(),"Never saved");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void anUndrivenFigureGoes(GameTestHelper h) {
        var r=drill(h);var ghost=ModEntities.VAULT_RECORDING.get().create(r.level());
        ghost.drive(new TwoPersonRule.Pose(r.at(70.5,.5).x,r.at(70.5,.5).y,r.at(70.5,.5).z,0,0));
        r.level().addFreshEntity(ghost);
        h.succeedWhen(()->h.assertTrue(ghost.isRemoved(),"Its recorder is gone"));
    }

    private static void dial(Room r,int setting) {VaultAcDialBlock.set(r.level(),r.hall.offset(TerminusVaultBuilder.AC_DIAL),setting);}
    /** Cold enough for the scanner, without the wait. */
    private static void freeze(Room r,ServerPlayer op) {for(int i=0;i<1000;i++) vitals(r).gate().expose(java.util.Set.of(op.getUUID()));}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void aClickTurnsTheDialRoundAndEitherBlockWillDo(GameTestHelper h) {
        var r=morgue(h);var op=r.player("morgue_dial",90.5,-3.5);var lower=r.hall.offset(TerminusVaultBuilder.AC_DIAL);
        var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(lower),net.minecraft.core.Direction.SOUTH,lower,false);
        for(int expected:new int[]{VaultAcDialBlock.MID,VaultAcDialBlock.MAX,VaultAcDialBlock.LOW}) {
            var at=expected==VaultAcDialBlock.MAX?lower.above():lower;
            r.level().getBlockState(at).useWithoutItem(r.level(),op,hit.withPosition(at));
            h.assertTrue(r.level().getBlockState(lower).getValue(VaultAcDialBlock.SETTING)==expected&&r.level().getBlockState(lower.above()).getValue(VaultAcDialBlock.SETTING)==expected,"Both halves at "+expected);
        }
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void onlyMaxDoesAnything(GameTestHelper h) {
        var r=morgue(h);var op=r.player("morgue_low",88.5,-2.5);
        begin(r,TerminusVaultBuilder.VITALS).thenExecute(()->dial(r,VaultAcDialBlock.MID))
                .thenExecuteAfter(MorgueGate.BITE_EVERY+4,()->h.assertTrue(op.getHealth()==20&&vitals(r).gate().temperature(op.getUUID())==MorgueGate.BODY,"LOW and MID leave you be"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void theBlastAtMaxTakesAHeartEveryTwoSecondsThroughArmor(GameTestHelper h) {
        var r=morgue(h);var op=r.player("morgue_cold",88.5,-2.5);
        op.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
        begin(r,TerminusVaultBuilder.VITALS).thenExecute(()->dial(r,VaultAcDialBlock.MAX))
                .thenWaitUntil(()->h.assertTrue(op.getHealth()==18,"The first bite: "+op.getHealth()))
                .thenExecuteAfter(MorgueGate.BITE_EVERY+2,()->{
                    h.assertTrue(op.getHealth()==16,"The next, two seconds on: "+op.getHealth());
                    h.assertTrue(vitals(r).gate().temperature(op.getUUID())<MorgueGate.BODY-2,"And you are colder");
                    op.setPos(r.at(86.5,.5));
                })
                .thenExecuteAfter(MorgueGate.BITE_EVERY+2,()->h.assertTrue(op.getHealth()==16,"Out of it, nothing: "+op.getHealth()))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void theBlastLeavesHalfAHeartAndKeepsCooling(GameTestHelper h) {
        var r=morgue(h);var op=r.player("morgue_dead",88.5,-2.5);
        op.setHealth(2);
        begin(r,TerminusVaultBuilder.VITALS).thenExecute(()->dial(r,VaultAcDialBlock.MAX))
                .thenWaitUntil(()->h.assertTrue(op.getHealth()==1,"Down to half a heart: "+op.getHealth()))
                .thenExecuteAfter(2*MorgueGate.BITE_EVERY+2,()->{
                    h.assertTrue(op.getHealth()==1&&op.isAlive(),"And no further: "+op.getHealth());
                    h.assertTrue(vitals(r).gate().temperature(op.getUUID())<MorgueGate.BODY-5,"Still cooling");
                    op.kill();
                })
                .thenExecuteAfter(2,()->h.assertTrue(vitals(r).gate().temperature(op.getUUID())==MorgueGate.BODY,"A body that dies of something else starts its next life warm"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void theScannerOpensForTheColdAlone(GameTestHelper h) {
        var r=morgue(h);var be=vitals(r);var op=r.player("morgue_scan",94.5,.5);
        begin(r,TerminusVaultBuilder.VITALS).thenExecuteAfter(3,()->{
                    h.assertTrue(be.tenths()==370&&!be.none(),"VITALS at body heat: "+be.tenths());
                    h.assertTrue(r.shut(MORGUE)&&!r.lampLit(MORGUE),"Alive, it stays shut");
                    h.assertTrue(r.level().getBlockState(r.hall.offset(94,TerminusVaultBuilder.FLOOR,0)).getValue(VaultTallyBlock.LIT),"The plate reads you");
                    op.setHealth(2);
                })
                .thenExecuteAfter(3,()->{
                    h.assertTrue(r.shut(MORGUE),"One heart is not dead: it reads temperature");
                    freeze(r,op);
                })
                .thenExecuteAfter(2,()->{
                    h.assertTrue(be.none()&&be.tenths()<=MorgueGate.LINE*10,"NONE: "+be.tenths());
                    h.assertTrue(r.open(MORGUE)&&r.lampLit(MORGUE),"Cold enough it opens");
                    op.setPos(r.at(90.5,3.5));
                })
                .thenExecuteAfter(MorgueGate.OPEN_TICKS-10,()->h.assertTrue(r.open(MORGUE),"Off the plate it stays a few seconds"))
                .thenExecuteAfter(15,()->h.assertTrue(r.shut(MORGUE)&&be.tenths()<0,"Then it shuts"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void anyoneStillWarmOnThePlateKeepsItShut(GameTestHelper h) {
        var r=morgue(h);var cold=r.player("morgue_low",94.5,-.5);r.player("morgue_well",94.5,1.5);
        begin(r,TerminusVaultBuilder.VITALS).thenExecute(()->freeze(r,cold))
                .thenExecuteAfter(3,()->h.assertTrue(r.shut(MORGUE)&&vitals(r).tenths()==370,"It reads the living")).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void nobodyIsShutInWithTheWand(GameTestHelper h) {
        var r=morgue(h);var op=r.player("morgue_out",96.5,.5);
        begin(r,TerminusVaultBuilder.VITALS).thenExecuteAfter(3,()->h.assertTrue(r.open(MORGUE),"From the cot room it opens"))
                .thenExecute(()->op.setPos(r.at(95.5,.5)))
                .thenExecuteAfter(MorgueGate.OPEN_TICKS,()->h.assertTrue(r.open(MORGUE),"It never shuts on you"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms",timeoutTicks=2000)
    public static void nothingIsPlacedInThePuzzleRooms(GameTestHelper h) {
        var r=room(h,65);var state=HelionSiteState.get(r.level().getServer());var was=state.vault();
        TerminusVaultBuilder.morgue(r.level(),r.hall);TerminusVaultBuilder.drillHall(r.level(),r.hall);
        try {
            state.setVaultForTest(r.hall);
            for(var at:new BlockPos[]{new BlockPos(66,FEET,-7),new BlockPos(82,FEET,7),new BlockPos(75,TerminusVaultBuilder.ROOM_ROOF-1,0),DRILL.below(3),new BlockPos(84,FEET,-4),new BlockPos(93,FEET,4),new BlockPos(89,FEET,0),MORGUE.below(3)})
                h.assertTrue(VaultArgusBlockEntity.refuses(r.level(),r.hall.offset(at)),"Nothing built at "+at);
            for(var at:new BlockPos[]{new BlockPos(60,FEET,0),new BlockPos(96,FEET,0)})
                h.assertTrue(!VaultArgusBlockEntity.refuses(r.level(),r.hall.offset(at)),"Free at "+at);
        } finally {state.setVaultForTest(was);}
        h.succeed();
    }

    /** A batch of its own: both rooms together are wider than the gap to the next test, whose players would stand behind its doors. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_rooms_alone",timeoutTicks=2000)
    public static void aResetClearsBothRooms(GameTestHelper h) {
        var r=room(h,65);TerminusVaultBuilder.morgue(r.level(),r.hall);TerminusVaultBuilder.drillHall(r.level(),r.hall);
        var rec=recorder(r);var be=vitals(r);var a=r.player("reset_a",72.5,-5.5);var b=r.player("reset_b",79.5,5.5);var c=r.player("reset_c",94.5,.5);
        begin(r,TerminusVaultBuilder.RECORDER).thenExecute(()->{freeze(r,c);dial(r,VaultAcDialBlock.MAX);}).thenWaitUntil(()->{
                    rec.holdKey(a,0);rec.holdKey(b,1);
                    h.assertTrue(r.open(DRILL)&&r.open(MORGUE),"Both open");
                })
                .thenExecute(()->{
                    c.setPos(r.at(90.5,3.5));
                    TerminusVaultBuilder.reset(r.level(),r.hall);
                })
                .thenExecuteAfter(12,()->{
                    h.assertTrue(r.shut(DRILL)&&r.shut(MORGUE)&&be.tenths()<0,"Both shut");
                    h.assertTrue(r.level().getBlockState(r.hall.offset(TerminusVaultBuilder.AC_DIAL)).getValue(VaultAcDialBlock.SETTING)==VaultAcDialBlock.LOW,"The dial back on LOW");
                })
                .thenSucceed();
    }
}
