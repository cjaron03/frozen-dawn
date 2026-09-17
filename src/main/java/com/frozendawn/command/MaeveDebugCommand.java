package com.frozendawn.command;

import com.frozendawn.maeve.MaeveDirector;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
                .then(Commands.literal("status").executes(c -> display(c.getSource(), null, null)))
                .then(Commands.literal("dump").executes(c -> inspect(c.getSource(), null, null))
                        .then(Commands.argument("subject", StringArgumentType.word())
                                .suggests(MaeveDebugCommand::subjects)
                                .executes(c -> inspect(c.getSource(), StringArgumentType.getString(c, "subject"), null))))
                .then(Commands.literal("explain")
                        .then(Commands.argument("pattern", StringArgumentType.word())
                                .suggests((c, builder) -> SharedSuggestionProvider.suggest(
                                        MaeveDirector.diagnosticPatterns(c.getSource().getServer(),
                                                c.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null), builder))
                                .executes(c -> inspect(c.getSource(), null, StringArgumentType.getString(c, "pattern")))
                                .then(Commands.argument("subject", StringArgumentType.word())
                                        .suggests(MaeveDebugCommand::subjects)
                                        .executes(c -> inspect(c.getSource(), StringArgumentType.getString(c, "subject"),
                                                StringArgumentType.getString(c, "pattern"))))));
    }

    private static CompletableFuture<Suggestions> subjects(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Stream.concat(
                context.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()),
                MaeveDirector.knownPlayers(context.getSource().getServer()).stream().map(UUID::toString)), builder);
    }

    private static int inspect(CommandSourceStack source, String subject, String pattern) {
        UUID id;
        if (subject == null) {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("Console use requires /fd maeve "
                        + (pattern == null ? "dump" : "explain <pattern>") + " <player-or-uuid>."));
                return 0;
            }
            id = player.getUUID();
        } else {
            ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(subject);
            try {
                id = player == null ? UUID.fromString(subject) : player.getUUID();
            } catch (IllegalArgumentException invalid) {
                source.sendFailure(Component.literal("Use an online player name or a player UUID."));
                return 0;
            }
        }
        return display(source, id, pattern == null ? null : pattern.toUpperCase(Locale.ROOT));
    }

    private static int display(CommandSourceStack source, UUID player, String pattern) {
        var lines = pattern == null ? MaeveDirector.diagnostics(source.getServer(), player)
                : MaeveDirector.explain(source.getServer(), player, pattern);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }
}
