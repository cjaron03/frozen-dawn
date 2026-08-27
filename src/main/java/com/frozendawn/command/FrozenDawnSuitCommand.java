package com.frozendawn.command;

import com.frozendawn.data.SuitIntegrity;
import com.frozendawn.event.SuitIntegrityHandler;
import com.frozendawn.hearthrot.HearthrotManager;
import com.frozendawn.hearthrot.HearthrotPolicy;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class FrozenDawnSuitCommand {

    private FrozenDawnSuitCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> suitCommands() {
        return Commands.literal("suit")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), false))
                        .then(Commands.literal("verbose")
                                .executes(context -> status(context.getSource(), true))))
                .then(Commands.literal("punctures")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 2))
                                .executes(context -> setPunctures(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("hearthrot")
                        .then(Commands.literal("status")
                                .executes(context -> hearthrotStatus(
                                        context.getSource(), false))
                                .then(Commands.literal("verbose")
                                        .executes(context -> hearthrotStatus(
                                                context.getSource(), true))))
                        .then(Commands.literal("infect")
                                .executes(context -> infect(
                                        context.getSource())))
                        .then(Commands.literal("set-stage")
                                .then(Commands.argument(
                                                "stage", IntegerArgumentType.integer(0, 6))
                                        .executes(context -> setStage(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(
                                                        context, "stage")))))
                        .then(Commands.literal("set-progress")
                                .then(Commands.argument(
                                                "percent", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> setProgress(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(
                                                        context, "percent")))))
                        .then(Commands.literal("set-colonization")
                                .then(Commands.argument(
                                                "amount", IntegerArgumentType.integer(
                                                        0, HearthrotPolicy.MAX_COLONIZATION))
                                        .executes(context -> setColonization(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(
                                                        context, "amount")))))
                        .then(Commands.literal("clear-disease")
                                .then(Commands.literal("confirm")
                                        .executes(context -> clear(
                                                context.getSource(), false))))
                        .then(Commands.literal("clear-all")
                                .then(Commands.literal("confirm")
                                        .executes(context -> clear(
                                                context.getSource(), true))))
                        .then(Commands.literal("cough")
                                .executes(context -> cough(
                                        context.getSource())))
                        .then(Commands.literal("wheeze")
                                .executes(context -> wheeze(
                                        context.getSource())))
                        .then(Commands.literal("breath-catch")
                                .executes(context -> breathCatch(
                                        context.getSource())))
                        .then(Commands.literal("fire-salvation")
                                .executes(context -> fireSalvation(
                                        context.getSource())))
                        .then(Commands.literal("reset-salvation")
                                .then(Commands.literal("confirm")
                                        .executes(context -> resetSalvation(
                                                context.getSource())))));
    }

    private static int status(CommandSourceStack source, boolean verbose)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SuitIntegrity state = SuitIntegrityHandler.getState(player);
        FrozenDawnCommandOutput.heading(source, "Suit");
        FrozenDawnCommandOutput.line(source, "Seal",
                state.punctures() == 0 ? "intact" : state.punctures() + " puncture(s)");
        FrozenDawnCommandOutput.line(source, "O2",
                state.o2Ticks() + " ticks (" + state.o2Ticks() / 20 + "s)");
        FrozenDawnCommandOutput.line(source, "Exposure",
                SuitIntegrityHandler.isVacuumExposure(player) ? "vacuum" : "pressurized");
        if (verbose) {
            FrozenDawnCommandOutput.detail(source, "Sealed suit",
                    SuitIntegrityHandler.isWearingSealedSuit(player));
            FrozenDawnCommandOutput.detail(source, "Grace ticks", state.graceTicks());
            FrozenDawnCommandOutput.detail(source, "Patch ticks", state.patchTicks());
            FrozenDawnCommandOutput.detail(source, "Temporary seals",
                    state.temporarySeals());
        } else {
            FrozenDawnCommandOutput.hint(source, "/fd suit status verbose");
        }
        return state.punctures();
    }

    private static int setPunctures(CommandSourceStack source, int count)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SuitIntegrityHandler.setPunctures(player, count);
        source.sendSuccess(
                () -> Component.literal("EVA punctures set to " + count), false);
        return count;
    }

    private static int hearthrotStatus(CommandSourceStack source, boolean verbose)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var state = HearthrotManager.state(player);
        int colonization = HearthrotManager.effectiveColonization(player);
        FrozenDawnCommandOutput.heading(source, "Hearthrot");
        FrozenDawnCommandOutput.line(source, "Disease",
                "stage " + state.stage() + " - " + Math.round(
                        HearthrotManager.progressRatio(player) * 100.0F) + "%");
        FrozenDawnCommandOutput.line(source, "Exterior colonization",
                colonization + "/" + HearthrotPolicy.MAX_COLONIZATION
                        + " - visual stage " + HearthrotPolicy.visualStage(colonization));
        if (verbose) {
            FrozenDawnCommandOutput.detail(source, "Contamination warning",
                    state.contaminationWarned());
            FrozenDawnCommandOutput.detail(source, "Stillness roll",
                    state.stillnessEpisodeRolled());
            FrozenDawnCommandOutput.detail(source, "Salvation fired",
                    com.frozendawn.data.ReturnedHearthSavedData
                            .get(player.getServer()).hearthrotSalvationFired());
        } else {
            FrozenDawnCommandOutput.hint(source,
                    "/fd suit hearthrot status verbose");
        }
        return state.stage();
    }

    private static int infect(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.infectForDebug(player);
        source.sendSuccess(() -> Component.literal(
                "Hearthrot infection applied"), false);
        return HearthrotManager.stage(player);
    }

    private static int setStage(CommandSourceStack source, int stage)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.setStageForDebug(player, stage);
        source.sendSuccess(() -> Component.literal(
                "Hearthrot stage set to " + stage), false);
        return stage;
    }

    private static int setProgress(CommandSourceStack source, int percent)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.setProgressForDebug(player, percent);
        source.sendSuccess(() -> Component.literal(
                "Hearthrot progress set to " + percent + "%"), false);
        return percent;
    }

    private static int setColonization(CommandSourceStack source, int amount)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.setColonizationForDebug(player, amount);
        source.sendSuccess(() -> Component.literal(
                "Equipped EVA colonization set to " + amount), false);
        return amount;
    }

    private static int clear(CommandSourceStack source, boolean all)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.clearForDebug(player, all);
        source.sendSuccess(() -> Component.literal(all
                ? "Hearthrot disease and inventory colonization cleared"
                : "Hearthrot disease cleared; exterior colonization preserved"), false);
        return 1;
    }

    private static int cough(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.coughForDebug(player);
        source.sendSuccess(() -> Component.literal(
                "Hearthrot cough presentation fired"), false);
        return 1;
    }

    private static int wheeze(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.wheezeForDebug(player);
        source.sendSuccess(() -> Component.literal(
                "Hearthrot wheeze presentation fired"), false);
        return 1;
    }

    private static int breathCatch(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.breathCatchForDebug(player);
        source.sendSuccess(() -> Component.literal(
                "Hearthrot breathing interruption fired"), false);
        return 1;
    }

    private static int resetSalvation(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean changed = HearthrotManager.resetSalvationForDebug(player);
        source.sendSuccess(() -> Component.literal(changed
                ? "World-global Hearthrot salvation flag reset"
                : "Hearthrot salvation flag was already clear"), false);
        return changed ? 1 : 0;
    }

    private static int fireSalvation(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean fired = HearthrotManager.fireSalvationForDebug(player);
        source.sendSuccess(() -> Component.literal(fired
                ? "Silent Hearthrot salvation payload fired globally"
                : "Salvation already fired; reset the flag first"), false);
        return fired ? 1 : 0;
    }
}
