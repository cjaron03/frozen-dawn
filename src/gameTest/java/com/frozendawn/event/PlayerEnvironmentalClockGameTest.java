package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.*;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.*;
import com.frozendawn.world.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerEnvironmentalClockGameTest {
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="player_clock",timeoutTicks=100)
    public static void environmentalTankClockKeepsNormalRateWithProgressionPaused(GameTestHelper h) {
        prepare(h,false,player->{
            var l=player.serverLevel();var a=ApocalypseState.get(player.getServer());
            EmergencyEvaGameTest.setProgress(player,.90F);equip(player);
            var tank=new ItemStack(ModItems.O2_TANK.get());player.getInventory().setItem(0,tank);
            long fixed=a.getApocalypseTicks();
            for(int parity=0;parity<2;parity++) {
                a.setApocalypseTicks(fixed+parity,player.getServer());tank.set(ModDataComponents.O2_LEVEL.get(),400);
                for(int t=0;t<200;t++) {
                    ((ServerLevelData)l.getLevelData()).setGameTime(1000+t);
                    PlayerTickHandler.tickPlayerSuffocation(player,a,true);
                }
                h.assertTrue(a.getApocalypseTicks()==fixed+parity,"Simulation cannot advance frozen apocalypse progress");
                h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())==300,
                        "200 simulation ticks cost exactly100 ordinary O2 units at either frozen-clock parity; got "+tank.get(ModDataComponents.O2_LEVEL.get()));
            }
            // Running progression has the same original one-unit-per-two-ticks rate.
            tank.set(ModDataComponents.O2_LEVEL.get(),400);
            for(int t=0;t<200;t++) {
                a.setApocalypseTicks(fixed+t,player.getServer());
                ((ServerLevelData)l.getLevelData()).setGameTime(2000+t);
                PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            }
            h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())==300,"Normal running rate remains unchanged");
            player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.ORSA_THERMAL_VISOR.get()));
            tank.set(ModDataComponents.O2_LEVEL.get(),400);a.setApocalypseTicks(fixed,player.getServer());
            for(int t=0;t<200;t++) {
                ((ServerLevelData)l.getLevelData()).setGameTime(3000+t);
                PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            }
            h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())==350,"Visor retains half-rate consumption with progression paused");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="player_clock",timeoutTicks=100)
    public static void environmentalWindClockKeepsNormalOutdoorRateWithProgressionPaused(GameTestHelper h) {
        prepare(h,false,player->{
            var l=player.serverLevel();var a=ApocalypseState.get(player.getServer());
            EmergencyEvaGameTest.setProgress(player,.50F);long fixed=a.getApocalypseTicks();
            h.assertTrue(l.canSeeSky(player.blockPosition().above()),"Open-air control actually sees the sky");
            for(int parity=0;parity<2;parity++) {
                a.setApocalypseTicks(fixed+parity,player.getServer());player.getFoodData().setExhaustion(0);
                for(int t=0;t<200;t++) {
                    ((ServerLevelData)l.getLevelData()).setGameTime(1000+t);
                    PlayerTickHandler.tickPlayerWindChill(player,a,a.getPhase(),a.getProgress());
                }
                h.assertTrue(Math.abs(player.getFoodData().getExhaustionLevel()-2)<.00001,
                        "Idle phase5 outdoor rate is ten charges of0.2 per200 ticks at either frozen parity; got "+player.getFoodData().getExhaustionLevel());
            }
            a.setApocalypseTicks(fixed,player.getServer());player.getFoodData().setExhaustion(0);player.setSprinting(true);
            for(int t=0;t<200;t++) {
                ((ServerLevelData)l.getLevelData()).setGameTime(2000+t);
                PlayerTickHandler.tickPlayerWindChill(player,a,a.getPhase(),a.getProgress());
            }
            h.assertTrue(Math.abs(player.getFoodData().getExhaustionLevel()-10)<.00001,"Existing sprint exhaustion remains1 per second");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="player_clock",timeoutTicks=100)
    public static void environmentalVacuumHasNoWindChillExhaustion(GameTestHelper h) {
        prepare(h,false,player->{
            var a=ApocalypseState.get(player.getServer());EmergencyEvaGameTest.setProgress(player,.90F);
            player.getFoodData().setExhaustion(0);
            ((ServerLevelData)player.serverLevel().getLevelData()).setGameTime(1000);
            PlayerTickHandler.tickPlayerWindChill(player,a,a.getPhase(),a.getProgress());
            h.assertTrue(player.getFoodData().getExhaustionLevel()==0,"Vacuum has no wind, even without EVA under open sky");
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="player_clock",timeoutTicks=100)
    public static void environmentalGlassRoofSealStopsWindWithoutEVA(GameTestHelper h) {
        prepare(h,true,player->{
            var a=ApocalypseState.get(player.getServer());EmergencyEvaGameTest.setProgress(player,.50F);
            h.assertTrue(player.level().canSeeSky(player.blockPosition().above()),"Glass roof retains the problematic vanilla skylight predicate");
            var view=RoomAtmosphere.view(player.serverLevel(),BlockPos.containing(player.getX(),player.getEyeY(),player.getZ()));
            h.assertTrue(view!=null&&view.geometry().seal()==RoomAtmosphere.Seal.SEALED,"Transparent fixture really seals pressure geometry");
            player.getFoodData().setExhaustion(0);
            ((ServerLevelData)player.serverLevel().getLevelData()).setGameTime(1000);
            PlayerTickHandler.tickPlayerWindChill(player,a,a.getPhase(),a.getProgress());
            h.assertTrue(player.getFoodData().getExhaustionLevel()==0,"Sealed glass shelter prevents wind hunger without armor");
            EmergencyEvaGameTest.setProgress(player,.90F);equip(player);
            var tank=new ItemStack(ModItems.O2_TANK.get());tank.set(ModDataComponents.O2_LEVEL.get(),400);player.getInventory().setItem(0,tank);
            PlayerTickHandler.syncBreathableState(player);
            h.assertTrue(PlayerTickHandler.isPlayerBreathable(player),"Sealed unstarved gas is breathable");
            for(int t=0;t<200;t++) {
                ((ServerLevelData)player.serverLevel().getLevelData()).setGameTime(2000+t);
                PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            }
            h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())==400,"Supported indoor breathing does not consume the canister");
        });
    }

    private static void equip(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));
        p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.EVA_CHESTPLATE.get()));
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.EVA_LEGGINGS.get()));
        p.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.EVA_BOOTS.get()));
    }
    private static void prepare(GameTestHelper h,boolean sealed,Consumer<ServerPlayer> test) {
        var l=h.getLevel();var origin=h.absolutePos(new BlockPos(3,2,3));
        var center=new BlockPos(origin.getX(),80,origin.getZ());
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<l.getMaxBuildHeight()-80;y++)
            l.setBlock(center.offset(x,y,z),(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        if(sealed)for(int x=-2;x<=2;x++)for(int y=0;y<=3;y++)for(int z=-2;z<=2;z++)
            if(Math.abs(x)==2||Math.abs(z)==2||y==3)l.setBlock(center.offset(x,y,z),Blocks.GLASS.defaultBlockState(),2);
        h.runAfterDelay(20,()->EmergencyEvaGameTest.scene(h,player->{
            long clock=l.getGameTime();boolean paused=FrozenDawnConfig.PAUSE_PROGRESSION.get();
            var ids=RoomIdentityState.get(l).save(new CompoundTag(),l.registryAccess());
            var air=RoomAirState.get(l).save(new CompoundTag(),l.registryAccess());
            var oxygen=RoomOxygenState.get(l).save(new CompoundTag(),l.registryAccess());
            var heat=RoomThermalState.get(l).save(new CompoundTag(),l.registryAccess());
            try {
                FrozenDawnConfig.PAUSE_PROGRESSION.set(true);RoomAtmosphere.reset();RoomOxygenManager.reset();RoomThermalManager.reset();CombustionAtmosphere.reset();
                l.getDataStorage().set(RoomIdentityState.NAME,new RoomIdentityState());l.getDataStorage().set(RoomAirState.NAME,new RoomAirState());
                l.getDataStorage().set(RoomOxygenState.NAME,new RoomOxygenState());l.getDataStorage().set(RoomThermalState.NAME,new RoomThermalState());
                player.setPos(center.getCenter());player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);test.accept(player);
            } finally {
                FrozenDawnConfig.PAUSE_PROGRESSION.set(paused);((ServerLevelData)l.getLevelData()).setGameTime(clock);
                RoomAtmosphere.reset();RoomOxygenManager.reset();RoomThermalManager.reset();CombustionAtmosphere.reset();
                l.getDataStorage().set(RoomIdentityState.NAME,RoomIdentityState.load(ids,l.registryAccess()));
                l.getDataStorage().set(RoomAirState.NAME,RoomAirState.load(air,l.registryAccess()));
                l.getDataStorage().set(RoomOxygenState.NAME,RoomOxygenState.load(oxygen,l.registryAccess()));
                l.getDataStorage().set(RoomThermalState.NAME,RoomThermalState.load(heat,l.registryAccess()));
            }
        }));
    }
}
