package com.frozendawn.network;

import com.frozendawn.event.IceClawsHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class IceClawsInputState {

    private static boolean clientJumpHeld;
    private static BlockPos clientAnchorPos;
    private static Direction clientWallSide;

    private IceClawsInputState() {
    }

    public static boolean isClientJumpHeld() {
        return clientJumpHeld;
    }

    public static void setClientJumpHeld(boolean jumpHeld) {
        clientJumpHeld = jumpHeld;
    }

    public static IceClawsHandler.ClimbAnchor getClientAnchor() {
        if (clientAnchorPos == null || clientWallSide == null) {
            return null;
        }
        return new IceClawsHandler.ClimbAnchor(clientAnchorPos, clientWallSide);
    }

    public static void setClientAnchor(IceClawsHandler.ClimbAnchor anchor) {
        if (anchor == null) {
            clientAnchorPos = null;
            clientWallSide = null;
            return;
        }
        clientAnchorPos = anchor.pos().immutable();
        clientWallSide = anchor.wallSide();
    }
}
