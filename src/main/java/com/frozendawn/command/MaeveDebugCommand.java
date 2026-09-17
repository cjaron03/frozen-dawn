package com.frozendawn.command;

import com.frozendawn.maeve.MaeveDirector;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** An operator can inspect evidence, but cannot inject beliefs or restore erased state. */
final class MaeveDebugCommand {
    private MaeveDebugCommand() { }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("maeve").requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(c -> display(c.getSource(), null)))
                .then(Commands.literal("dump").executes(c -> {
                    if (c.getSource().getEntity() instanceof ServerPlayer player) {
                        return display(c.getSource(), player.getUUID());
                    }
                    c.getSource().sendFailure(Component.literal("Console use requires /fd maeve dump <player-or-uuid>."));
                    return 0;
                }).then(Commands.argument("subject", StringArgumentType.word())
                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(Stream.concat(
                                c.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()),
                                MaeveDirector.knownPlayers(c.getSource().getServer()).stream().map(UUID::toString)), builder))
                        .executes(c -> dump(c.getSource(), StringArgumentType.getString(c, "subject")))));
    }

    private static int dump(CommandSourceStack source, String subject) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(subject);
        if (player != null) return display(source, player.getUUID());
        try {
            return display(source, UUID.fromString(subject));
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal("Use an online player name or a player UUID."));
            return 0;
        }
    }

    private static int display(CommandSourceStack source, UUID player) {
        MaeveDirector.diagnostics(source.getServer(), player).forEach(line ->
                source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }
}
