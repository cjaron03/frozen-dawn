package com.frozendawn.command;

import com.frozendawn.data.WinConditionState;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

final class FrozenDawnWinCommand {

    private FrozenDawnWinCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> winCommands() {
        return Commands.literal("win")
                .executes(FrozenDawnWinCommand::satellite)
                .then(Commands.literal("status").executes(FrozenDawnWinCommand::satellite))
                .then(Commands.literal("satellite").executes(FrozenDawnWinCommand::satellite));
    }

    private static int satellite(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        WinConditionState winState = WinConditionState.get(server);
        BlockPos pos = winState.getSatellitePos();
        FrozenDawnCommandOutput.heading(context.getSource(), "Win Condition");
        if (pos == null) {
            FrozenDawnCommandOutput.line(context.getSource(), "Satellite",
                    "not initialized");
        } else {
            FrozenDawnCommandOutput.line(context.getSource(), "Satellite",
                    pos.toShortString() + " - "
                            + (winState.isSatellitePlaced() ? "placed" : "pending"));
            FrozenDawnCommandOutput.line(context.getSource(), "Unlocks",
                    "schematic=" + winState.isSchematicUnlocked()
                            + " conspiracy=" + winState.isConspiracyDiscovered()
                            + " rocket=" + winState.isRocketBlueprintUnlocked());
            FrozenDawnCommandOutput.line(context.getSource(), "Outcome",
                    "reply=" + winState.isMartianReplySent()
                            + " launched=" + winState.isLaunchCompleted());
        }
        return 1;
    }
}
