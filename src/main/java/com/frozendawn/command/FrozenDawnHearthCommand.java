package com.frozendawn.command;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthArchitectManager;
import com.frozendawn.homo.HearthMasterArchitectManager;
import com.frozendawn.homo.HearthMaturationManager;
import com.frozendawn.homo.HearthMaturationPolicy;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.HearthPopulationManager;
import com.frozendawn.homo.HearthReconciliationManager;
import com.frozendawn.homo.HearthSelectionManager;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.homo.HearthTransmissionManager;
import com.frozendawn.homo.HearthViolationManager;
import com.frozendawn.homo.HearthWatcherManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

final class FrozenDawnHearthCommand {
    private static final int MAX_DEBUG_ADVANCE_DAYS = 365;
    private static final long MAX_DEBUG_ADVANCE_TICKS =
            MAX_DEBUG_ADVANCE_DAYS * HearthMaturationPolicy.MINECRAFT_DAY_TICKS;

    private FrozenDawnHearthCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> hearthCommands() {
        return Commands.literal("hearth")
                .executes(FrozenDawnHearthCommand::status)
                .then(Commands.literal("status").executes(FrozenDawnHearthCommand::status))
                .then(Commands.literal("list").executes(FrozenDawnHearthCommand::list))
                .then(Commands.literal("locate")
                        .then(Commands.literal("major")
                                .executes(context -> locate(
                                        context, HearthSelectionPolicy.HearthType.MAJOR)))
                        .then(Commands.literal("minor")
                                .executes(context -> locate(
                                        context, HearthSelectionPolicy.HearthType.MINOR))))
                .then(Commands.literal("force-select")
                        .executes(FrozenDawnHearthCommand::forceSelect))
                .then(Commands.literal("reconcile")
                        .executes(FrozenDawnHearthCommand::reconcile))
                .then(Commands.literal("watcher")
                        .executes(FrozenDawnHearthCommand::watcher)
                        .then(Commands.literal("respawn")
                                .then(Commands.literal("major")
                                        .executes(context -> respawnWatcher(
                                                context, HearthSelectionPolicy.HearthType.MAJOR)))
                                .then(Commands.literal("minor")
                                        .executes(context -> respawnWatcher(
                                                context, HearthSelectionPolicy.HearthType.MINOR)))))
                .then(Commands.literal("population")
                        .executes(FrozenDawnHearthCommand::population)
                        .then(Commands.literal("respawn")
                                .executes(FrozenDawnHearthCommand::respawnPopulation)))
                .then(Commands.literal("master")
                        .executes(FrozenDawnHearthCommand::masterArchitect)
                        .then(Commands.literal("respawn")
                                .executes(FrozenDawnHearthCommand::respawnMasterArchitect)))
                .then(Commands.literal("architect")
                        .executes(FrozenDawnHearthCommand::architect)
                        .then(Commands.literal("respawn")
                                .executes(FrozenDawnHearthCommand::respawnArchitect))
                        .then(Commands.literal("assessment")
                                .executes(FrozenDawnHearthCommand::assessment)
                                .then(Commands.literal("reset")
                                        .executes(FrozenDawnHearthCommand::resetAssessment))))
                .then(Commands.literal("transmission")
                        .executes(FrozenDawnHearthCommand::transmission)
                        .then(Commands.literal("reset")
                                .executes(FrozenDawnHearthCommand::resetTransmission))
                        .then(Commands.literal("replay")
                                .executes(FrozenDawnHearthCommand::replayTransmission)))
                .then(Commands.literal("survey")
                        .executes(FrozenDawnHearthCommand::survey)
                        .then(Commands.literal("reset")
                                .executes(FrozenDawnHearthCommand::resetSurvey)))
                .then(Commands.literal("relationship")
                        .executes(FrozenDawnHearthCommand::relationship)
                        .then(Commands.literal("set")
                                .then(relationshipState("neutral",
                                        ReturnedHearthSavedData.HiveRelationship.NEUTRAL))
                                .then(relationshipState("suspicious",
                                        ReturnedHearthSavedData.HiveRelationship.SUSPICIOUS))
                                .then(relationshipState("orsathae",
                                        ReturnedHearthSavedData.HiveRelationship.ORSATHAE))))
                .then(Commands.literal("violation")
                        .executes(FrozenDawnHearthCommand::violation)
                        .then(Commands.literal("reset")
                                .executes(FrozenDawnHearthCommand::resetViolation)))
                .then(Commands.literal("mood")
                        .executes(FrozenDawnHearthCommand::list)
                        .then(Commands.literal("set")
                                .then(moodScope("major", HearthSelectionPolicy.HearthType.MAJOR))
                                .then(moodScope("minor", HearthSelectionPolicy.HearthType.MINOR))
                                .then(moodScope("all", null))))
                .then(Commands.literal("advance")
                        .then(Commands.literal("ticks")
                                .then(Commands.argument("amount", LongArgumentType.longArg(
                                                1L, MAX_DEBUG_ADVANCE_TICKS))
                                        .executes(FrozenDawnHearthCommand::advanceTicks)))
                        .then(Commands.literal("days")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(
                                                1, MAX_DEBUG_ADVANCE_DAYS))
                                        .executes(FrozenDawnHearthCommand::advanceDays))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ReturnedHearthSavedData hearthState = ReturnedHearthSavedData.get(server);
        ApocalypseState apocalypse = ApocalypseState.get(server);
        long eligibilityTick = HearthSelectionPolicy.selectionEligibilityTick(apocalypse.getTotalDays());
        long ticksRemaining = eligibilityTick == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : Math.max(0L, eligibilityTick - apocalypse.getApocalypseTicks());

        String selectionGate;
        if (hearthState.selectionComplete()) {
            selectionGate = "complete at game time " + hearthState.selectionGameTime();
        } else if (ticksRemaining == Long.MAX_VALUE) {
            selectionGate = "disabled: invalid apocalypse duration";
        } else if (ticksRemaining == 0L) {
            selectionGate = "eligible; awaiting a completed overworld transponder";
        } else {
            selectionGate = "locked for " + formatTicks(ticksRemaining)
                    + " (late Phase 6 + 15 minutes)";
        }

        String anchor = hearthState.transponderAnchor()
                .map(FrozenDawnHearthCommand::formatPos)
                .orElse("not recorded");
        boolean maturationActive = HearthSelectionPolicy.isSelectionEligible(
                apocalypse.getApocalypseTicks(), apocalypse.getTotalDays());

        context.getSource().sendSuccess(() -> Component.literal("--- Homo Reliquus Hearths ---"), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Schema: " + hearthState.dataVersion()
                        + " | Records: " + hearthState.hearths().size()), false);
        context.getSource().sendSuccess(() -> Component.literal("  Transponder anchor: " + anchor), false);
        context.getSource().sendSuccess(() -> Component.literal("  Selection: " + selectionGate), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Maturation clock: " + (maturationActive ? "active" : "phase-gated")), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Hive memories: " + hearthState.playerMemories().size()
                        + " player(s) | Legacy default: "
                        + hearthState.legacyRelationship().name().toLowerCase(Locale.ROOT)), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Contact memory: " + HearthMemoryManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Reconciliation: " + HearthReconciliationManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Watchers: " + HearthWatcherManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  INTACT population: " + HearthPopulationManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Master Architect: " + HearthMasterArchitectManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Architect assessor: " + HearthArchitectManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Thaeven transmissions: " + HearthTransmissionManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Protected conduct: " + HearthViolationManager.statusLine()), false);
        long discovered = hearthState.hearths().stream()
                .filter(ReturnedHearthSavedData.HearthRecord::discovered)
                .count();
        context.getSource().sendSuccess(() -> Component.literal(
                "  ORSA survey: " + discovered + "/" + hearthState.hearths().size()
                        + " Hearth(s) catalogued"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("--- Returned Hearth Records ---"), false);
        if (state.hearths().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("  No sites selected"), false);
            return 1;
        }

        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            String id = hearth.id().toString().substring(0, 8);
            context.getSource().sendSuccess(() -> Component.literal(
                    "  " + hearth.type().name().toLowerCase(Locale.ROOT)
                            + " [" + id + "]"
                            + " center=" + formatHorizontalPos(hearth.center())
                            + " stage=" + hearth.stage().name().toLowerCase(Locale.ROOT)
                            + " mood=" + hearth.mood().name().toLowerCase(Locale.ROOT)
                            + " maturity=" + formatMaturity(hearth.maturityTicks())
                            + " resolved=" + yesNo(hearth.surfaceResolved())
                            + " structure=" + hearth.structureStageApplied().name().toLowerCase(Locale.ROOT)
                            + " cursor=" + hearth.structureCursor()
                            + " plan=" + hearth.structurePlanVersion()
                            + " complete=" + yesNo(hearth.structurePlaced())
                            + " watcher=" + hearth.watcherEntityId()
                                    .map(uuid -> uuid.toString().substring(0, 8))
                                    .orElse(hearth.watcherSpawned() ? "missing" : "none")
                            + " profile=" + (hearth.boundVariantProfile().isBlank()
                                    ? "none" : hearth.boundVariantProfile())
                            + " architect=" + hearth.architectAssessorEntityId()
                                    .map(uuid -> uuid.toString().substring(0, 8))
                                    .orElse(hearth.architectAssessorSpawned() ? "missing" : "none")
                            + " assessor=" + (hearth.architectAssessorProfile().isBlank()
                                    ? "none" : hearth.architectAssessorProfile())
                            + " population=" + formatPopulation(hearth)
                            + " master=" + formatMasterArchitect(hearth)
                            + " contacts=" + hearth.playerContacts().size()
                            + " transmission=" + yesNo(hearth.firstTransmissionFired())
                            + " discovered=" + yesNo(hearth.discovered())
                            + " signal=" + String.format(Locale.ROOT, "%.2f", hearth.signalStrength())
                            + " lootOpened=" + yesNo(hearth.lootTaken())
                            + " violation=" + hearth.violationState().name().toLowerCase(Locale.ROOT)), false);
        }
        return state.hearths().size();
    }

    private static int survey(CommandContext<CommandSourceStack> context) {
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("--- ORSA Hearth Survey ---"), false);
        if (state.hearths().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("  No Hearth signals selected"), false);
            return 1;
        }

        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  " + displayName(hearth.type())
                            + " stage=" + hearth.stage().name().toLowerCase(Locale.ROOT)
                            + " discovered=" + yesNo(hearth.discovered())
                            + " signal=" + String.format(Locale.ROOT, "%.2f", hearth.signalStrength())
                            + " violation=" + hearth.violationState().name().toLowerCase(Locale.ROOT)), false);
        }
        return state.hearths().size();
    }

    private static int resetSurvey(CommandContext<CommandSourceStack> context) {
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(context.getSource().getServer());
        int reset = state.resetSurveyDiscoveryForDebug();
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset ORSA survey state for " + reset + " Hearth record(s)"), true);
        return Math.max(1, reset);
    }

    private static int locate(CommandContext<CommandSourceStack> context,
                              HearthSelectionPolicy.HearthType type) {
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(context.getSource().getServer());
        return state.hearth(type).map(hearth -> {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  " + displayName(type) + " Hearth planned center: "
                            + formatHorizontalPos(hearth.center())
                            + " | Surface unresolved: " + yesNo(!hearth.surfaceResolved())), false);
            return 1;
        }).orElseGet(() -> {
            context.getSource().sendFailure(Component.literal(
                    displayName(type) + " Hearth has not been selected"));
            return 0;
        });
    }

    private static int forceSelect(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(server);
        boolean hadAnchor = state.transponderAnchor().isPresent();
        BlockPos fallbackAnchor = BlockPos.containing(context.getSource().getPosition());
        HearthSelectionManager.SelectionResult result = HearthSelectionManager.forceSelect(
                server.overworld(), fallbackAnchor);

        if (!result.selected()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Returned Hearth selection already exists; no records changed"), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal(
                "Selected " + result.hearths().size() + " Returned Hearth site(s) around "
                        + (hadAnchor ? "the recorded transponder " : "the debug fallback ")
                        + formatPos(result.anchor())), true);
        return result.hearths().size();
    }

    private static int reconcile(CommandContext<CommandSourceStack> context) {
        int queued = HearthReconciliationManager.queueAll(
                context.getSource().getServer().overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Queued " + queued + " eligible Hearth reconciliation(s). "
                        + HearthReconciliationManager.statusLine()), true);
        return 1;
    }

    private static int watcher(CommandContext<CommandSourceStack> context) {
        int spawned = HearthWatcherManager.reconcileNow(
                context.getSource().getServer().overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reconciled Hearth watchers; spawned=" + spawned + " | "
                        + HearthWatcherManager.statusLine()), true);
        list(context);
        return 1;
    }

    private static int respawnWatcher(CommandContext<CommandSourceStack> context,
                                      HearthSelectionPolicy.HearthType type) {
        HearthWatcherManager.DebugRespawnResult result = HearthWatcherManager.respawnForDebug(
                context.getSource().getServer().overworld(), type);
        if (!result.hearthLoaded()) {
            context.getSource().sendFailure(Component.literal(
                    displayName(type) + " Hearth does not exist or its center chunk is not loaded"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Respawned " + displayName(type) + " Hearth watcher"
                        + " | removed=" + result.removed()
                        + " spawned=" + result.spawned()), true);
        list(context);
        return result.spawned() > 0 ? 1 : 0;
    }

    private static int population(CommandContext<CommandSourceStack> context) {
        int spawned = HearthPopulationManager.reconcileNow(
                context.getSource().getServer().overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reconciled INTACT Major Hearth population; spawned=" + spawned + " | "
                        + HearthPopulationManager.statusLine()), true);
        list(context);
        return 1;
    }

    private static int respawnPopulation(CommandContext<CommandSourceStack> context) {
        HearthPopulationManager.DebugRespawnResult result =
                HearthPopulationManager.respawnForDebug(
                        context.getSource().getServer().overworld());
        if (!result.hearthLoaded()) {
            context.getSource().sendFailure(Component.literal(
                    "Major Hearth does not exist or its center chunk is not loaded"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Respawned INTACT Major Hearth population"
                        + " | removed=" + result.removed()
                        + " spawned=" + result.spawned()), true);
        list(context);
        return result.spawned() > 0 ? 1 : 0;
    }

    private static int masterArchitect(CommandContext<CommandSourceStack> context) {
        int spawned = HearthMasterArchitectManager.reconcileNow(
                context.getSource().getServer().overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reconciled INTACT Major Hearth Master Architect; spawned=" + spawned + " | "
                        + HearthMasterArchitectManager.statusLine()), true);
        list(context);
        return 1;
    }

    private static int respawnMasterArchitect(CommandContext<CommandSourceStack> context) {
        HearthMasterArchitectManager.DebugRespawnResult result =
                HearthMasterArchitectManager.respawnForDebug(
                        context.getSource().getServer().overworld());
        if (!result.hearthLoaded()) {
            context.getSource().sendFailure(Component.literal(
                    "Major Hearth does not exist or its center chunk is not loaded"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Respawned INTACT Major Hearth Master Architect"
                        + " | removed=" + result.removed()
                        + " spawned=" + result.spawned()), true);
        list(context);
        return result.spawned() > 0 ? 1 : 0;
    }

    private static int architect(CommandContext<CommandSourceStack> context) {
        int spawned = HearthArchitectManager.reconcileNow(
                context.getSource().getServer().overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reconciled Major Hearth Architect assessor; spawned=" + spawned + " | "
                        + HearthArchitectManager.statusLine()), true);
        list(context);
        return 1;
    }

    private static int respawnArchitect(CommandContext<CommandSourceStack> context) {
        HearthArchitectManager.DebugRespawnResult result = HearthArchitectManager.respawnForDebug(
                context.getSource().getServer().overworld());
        if (!result.hearthLoaded()) {
            context.getSource().sendFailure(Component.literal(
                    "Major Hearth does not exist or its center chunk is not loaded"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Respawned Major Hearth Architect assessor"
                        + " | removed=" + result.removed()
                        + " spawned=" + result.spawned()), true);
        list(context);
        return result.spawned() > 0 ? 1 : 0;
    }

    private static int assessment(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord major = state
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (major == null) {
            context.getSource().sendFailure(Component.literal("Major Hearth does not exist"));
            return 0;
        }
        String details = major.playerContact(player.getUUID())
                .map(memory -> "complete=" + yesNo(memory.architectAssessmentComplete())
                        + " time=" + memory.architectAssessmentGameTime()
                        + " orsa=" + yesNo(memory.orsaDetectedAtAssessment()))
                .orElse("complete=no time=-1 orsa=no");
        context.getSource().sendSuccess(() -> Component.literal(
                "Architect assessment for " + player.getGameProfile().getName()
                        + ": " + details), false);
        return 1;
    }

    private static int resetAssessment(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord major = state
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (major == null) {
            context.getSource().sendFailure(Component.literal("Major Hearth does not exist"));
            return 0;
        }
        boolean changed = state.clearArchitectAssessmentForDebug(
                player.getUUID(), major.id());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset Architect assessment for " + player.getGameProfile().getName()
                        + (changed ? "" : " (unchanged)")), true);
        return assessment(context);
    }

    private static int transmission(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord major = state
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (major == null) {
            context.getSource().sendFailure(Component.literal("Major Hearth does not exist"));
            return 0;
        }
        String details = major.playerContact(player.getUUID())
                .map(memory -> "complete=" + yesNo(memory.firstTransmissionComplete())
                        + " time=" + memory.firstTransmissionGameTime()
                        + " active=" + yesNo(HearthTransmissionManager.isActive(player.getUUID()))
                        + " awaitingExit=" + yesNo(HearthTransmissionManager
                                .isAwaitingContactExit(player.getUUID())))
                .orElse("complete=no time=-1 active=no awaitingExit=no");
        context.getSource().sendSuccess(() -> Component.literal(
                "Thaeven transmission for " + player.getGameProfile().getName()
                        + ": " + details), false);
        return 1;
    }

    private static int resetTransmission(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord major = state
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (major == null) {
            context.getSource().sendFailure(Component.literal("Major Hearth does not exist"));
            return 0;
        }
        boolean changed = HearthTransmissionManager.resetForDebug(player, major.id());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset first Thaeven transmission for " + player.getGameProfile().getName()
                        + (changed ? "" : " (unchanged)")), true);
        return transmission(context);
    }

    private static int replayTransmission(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord major = state
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (major == null) {
            context.getSource().sendFailure(Component.literal("Major Hearth does not exist"));
            return 0;
        }
        boolean started = HearthTransmissionManager.replayForDebug(player, major.id());
        if (!started) {
            context.getSource().sendFailure(Component.literal(
                    "Could not replay transmission; remain near and visible to the loaded assessor"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Replaying first Thaeven transmission for "
                        + player.getGameProfile().getName()), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> relationshipState(
            String name, ReturnedHearthSavedData.HiveRelationship relationship) {
        return Commands.literal(name).executes(context -> setRelationship(context, relationship));
    }

    private static int violation(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(
                "Protected conduct for " + player.getGameProfile().getName()
                        + ": relationship="
                        + state.relationship(player.getUUID()).name().toLowerCase(Locale.ROOT)), false);
        int records = 0;
        for (ReturnedHearthSavedData.HearthRecord hearth : state.hearths()) {
            ReturnedHearthSavedData.HearthContactMemory memory = hearth
                    .playerContact(player.getUUID()).orElse(null);
            if (memory == null) {
                continue;
            }
            records++;
            String reasons = memory.violationReasons().isEmpty()
                    ? "none"
                    : memory.violationReasons().stream()
                            .map(reason -> reason.name().toLowerCase(Locale.ROOT))
                            .sorted()
                            .reduce((left, right) -> left + "," + right)
                            .orElse("none");
            context.getSource().sendSuccess(() -> Component.literal(
                    "  " + hearth.type().name().toLowerCase(Locale.ROOT)
                            + " [" + hearth.id().toString().substring(0, 8) + "]"
                            + " reasons=" + reasons
                            + " first=" + memory.firstViolationGameTime()
                            + " lootOpened=" + yesNo(hearth.lootTaken())), false);
        }
        if (records == 0) {
            context.getSource().sendSuccess(() -> Component.literal("  No Hearth contact memory"), false);
        }
        return 1;
    }

    private static int resetViolation(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        boolean changed = state.clearPlayerViolationsForDebug(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset protected conduct for " + player.getGameProfile().getName()
                        + (changed ? "" : " (unchanged)")), true);
        return violation(context);
    }

    private static int relationship(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HiveRelationship relationship = state.relationship(player.getUUID());
        String details = state.playerMemory(player.getUUID())
                .map(memory -> " | contacts=" + memory.totalVisits()
                        + " first=" + memory.firstContactGameTime()
                        + " last=" + memory.lastContactGameTime()
                        + " source=" + memory.relationshipSourceHearthId()
                                .map(id -> id.toString().substring(0, 8)).orElse("none"))
                .orElse(" | no recorded contact");
        context.getSource().sendSuccess(() -> Component.literal(
                "Hive relationship for " + player.getGameProfile().getName() + ": "
                        + relationship.name().toLowerCase(Locale.ROOT) + details), false);
        return 1;
    }

    private static int setRelationship(
            CommandContext<CommandSourceStack> context,
            ReturnedHearthSavedData.HiveRelationship relationship)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        boolean changed = state.setRelationshipForDebug(
                player.getUUID(), relationship, context.getSource().getLevel().getGameTime());
        context.getSource().sendSuccess(() -> Component.literal(
                "Set hive relationship for " + player.getGameProfile().getName() + " to "
                        + relationship.name().toLowerCase(Locale.ROOT)
                        + (changed ? "" : " (unchanged)")), true);
        return relationship(context);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moodScope(
            String name, HearthSelectionPolicy.HearthType type) {
        LiteralArgumentBuilder<CommandSourceStack> scope = Commands.literal(name);
        for (ReturnedHearthSavedData.HearthDisposition mood
                : ReturnedHearthSavedData.HearthDisposition.values()) {
            scope.then(Commands.literal(mood.name().toLowerCase(Locale.ROOT))
                    .executes(context -> setMood(context, type, mood)));
        }
        return scope;
    }

    private static int setMood(CommandContext<CommandSourceStack> context,
                               HearthSelectionPolicy.HearthType type,
                               ReturnedHearthSavedData.HearthDisposition mood) {
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        int changed;
        String target;
        if (type == null) {
            changed = state.setAllHearthMoodsForDebug(mood);
            target = "all Hearths";
        } else {
            boolean exists = state.hearth(type).isPresent();
            if (!exists) {
                context.getSource().sendFailure(Component.literal(
                        displayName(type) + " Hearth does not exist"));
                return 0;
            }
            changed = state.setHearthMoodForDebug(type, mood) ? 1 : 0;
            target = displayName(type) + " Hearth";
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Set " + target + " mood to " + mood.name().toLowerCase(Locale.ROOT)
                        + " | changed=" + changed), true);
        list(context);
        return 1;
    }

    private static int advanceTicks(CommandContext<CommandSourceStack> context) {
        return advance(context, LongArgumentType.getLong(context, "amount"));
    }

    private static int advanceDays(CommandContext<CommandSourceStack> context) {
        long days = IntegerArgumentType.getInteger(context, "amount");
        return advance(context, days * HearthMaturationPolicy.MINECRAFT_DAY_TICKS);
    }

    private static int advance(CommandContext<CommandSourceStack> context, long ticks) {
        MinecraftServer server = context.getSource().getServer();
        ReturnedHearthSavedData state = ReturnedHearthSavedData.get(server);
        if (!state.selectionComplete() || state.hearths().isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "No Returned Hearth sites exist; run force-select first"));
            return 0;
        }

        ReturnedHearthSavedData.MaturationResult result = HearthMaturationManager.advanceForDebug(
                server.overworld(), ticks);
        context.getSource().sendSuccess(() -> Component.literal(
                "Advanced each Returned Hearth by " + formatMaturity(ticks)
                        + " | Records: " + result.recordsAdvanced()
                        + " | Stage transitions: " + result.transitions().size()), true);
        list(context);
        return 1;
    }

    private static String formatTicks(long ticks) {
        long seconds = (ticks + 19L) / 20L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, remainder);
        }
        return String.format(Locale.ROOT, "%dm %02ds", minutes, remainder);
    }

    private static String formatMaturity(long ticks) {
        double days = (double) ticks / HearthMaturationPolicy.MINECRAFT_DAY_TICKS;
        return ticks + "t (" + String.format(Locale.ROOT, "%.2f", days) + "d)";
    }

    private static String formatPopulation(ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.populationResidents().isEmpty()) {
            return "none";
        }
        StringBuilder result = new StringBuilder();
        for (ReturnedHearthSavedData.HearthResidentBinding binding
                : hearth.populationResidents()) {
            if (!result.isEmpty()) {
                result.append(',');
            }
            result.append(binding.role().serializedName()).append(':');
            binding.entityId().ifPresentOrElse(
                    id -> result.append(id.toString(), 0, 8),
                    () -> result.append("waiting@").append(binding.respawnAfterGameTime()));
        }
        return result.toString();
    }

    private static String formatMasterArchitect(
            ReturnedHearthSavedData.HearthRecord hearth) {
        if (hearth.masterArchitectDefeated()) {
            return "defeated@" + hearth.masterArchitectDefeatedGameTime();
        }
        return hearth.masterArchitectEntityId()
                .map(id -> id.toString().substring(0, 8))
                .orElse("none");
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static String formatHorizontalPos(BlockPos pos) {
        return "(" + pos.getX() + ", ?, " + pos.getZ() + ")";
    }

    private static String displayName(HearthSelectionPolicy.HearthType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
