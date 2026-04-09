package com.frozendawn.command;

import com.frozendawn.data.WinConditionState;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

final class FrozenDawnWinCommand {

    private FrozenDawnWinCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> winCommands() {
        return Commands.literal("win")
                .then(Commands.literal("satellite").executes(FrozenDawnWinCommand::satellite));
    }

    private static int satellite(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        WinConditionState winState = WinConditionState.get(server);
        BlockPos pos = winState.getSatellitePos();
        if (pos == null) {
            context.getSource().sendSuccess(() -> Component.literal("  Satellite: not yet initialized"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Satellite: (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                            + " | Placed: " + winState.isSatellitePlaced()
                            + " | Schematic: " + winState.isSchematicUnlocked()), false);
        }
        return 1;
    }
}
