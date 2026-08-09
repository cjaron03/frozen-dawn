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
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("punctures")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 2))
                                .executes(context -> setPunctures(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("hearthrot")
                        .then(Commands.literal("status")
                                .executes(context -> hearthrotStatus(
                                        context.getSource())))
                        .then(Commands.literal("infect")
                                .executes(context -> infect(
                                        context.getSource())))
                        .then(Commands.literal("setstage")
                                .then(Commands.argument(
                                                "stage", IntegerArgumentType.integer(0, 6))
                                        .executes(context -> setStage(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(
                                                        context, "stage")))))
                        .then(Commands.literal("setprogress")
                                .then(Commands.argument(
                                                "percent", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> setProgress(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(
                                                        context, "percent")))))
                        .then(Commands.literal("setcolonization")
                                .then(Commands.argument(
                                                "amount", IntegerArgumentType.integer(
                                                        0, HearthrotPolicy.MAX_COLONIZATION))
                                        .executes(context -> setColonization(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(
                                                        context, "amount")))))
                        .then(Commands.literal("debug-clear-disease")
                                .executes(context -> clear(
                                        context.getSource(), false)))
                        .then(Commands.literal("debug-clear-all")
                                .executes(context -> clear(
                                        context.getSource(), true)))
                        .then(Commands.literal("debug-cough")
                                .executes(context -> cough(
                                        context.getSource())))
                        .then(Commands.literal("debug-wheeze")
                                .executes(context -> wheeze(
                                        context.getSource())))
                        .then(Commands.literal("debug-breath-catch")
                                .executes(context -> breathCatch(
                                        context.getSource())))
                        .then(Commands.literal("debug-fire-salvation")
                                .executes(context -> fireSalvation(
                                        context.getSource())))
                        .then(Commands.literal("debug-reset-salvation")
                                .executes(context -> resetSalvation(
                                        context.getSource()))));
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SuitIntegrity state = SuitIntegrityHandler.getState(player);
        source.sendSuccess(
                () -> Component.literal(
                        "Suit integrity: punctures=" + state.punctures()
                                + " o2=" + state.o2Ticks()
                                + " grace=" + state.graceTicks()
                                + " patch=" + state.patchTicks()
                                + " temporarySeals=" + state.temporarySeals()
                                + " sealed=" + SuitIntegrityHandler.isWearingSealedSuit(player)
                                + " vacuum=" + SuitIntegrityHandler.isVacuumExposure(player)),
                false);
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

    private static int hearthrotStatus(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var state = HearthrotManager.state(player);
        int colonization = HearthrotManager.effectiveColonization(player);
        source.sendSuccess(() -> Component.literal(
                "Hearthrot: stage=" + state.stage()
                        + " progress=" + Math.round(
                                HearthrotManager.progressRatio(player) * 100.0F) + "%"
                        + " colonization=" + colonization + "/"
                        + HearthrotPolicy.MAX_COLONIZATION
                        + " visual=" + HearthrotPolicy.visualStage(colonization)
                        + " warning=" + state.contaminationWarned()
                        + " salvationRoll=" + state.stillnessEpisodeRolled()
                        + " salvationFired="
                        + com.frozendawn.data.ReturnedHearthSavedData
                                .get(player.getServer()).hearthrotSalvationFired()), false);
        return state.stage();
    }

    private static int infect(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.infectForDebug(player);
        source.sendSuccess(() -> Component.literal(
                "DEBUG: Hearthrot infection applied"), false);
        return HearthrotManager.stage(player);
    }

    private static int setStage(CommandSourceStack source, int stage)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.setStageForDebug(player, stage);
        source.sendSuccess(() -> Component.literal(
                "DEBUG: Hearthrot stage set to " + stage), false);
        return stage;
    }

    private static int setProgress(CommandSourceStack source, int percent)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.setProgressForDebug(player, percent);
        source.sendSuccess(() -> Component.literal(
                "DEBUG: Hearthrot progress set to " + percent + "%"), false);
        return percent;
    }

    private static int setColonization(CommandSourceStack source, int amount)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.setColonizationForDebug(player, amount);
        source.sendSuccess(() -> Component.literal(
                "DEBUG: equipped EVA colonization set to " + amount), false);
        return amount;
    }

    private static int clear(CommandSourceStack source, boolean all)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.clearForDebug(player, all);
        source.sendSuccess(() -> Component.literal(all
                ? "DEBUG: Hearthrot disease and inventory colonization cleared"
                : "DEBUG: Hearthrot disease cleared; exterior colonization preserved"), false);
        return 1;
    }

    private static int cough(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.coughForDebug(player);
        source.sendSuccess(() -> Component.literal(
                "DEBUG: Hearthrot cough presentation fired"), false);
        return 1;
    }

    private static int wheeze(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.wheezeForDebug(player);
        source.sendSuccess(() -> Component.literal(
                "DEBUG: Hearthrot wheeze presentation fired"), false);
        return 1;
    }

    private static int breathCatch(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HearthrotManager.breathCatchForDebug(player);
        source.sendSuccess(() -> Component.literal(
                "DEBUG: Hearthrot breathing interruption fired"), false);
        return 1;
    }

    private static int resetSalvation(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean changed = HearthrotManager.resetSalvationForDebug(player);
        source.sendSuccess(() -> Component.literal(changed
                ? "DEBUG: world-global Hearthrot salvation flag reset"
                : "DEBUG: Hearthrot salvation flag was already clear"), false);
        return changed ? 1 : 0;
    }

    private static int fireSalvation(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean fired = HearthrotManager.fireSalvationForDebug(player);
        source.sendSuccess(() -> Component.literal(fired
                ? "DEBUG: silent Hearthrot salvation payload fired globally"
                : "DEBUG: salvation already fired; reset the debug flag first"), false);
        return fired ? 1 : 0;
    }
}
