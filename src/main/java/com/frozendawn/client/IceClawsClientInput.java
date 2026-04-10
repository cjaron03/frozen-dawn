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

    private static final long DEBUG_LOG_INTERVAL = 100L;
    private static final long SAMPLE_LOG_INTERVAL = 120L;
    private static boolean lastSentJumpHeld;
    private static BlockPos lastSentAnchorPos;
    private static byte lastSentWallSide = IceClawsHandler.FACE_NONE;
    private static long lastDebugLogTick = -DEBUG_LOG_INTERVAL;
    private static long lastSampleLogTick = -SAMPLE_LOG_INTERVAL;
    private static String lastDebugState = "";

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

        long gameTime = mc.level.getGameTime();
        String anchorState = formatAnchor(anchor);
        String debugState = equipped + "|" + jumpHeld + "|" + mc.player.isShiftKeyDown()
                + "|" + anchorState + "|" + mc.player.onGround();
        boolean shouldLogState = !debugState.equals(lastDebugState) || gameTime >= lastDebugLogTick + DEBUG_LOG_INTERVAL;
        if ((equipped || jumpHeld || anchor != null) && shouldLogState) {
            lastDebugLogTick = gameTime;
            lastDebugState = debugState;
            FrozenDawn.LOGGER.info(
                    "[ICE_CLAWS][CLIENT] tick={} equipped={} keyJump={} jumpHeld={} shift={} anchor={} climb={} onGround={} dY={} pos=({}, {}, {})",
                    gameTime,
                    equipped,
                    mc.options.keyJump.isDown(),
                    jumpHeld,
                    mc.player.isShiftKeyDown(),
                    anchorState,
                    anchor != null && (jumpHeld || mc.player.isShiftKeyDown()),
                    mc.player.onGround(),
                    String.format("%.3f", mc.player.getDeltaMovement().y),
                    String.format("%.2f", mc.player.getX()),
                    String.format("%.2f", mc.player.getY()),
                    String.format("%.2f", mc.player.getZ())
            );

            if (equipped && anchor == null && gameTime >= lastSampleLogTick + SAMPLE_LOG_INTERVAL) {
                lastSampleLogTick = gameTime;
                FrozenDawn.LOGGER.info(
                        "[ICE_CLAWS][CLIENT] samples {}",
                        IceClawsHandler.debugSampleSummary(mc.player)
                );
            }
        }

        BlockPos anchorPos = anchor == null ? null : anchor.pos();
        byte wallSide = anchor == null ? IceClawsHandler.FACE_NONE : anchor.encodedWallSide();
        boolean anchorChanged = !sameAnchor(anchorPos, wallSide, lastSentAnchorPos, lastSentWallSide);
        if (jumpHeld != lastSentJumpHeld || anchorChanged) {
            PacketDistributor.sendToServer(new IceClawsInputPayload(
                    jumpHeld,
                    anchorPos == null ? BlockPos.ZERO : anchorPos,
                    wallSide
            ));
            FrozenDawn.LOGGER.info(
                    "[ICE_CLAWS][CLIENT] sent jumpHeld={} anchor={}",
                    jumpHeld,
                    formatAnchor(anchor)
            );
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
        lastDebugState = "";
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

    private static String formatAnchor(IceClawsHandler.ClimbAnchor anchor) {
        return anchor == null ? "none" : anchor.pos().toShortString() + ":" + anchor.wallSide();
    }
}
