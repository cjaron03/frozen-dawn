package com.frozendawn.command;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.debug.architect.ArchitectLab;
import com.frozendawn.debug.architect.ArchitectLabScenario;
import com.frozendawn.debug.architect.ArchitectDebugReports;
import com.frozendawn.init.ModEntities;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Operator-only, server-side diagnostics and lab control. */
final class ArchitectDebugCommand {
    private ArchitectDebugCommand() { }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var root = Commands.literal("architect").requires(source -> source.hasPermission(2));
        for (String operation : new String[]{"inspect", "dump", "stop"}) {
            root.then(Commands.literal(operation).then(Commands.argument("entity", EntityArgument.entity())
                    .executes(c -> execute(c.getSource(), actor(EntityArgument.getEntity(c, "entity")), operation))));
        }
        root.then(Commands.literal("record").then(Commands.argument("entity", EntityArgument.entity())
                .executes(c -> start(c.getSource(), actor(EntityArgument.getEntity(c, "entity")), null))
                .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(c -> start(c.getSource(), actor(EntityArgument.getEntity(c, "entity")), LongArgumentType.getLong(c, "seed"))))));
        root.then(Commands.literal("mark").then(Commands.argument("entity", EntityArgument.entity())
                .then(Commands.argument("label", StringArgumentType.greedyString())
                        .executes(c -> {
                            var a = actor(EntityArgument.getEntity(c, "entity"));
                            if (!a.decisionJournal().enabled()) {
                                c.getSource().sendFailure(Component.literal("Recording is off. Use /fd architect record <entity> first."));
                                return 0;
                            }
                            String label = StringArgumentType.getString(c, "label");
                            a.recordDecision("MARK", null, label.substring(0, Math.min(200, label.length())));
                            c.getSource().sendSuccess(() -> Component.literal("Architect marker recorded."), false);
                            return 1;
                        }))));
        root.then(Commands.literal("list").executes(c -> list(c.getSource())));
        root.then(Commands.literal("reset").then(Commands.argument("entity", EntityArgument.entity())
                .executes(c -> {
                    var a = actor(EntityArgument.getEntity(c, "entity"));
                    a.debugResetApproach();
                    c.getSource().sendSuccess(() -> Component.literal("Reset Architect approach state: " + a.labSummary()), false);
                    return 1;
                })));
        root.then(Commands.literal("approach").then(Commands.argument("entity", EntityArgument.entity())
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(c -> approach(c.getSource(), actor(EntityArgument.getEntity(c, "entity")),
                                EntityArgument.getEntity(c, "target"))))));
        root.then(labCommands());
        root.then(wildernessCommands());
        root.then(com.frozendawn.debug.architect.ArchitectVisualDebug.commands("auto"));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> wildernessCommands() {
        var root = Commands.literal("wilderness");
        root.then(com.frozendawn.debug.architect.ArchitectVisualDebug.commands("wilderness"));
        for (String operation : new String[]{"setup", "reset", "run", "stop", "dump", "inspect", "tp", "leave"}) {
            root.then(Commands.literal(operation).executes(c ->
                    com.frozendawn.debug.architect.ArchitectWildernessLab.command(c.getSource(), operation, "")));
        }
        root.then(Commands.literal("seed").then(Commands.argument("seed", LongArgumentType.longArg())
                .executes(c -> com.frozendawn.debug.architect.ArchitectWildernessLab.command(c.getSource(), "seed",
                        Long.toString(LongArgumentType.getLong(c, "seed"))))));
        root.then(Commands.literal("scenario").then(Commands.argument("scenario", StringArgumentType.word())
                .suggests((c,b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        com.frozendawn.debug.architect.ArchitectWildernessLab.scenarios(), b))
                .executes(c -> com.frozendawn.debug.architect.ArchitectWildernessLab.command(c.getSource(), "scenario",
                        StringArgumentType.getString(c, "scenario")))));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> labCommands() {
        var lab = Commands.literal("lab");
        lab.then(com.frozendawn.debug.architect.ArchitectVisualDebug.commands("lab"));
        for (String operation : new String[]{"setup", "reset", "run", "dump", "inspect", "tp"}) {
            lab.then(Commands.literal(operation).executes(c -> ArchitectLab.command(c.getSource(), operation, "")));
        }
        lab.then(Commands.literal("scenario").then(Commands.argument("scenario", StringArgumentType.word())
                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        java.util.Arrays.stream(ArchitectLabScenario.values()).map(s -> s.id), b))
                .executes(c -> ArchitectLab.command(c.getSource(), "scenario", StringArgumentType.getString(c, "scenario")))));
        lab.then(Commands.literal("seed").then(Commands.argument("seed", LongArgumentType.longArg())
                .executes(c -> ArchitectLab.command(c.getSource(), "seed", Long.toString(LongArgumentType.getLong(c, "seed"))))));
        var rotation = Commands.literal("rotation");
        for (String degrees : new String[]{"0", "90", "180", "270"}) {
            rotation.then(Commands.literal(degrees).executes(c -> ArchitectLab.command(c.getSource(), "rotation", degrees)));
        }
        lab.then(rotation);
        lab.then(Commands.literal("target")
                .then(Commands.literal("static").executes(c -> ArchitectLab.command(c.getSource(), "target", "static")))
                .then(Commands.literal("live").executes(c -> ArchitectLab.command(c.getSource(), "target", "live"))));
        lab.then(Commands.literal("mark").then(Commands.argument("label", StringArgumentType.greedyString())
                .executes(c -> ArchitectLab.command(c.getSource(), "mark", StringArgumentType.getString(c, "label")))));
        return lab;
    }

    private static ArchitectEntity actor(net.minecraft.world.entity.Entity entity) throws CommandSyntaxException {
        if (entity instanceof ArchitectEntity architect) return architect;
        throw new SimpleCommandExceptionType(Component.literal("Select one ordinary Architect.")).create();
    }

    private static int approach(CommandSourceStack source, ArchitectEntity architect, net.minecraft.world.entity.Entity target)
            throws CommandSyntaxException {
        if (!(target instanceof LivingEntity living)) {
            throw new SimpleCommandExceptionType(Component.literal("Approach target must be a living entity.")).create();
        }
        if (living == architect) {
            throw new SimpleCommandExceptionType(Component.literal("An Architect cannot approach itself.")).create();
        }
        architect.debugForceApproach(living);
        source.sendSuccess(() -> Component.literal("Forced APPROACH: " + architect.labSummary()), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (ArchitectEntity architect : level.getEntities(ModEntities.ARCHITECT.get(), a -> true)) {
                lines.add(level.dimension().location() + " " + architect.labSummary());
            }
        }
        if (lines.isEmpty()) {
            source.sendFailure(Component.literal("No Architects are loaded."));
            return 0;
        }
        String text = String.join("\n", lines);
        source.sendSuccess(() -> Component.literal(text), false);
        return lines.size();
    }

    private static int start(CommandSourceStack source, ArchitectEntity architect, Long seed) {
        architect.startDecisionRecording(seed);
        source.sendSuccess(() -> Component.literal("Started a fresh Architect journal" + (seed == null ? "." : " with RNG seed " + seed + ".")), false);
        return 1;
    }

    private static int execute(CommandSourceStack source, ArchitectEntity architect, String operation) {
        if (operation.equals("stop")) {
            architect.recordDecision("RECORD_STOP", null, "");
            com.frozendawn.debug.architect.ArchitectVisualDebug.capture(architect, true);
            architect.decisionJournal().finish(architect.level().getGameTime(), "STOPPED", "Stopped by operator");
            architect.clearDebugTargetLock();
        }
        if (!operation.equals("dump")) {
            ArchitectLab.reply(source, architect.inspectDecisions());
            return 1;
        }
        try {
            com.frozendawn.debug.architect.ArchitectVisualDebug.capture(architect, true);
            var path = ArchitectDebugReports.export(ArchitectLab.reports(source.getLevel()), architect.decisionJournal(),
                    java.util.Map.of("actorUuid", architect.getUUID().toString(), "inspection", architect.inspectDecisions()));
            ArchitectLab.reply(source, "Exported run " + architect.decisionJournal().runId() + " to " + path);
            return 1;
        } catch (IOException e) {
            ArchitectLab.reply(source, "EXPORT FAILED: " + e.getMessage());
            return 0;
        }
    }
}
