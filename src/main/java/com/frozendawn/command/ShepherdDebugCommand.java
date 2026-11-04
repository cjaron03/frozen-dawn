package com.frozendawn.command;

import com.frozendawn.shepherd.ShepherdSavedData;
import com.frozendawn.shepherd.ShepherdTalk;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** CS-1 diagnostics: a read-only dump, and a replay of the first meeting for playtests. */
final class ShepherdDebugCommand {
    private ShepherdDebugCommand() { }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("shepherd").requires(source -> source.hasPermission(2))
                .then(Commands.literal("dump").executes(c -> {
                    FrozenDawnCommandOutput.heading(c.getSource(), "CS-1 Shepherd");
                    for (var line : ShepherdSavedData.get(c.getSource().getServer()).describe())
                        FrozenDawnCommandOutput.item(c.getSource(), line);
                    return 1;
                }))
                .then(Commands.literal("replay").executes(c -> {
                    boolean done = ShepherdTalk.replay(c.getSource().getServer());
                    FrozenDawnCommandOutput.item(c.getSource(), done ? "first meeting reset; right-click her to start it" : "she is not awake");
                    return done ? 1 : 0;
                }));
    }
}
