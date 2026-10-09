package com.frozendawn.event;
import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.RoomAtmosphere;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphericBreathingGameTest {
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="atmospheric_breathing",timeoutTicks=100)
    public static void atmosphericUnsuitedPlayerKeepsTenSecondGrace(GameTestHelper h) {
        EmergencyEvaGameTest.scene(h,player->{
            RoomAtmosphere.reset();EmergencyEvaGameTest.setProgress(player,0.90F);
            var a=ApocalypseState.get(player.getServer());float health=player.getHealth();
            var stages=new java.util.ArrayList<SuffocationStage>();
            int[] actionBars={0};
            captureWarnings(player,stages,actionBars);
            for(int i=0;i<199;i++)PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            h.assertTrue(player.getHealth()==health,"Unsuited vacuum is not instant damage/death");
            PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            h.assertTrue(player.getHealth()==health-4,"After 200 ticks, existing two-heart atmospheric damage begins");
            h.assertTrue(!player.getDeltaMovement().equals(new net.minecraft.world.phys.Vec3(0,-1,0)),"Suffocation does not freeze/teleport movement");
            h.assertTrue(stages.equals(java.util.List.of(SuffocationStage.NONE,SuffocationStage.LIGHTHEADED,
                    SuffocationStage.NAUSEA,SuffocationStage.FADING,SuffocationStage.DYING)),
                    "Real handler sends only the highest crossed severity, in order: "+stages);
            h.assertTrue(actionBars[0]==0,"Server sends no competing vanilla action-bar packets");
            h.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)
                    &&player.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION)
                    &&player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==4,
                    "Message arbitration preserves all cumulative suffocation effects");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            h.assertTrue(stages.getLast()==SuffocationStage.NONE,"Entering safety clears client danger authoritatively");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="atmospheric_breathing",timeoutTicks=100)
    public static void atmosphericSealedEvaUsesTankAfterBreach(GameTestHelper h) {
        EmergencyEvaGameTest.scene(h,player->{
            RoomAtmosphere.reset();EmergencyEvaGameTest.setProgress(player,0.90F);
            var a=ApocalypseState.get(player.getServer());
            var stages=new java.util.ArrayList<SuffocationStage>();int[] actionBars={0};
            captureWarnings(player,stages,actionBars);
            for(int i=0;i<140;i++)PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));
            player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.EVA_CHESTPLATE.get()));
            player.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.EVA_LEGGINGS.get()));
            player.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.EVA_BOOTS.get()));
            var tank=new ItemStack(ModItems.O2_TANK.get());tank.set(ModDataComponents.O2_LEVEL.get(),400);
            player.getInventory().setItem(0,tank);float health=player.getHealth();
            for(int i=0;i<220;i++)PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            h.assertTrue(player.getHealth()==health,"Full EVA with tank remains protected after ambient air is gone");
            h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())<400,"Real tank debit replaces ambient intake");
            h.assertTrue(stages.equals(java.util.List.of(SuffocationStage.NONE,SuffocationStage.LIGHTHEADED,
                    SuffocationStage.NAUSEA,SuffocationStage.FADING,SuffocationStage.NONE)),
                    "Working EVA authoritatively clears existing danger and stays protected: "+stages);
        });
    }

    private static void captureWarnings(net.minecraft.server.level.ServerPlayer player,
            java.util.List<SuffocationStage> stages,int[] actionBars) {
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(player.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        &&custom.payload() instanceof com.frozendawn.network.SuffocationStatePayload warning) {
                    if(stages.isEmpty()||stages.getLast()!=warning.stage()) stages.add(warning.stage());
                }
                if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket)
                    actionBars[0]++;
                if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat&&chat.overlay())
                    actionBars[0]++;
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,
                    net.minecraft.network.PacketSendListener listener) {send(packet);}
        };
    }
}
