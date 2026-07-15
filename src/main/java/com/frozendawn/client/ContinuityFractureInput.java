package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.network.ContinuityFracturePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/** Client-only movement fracture. Jump, camera, inventory, and attack remain untouched. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ContinuityFractureInput {
    private static int remainingTicks;
    private static int quarterTurns;

    private ContinuityFractureInput() {
    }

    public static void start(ContinuityFracturePayload payload) {
        remainingTicks = Math.max(0, payload.durationTicks());
        quarterTurns = payload.quarterTurns() < 0 ? -1 : 1;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (remainingTicks <= 0) {
            return;
        }
        rotate(event.getInput(), quarterTurns);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            reset();
        } else if (!minecraft.isPaused() && remainingTicks > 0) {
            remainingTicks--;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    static void rotate(Input input, int direction) {
        float oldLeftImpulse = input.leftImpulse;
        float oldForwardImpulse = input.forwardImpulse;
        boolean oldUp = input.up;
        boolean oldDown = input.down;
        boolean oldLeft = input.left;
        boolean oldRight = input.right;

        if (direction >= 0) {
            input.leftImpulse = -oldForwardImpulse;
            input.forwardImpulse = oldLeftImpulse;
            input.up = oldLeft;
            input.down = oldRight;
            input.left = oldDown;
            input.right = oldUp;
        } else {
            input.leftImpulse = oldForwardImpulse;
            input.forwardImpulse = -oldLeftImpulse;
            input.up = oldRight;
            input.down = oldLeft;
            input.left = oldUp;
            input.right = oldDown;
        }
    }

    static void reset() {
        remainingTicks = 0;
        quarterTurns = 0;
    }
}
