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
            long clock=player.serverLevel().getGameTime();
            var a=ApocalypseState.get(player.getServer());
            try {
            var stages=new java.util.ArrayList<SuffocationStage>();int[] actionBars={0};
            captureWarnings(player,stages,actionBars);
            for(int i=0;i<140;i++)PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));
            player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.EVA_CHESTPLATE.get()));
            player.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.EVA_LEGGINGS.get()));
            player.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.EVA_BOOTS.get()));
            var tank=new ItemStack(ModItems.O2_TANK.get());tank.set(ModDataComponents.O2_LEVEL.get(),400);
            player.getInventory().setItem(0,tank);float health=player.getHealth();
            for(int i=0;i<220;i++)advanceSuffocation(player,a);
            h.assertTrue(player.getHealth()==health,"Full EVA with tank remains protected after ambient air is gone");
            h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())<400,"Real tank debit replaces ambient intake");
            h.assertTrue(stages.equals(java.util.List.of(SuffocationStage.NONE,SuffocationStage.LIGHTHEADED,
                    SuffocationStage.NAUSEA,SuffocationStage.FADING,SuffocationStage.NONE)),
                    "Working EVA authoritatively clears existing danger and stays protected: "+stages);
            } finally {((net.minecraft.world.level.storage.ServerLevelData)player.serverLevel().getLevelData()).setGameTime(clock);}
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="atmospheric_breathing",timeoutTicks=100)
    public static void atmosphericRoomOxygenScarcityUsesRealSuffocationAndTankProtection(GameTestHelper h) {
        EmergencyEvaGameTest.scene(h,player->{
            var l=player.serverLevel();var a=ApocalypseState.get(player.getServer());long clock=l.getGameTime();
            var oldOxygen=com.frozendawn.data.RoomOxygenState.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());
            var cows=new java.util.ArrayList<net.minecraft.world.entity.animal.Cow>();
            var c=h.absolutePos(new net.minecraft.core.BlockPos(10,3,10));var core=c.west(2);
            try {
                com.frozendawn.world.RoomOxygenManager.reset();RoomAtmosphere.reset();
                l.getDataStorage().set(com.frozendawn.data.RoomOxygenState.NAME,new com.frozendawn.data.RoomOxygenState());
                for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)
                    l.setBlock(c.offset(x,y,z),(Math.abs(x)==2||y==-1||y==3||Math.abs(z)==2?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(),2);
                l.setBlock(core,com.frozendawn.init.ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
                ((com.frozendawn.block.GeothermalCoreBlockEntity)l.getBlockEntity(core)).onLoad();
                player.setPos(c.getCenter());
                for(var p:java.util.List.of(c.north().above(),c.south().above())) {
                    var cow=new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW,l);cow.setPos(p.getCenter());l.addFreshEntity(cow);cows.add(cow);
                }
                EmergencyEvaGameTest.setProgress(player,0);RoomAtmosphere.view(l,c);
                for(int i=0;i<5;i++)com.frozendawn.world.RoomOxygenManager.tickLevel(l);
                EmergencyEvaGameTest.setProgress(player,.90F);
                for(int t=0;t<=200;t+=20){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock+t);RoomAtmosphere.tickLevel(l);com.frozendawn.world.RoomOxygenManager.tickLevel(l);}
                h.assertTrue(RoomAtmosphere.hasAir(l,c)&&!com.frozendawn.world.TemperatureManager.hasBreathableAir(l,c),"Sealed pressurized room loses only breathable oxygen");
                var stages=new java.util.ArrayList<SuffocationStage>();int[] bars={0};var pressures=new java.util.ArrayList<Boolean>();captureWarnings(player,stages,bars,pressures);
                float health=player.getHealth();
                for(int i=0;i<199;i++)PlayerTickHandler.tickPlayerSuffocation(player,a,true);
                h.assertTrue(player.getHealth()==health&&PlayerTickHandler.isSuffocationImminent(player.getUUID()),"Existing warnings and imminent danger precede damage");
                PlayerTickHandler.tickPlayerSuffocation(player,a,true);
                h.assertTrue(player.getHealth()==health-4&&bars[0]==0,"Real suffocation damages after 200 ticks without competing action bars");
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);PlayerTickHandler.tickPlayerSuffocation(player,a,true);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);float retryHealth=player.getHealth();
                PlayerTickHandler.tickPlayerSuffocation(player,a,true);
                h.assertTrue(player.getHealth()==retryHealth&&!PlayerTickHandler.isSuffocationImminent(player.getUUID()),"Creative safety resets previous suffocation grace instead of causing immediate damage on retry");
                h.assertTrue(pressures.equals(java.util.List.of(true)),"Client receives pressure-present state while breathing oxygen is absent");
                var bytes=io.netty.buffer.Unpooled.buffer();
                try {var packet=new com.frozendawn.network.RoomPressureStatePayload(true);com.frozendawn.network.RoomPressureStatePayload.STREAM_CODEC.encode(bytes,packet);
                    h.assertTrue(packet.equals(com.frozendawn.network.RoomPressureStatePayload.STREAM_CODEC.decode(bytes)),"Pressure wire codec preserves independent gas authority");}finally{bytes.release();}
                player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));
                player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.EVA_CHESTPLATE.get()));
                player.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.EVA_LEGGINGS.get()));
                player.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.EVA_BOOTS.get()));
                var tank=new ItemStack(ModItems.O2_TANK.get());tank.set(ModDataComponents.O2_LEVEL.get(),400);player.getInventory().setItem(0,tank);
                health=player.getHealth();
                for(int i=0;i<220;i++)advanceSuffocation(player,a);
                h.assertTrue(player.getHealth()==health&&tank.get(ModDataComponents.O2_LEVEL.get())<400,"EVA tank protects and consumes oxygen in the starved room");
                h.assertTrue(!PlayerTickHandler.isSuffocationImminent(player.getUUID()),"Protected EVA clears critical danger so room NO O2 alone cannot sound the klaxon");
                var integrity=player.getData(com.frozendawn.init.ModAttachments.SUIT_INTEGRITY);integrity.setPunctures(1);
                h.assertTrue(!SuitIntegrityHandler.isVacuumExposure(player),"O2-starved pressurized gas is not a pressure differential");
                int before=tank.get(ModDataComponents.O2_LEVEL.get());
                for(int i=0;i<40;i++)SuitIntegrityHandler.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
                h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())==before,"A puncture cannot vent tank pressure into equally pressurized room gas");
                for(int i=0;i<20;i++)advanceSuffocation(player,a);
                h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())<before,"Punctured suit still pays baseline breathing consumption when no vacuum leak exists");
                integrity.setPunctures(0);EmergencyEvaHandler.issueKit(player);EmergencyEvaHandler.tick(player);
                h.assertTrue(EmergencyEvaHandler.hasLifeSupport(player),"Issued Emergency EVA remains life support when room oxygen is unavailable");
            } finally {
                cows.forEach(net.minecraft.world.entity.animal.Cow::discard);l.setBlock(core,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
                ((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(clock);com.frozendawn.world.RoomOxygenManager.reset();RoomAtmosphere.reset();com.frozendawn.world.CombustionAtmosphere.reset();
                l.getDataStorage().set(com.frozendawn.data.RoomOxygenState.NAME,com.frozendawn.data.RoomOxygenState.load(oldOxygen,l.registryAccess()));
            }
        });
    }
    private static void advanceSuffocation(net.minecraft.server.level.ServerPlayer player, ApocalypseState state) {
        var level=player.serverLevel();
        ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
        PlayerTickHandler.tickPlayerSuffocation(player,state,true);
    }
    private static void captureWarnings(net.minecraft.server.level.ServerPlayer player,
            java.util.List<SuffocationStage> stages,int[] actionBars) {
        captureWarnings(player,stages,actionBars,new java.util.ArrayList<>());
    }
    private static void captureWarnings(net.minecraft.server.level.ServerPlayer player,
            java.util.List<SuffocationStage> stages,int[] actionBars,java.util.List<Boolean> pressures) {
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(player.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        &&custom.payload() instanceof com.frozendawn.network.SuffocationStatePayload warning) {
                    if(stages.isEmpty()||stages.getLast()!=warning.stage()) stages.add(warning.stage());
                }
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        &&custom.payload() instanceof com.frozendawn.network.RoomPressureStatePayload pressure)pressures.add(pressure.pressurized());
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
