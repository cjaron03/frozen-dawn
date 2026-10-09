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
            for(int i=0;i<199;i++)PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            h.assertTrue(player.getHealth()==health,"Unsuited vacuum is not instant damage/death");
            PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            h.assertTrue(player.getHealth()==health-4,"After 200 ticks, existing two-heart atmospheric damage begins");
            h.assertTrue(!player.getDeltaMovement().equals(new net.minecraft.world.phys.Vec3(0,-1,0)),"Suffocation does not freeze/teleport movement");
        });
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="atmospheric_breathing",timeoutTicks=100)
    public static void atmosphericSealedEvaUsesTankAfterBreach(GameTestHelper h) {
        EmergencyEvaGameTest.scene(h,player->{
            RoomAtmosphere.reset();EmergencyEvaGameTest.setProgress(player,0.90F);
            player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));
            player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.EVA_CHESTPLATE.get()));
            player.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.EVA_LEGGINGS.get()));
            player.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.EVA_BOOTS.get()));
            var tank=new ItemStack(ModItems.O2_TANK.get());tank.set(ModDataComponents.O2_LEVEL.get(),400);
            player.getInventory().setItem(0,tank);float health=player.getHealth();
            var a=ApocalypseState.get(player.getServer());
            for(int i=0;i<220;i++)PlayerTickHandler.tickPlayerSuffocation(player,a,true);
            h.assertTrue(player.getHealth()==health,"Full EVA with tank remains protected after ambient air is gone");
            h.assertTrue(tank.get(ModDataComponents.O2_LEVEL.get())<400,"Real tank debit replaces ambient intake");
        });
    }
}
