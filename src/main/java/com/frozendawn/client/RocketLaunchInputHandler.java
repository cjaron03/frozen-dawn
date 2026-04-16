package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.RocketLaunchEntity;
import com.frozendawn.network.RocketLaunchInputPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class RocketLaunchInputHandler {

    private static boolean jumpWasDown;

    private RocketLaunchInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null) {
            jumpWasDown = false;
            return;
        }

        RocketLaunchEntity rocket = mc.player.getVehicle() instanceof RocketLaunchEntity launchEntity ? launchEntity : null;
        boolean ridingIdleRocket = rocket != null && rocket.isIdle();
        boolean jumpDown = mc.options.keyJump.isDown();
        if (ridingIdleRocket && jumpDown && !jumpWasDown) {
            PacketDistributor.sendToServer(new RocketLaunchInputPayload());
            RocketLaunchClientController.markLaunchJumpAttempt(rocket);
        }
        jumpWasDown = jumpDown;
    }
}
