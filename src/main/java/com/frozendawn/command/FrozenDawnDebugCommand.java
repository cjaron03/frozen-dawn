package com.frozendawn.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Composes the operator surface without duplicating any gameplay authority. */
final class FrozenDawnDebugCommand {
    static final List<String> HELP_CATEGORIES = List.of(
            "world", "hearth", "heart", "postmaeve", "aggregate", "suit", "lore");

    private static final List<String> HEARTH_CHILDREN = List.of(
            "status", "list", "locate", "force-select", "reconcile", "watcher",
            "population", "master", "architect", "transmission", "survey",
            "relationship", "violation", "mood", "advance");

    private FrozenDawnDebugCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        LiteralCommandNode<CommandSourceStack> legacyHearth =
                FrozenDawnHearthCommand.hearthCommands().build();
        CommandNode<CommandSourceStack> postMaeve = requiredChild(legacyHearth, "postmaeve");
        postMaeve.addChild(requiredChild(legacyHearth, "bloom"));

        LiteralArgumentBuilder<CommandSourceStack> hearth = Commands.literal("hearth")
                .executes(command(legacyHearth));
        for (String child : HEARTH_CHILDREN) {
            hearth.then(requiredChild(legacyHearth, child));
        }

        return Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> sendHelp(context.getSource(), "debug"))
                .then(FrozenDawnWorldCommand.worldCommands())
                .then(hearth)
                .then(requiredChild(legacyHearth, "heart"))
                .then(postMaeve)
                .then(AggregateCommand.commands())
                .then(FrozenDawnSuitCommand.suitCommands())
                .then(requiredChild(legacyHearth, "lore"));
    }

    static int sendHelp(CommandSourceStack source, String requestedCategory) {
        String category = requestedCategory.toLowerCase(Locale.ROOT);
        List<String> lines = switch (category) {
            case "world" -> List.of(
                    "/fd world status [verbose] | catchup | pause | preset <name>",
                    "/fd world set day <day>",
                    "/fd world set phase <phase> [early|mid|late]",
                    "/fd world reset confirm");
            case "hearth" -> List.of(
                    "/fd hearth status [verbose] | list [verbose]",
                    "/fd hearth locate <major|minor> | reconcile",
                    "/fd hearth advance <ticks|days> <amount>",
                    "/fd hearth population|master|architect|watcher|mood|relationship");
            case "heart" -> List.of(
                    "/fd heart status [verbose] | start | set-stage <stage>",
                    "/fd heart load|nodes|collapse|maeve",
                    "/fd heart reset confirm");
            case "postmaeve" -> List.of(
                    "/fd postmaeve status [verbose] | encounters | moon | bloom",
                    "/fd postmaeve <archivist|rimebound|resonant|remnant|frostwrithe> ...",
                    "Use tab completion for spawn, force, set, purge, and reset actions.");
            case "aggregate" -> List.of(
                    "/fd aggregate status [verbose] | pressure | stage | spawn | trait",
                    "/fd aggregate stillpoint ...",
                    "/fd aggregate resolve confirm | reset confirm");
            case "suit" -> List.of(
                    "/fd suit status [verbose] | punctures <0-2>",
                    "/fd suit hearthrot status [verbose] | infect | set-stage ...");
            case "lore" -> List.of(
                    "/fd lore status [verbose] | grant | spawn-carrier | explain",
                    "/fd lore reset-player <player> confirm",
                    "/fd lore reset-world-semantic confirm");
            case "debug" -> List.of(
                    "/fd <world|hearth|heart|postmaeve|aggregate|suit|lore> ...",
                    "Destructive reset, purge, resolve, and completion actions require 'confirm'.",
                    "Use /frozendawn help <category> for focused examples.");
            default -> {
                source.sendFailure(Component.literal(
                        "Unknown category '" + requestedCategory + "'. Expected: "
                                + String.join(", ", HELP_CATEGORIES)));
                yield List.of();
            }
        };
        if (lines.isEmpty()) {
            return 0;
        }
        FrozenDawnCommandOutput.heading(source, title(category) + " Commands");
        for (String line : lines) {
            FrozenDawnCommandOutput.item(source, line);
        }
        return 1;
    }

    private static Command<CommandSourceStack> command(CommandNode<CommandSourceStack> node) {
        Command<CommandSourceStack> command = node.getCommand();
        if (command == null) {
            throw new IllegalStateException("Missing command executor for " + node.getName());
        }
        return command;
    }

    private static CommandNode<CommandSourceStack> requiredChild(
            CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        if (child == null) {
            throw new IllegalStateException(
                    "Missing command node " + parent.getName() + " " + name);
        }
        return child;
    }

    private static String title(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
