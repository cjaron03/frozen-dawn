package com.frozendawn.vault;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.HelionSiteState;
import com.frozendawn.defense.TerminusLog;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.shepherd.ShepherdDockBlock;
import com.frozendawn.world.HelionSiteBuilder;
import com.frozendawn.world.HelionSitePlacement;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** The Unwatched Door: three mechanisms built alone in the test room, each judged the way the server judges a player. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerminusVaultGameTest {
    /** Tests of a batch stand side by side along x, close enough to count as watchers: "away" here is north, out of their way. */
    private static final float EAST=-90,WEST=90,AWAY=180;
    private static final BlockPos DOOR=new BlockPos(10,9,10),BRIDGE=new BlockPos(4,3,10);

    @BeforeBatch(batch="terminus_vault") public static void report(ServerLevel level) {GameTestReporting.installReporter(level);}
    @BeforeBatch(batch="terminus_vault_bridge") public static void reportBridge(ServerLevel level) {GameTestReporting.installReporter(level);}
    @BeforeBatch(batch="terminus_vault_alone") public static void reportAlone(ServerLevel level) {GameTestReporting.installReporter(level);}

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void doorMovesOnlyWhileNobodyFacesIt(GameTestHelper h) {
        var door=door(h);var op=player(h,"vault_watcher",new Vec3(4.5,5,10.5),EAST);
        begin(h)
                .thenExecuteAfter(30,()->h.assertTrue(door.progress()==0,"Faced, it holds: "+door.progress()))
                .thenExecute(()->look(op,AWAY))
                .thenExecuteAfter(30,()->h.assertTrue(door.progress()>=25,"Unwatched, it grinds on: "+door.progress()))
                .thenExecute(()->look(op,EAST))
                .thenExecuteAfter(2,()->{})
                .thenExecute(()->marked=door.progress())
                .thenExecuteAfter(20,()->h.assertTrue(door.progress()==marked,"Faced again, it freezes where it is: "+door.progress()))
                .thenExecute(()->look(op,AWAY))
                .thenExecuteAfter(VaultMechanismBlockEntity.DOOR_TICKS-marked+4,()->{
                    h.assertTrue(door.finished(),"Unwatched for the rest of its travel it opens");
                    for(int dy=-4;dy<=-2;dy++) for(int dz=-1;dz<=1;dz++)
                        h.assertTrue(h.getBlockState(DOOR.offset(0,dy,dz)).isAir(),"The doorway is clear");
                })
                .thenSucceed();
    }
    private static int marked;

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void aSusceptibleMobFreezesTheDoorAndAnAnimalDoesNot(GameTestHelper h) {
        var door=door(h);player(h,"vault_turned",new Vec3(4.5,5,10.5),AWAY);
        var husk=mob(h,EntityType.HUSK,new Vec3(4.5,5,8.5),EAST);
        mob(h,EntityType.SHEEP,new Vec3(4.5,5,12.5),EAST);
        begin(h)
                .thenExecuteAfter(25,()->h.assertTrue(door.progress()==0,"A mind facing it holds it: "+door.progress()))
                .thenExecute(husk::discard)
                .thenExecuteAfter(25,()->h.assertTrue(door.progress()>=20,"With the husk gone only a sheep faces it: "+door.progress()))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void aWallBetweenAFaceAndTheDoorIsNoWatching(GameTestHelper h) {
        var door=door(h);player(h,"vault_turned",new Vec3(4.5,5,10.5),AWAY);
        mob(h,EntityType.HUSK,new Vec3(4.5,5,8.5),EAST);
        for(int y=4;y<=9;y++) for(int z=6;z<=11;z++) h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
        begin(h).thenExecuteAfter(25,()->h.assertTrue(door.progress()>=20,"No clear line, no watching: "+door.progress())).thenSucceed();
    }

    /** A batch of its own: tests in one batch stand side by side, and a neighbour's player would be near. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault_alone",timeoutTicks=4000)
    public static void aDoorWithNoPlayerNearStaysWhereItIs(GameTestHelper h) {
        var door=door(h);
        begin(h).thenExecuteAfter(30,()->h.assertTrue(door.progress()==0,"Nobody within 24 blocks: "+door.progress())).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void doorProgressSurvivesAReload(GameTestHelper h) {
        var door=door(h);var level=h.getLevel();player(h,"vault_turned",new Vec3(4.5,5,10.5),AWAY);
        begin(h).thenExecuteAfter(30,()->{
            int was=door.progress();
            h.assertTrue(was>0,"It moved");
            var tag=door.saveWithFullMetadata(level.registryAccess());
            var again=(VaultMechanismBlockEntity)BlockEntity.loadStatic(door.getBlockPos(),door.getBlockState(),tag,level.registryAccess());
            h.assertTrue(again!=null&&again.progress()==was,"Reloaded it stands where it stood");
        }).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault_bridge",timeoutTicks=4000)
        public static void theBridgeIsCrossedWithTheBodyFacingBack(GameTestHelper h) {
        var bridge=bridge(h);
        // The ledge's lip: cell -1. The body faces back, whatever a camera might be doing.
        var op=player(h,"vault_crosser",new Vec3(4.5,5,10.5),WEST);
        var cell=VaultMechanismBlockEntity.CELL_TICKS;
        begin(h)
                .thenExecuteAfter(cell*4,()->h.assertTrue(bridge.cellsOut()==2,"From the lip it reaches two cells and waits: "+bridge.cellsOut()))
                .thenExecute(()->h.assertTrue(h.getBlockState(BRIDGE.offset(1,1,0)).is(ModBlocks.VAULT_WALKWAY.get())&&h.getBlockState(BRIDGE.offset(2,1,0)).is(ModBlocks.VAULT_WALKWAY.get())
                        &&h.getBlockState(BRIDGE.offset(3,1,0)).isAir(),"Two walkable cells out, the third still open: "+h.getBlockState(BRIDGE.offset(3,1,0))))
                .thenExecute(()->stand(h,op,6.5))
                .thenExecuteAfter(cell*3,()->h.assertTrue(bridge.cellsOut()==4,"A step back brings more: "+bridge.cellsOut()))
                .thenExecute(()->look(op,EAST))
                .thenExecute(()->stand(h,op,8.5))
                .thenExecuteAfter(cell*3,()->h.assertTrue(bridge.cellsOut()==4,"Facing the gap, nothing comes: "+bridge.cellsOut()))
                .thenExecute(()->look(op,WEST))
                .thenExecute(()->stand(h,op,8.5))
                .thenExecuteAfter(cell*3,()->h.assertTrue(bridge.cellsOut()==6,"Turned back, it comes on: "+bridge.cellsOut()))
                .thenExecute(()->stand(h,op,10.5))
                .thenExecuteAfter(cell*3,()->h.assertTrue(bridge.cellsOut()==8,"And on: "+bridge.cellsOut()))
                .thenExecute(()->stand(h,op,12.5))
                .thenExecuteAfter(cell*2,()->h.assertTrue(bridge.finished()&&bridge.cellsOut()==VaultBridge.SPAN,"Backward, it reaches the far side: "+bridge.cellsOut()))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault_bridge",timeoutTicks=4000)
    public static void theBridgeWaitsForSomeoneOnIt(GameTestHelper h) {
        var bridge=bridge(h);
        player(h,"vault_aside",new Vec3(4.5,5,14.5),WEST);
        begin(h).thenExecuteAfter(VaultMechanismBlockEntity.CELL_TICKS*3,()->h.assertTrue(bridge.cellsOut()==0,"Off the lane nothing comes: "+bridge.cellsOut())).thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void theLastDoorOpensOnlyThroughTheCot(GameTestHelper h) {
        var door=lastDoor(h);var level=h.getLevel();
        var op=player(h,"vault_rester",new Vec3(4.5,5,10.5),AWAY);
        var cot=h.absolutePos(DOOR.offset(VaultMechanismBlockEntity.COT));
        var head=(VaultTechnicianBlockEntity)level.getBlockEntity(h.absolutePos(DOOR.offset(VaultMechanismBlockEntity.TECHNICIAN)));
        begin(h)
                .thenExecuteAfter(40,()->h.assertTrue(door.progress()==0&&!door.finished(),"Unwatched, this one still holds"))
                .thenExecute(()->h.assertTrue(VaultCotBlock.rest(op,level,cot),"The cot takes anyone, at any hour"))
                .thenExecuteAfter(VaultMechanismBlockEntity.REST_TICKS-20,()->{
                    h.assertTrue(op.isSleeping(),"Still lying there");
                    h.assertTrue(!door.finished()&&!head.turned(),"Not yet");
                })
                .thenExecuteAfter(24,()->{
                    h.assertTrue(door.finished(),"Awake, the door stands open");
                    h.assertTrue(h.getBlockState(DOOR.offset(0,-3,0)).isAir(),"The doorway is clear");
                    h.assertTrue(head.turned(),"The one change: the head");
                    h.assertTrue(!op.isSleeping(),"The rest ends");
                })
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void aFaceOnTheLastDoorSpoilsTheRest(GameTestHelper h) {
        var door=lastDoor(h);var level=h.getLevel();
        var op=player(h,"vault_rester",new Vec3(4.5,5,14.5),AWAY);
        mob(h,EntityType.HUSK,new Vec3(4.5,5,10.5),EAST);
        var cot=h.absolutePos(DOOR.offset(VaultMechanismBlockEntity.COT));
        begin(h).thenExecute(()->h.assertTrue(VaultCotBlock.rest(op,level,cot),"The cot takes them"))
                .thenWaitUntil(()->h.assertTrue(!op.isSleeping(),"The rest still ends"))
                .thenExecute(()->h.assertTrue(!door.finished(),"Someone was watching: it stays shut"))
                .thenSucceed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void theRestLastsThroughTheDay(GameTestHelper h) {
        lastDoor(h);var level=h.getLevel();
        var op=player(h,"vault_day_rester",new Vec3(4.5,5,10.5),AWAY);
        var was=level.getDayTime();level.setDayTime(1000);
        try {
            h.assertTrue(VaultCotBlock.rest(op,level,h.absolutePos(DOOR.offset(VaultMechanismBlockEntity.COT))),"The cot takes them by day");
            op.tick();
            h.assertTrue(op.isSleeping(),"Daylight does not wake them");
        } finally {level.setDayTime(was);}
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void theCradleGivesTheFoundWandOnce(GameTestHelper h) {
        var level=h.getLevel();var at=new BlockPos(10,4,10);
        h.setBlock(at,ModBlocks.VAULT_CRADLE.get());
        var cradle=(VaultCradleBlockEntity)level.getBlockEntity(h.absolutePos(at));
        var op=player(h,"vault_taker",new Vec3(8.5,4,10.5),EAST);
        h.assertTrue(cradle.holding(),"It holds the wand as found");
        h.assertTrue(cradle.take(op),"The wand comes away");
        var wand=op.getInventory().items.stream().filter(s->s.is(ModItems.TERMINUS.get())).findFirst().orElse(ItemStack.EMPTY);
        var log=wand.get(ModDataComponents.TERMINUS_LOG.get());
        h.assertTrue(log!=null&&log.equals(new TerminusLog(3,1)),"Three discharges, one transfer logged: "+log);
        h.assertTrue(!cradle.holding()&&!cradle.take(op),"The forks stay empty");
        h.assertTrue(op.getInventory().countItem(ModItems.TERMINUS.get())==1,"Only ever one");
        h.succeed();
    }

    /** The whole vault under a real hall, in the air above the test grid: every piece where the mockup has it. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault_alone",timeoutTicks=4000)
    public static void theVaultStandsUnderTheCoreOncePerWorld(GameTestHelper h) {
        var level=h.getLevel();var hall=h.absolutePos(BlockPos.ZERO).atY(60);var state=HelionSiteState.get(level.getServer());var was=state.vault();
        try {
            state.setVaultForTest(null);
            HelionSiteBuilder.place(level,hall,hall.getY()+HelionSitePlacement.DEPTH);
            h.assertTrue(TerminusVaultBuilder.claim(level,hall)&&hall.equals(state.vault()),"The first site that fits takes the vault");
            h.assertTrue(!TerminusVaultBuilder.claim(level,hall.east(64)),"And it is never placed again");
            h.assertTrue(hall.equals(state.vault()),"The world's flag stays on the first");
            h.assertTrue(level.getBlockState(hall.offset(HelionSiteBuilder.CORE)).is(ModBlocks.CONFINEMENT_CORE.get()),"The Core still sits on top");
            var grate=level.getBlockState(hall.offset(TerminusVaultBuilder.GRATE));
            h.assertTrue(grate.is(ModBlocks.VAULT_GRATE.get())&&!grate.getValue(TrapDoorBlock.OPEN),"Right under it, the shut grate");
            for(int y=0;y>TerminusVaultBuilder.FLOOR;y--) h.assertTrue(level.getBlockState(hall.offset(0,y,0)).is(Blocks.LADDER),"An unbroken ladder down, at "+y);
            for(var at:new BlockPos[] {TerminusVaultBuilder.DOOR,TerminusVaultBuilder.BRIDGE,TerminusVaultBuilder.LAST_DOOR})
                h.assertTrue(level.getBlockEntity(hall.offset(at)) instanceof VaultMechanismBlockEntity,"A mechanism at "+at);
            h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.LAST_DOOR).offset(VaultMechanismBlockEntity.COT)).is(ModBlocks.VAULT_COT.get()),"The cot");
            h.assertTrue(level.getBlockEntity(hall.offset(TerminusVaultBuilder.CRADLE)) instanceof VaultCradleBlockEntity cradle&&cradle.holding(),"The wand in its cradle");
            h.assertTrue(level.getBlockEntity(hall.offset(TerminusVaultBuilder.CLOCK)) instanceof VaultClockBlockEntity clock&&clock.start()==VaultClock.start(level.getSeed()),"The clock, set by the seed");
            h.assertTrue(level.getBlockEntity(hall.offset(TerminusVaultBuilder.LOCKER)) instanceof BarrelBlockEntity locker&&IntStream.range(0,locker.getContainerSize()).filter(i->locker.getItem(i).is(ModItems.ORSA_DOCUMENT.get())).count()==VaultDocuments.TALE_LOGS.size(),"The tale logs");
            h.assertTrue(level.getBlockEntity(hall.offset(TerminusVaultBuilder.LOCKER)) instanceof BarrelBlockEntity spare&&PowerCellItem.energy(spare.getItem(TerminusVaultBuilder.SPARE_CELL_SLOT))==PowerCellItem.CAPACITY_JOULES,"The cell he never got to fit, charged");
            var dock=level.getBlockState(hall.offset(TerminusVaultBuilder.DOCK));
            h.assertTrue(dock.is(ModBlocks.VAULT_CS1_DOCK.get())&&dock.getValue(ShepherdDockBlock.FACING)==Direction.SOUTH&&dock.getValue(ShepherdDockBlock.STATE)==ShepherdDockBlock.DockState.FOUND,"Her dock beside him, facing south, as found");
            h.assertTrue(dock.getDestroySpeed(level,hall)<0&&dock.getBlock().getExplosionResistance()>1000,"Sealed like the vault's other fittings");
            h.assertTrue(level.getBlockEntity(hall.offset(HelionSiteBuilder.LOG_DESK)) instanceof BarrelBlockEntity desk&&desk.getItem(0).is(ModItems.ORSA_DOCUMENT.get()),"The one clue, in the hall's desk");
            h.assertTrue(level.getBlockEntity(hall.offset(TerminusVaultBuilder.ARGUS)) instanceof VaultArgusBlockEntity,"Argus over the Watch Floor");
            h.assertTrue(level.getBlockState(hall.offset(16,TerminusVaultBuilder.FLOOR+1,-3)).getOptionalValue(VaultMarkBlock.MARK).filter(m->m==VaultMarkBlock.Mark.WARNING_14).isPresent(),"The warning ends by the door");
            h.assertTrue(TerminusVaultBuilder.LAST_DOOR.getX()==100&&TerminusVaultBuilder.CRADLE.getX()==105,"The last door and the vault moved east of the last rooms");
            h.assertTrue(TerminusVaultBuilder.REACH==107&&level.getBlockState(hall.offset(TerminusVaultBuilder.REACH-1,TerminusVaultBuilder.FLOOR+1,0)).is(ModBlocks.VAULT_SHELL.get()),"The shell runs that far");
            h.assertTrue(level.getBlockEntity(hall.offset(TerminusVaultBuilder.PLOT)) instanceof VaultPlotBlockEntity&&TerminusVaultBuilder.PLOT.getZ()==-11,"The plot's wall, the mess hall's north wall");
            h.assertTrue(level.getBlockState(hall.offset(64,TerminusVaultBuilder.FLOOR+8,-10)).isAir(),"A wall twenty-four wide and eight tall");
            h.assertTrue(level.getBlockState(hall.offset(97,TerminusVaultBuilder.FLOOR+1,-4)).is(ModBlocks.VAULT_SHELL.get()),"No gap in the cot room's north wall");
            h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.EAST_DOOR).below(3)).is(ModBlocks.VAULT_DOOR_CELL.get()),"The Watch Floor's gate survives the room built on it");
            for(var at:new BlockPos[] {TerminusVaultBuilder.MESS_DOOR,TerminusVaultBuilder.DRILL_DOOR,TerminusVaultBuilder.MORGUE_DOOR})
                h.assertTrue(level.getBlockEntity(hall.offset(at)) instanceof VaultMechanismBlockEntity door&&door.kind()==VaultMechanismBlock.Kind.GATE,"A gate at "+at);
            h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.MESS_DOOR).below(3)).isAir(),"The mess hall's door stands open");
            for(var at:new BlockPos[] {TerminusVaultBuilder.DRILL_DOOR,TerminusVaultBuilder.MORGUE_DOOR}) {
                h.assertTrue(level.getBlockState(hall.offset(at).below(3)).is(ModBlocks.VAULT_DOOR_CELL.get()),"A puzzle's door is shut at "+at);
                h.assertTrue(level.getBlockState(hall.offset(at).below()).is(ModBlocks.VAULT_LINTEL.get()),"Its lamp");
            }
            for(int x:new int[]{44,47,50,53,56,59}) for(int z:new int[]{-4,-3,3,4})
                h.assertTrue(level.getBlockState(hall.offset(x,TerminusVaultBuilder.FLOOR+1,z)).getOptionalValue(VaultDressingBlock.KIND).filter(k->k==VaultDressingBlock.Kind.TABLE).isPresent(),"A table at "+x+", "+z);
            h.assertTrue(level.getBlockState(hall.offset(48,TerminusVaultBuilder.FLOOR+1,-4)).isAir(),"With a gap between tables");
            h.assertTrue(level.getBlockEntity(hall.offset(TerminusVaultBuilder.RECORDER)) instanceof VaultRecorderBlockEntity,"The recorder");
            h.assertTrue(level.getBlockEntity(hall.offset(TerminusVaultBuilder.VITALS)) instanceof VaultVitalsBlockEntity,"The scanner's panel");
            h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.PROJECTOR)).is(ModBlocks.VAULT_PROJECTOR.get()),"The projector over the way in");
            h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.AC_DIAL)).is(ModBlocks.VAULT_AC_DIAL.get())&&level.getBlockState(hall.offset(TerminusVaultBuilder.AC_DIAL).above()).is(ModBlocks.VAULT_AC_DIAL.get()),"The A/C's dial");
            h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.PLATE_X,TerminusVaultBuilder.FLOOR+1,-2)).is(ModBlocks.VAULT_ARCH.get())&&level.getBlockState(hall.offset(94,TerminusVaultBuilder.FLOOR+1,0)).isAir(),"The scanner's arch, clear through the middle");
            for(int z=-1;z<=1;z++) h.assertTrue(level.getBlockState(hall.offset(TerminusVaultBuilder.PLATE_X,TerminusVaultBuilder.FLOOR,z)).is(ModBlocks.VAULT_PLATE.get()),"The scanner's plate");
            h.assertTrue(level.getBlockState(hall.offset(97,TerminusVaultBuilder.FLOOR+1,-2)).is(ModBlocks.VAULT_DRESSING.get())&&level.getBlockState(hall.offset(98,TerminusVaultBuilder.CEILING-1,-3)).is(ModBlocks.VAULT_LIGHT.get()),"The cot room's paper and lamp came too");
            var shell=level.getBlockState(hall.offset(-3,TerminusVaultBuilder.CEILING,0));
            h.assertTrue(shell.is(ModBlocks.VAULT_SHELL.get())&&shell.getDestroySpeed(level,hall)<0&&shell.getBlock().getExplosionResistance()>1000,"A shell nothing mines or blasts");
        } finally {state.setVaultForTest(was);}
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault_alone",timeoutTicks=4000)
    public static void aSiteTooShallowForThePitPassesTheVaultOn(GameTestHelper h) {
        var level=h.getLevel();var state=HelionSiteState.get(level.getServer());var was=state.vault();
        try {
            state.setVaultForTest(null);
            h.assertTrue(!TerminusVaultBuilder.claim(level,h.absolutePos(BlockPos.ZERO).atY(level.getMinBuildHeight()+10))&&state.vault()==null,"Too near the world's floor: the next site takes it");
        } finally {state.setVaultForTest(was);}
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="terminus_vault",timeoutTicks=4000)
    public static void everyVaultDocumentArchivesWhenRead(GameTestHelper h) {
        var level=h.getLevel();var op=player(h,"vault_reader",new Vec3(4.5,5,10.5),AWAY);
        var papers=new java.util.ArrayList<>(VaultDocuments.onTheDesk());papers.addAll(VaultDocuments.inTheLocker());papers.add(VaultDocuments.drainSchedule());papers.add(VaultDocuments.sealOrder());
        for(var paper:papers) {
            var type=paper.get(DataComponents.CUSTOM_DATA).copyTag().getString("doc_type");
            op.setItemInHand(InteractionHand.MAIN_HAND,paper);
            paper.use(level,op,InteractionHand.MAIN_HAND);
            var holder=level.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"found_"+type));
            h.assertTrue(holder!=null&&op.getAdvancements().getOrStartProgress(holder).isDone(),"Reading it unlocks its manual entry: "+type);
            h.assertTrue(paper.isEmpty(),"Reading it files it: "+type);
        }
        h.succeed();
    }

    /** A test can start before its chunks tick block entities, and these count ticks: wait for that first. */
    private static GameTestSequence begin(GameTestHelper h) {
        return h.startSequence().thenWaitUntil(()->h.assertTrue(ticking(h,DOOR)&&ticking(h,BRIDGE),"Chunks are ticking"));
    }
    private static boolean ticking(GameTestHelper h,BlockPos relative) {
        var level=h.getLevel();var at=h.absolutePos(relative);
        return level.shouldTickBlocksAt(at)&&level.getChunkAt(at).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)&&level.areEntitiesLoaded(ChunkPos.asLong(at));
    }
    private static VaultMechanismBlockEntity door(GameTestHelper h) {
        TerminusVaultBuilder.door(h.getLevel(),h.absolutePos(DOOR));
        return (VaultMechanismBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(DOOR));
    }
    private static VaultMechanismBlockEntity lastDoor(GameTestHelper h) {
        TerminusVaultBuilder.lastDoor(h.getLevel(),h.absolutePos(DOOR));
        return (VaultMechanismBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(DOOR));
    }
    private static VaultMechanismBlockEntity bridge(GameTestHelper h) {
        TerminusVaultBuilder.bridge(h.getLevel(),h.absolutePos(BRIDGE));
        return (VaultMechanismBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(BRIDGE));
    }
    private static void look(ServerPlayer p,float yaw) {p.setYRot(yaw);p.setYHeadRot(yaw);p.setXRot(0);}
    private static void stand(GameTestHelper h,ServerPlayer p,double x) {var at=h.absoluteVec(new Vec3(x,5,10.5));p.setPos(at);}
    private static <T extends Mob> T mob(GameTestHelper h,EntityType<T> type,Vec3 at,float yaw) {
        var mob=h.spawnWithNoFreeWill(type,at);mob.setNoGravity(true);mob.setYRot(yaw);mob.setYHeadRot(yaw);mob.setYBodyRot(yaw);return mob;
    }
    private static ServerPlayer player(GameTestHelper h,String name,Vec3 at,float yaw) {
        var level=h.getLevel();var profile=new GameProfile(UUID.randomUUID(),name);
        var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        player.connection=new net.neoforged.neoforge.common.util.FakePlayer(level,profile).connection;
        player.setPos(h.absoluteVec(at));player.setGameMode(GameType.SURVIVAL);GameTestPlayers.join(h,player);
        look(player,yaw);
        return player;
    }
}
