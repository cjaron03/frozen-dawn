package com.frozendawn.command;

import com.frozendawn.shepherd.ShepherdSavedData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Read-only CS-1 diagnostics: her stage, where she was last seen, whom she follows, her score and flags. */
final class ShepherdDebugCommand {
    private ShepherdDebugCommand() { }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("shepherd").requires(source -> source.hasPermission(2))
                .then(Commands.literal("dump").executes(c -> {
                    FrozenDawnCommandOutput.heading(c.getSource(), "CS-1 Shepherd");
                    for (var line : ShepherdSavedData.get(c.getSource().getServer()).describe())
                        FrozenDawnCommandOutput.item(c.getSource(), line);
                    return 1;
                }));
    }
}
