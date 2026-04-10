package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.compat.curios.CuriosCompat;
import com.frozendawn.event.IceClawsHandler;
import com.frozendawn.network.IceClawsInputPayload;
import com.frozendawn.network.IceClawsInputState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class IceClawsClientInput {

    private static boolean lastSentJumpHeld;
    private static BlockPos lastSentAnchorPos;
    private static byte lastSentWallSide = IceClawsHandler.FACE_NONE;

    private IceClawsClientInput() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            resetState();
            return;
        }

        boolean equipped = CuriosCompat.hasIceClawsEquipped(mc.player);
        boolean jumpHeld = equipped && mc.options.keyJump.isDown() && !mc.player.isPassenger();
        IceClawsHandler.ClimbAnchor anchor = equipped ? IceClawsHandler.detectLocalAnchor(mc.player) : null;
        IceClawsInputState.setClientJumpHeld(jumpHeld);
        IceClawsInputState.setClientAnchor(anchor);

        BlockPos anchorPos = anchor == null ? null : anchor.pos();
        byte wallSide = anchor == null ? IceClawsHandler.FACE_NONE : anchor.encodedWallSide();
        boolean anchorChanged = !sameAnchor(anchorPos, wallSide, lastSentAnchorPos, lastSentWallSide);
        if (jumpHeld != lastSentJumpHeld || anchorChanged) {
            PacketDistributor.sendToServer(new IceClawsInputPayload(
                    jumpHeld,
                    anchorPos == null ? BlockPos.ZERO : anchorPos,
                    wallSide
            ));
            lastSentJumpHeld = jumpHeld;
            lastSentAnchorPos = anchorPos == null ? null : anchorPos.immutable();
            lastSentWallSide = wallSide;
        }
    }

    private static void resetState() {
        IceClawsInputState.setClientJumpHeld(false);
        IceClawsInputState.setClientAnchor(null);
        lastSentJumpHeld = false;
        lastSentAnchorPos = null;
        lastSentWallSide = IceClawsHandler.FACE_NONE;
    }

    private static boolean sameAnchor(BlockPos leftPos, byte leftSide, BlockPos rightPos, byte rightSide) {
        if (leftSide != rightSide) {
            return false;
        }
        if (leftPos == null || rightPos == null) {
            return leftPos == rightPos;
        }
        return leftPos.equals(rightPos);
    }
}
