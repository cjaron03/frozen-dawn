package com.frozendawn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

final class TerminalAccessValidator {

    private static final double MAX_HORIZONTAL_DISTANCE_SQ = 4.5D * 4.5D;
    private static final double MAX_VERTICAL_DISTANCE = 3.5D;

    boolean isPlayerInRange(ServerPlayer player, BlockPos terminalPos) {
        double dx = player.getX() - (terminalPos.getX() + 0.5D);
        double dz = player.getZ() - (terminalPos.getZ() + 0.5D);
        double dy = Math.abs(player.getY() - (terminalPos.getY() + 0.5D));
        return (dx * dx + dz * dz) <= MAX_HORIZONTAL_DISTANCE_SQ && dy <= MAX_VERTICAL_DISTANCE;
    }
}
