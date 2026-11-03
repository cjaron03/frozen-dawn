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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
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
        h.succeed();
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
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashIgnoresHurtCooldownWithoutKnockback(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        var pig=target(h,EntityType.PIG,0,5);aim(p,0);
        for(int t=0;t<5;t++)SeverLash.fire(p,lash,1000+t);
        h.assertTrue(Math.abs(pig.getHealth()-(pig.getMaxHealth()-5*SeverLash.DAMAGE_PER_TICK))<1e-4,"Every tick of contact lands: twelve damage a second");
        h.assertTrue(pig.getDeltaMovement().horizontalDistance()<1e-6,"A cutter does not shove");
        var stale=target(h,EntityType.PIG,30,5);
        aim(p,60);h.assertTrue(SeverLash.fire(p,lash,1010),"Fires again after a gap");
        h.assertTrue(full(stale),"A broken sweep resumes as a single ray, not an arc from the stale aim");
        h.succeed();
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
            h.succeed();
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
        victim.setPos(h.absoluteVec(new Vec3(EYE_X,FEET_Y,EYE_Z+5)));victim.setGameMode(GameType.SURVIVAL);GameTestPlayers.join(h,victim);
        try {var field=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(victim,0);}
        catch(ReflectiveOperationException e) {throw new IllegalStateException("The target player must lose spawn protection",e);}
        var helmet=new ItemStack(Items.IRON_HELMET);victim.setItemSlot(EquipmentSlot.HEAD,helmet);
        try {
            aim(p,0);p.setXRot(10);
            for(int t=0;t<20;t++)SeverLash.fire(p,lash,1000+t);
            h.assertTrue(victim.getHealth()<victim.getMaxHealth(),"The beam reached the armored player");
            h.assertTrue(helmet.getDamageValue()==0,"A second of beam leaves the helmet unworn: "+helmet.getDamageValue());
        } finally {server.setPvpAllowed(pvp);}
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashOverheatLockoutSpendsNothing(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();aim(p,0);
        for(int t=0;t<LashHeat.FIRE_TICKS;t++)h.assertTrue(SeverLash.fire(p,lash,1000+t),"Six seconds of continuous fire");
        int charge=energy(p,1);long last=1000+LashHeat.FIRE_TICKS-1;
        h.assertTrue(!SeverLash.fire(p,lash,last+1)&&energy(p,1)==charge,"Overheated, it neither fires nor drains");
        h.assertTrue(SeverLash.fire(p,lash,last+LashHeat.LOCKOUT_TICKS)&&energy(p,1)==charge-SeverLash.JOULES_PER_TICK,"Four seconds later it cuts again");
        h.succeed();
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
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashSearsWhatItCuts(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        // A husk outlasts the 21 cuts; a pig would die first.
        var husk=target(h,EntityType.HUSK,0,5);var spared=target(h,EntityType.PIG,70,5);aim(p,0);
        SeverLash.fire(p,lash,1000);
        var sear=husk.getEffect(ModEffects.SEARED);
        h.assertTrue(sear!=null&&sear.getAmplifier()==0&&sear.getDuration()==Sear.BASE_TICKS,"One tick of beam sears for two seconds: "+sear);
        h.assertTrue(!spared.hasEffect(ModEffects.SEARED),"Only what the beam cuts is seared");
        // Direct calls in one game tick: the effect does not tick down between them.
        for(int t=1;t<=20;t++)SeverLash.fire(p,lash,1000+t);
        sear=husk.getEffect(ModEffects.SEARED);
        h.assertTrue(sear!=null&&sear.getAmplifier()==2&&sear.getDuration()==Sear.MAX_TICKS,"A held beam deepens it to Seared III, capped: "+sear);
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=200)
    public static void searedPulsesOnceASecondUnderAHeldBeam(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        // An iron golem outlasts three seconds of beam.
        var golem=target(h,EntityType.IRON_GOLEM,0,5);aim(p,0);
        int[] tick={0};float[] last={golem.getHealth()};List<Integer> pulses=new ArrayList<>();
        h.onEachTick(()->{
            if(tick[0]>=60)return;
            SeverLash.fire(p,lash,h.getLevel().getGameTime());
            float lost=last[0]-golem.getHealth();last[0]=golem.getHealth();
            if(tick[0]>0&&lost>SeverLash.DAMAGE_PER_TICK+1e-3)pulses.add(tick[0]);
            if(++tick[0]==60){
                h.assertTrue(pulses.size()<=3,"Seared pulses at most once a second under a held beam: pulses at ticks "+pulses);
                h.assertTrue(golem.getEffect(ModEffects.SEARED).getDuration()>=Sear.MAX_TICKS-1,"Three seconds of beam holds the sear at its cap: "+golem.getEffect(ModEffects.SEARED));
                h.succeed();
            }
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void searedBurnsOnThroughArmorAfterTheBeamLeaves(GameTestHelper h) {
        var husk=target(h,EntityType.HUSK,0,5);
        husk.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
        // Worn armor becomes attributes on the husk's next tick.
        h.runAfterDelay(2,()->{
            float before=husk.getHealth();
            husk.addEffect(new MobEffectInstance(ModEffects.SEARED,Sear.BASE_TICKS,0));
            h.runAfterDelay(Sear.BASE_TICKS+5,()->{
                h.assertTrue(!husk.hasEffect(ModEffects.SEARED),"The sear burns out");
                h.assertTrue(Math.abs((before-husk.getHealth())-2*Sear.PULSE_DAMAGE)<1e-4,"Two seconds of Seared I burn twice, armor or not: "+(before-husk.getHealth()));
                h.assertTrue(husk.getDeltaMovement().horizontalDistance()<1e-6,"A burn does not shove");
                h.succeed();
            });
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void searedSlowsWithItsLevel(GameTestHelper h) {
        var pig=target(h,EntityType.PIG,0,5);
        double base=pig.getAttributeValue(Attributes.MOVEMENT_SPEED);
        pig.addEffect(new MobEffectInstance(ModEffects.SEARED,Sear.MAX_TICKS,2));
        double seared=pig.getAttributeValue(Attributes.MOVEMENT_SPEED);
        h.assertTrue(Math.abs(seared-base*(1-3*Sear.SLOW_PER_LEVEL))<1e-6,"Seared III slows by three steps: "+seared+" of "+base);
        h.succeed();
    }

    /** The block the shooter's level aim lands on, four and a half blocks out. */
    private static final BlockPos STRUCK=new BlockPos(10,5,9);

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashFlashesSnowAndIceToSteam(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        h.setBlock(STRUCK,Blocks.ICE);SeverLash.fire(p,lash,1000);
        h.assertTrue(h.getBlockState(STRUCK).isAir(),"Ice goes in one tick, leaving no meltwater: "+h.getBlockState(STRUCK));
        h.setBlock(STRUCK,Blocks.SNOW_BLOCK);SeverLash.fire(p,lash,1001);
        h.assertTrue(h.getBlockState(STRUCK).isAir(),"So does snow");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashMeltsPackedAndBlueIceOnlyUnderAHeldBeam(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        h.setBlock(STRUCK,Blocks.PACKED_ICE);
        for(int t=0;t<BlockHeat.PACKED_ICE_TICKS-1;t++)SeverLash.fire(p,lash,1000+t);
        h.assertTrue(h.getBlockState(STRUCK).is(Blocks.PACKED_ICE),"Packed ice holds for most of a second");
        SeverLash.fire(p,lash,1000+BlockHeat.PACKED_ICE_TICKS-1);
        h.assertTrue(h.getBlockState(STRUCK).isAir(),"A second of beam melts it: "+h.getBlockState(STRUCK));
        // A fresh beam: blue ice takes twice as long.
        h.setBlock(STRUCK,Blocks.BLUE_ICE);p.getMainHandItem().remove(ModDataComponents.LASH_HEAT.get());
        for(int t=0;t<BlockHeat.BLUE_ICE_TICKS-1;t++)SeverLash.fire(p,lash,2000+t);
        h.assertTrue(h.getBlockState(STRUCK).is(Blocks.BLUE_ICE),"Blue ice holds for most of two seconds");
        SeverLash.fire(p,lash,2000+BlockHeat.BLUE_ICE_TICKS-1);
        h.assertTrue(h.getBlockState(STRUCK).isAir(),"Two seconds melt it");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashScorchesStoneItDwellsOn(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        h.setBlock(STRUCK,Blocks.STONE);SeverLash.fire(p,lash,1000);
        h.assertTrue(h.getBlockState(STRUCK).is(Blocks.STONE),"A sweep past leaves stone");
        for(int t=0;t<BlockHeat.STONE_TICKS;t++)SeverLash.fire(p,lash,1100+t);
        h.assertTrue(h.getBlockState(STRUCK).is(ModBlocks.SCORCHED_GROUND.get()),"Half a second of beam scorches it: "+h.getBlockState(STRUCK));
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashIgnitesWoodInAir(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        h.setBlock(STRUCK,Blocks.OAK_PLANKS);SeverLash.fire(p,lash,1000);
        h.assertTrue(h.getBlockState(STRUCK.north()).is(Blocks.FIRE),"Wood catches where the beam lands: "+h.getBlockState(STRUCK.north()));
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashLeavesTheLastWallStanding(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();var level=h.getLevel();
        h.setBlock(STRUCK,Blocks.PACKED_ICE);
        // A Master Architect behind the shooter, holding the struck block as part of its Last Wall.
        var master=ModEntities.ARCHITECT.get().create(level);master.setPos(h.absoluteVec(new Vec3(EYE_X,FEET_Y,1.5)));
        master.bindToHearthMasterArchitect(UUID.randomUUID(),master.blockPosition(),0);
        var tag=master.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        tag.putLongArray("MasterWallBlocks",new long[]{h.absolutePos(STRUCK).asLong()});master.load(tag);
        level.addFreshEntity(master);
        try {
            for(int t=0;t<2*BlockHeat.PACKED_ICE_TICKS;t++)SeverLash.fire(p,lash,1000+t);
            h.assertTrue(h.getBlockState(STRUCK).is(Blocks.PACKED_ICE),"The Last Wall does not melt");
        } finally {master.discard();}
        p.getMainHandItem().remove(ModDataComponents.LASH_HEAT.get());
        for(int t=0;t<BlockHeat.PACKED_ICE_TICKS;t++)SeverLash.fire(p,lash,2000+t);
        h.assertTrue(h.getBlockState(STRUCK).isAir(),"Once the wall is gone, the same ice melts");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashRespectsBlockProtection(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();p.setGameMode(GameType.ADVENTURE);
        h.setBlock(STRUCK,Blocks.ICE);
        h.assertTrue(SeverLash.fire(p,lash,1000),"An adventure player can still fire");
        h.assertTrue(h.getBlockState(STRUCK).is(Blocks.ICE),"but cannot change blocks with it, as with breaking them");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash",timeoutTicks=100)
    public static void severLashIgnitionRespectsPlacementProtection(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();var flame=h.absolutePos(STRUCK.north());
        h.setBlock(STRUCK,Blocks.OAK_PLANKS);
        // A claim that covers only the cell the fire would go in, not the wood.
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> claim=e->{if(e.getPos().equals(flame))e.setCanceled(true);};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(claim);
        try {SeverLash.fire(p,lash,1000);}
        finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(claim);}
        h.assertTrue(h.getBlockState(STRUCK.north()).isAir(),"No fire goes where placing it is refused: "+h.getBlockState(STRUCK.north()));
        p.setGameMode(GameType.ADVENTURE);SeverLash.fire(p,lash,1001);
        h.assertTrue(h.getBlockState(STRUCK.north()).isAir(),"Nor for an adventure player");
        h.succeed();
    }

    @BeforeBatch(batch="sever_lash_vacuum") public static void vacuum(ServerLevel level) {
        GameTestReporting.installReporter(level);var a=com.frozendawn.data.ApocalypseState.get(level.getServer());phaseBefore=a.getApocalypseTicks();
        a.setApocalypseTicks((long)(a.getTotalDays()*24000L*.90),level.getServer());com.frozendawn.world.RoomAtmosphere.reset();com.frozendawn.world.CombustionAtmosphere.reset();
    }
    @AfterBatch(batch="sever_lash_vacuum") public static void air(ServerLevel level) {
        com.frozendawn.data.ApocalypseState.get(level.getServer()).setApocalypseTicks(phaseBefore,level.getServer());
        com.frozendawn.world.RoomAtmosphere.reset();com.frozendawn.world.CombustionAtmosphere.reset();
    }
    private static long phaseBefore;

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="sever_lash_vacuum",timeoutTicks=100)
    public static void severLashBurnsNothingInVacuumButStillScorches(GameTestHelper h) {
        var p=shooter(h,500000);var lash=p.getMainHandItem();
        h.assertTrue(!com.frozendawn.world.CombustionAtmosphere.canBurnAt(h.getLevel(),h.absolutePos(STRUCK.north())),"The range is in vacuum");
        h.setBlock(STRUCK,Blocks.OAK_PLANKS);SeverLash.fire(p,lash,1000);
        h.assertTrue(h.getBlockState(STRUCK.north()).isAir()&&h.getBlockState(STRUCK).is(Blocks.OAK_PLANKS),"Nothing burns in vacuum");
        h.setBlock(STRUCK,Blocks.STONE);
        for(int t=0;t<BlockHeat.STONE_TICKS;t++)SeverLash.fire(p,lash,1100+t);
        h.assertTrue(h.getBlockState(STRUCK).is(ModBlocks.SCORCHED_GROUND.get()),"Heat still scorches stone");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="sever_lash",timeoutTicks=100)
    public static void severLashCraftsCoolFromItsEmitter(GameTestHelper h) {
        var level=h.getLevel();
        var grid=List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(ModItems.LASH_EMITTER.get()),new ItemStack(ModItems.ACHERONITE_SHARD.get()),
                new ItemStack(ModItems.LEAD_INGOT.get()),PowerCellItem.charged(PowerCellItem.CAPACITY_JOULES),new ItemStack(Items.IRON_INGOT));
        var input=CraftingInput.of(3,2,grid);
        var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level);
        h.assertTrue(recipe.isPresent(),"Emitter, lens, cell, two iron and a lead ingot make a Lash");
        var lash=recipe.get().value().assemble(input,level.registryAccess());
        h.assertTrue(lash.is(ModItems.SEVER_LASH.get())&&lash.getCount()==1,"The grid makes one Sever Lash: "+lash);
        h.assertTrue(!lash.has(ModDataComponents.LASH_HEAT.get()),"A crafted Lash starts cool");
        h.succeed();
    }

    /** Own batch: the site and its ice jacket reach well past the template. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="helion_site",timeoutTicks=100)
    public static void helionDiagnosticsBenchHoldsOneOrTwoLashEmitters(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var level=h.getLevel();
            var hall=h.absolutePos(new BlockPos(10,2,10));
            com.frozendawn.world.HelionSiteBuilder.place(level,hall,hall.getY()+com.frozendawn.world.HelionSitePlacement.DEPTH);
            int half=com.frozendawn.world.HelionSiteBuilder.HALF,emitters=0;
            for(var p:BlockPos.betweenClosed(hall.offset(-half,-1,-half),hall.offset(half,com.frozendawn.world.HelionSiteBuilder.CEILING,half)))
                if(level.getBlockEntity(p) instanceof BarrelBlockEntity barrel)for(int i=0;i<barrel.getContainerSize();i++)
                    if(barrel.getItem(i).is(ModItems.LASH_EMITTER.get()))emitters+=barrel.getItem(i).getCount();
            var bench=(BarrelBlockEntity)level.getBlockEntity(hall.offset(com.frozendawn.world.HelionSiteBuilder.DIAGNOSTICS));
            int expected=LashSalvage.helionEmitters(level.getSeed(),hall.asLong());
            h.assertTrue(emitters==expected&&emitters>=1&&emitters<=2,"One or two emitters, as the site rolled: "+emitters+" of "+expected);
            h.assertTrue(bench.hasAnyMatching(s->s.is(ModItems.LASH_EMITTER.get())),"The emitters sit on the diagnostics bench");
            h.succeed();
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="helion_site",timeoutTicks=100)
    public static void helionBenchFilesTheHellwhipMemoWhereItRolls(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var level=h.getLevel();
            BlockPos hall=null;
            for(int i=0;i<49&&hall==null;i++){var at=h.absolutePos(new BlockPos(7+i%7,2,7+i/7));if(LashSalvage.helionMemo(level.getSeed(),at.asLong()))hall=at;}
            h.assertTrue(hall!=null,"Some nearby hall rolls the memo");
            com.frozendawn.world.HelionSiteBuilder.place(level,hall,hall.getY()+com.frozendawn.world.HelionSitePlacement.DEPTH);
            var bench=(BarrelBlockEntity)level.getBlockEntity(hall.offset(com.frozendawn.world.HelionSiteBuilder.DIAGNOSTICS));
            ItemStack memo=ItemStack.EMPTY;
            for(int i=0;i<bench.getContainerSize();i++)if(com.frozendawn.world.HelionSiteBuilder.isHellwhipMemo(bench.getItem(i)))memo=bench.getItem(i).copy();
            h.assertTrue(memo.is(ModItems.ORSA_DOCUMENT.get()),"The memo is an ORSA document on the diagnostics bench");
            var p=shooter(h,0);p.setItemInHand(InteractionHand.MAIN_HAND,memo);
            memo.use(level,p,InteractionHand.MAIN_HAND);
            h.assertTrue(earned(p,"found_lc4_requisition_memo")&&earned(p,"classified_information"),"Reading it archives the memo");
            h.succeed();
        });
    }

    /** Own batch: the site grades and contaminates well past this structure. */
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="reactor_site",timeoutTicks=100)
    public static void reactorMaintenanceBayHoldsTheLuckyEmitter(GameTestHelper h) {
        h.runAfterDelay(1,()->{
            var level=h.getLevel();
            BlockPos site=null;
            for(int i=0;i<49&&site==null;i++){var at=h.absolutePos(new BlockPos(7+i%7,10,7+i/7));if(LashSalvage.reactorBayEmitter(level.getSeed(),at.asLong()))site=at;}
            h.assertTrue(site!=null,"Some nearby site rolls the emitter");
            com.frozendawn.world.ReactorSiteBuilder.place(level,site);
            int emitters=0;
            for(var p:BlockPos.betweenClosed(site.offset(-13,-10,-13),site.offset(13,10,13)))
                if(level.getBlockEntity(p) instanceof BarrelBlockEntity barrel)for(int i=0;i<barrel.getContainerSize();i++)
                    if(barrel.getItem(i).is(ModItems.LASH_EMITTER.get()))emitters+=barrel.getItem(i).getCount();
            var bay=level.getBlockEntity(site.offset(com.frozendawn.world.ReactorSiteBuilder.MAINTENANCE));
            h.assertTrue(bay instanceof BarrelBlockEntity b&&b.hasAnyMatching(s->s.is(ModItems.LASH_EMITTER.get())),"The maintenance bay holds the emitter");
            h.assertTrue(emitters==1,"One emitter in the whole site: "+emitters);
            h.succeed();
        });
    }

    /** A real survival player in the template, Lash in hand, one charged cell in slot 1. */
    private static ServerPlayer shooter(GameTestHelper h,int joules) {
        var level=h.getLevel();var profile=new GameProfile(UUID.randomUUID(),"sever_lash");
        var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        player.connection=new net.neoforged.neoforge.common.util.FakePlayer(level,profile).connection;
        player.setPos(h.absoluteVec(new Vec3(EYE_X,FEET_Y,EYE_Z)));player.setGameMode(GameType.SURVIVAL);GameTestPlayers.join(h,player);
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
