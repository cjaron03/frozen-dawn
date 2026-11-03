package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.cyclone.*;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CycloneWindGameTest {
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone_wind",timeoutTicks=100)
    public static void cycloneWindMobMotionUsesRealRoofWindbreakAndRestoresSpeed(GameTestHelper h) {
        scene(h,p->{
            var l=p.serverLevel();var mob=ModEntities.FROSTBITTEN.get().create(l);mob.setNoAi(true);mob.setPos(p.position());l.addFreshEntity(mob);
            try {
                force(p,CycloneStage.PEAK);float health=mob.getHealth();var head=BlockPos.containing(mob.getEyePosition());
                h.assertTrue(CycloneWind.exposure(l,mob,0)==1,"Actual open-sky fixture has full exposure");
                var v=new Vec3(-.2,.07,.1);mob.setDeltaMovement(v);CycloneMobWind.apply(l,mob);
                h.assertTrue(mob.getDeltaMovement().x>v.x&&mob.getDeltaMovement().y==v.y&&mob.getDeltaMovement().z==v.z,"Real mob gets horizontal drift/headwind drag only");
                h.assertTrue(mob.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CycloneMobWind.DRAG),"Peak owns a transient movement modifier");
                l.setBlock(head.west(2),Blocks.STONE.defaultBlockState(),3);
                h.assertTrue(CycloneWind.exposure(l,mob,0)==.5f,"Upwind full block halves exposure");
                mob.setDeltaMovement(Vec3.ZERO);CycloneMobWind.apply(l,mob);double sheltered=mob.getDeltaMovement().x;
                l.setBlock(head.west(2),Blocks.AIR.defaultBlockState(),3);mob.setDeltaMovement(Vec3.ZERO);CycloneMobWind.apply(l,mob);
                near(h,mob.getDeltaMovement().x,2*sheltered,"Open drift is twice the windbreak drift");
                l.setBlock(head.above(2),Blocks.GLASS.defaultBlockState(),3);
                h.assertTrue(CycloneWind.exposure(l,mob,0)==0,"Transparent roof blocks physical wind via heightmap");
                mob.setDeltaMovement(v);CycloneMobWind.apply(l,mob);h.assertTrue(mob.getDeltaMovement().equals(v),"Roof leaves velocity untouched");
                h.assertTrue(!mob.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CycloneMobWind.DRAG),"Shelter immediately removes only the wind modifier");
                h.assertTrue(mob.getHealth()==health&&mob.getTarget()==null,"Wind neither damages nor assigns an AI target");
            }finally {mob.discard();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone_wind",timeoutTicks=100)
    public static void cycloneWindAnchorsBossesAndCleansUpAtStormEnd(GameTestHelper h) {
        scene(h,p->{
            var l=p.serverLevel();force(p,CycloneStage.PEAK);
            var sheep=EntityType.SHEEP.create(l);sheep.setPos(p.position());sheep.setNoAi(true);l.addFreshEntity(sheep);
            var boss=EntityType.WITHER.create(l);boss.setPos(p.position());boss.setNoAi(true);
            var master=ModEntities.ARCHITECT.get().create(l);master.setPos(p.position());master.bindToHearthMasterArchitect(UUID.randomUUID(),p.blockPosition(),0);
            try {
                h.assertTrue(boss.getType().is(CycloneMobWind.ANCHORED)&&ModEntities.AGGREGATE.get().is(CycloneMobWind.ANCHORED),"Real data tag anchors vanilla and Frozen Dawn bosses");
                h.assertTrue(CycloneMobWind.anchored(master),"Master subtype is anchored without anchoring all regular Architects");
                var v=new Vec3(-.2,.3,.1);boss.setDeltaMovement(v);master.setDeltaMovement(v);
                CycloneMobWind.apply(l,boss);CycloneMobWind.apply(l,master);
                h.assertTrue(boss.getDeltaMovement().equals(v)&&master.getDeltaMovement().equals(v),"Boss motion untouched");
                sheep.setDeltaMovement(Vec3.ZERO);CycloneMobWind.apply(l,sheep);h.assertTrue(sheep.getDeltaMovement().x>0,"Ordinary vanilla animals receive wind");
                CycloneSavedData.get(l.getServer()).end();sheep.setDeltaMovement(v);CycloneMobWind.apply(l,sheep);
                h.assertTrue(sheep.getDeltaMovement().equals(v)&&!sheep.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CycloneMobWind.DRAG),"Ending storm restores exact motion and removes drag immediately");
            }finally {sheep.discard();boss.discard();master.discard();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone_wind",timeoutTicks=100)
    public static void cycloneWindKeepsEvaEligibleAndExemptsFlightRidingCalmAndScaleZero(GameTestHelper h) {
        scene(h,p->{
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));
            p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.EVA_CHESTPLATE.get()));
            p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.EVA_LEGGINGS.get()));
            p.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.EVA_BOOTS.get()));
            h.assertTrue(MobFreezeHandler.getFullSetTier(p)==3&&CycloneWind.eligiblePlayer(p),"EVA prevents cold, not physical wind");
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.ORSA_THERMAL_VISOR.get()));
            h.assertTrue(MobFreezeHandler.getFullSetTier(p)==3&&CycloneWind.eligiblePlayer(p),"Visor rig retains wind movement");
            p.setGameMode(GameType.CREATIVE);h.assertTrue(!CycloneWind.eligiblePlayer(p),"Creative exempt");
            p.setGameMode(GameType.SPECTATOR);h.assertTrue(!CycloneWind.eligiblePlayer(p),"Spectator exempt");p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().flying=true;h.assertTrue(!CycloneWind.eligiblePlayer(p),"Flight deferred");p.getAbilities().flying=false;
            var l=p.serverLevel();var sheep=EntityType.SHEEP.create(l);sheep.setPos(p.position());sheep.setNoAi(true);l.addFreshEntity(sheep);
            var boat=EntityType.BOAT.create(l);boat.setPos(p.position());l.addFreshEntity(boat);
            try {
                p.startRiding(boat,true);h.assertTrue(!CycloneWind.eligiblePlayer(p),"Vehicle handling deferred");p.stopRiding();p.setPos(sheep.position());
                var v=new Vec3(-.2,0,.1);force(p,CycloneStage.FALSE_CALM);sheep.setDeltaMovement(v);CycloneMobWind.apply(l,sheep);
                h.assertTrue(sheep.getDeltaMovement().equals(v),"False calm leaves ordinary movement exact");
                force(p,CycloneStage.PEAK);FrozenDawnConfig.CYCLONE_PUSH_SCALE.set(0.0);sheep.setDeltaMovement(Vec3.ZERO);CycloneMobWind.apply(l,sheep);
                h.assertTrue(sheep.getDeltaMovement().equals(Vec3.ZERO),"Server scale zero disables added drift");
                sheep.setPos(p.position().add(100,0,0));sheep.setDeltaMovement(v);CycloneMobWind.apply(l,sheep);
                h.assertTrue(sheep.getDeltaMovement().equals(v)&&!sheep.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(CycloneMobWind.DRAG),"Mobs beyond64blocks untouched and cleaned up");
            }finally {p.stopRiding();boat.discard();sheep.discard();}
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone_wind",timeoutTicks=100)
    public static void cycloneWindReplayFunctionsLoadWithNativeParser(GameTestHelper h) {
        for(var name:java.util.List.of("load","bootstrap","tick","setup","controls","more","peak","end","read","survival","creative","mobs","open","windbreak","roof","glass","moderate","severe","catastrophic","eva","visor"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cyclone_wind",name)).isPresent(),"Native wind replay parser loaded "+name);
        h.succeed();
    }
    private static void force(ServerPlayer p,CycloneStage stage) {
        var l=p.serverLevel();long time=l.getGameTime();var s=CycloneStorm.roll(123,CycloneSeverity.CATASTROPHIC,p.getX(),p.getZ(),time,p.getUUID(),0).atStage(stage,time,.5f);
        CycloneSavedData.get(l.getServer()).startForced(s);
    }
    private static void near(GameTestHelper h,double a,double b,String message){h.assertTrue(Math.abs(a-b)<1e-6,message+": "+a+" vs "+b);}
    private static void scene(GameTestHelper h,Consumer<ServerPlayer> body) {
        var l=h.getLevel();var c=h.absolutePos(new BlockPos(5,0,5));c=new BlockPos(c.getX(),80,c.getZ());final var center=c;
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=79;y<l.getMaxBuildHeight();y++)
            l.setBlock(center.offset(x,y-80,z),(y==79?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        h.runAfterDelay(20,()->EmergencyEvaGameTest.scene(h,p->{
            long time=l.getGameTime();double scale=FrozenDawnConfig.CYCLONE_PUSH_SCALE.get();
            var saved=CycloneSavedData.get(l.getServer()).save(new CompoundTag(),l.registryAccess());
            try {
                EmergencyEvaGameTest.setProgress(p,.5f);p.setPos(center.getX()+.5,80,center.getZ()+.5);
                ((ServerLevelData)l.getLevelData()).setGameTime(40000);FrozenDawnConfig.CYCLONE_PUSH_SCALE.set(1.0);
                l.getDataStorage().set("frozendawn_cyclones",new CycloneSavedData());body.accept(p);
            }finally {
                ((ServerLevelData)l.getLevelData()).setGameTime(time);FrozenDawnConfig.CYCLONE_PUSH_SCALE.set(scale);
                l.getDataStorage().set("frozendawn_cyclones",CycloneSavedData.load(saved,l.registryAccess()));
            }
        }));
    }
}
