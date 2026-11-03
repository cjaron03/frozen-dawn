package com.frozendawn.command;

import com.frozendawn.uplink.UplinkSavedData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Read-only ORSA Uplink diagnostics: archive progress, cooldown, signal sites and supply drops. */
final class UplinkDebugCommand {
    private UplinkDebugCommand() { }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("uplink").requires(source -> source.hasPermission(2))
                .then(Commands.literal("dump").executes(c -> {
                    var server = c.getSource().getServer();
                    FrozenDawnCommandOutput.heading(c.getSource(), "ORSA Uplink");
                    for (var line : UplinkSavedData.get(server).describe(server.overworld().getGameTime()))
                        FrozenDawnCommandOutput.item(c.getSource(), line);
                    return 1;
                }));
    }
}
