package com.frozendawn.command;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.shepherd.ShepherdEggs;
import com.frozendawn.shepherd.ShepherdReactions;
import com.frozendawn.shepherd.ShepherdSavedData;
import com.frozendawn.shepherd.ShepherdTalk;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** CS-1 diagnostics: a read-only dump, and for playtests a replay of the first meeting, a set score and a skipped day. */
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
                }))
                .then(Commands.literal("skipday").executes(c -> {
                    ShepherdReactions.skipDay(c.getSource().getServer());
                    FrozenDawnCommandOutput.item(c.getSource(), "her cooldowns and clock are a day on");
                    return 1;
                }))
                .then(Commands.literal("resetgap").executes(c -> {
                    ShepherdSavedData.get(c.getSource().getServer()).lastAmbient(ShepherdSavedData.NOT_SET);
                    FrozenDawnCommandOutput.item(c.getSource(), "she may speak up at the next poll");
                    return 1;
                }))
                .then(Commands.literal("hearthhere").executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    ShepherdEggs.wall(player.serverLevel(), List.of(player), List.of(player.blockPosition()), ReturnedHearthSavedData.get(c.getSource().getServer()).maeveErased());
                    return 1;
                }))
                .then(Commands.literal("affinity").then(Commands.argument("value", IntegerArgumentType.integer(-1000, 1000)).executes(c -> {
                    var data = ShepherdSavedData.get(c.getSource().getServer());
                    data.affinity(IntegerArgumentType.getInteger(c, "value"));
                    FrozenDawnCommandOutput.item(c.getSource(), "affinity=" + data.affinity() + " tier=" + data.tier());
                    return 1;
                })));
    }
}
