package com.frozendawn.command;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModEntities;
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.maeve.ScribeLab;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

/** An operator can inspect evidence, but cannot inject beliefs or restore erased state. Only the lab client adds {@code scribe}. */
final class MaeveDebugCommand {
    private MaeveDebugCommand() { }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var root = Commands.literal("maeve").requires(source -> source.hasPermission(2));
        if (ScribeLab.ENABLED) root.then(Commands.literal("scribe")
                .then(Commands.literal("seed").executes(c -> lab(c.getSource(), ScribeLab.seed(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("clear-cooldown").executes(c -> lab(c.getSource(), ScribeLab.clearCooldown(c.getSource().getServer()))))
                .then(Commands.literal("roll").executes(c -> roll(c.getSource().getPlayerOrException(), BlockPos.containing(c.getSource().getPosition())))));
        return root
                .then(Commands.literal("status").executes(c -> display(c.getSource(), null, null)))
                .then(Commands.literal("dump").executes(c -> inspect(c.getSource(), null, null))
                        .then(Commands.argument("subject", StringArgumentType.word())
                                .suggests(MaeveDebugCommand::subjects)
                                .executes(c -> inspect(c.getSource(), StringArgumentType.getString(c, "subject"), null))))
                .then(Commands.literal("confidence")
                        .then(Commands.argument("pattern", StringArgumentType.word())
                                .suggests((c, builder) -> SharedSuggestionProvider.suggest(
                                        MaeveDirector.diagnosticPatterns(c.getSource().getServer(),
                                                c.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null), builder))
                                .executes(c -> confidence(c.getSource(), StringArgumentType.getString(c, "pattern")))))
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

    /** Function output is silent, so lab results go straight to the player. */
    private static int lab(CommandSourceStack source, String result) {
        if (source.getEntity() instanceof ServerPlayer player) player.sendSystemMessage(Component.literal(result));
        else source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    /** One natural Architect spawn decided here and now, as ArchitectSpawner would; a refused one never enters the world. */
    private static int roll(ServerPlayer player, BlockPos at) {
        var level = player.serverLevel();
        ArchitectEntity architect = ModEntities.ARCHITECT.get().create(level, null, at, MobSpawnType.NATURAL, true, false);
        if (architect == null) return lab(player.createCommandSourceStack(), "No Architect could be made there.");
        architect.preSeedObservation(level, player);
        boolean scribe = MaeveDirector.designateScribe(architect, player);
        if (scribe) {
            architect.becomeScribe();
            architect.armSpawnObserveCue(player);
            if (level.addFreshEntity(architect)) MaeveDirector.observePawn(architect);
            else MaeveDirector.scribeEnded(architect, "SPAWN_REJECTED");
        } else architect.discard();
        String decision = MaeveDirector.diagnostics(player.server, player.getUUID()).stream()
                .filter(line -> line.startsWith("SCRIBE: ")).findFirst().orElse("SCRIBE: no decision");
        return lab(player.createCommandSourceStack(), (scribe ? "Roll: Scribe. " : "Roll: ordinary, not spawned. ") + decision);
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

    /** Read-only: the result is the current confidence as a whole percent (rounded down), so functions can branch on it. */
    private static int confidence(CommandSourceStack source, String pattern) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Run /fd maeve confidence as a player."));
            return 0;
        }
        String key = pattern.toUpperCase(Locale.ROOT);
        double value = MaeveDirector.snapshot(source.getServer(), player.getUUID()).beliefs().stream()
                .filter(b -> b.pattern().equals(key)).mapToDouble(MaeveDirector.BeliefSnapshot::confidence).findFirst().orElse(0);
        int percent = (int) Math.floor(value * 100 + 1e-9);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s confidence=%.4f (%d%%)", key, value, percent)), false);
        return percent;
    }

    private static int display(CommandSourceStack source, UUID player, String pattern) {
        var lines = pattern == null ? MaeveDirector.diagnostics(source.getServer(), player)
                : MaeveDirector.explain(source.getServer(), player, pattern);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }
}
