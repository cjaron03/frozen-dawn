package com.frozendawn.airlock;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AirlockDoorBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Handle the valve before crouching with a held item bypasses normal block interaction. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class AirlockDoorInteraction {
    private AirlockDoorInteraction() { }
    @SubscribeEvent
    public static void useValve(PlayerInteractEvent.RightClickBlock event) {
        var player=event.getEntity();
        if(!player.isShiftKeyDown()||player.isSpectator()
                ||!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof AirlockDoorBlock))return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if(event.getHand()==InteractionHand.MAIN_HAND&&event.getLevel() instanceof ServerLevel level)
            AirlockManager.equalizeInnerDoor(level,event.getPos(),player);
    }
}
