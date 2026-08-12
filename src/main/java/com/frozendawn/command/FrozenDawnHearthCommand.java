package com.frozendawn.command;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.bloom.BloomSporeManager;
import com.frozendawn.homo.HearthArchitectManager;
import com.frozendawn.homo.CognitiveLoadManager;
import com.frozendawn.homo.HearthBoundaryManager;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMasterArchitectManager;
import com.frozendawn.homo.HearthMasterArchitectWeatherManager;
import com.frozendawn.homo.HearthHeartManager;
import com.frozendawn.homo.HeartCollapseStage;
import com.frozendawn.homo.HeartFormationStage;
import com.frozendawn.homo.HeartLattice;
import com.frozendawn.homo.HeartMemoryNodeManager;
import com.frozendawn.homo.HeartScavengerWaveManager;
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
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.homo.PostMaeveMoonManager;
import com.frozendawn.homo.PostMaeveMoonPolicy;
import com.frozendawn.world.UndoneSpawner;
import com.frozendawn.world.UndoneArchitectSpawner;
import com.frozendawn.world.BloomboundUndoneSpawner;
import com.frozendawn.world.ArchivistManager;
import com.frozendawn.world.RimeboundManager;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.RimeboundState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
                .then(Commands.literal("postmaeve")
                        .executes(FrozenDawnHearthCommand::postMaeveStatus)
                        .then(Commands.literal("status")
                                .executes(FrozenDawnHearthCommand::postMaeveStatus))
                        .then(Commands.literal("debug-set-erased")
                                .executes(context -> postMaeveSet(context, true)))
                        .then(Commands.literal("debug-reset-erased")
                                .executes(context -> postMaeveSet(context, false)))
                        .then(Commands.literal("debug-spawn-undone")
                                .executes(FrozenDawnHearthCommand::postMaeveSpawnUndone))
                        .then(Commands.literal("debug-spawn-undone-architect")
                                .executes(FrozenDawnHearthCommand::postMaeveSpawnUndoneArchitect))
                        .then(Commands.literal("debug-reset-undone-contact")
                                .executes(FrozenDawnHearthCommand::postMaeveResetContact))
                        .then(Commands.literal("archivist")
                                .executes(FrozenDawnHearthCommand::archivistStatus)
                                .then(Commands.literal("status")
                                        .executes(FrozenDawnHearthCommand::archivistStatus))
                                .then(Commands.literal("debug-spawn")
                                        .executes(FrozenDawnHearthCommand::archivistDebugSpawn))
                                .then(Commands.literal("debug-create-site")
                                        .executes(FrozenDawnHearthCommand::archivistCreateSite))
                                .then(Commands.literal("debug-fill-site")
                                        .executes(FrozenDawnHearthCommand::archivistFillSite))
                                .then(Commands.literal("force-sort-nearest")
                                        .executes(FrozenDawnHearthCommand::archivistForceSort))
                                .then(Commands.literal("purge-loaded")
                                        .executes(FrozenDawnHearthCommand::archivistPurgeLoaded)))
                        .then(Commands.literal("rimebound")
                                .executes(FrozenDawnHearthCommand::rimeboundStatus)
                                .then(Commands.literal("status")
                                        .executes(FrozenDawnHearthCommand::rimeboundStatus))
                                .then(Commands.literal("debug-spawn")
                                        .then(Commands.literal("dormant")
                                                .executes(context -> rimeboundSpawn(context, true)))
                                        .then(Commands.literal("stalking")
                                                .executes(context -> rimeboundSpawn(context, false))))
                                .then(Commands.literal("debug-set-age")
                                        .then(Commands.argument("days",
                                                        IntegerArgumentType.integer(0, 3_650))
                                                .executes(FrozenDawnHearthCommand::rimeboundSetAge)))
                                .then(Commands.literal("debug-setstate")
                                        .then(Commands.argument("state", StringArgumentType.word())
                                                .executes(FrozenDawnHearthCommand::rimeboundSetState)))
                                .then(Commands.literal("debug-force-burrow")
                                        .executes(FrozenDawnHearthCommand::rimeboundForceBurrow))
                                .then(Commands.literal("debug-force-lance")
                                        .executes(FrozenDawnHearthCommand::rimeboundForceLance))
                                .then(Commands.literal("debug-force-freeze")
                                        .executes(FrozenDawnHearthCommand::rimeboundForceFreeze))
                                .then(Commands.literal("purge-loaded")
                                        .executes(FrozenDawnHearthCommand::rimeboundPurgeLoaded)))
                        .then(Commands.literal("moon")
                                .executes(FrozenDawnHearthCommand::postMaeveMoonStatus)
                                .then(Commands.literal("status")
                                        .executes(FrozenDawnHearthCommand::postMaeveMoonStatus))
                                .then(Commands.literal("debug-start-rise")
                                        .executes(FrozenDawnHearthCommand::postMaeveMoonStartRise))
                                .then(Commands.literal("debug-set-age")
                                        .then(Commands.argument("days",
                                                        IntegerArgumentType.integer(0, 3_650))
                                                .executes(FrozenDawnHearthCommand::postMaeveMoonSetAge)))
                                .then(Commands.literal("debug-reset")
                                        .executes(FrozenDawnHearthCommand::postMaeveMoonReset))))
                .then(Commands.literal("bloom")
                        .executes(FrozenDawnHearthCommand::bloomStatus)
                        .then(Commands.literal("status")
                                .executes(FrozenDawnHearthCommand::bloomStatus))
                        .then(Commands.literal("seed")
                                .executes(FrozenDawnHearthCommand::bloomSeed))
                        .then(Commands.literal("start")
                                .executes(FrozenDawnHearthCommand::bloomStart))
                        .then(Commands.literal("advance")
                                .then(Commands.argument("days",
                                                IntegerArgumentType.integer(0, 3_650))
                                        .executes(FrozenDawnHearthCommand::bloomAdvance)))
                        .then(Commands.literal("setradius")
                                .then(Commands.argument("radius",
                                                IntegerArgumentType.integer(0, 1_000))
                                        .executes(FrozenDawnHearthCommand::bloomSetRadius)))
                        .then(Commands.literal("profile")
                                .executes(FrozenDawnHearthCommand::bloomProfile))
                        .then(Commands.literal("debug-spawn-bloombound")
                                .executes(FrozenDawnHearthCommand::bloomSpawnBloombound))
                        .then(Commands.literal("debug-spawn-spore")
                                .executes(FrozenDawnHearthCommand::bloomSpawnSpore))
                        .then(Commands.literal("spore")
                                .executes(FrozenDawnHearthCommand::bloomSporeStatus)
                                .then(Commands.literal("status")
                                        .executes(FrozenDawnHearthCommand::bloomSporeStatus))
                                .then(Commands.literal("root-nearest")
                                        .executes(FrozenDawnHearthCommand::bloomSporeRootNearest))
                                .then(Commands.literal("advance")
                                        .then(Commands.argument("days",
                                                        IntegerArgumentType.integer(0, 3_650))
                                                .executes(
                                                        FrozenDawnHearthCommand::bloomSporeAdvance)))
                                .then(Commands.literal("purge-loaded")
                                        .executes(
                                                FrozenDawnHearthCommand::bloomSporePurgeLoaded)))
                        .then(Commands.literal("purge-loaded")
                                .executes(FrozenDawnHearthCommand::bloomPurgeLoaded)))
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
                                .executes(FrozenDawnHearthCommand::respawnMasterArchitect))
                        .then(Commands.literal("phase")
                                .executes(FrozenDawnHearthCommand::masterArchitectPhase))
                        .then(Commands.literal("weather")
                                .executes(FrozenDawnHearthCommand::masterArchitectWeather)))
                .then(Commands.literal("heart")
                        .executes(FrozenDawnHearthCommand::heartStatus)
                        .then(Commands.literal("status")
                                .executes(FrozenDawnHearthCommand::heartStatus))
                        .then(Commands.literal("start")
                                .executes(FrozenDawnHearthCommand::heartStart))
                        .then(Commands.literal("setstage")
                                .then(heartStage("dead_air", HeartFormationStage.DEAD_AIR))
                                .then(heartStage("shake", HeartFormationStage.SHAKE))
                                .then(heartStage("gather", HeartFormationStage.GATHER))
                                .then(heartStage("hold", HeartFormationStage.HOLD))
                                .then(heartStage("live", HeartFormationStage.LIVE)))
                        .then(Commands.literal("reset")
                                .executes(FrozenDawnHearthCommand::heartReset))
                        .then(Commands.literal("load")
                                .executes(FrozenDawnHearthCommand::heartLoadStatus)
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value",
                                                        FloatArgumentType.floatArg(0.0F, 100.0F))
                                                .executes(FrozenDawnHearthCommand::heartLoadSet)))
                                .then(Commands.literal("clear")
                                        .executes(FrozenDawnHearthCommand::heartLoadClear)))
                        .then(Commands.literal("nodes")
                                .executes(FrozenDawnHearthCommand::heartNodesStatus)
                                .then(Commands.literal("destroy")
                                        .then(Commands.argument("node",
                                                        IntegerArgumentType.integer(
                                                                1, HeartLattice.NODE_COUNT))
                                                .executes(
                                                        FrozenDawnHearthCommand::heartNodeDestroy)))
                                .then(Commands.literal("reset")
                                        .executes(
                                                FrozenDawnHearthCommand::heartNodesReset)))
                        .then(Commands.literal("collapse")
                                .executes(FrozenDawnHearthCommand::heartStatus)
                                .then(Commands.literal("start")
                                        .executes(FrozenDawnHearthCommand::heartCollapseStart))
                                .then(Commands.literal("setstage")
                                        .then(heartCollapseStage("rupture",
                                                HeartCollapseStage.RUPTURE))
                                        .then(heartCollapseStage("fall",
                                                HeartCollapseStage.FALL))
                                        .then(heartCollapseStage("settle",
                                                HeartCollapseStage.SETTLE))
                                        .then(heartCollapseStage("dormant",
                                                HeartCollapseStage.DORMANT)))
                                .then(Commands.literal("reset")
                                        .executes(
                                                FrozenDawnHearthCommand::heartCollapseReset)))
                        .then(Commands.literal("maeve")
                                .executes(FrozenDawnHearthCommand::heartMaeveStatus)
                                .then(Commands.literal("start")
                                        .executes(FrozenDawnHearthCommand::heartMaeveStart))
                                .then(Commands.literal("complete")
                                        .executes(FrozenDawnHearthCommand::heartMaeveComplete))
                                .then(Commands.literal("reset")
                                        .executes(FrozenDawnHearthCommand::heartMaeveReset))))
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
                "  Master weather: "
                        + HearthMasterArchitectWeatherManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Thae Iven Heart: " + HearthHeartManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Heart scavengers: " + HeartScavengerWaveManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Architect assessor: " + HearthArchitectManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Thaeven transmissions: " + HearthTransmissionManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Protected conduct: " + HearthViolationManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Boundary response: " + HearthBoundaryManager.statusLine()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Master encounter roster: "
                        + HearthCombatRosterManager.statusLine()), false);
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

    private static int heartStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "--- Thae Iven Heart ---"), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  " + HearthHeartManager.describe(context.getSource().getLevel())), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Encounter: " + HeartScavengerWaveManager.describe(
                        context.getSource().getLevel())), false);
        return 1;
    }

    private static int heartStart(CommandContext<CommandSourceStack> context) {
        HearthHeartManager.startForDebug(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "Started Heart formation at the Major Hearth"), true);
        return heartStatus(context);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> heartStage(
            String name, HeartFormationStage stage) {
        return Commands.literal(name).executes(context -> {
            boolean changed = HearthHeartManager.setStageForDebug(
                    context.getSource().getLevel(), stage);
            context.getSource().sendSuccess(() -> Component.literal(
                    "Set Heart formation to " + stage.name().toLowerCase(Locale.ROOT)
                            + (changed ? "" : " (unchanged)")), true);
            return heartStatus(context);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> heartCollapseStage(
            String name, HeartCollapseStage stage) {
        return Commands.literal(name).executes(context -> {
            boolean changed = HearthHeartManager.setCollapseStageForDebug(
                    context.getSource().getLevel(), stage);
            context.getSource().sendSuccess(() -> Component.literal(
                    "Set Heart collapse to " + stage.name().toLowerCase(Locale.ROOT)
                            + (changed ? "" : " (unchanged)")), true);
            return heartStatus(context);
        });
    }

    private static int heartCollapseStart(
            CommandContext<CommandSourceStack> context) {
        boolean changed = HearthHeartManager.startCollapseForDebug(
                context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "Started Heart collapse" + (changed ? "" : " (unchanged)")), true);
        return heartStatus(context);
    }

    private static int heartCollapseReset(
            CommandContext<CommandSourceStack> context) {
        int removed = HearthHeartManager.resetCollapseForDebug(
                context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset Heart collapse; removed " + removed
                        + " transient fragment(s)"), true);
        return heartStatus(context);
    }

    private static int heartMaeveStatus(CommandContext<CommandSourceStack> context) {
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(context.getSource().getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            context.getSource().sendFailure(Component.literal(
                    "Major Hearth does not exist"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Maeve: exposed=" + yesNo(hearth.heartMaeveExposed())
                        + " erasureStart=" + hearth.heartMaeveErasureStartGameTime()
                        + " erased=" + yesNo(hearth.heartMaeveErasureComplete())
                        + " advancement="
                        + yesNo(hearth.heartFinalAdvancementGranted())), false);
        return 1;
    }

    private static int heartMaeveStart(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        boolean changed = hearth != null && data.startHeartMaeveErasure(
                hearth.id(), context.getSource().getLevel().getGameTime(),
                player.getUUID());
        if (changed) {
            PostMaeveWorldState.markErased(context.getSource().getLevel());
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Started Maeve erasure" + (changed ? "" : " (unchanged)")), true);
        HearthHeartManager.tick(context.getSource().getLevel());
        return heartMaeveStatus(context);
    }

    private static int heartMaeveComplete(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        boolean completed = false;
        if (hearth != null) {
            data.resetHeartMaeveErasureForDebug(hearth.id());
            long completionStart = Math.max(0L,
                    context.getSource().getLevel().getGameTime()
                            - com.frozendawn.homo.HeartMaeveErasurePolicy.UNMAKING_TICKS);
            completed = data.startHeartMaeveErasure(
                    hearth.id(), completionStart, player.getUUID());
            if (completed) {
                PostMaeveWorldState.markErased(context.getSource().getLevel());
            }
        }
        boolean changed = completed;
        context.getSource().sendSuccess(() -> Component.literal(
                "Completed Maeve erasure" + (changed ? "" : " (unchanged)")), true);
        HearthHeartManager.tick(context.getSource().getLevel());
        return heartMaeveStatus(context);
    }

    private static int postMaeveStatus(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(server);
        context.getSource().sendSuccess(() -> Component.literal(
                "Post-Maeve: effective=" + yesNo(PostMaeveWorldState.isErased(server))
                        + " saved=" + yesNo(data.maeveErased())
                        + " erasedAt=" + data.maeveErasedGameTime()
                        + " undoneReleased="
                        + yesNo(PostMaeveWorldState.isUndoneSpawningReleased(server))), false);
        return 1;
    }

    private static int postMaeveSet(
            CommandContext<CommandSourceStack> context, boolean erased) {
        PostMaeveWorldState.setForDebug(context.getSource().getServer(), erased);
        context.getSource().sendSuccess(() -> Component.literal(
                "DEBUG post-Maeve saved state set to " + yesNo(erased)), true);
        return postMaeveStatus(context);
    }

    private static int postMaeveMoonStatus(
            CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(server);
        PostMaeveMoonPolicy.Snapshot snapshot = PostMaeveMoonManager.snapshot(data);
        context.getSource().sendSuccess(() -> Component.literal(
                "Post-Maeve Moon: stage=" + snapshot.stage().name().toLowerCase(Locale.ROOT)
                        + " scheduledAt=" + data.postMaeveMoonriseStartDayTime()
                        + " started=" + yesNo(data.postMaeveMoonriseStarted())
                        + " elapsed=" + data.postMaeveMoonElapsedDayTicks()
                        + " damageDays=" + String.format(Locale.ROOT, "%.2f",
                        snapshot.damageAgeTicks() < 0L ? 0.0D
                                : snapshot.damageAgeTicks()
                                / (double) PostMaeveMoonPolicy.DAY_TICKS)
                        + " debris=" + snapshot.debrisCount()
                        + " ring=" + String.format(Locale.ROOT, "%.2f",
                        snapshot.ringAlpha())), false);
        return 1;
    }

    private static int postMaeveMoonStartRise(
            CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        boolean changed = data.startPostMaeveMoonriseForDebug(
                level.getDayTime(), PostMaeveMoonManager.visualSeed(level));
        PostMaeveWorldState.syncAll(level.getServer());
        context.getSource().sendSuccess(() -> Component.literal(changed
                ? "DEBUG Moon first rise started now"
                : "Moon first rise requires saved maeveErased state"), true);
        return changed ? postMaeveMoonStatus(context) : 0;
    }

    private static int postMaeveMoonSetAge(
            CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        int days = IntegerArgumentType.getInteger(context, "days");
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        boolean changed = data.setPostMaeveMoonDamageAgeForDebug(
                days * PostMaeveMoonPolicy.DAY_TICKS,
                level.getDayTime(), PostMaeveMoonManager.visualSeed(level));
        PostMaeveWorldState.syncAll(level.getServer());
        context.getSource().sendSuccess(() -> Component.literal(changed
                ? "DEBUG Moon damage age set to " + days + " days"
                : "Moon age requires saved maeveErased state"), true);
        return changed ? postMaeveMoonStatus(context) : 0;
    }

    private static int postMaeveMoonReset(
            CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        boolean changed = ReturnedHearthSavedData.get(server)
                .resetPostMaeveMoonForDebug();
        PostMaeveWorldState.syncAll(server);
        context.getSource().sendSuccess(() -> Component.literal(
                changed
                        ? "DEBUG Moon timeline reset; next eligible tick schedules the next dusk"
                        : "DEBUG Moon timeline was already reset"), true);
        return postMaeveMoonStatus(context);
    }

    private static int postMaeveSpawnUndone(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos probe = player.blockPosition().offset(4, 0, 4);
        BlockPos spawn = player.serverLevel().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                probe);
        boolean spawned = UndoneSpawner.spawn(player.serverLevel(), spawn) != null;
        context.getSource().sendSuccess(() -> Component.literal(
                spawned ? "Spawned debug Undone at " + spawn.toShortString()
                        : "Could not create an Undone"), false);
        return spawned ? 1 : 0;
    }

    private static int postMaeveResetContact(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.getPersistentData().remove("frozendawnUndoneContact");
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset first Undone contact for " + player.getName().getString()), false);
        return 1;
    }

    private static int postMaeveSpawnUndoneArchitect(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos probe = player.blockPosition().offset(6, 0, 4);
        BlockPos spawn = player.serverLevel().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                probe);
        boolean spawned = UndoneArchitectSpawner.spawn(
                player.serverLevel(), spawn) != null;
        context.getSource().sendSuccess(() -> Component.literal(
                spawned ? "Spawned debug Undone Architect at "
                        + spawn.toShortString()
                        : "Could not create an Undone Architect"), false);
        return spawned ? 1 : 0;
    }

    private static int archivistStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        context.getSource().sendSuccess(() -> Component.literal(
                "Archivists: " + ArchivistManager.statusLine(level)), false);
        return 1;
    }

    private static int archivistDebugSpawn(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var spawned = ArchivistManager.debugSpawn(player);
        context.getSource().sendSuccess(() -> Component.literal(spawned == null
                ? "Could not create an Archivist"
                : "Spawned debug Archivist bound to site "
                + spawned.siteId().map(Object::toString).orElse("none")), false);
        return spawned == null ? 0 : 1;
    }

    private static int archivistCreateSite(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int created = ArchivistManager.debugCreateSite(player);
        context.getSource().sendSuccess(() -> Component.literal(
                created > 0 ? "Created debug Archivist collection site"
                        : "Could not create collection site"), false);
        return created;
    }

    private static int archivistFillSite(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int added = ArchivistManager.debugFillNearest(player);
        context.getSource().sendSuccess(() -> Component.literal(
                added > 0 ? "Added " + added + " arranged relics"
                        : "No collection site found within 96 blocks"), false);
        return added > 0 ? 1 : 0;
    }

    private static int archivistForceSort(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean sorted = ArchivistManager.forceSortNearest(player);
        context.getSource().sendSuccess(() -> Component.literal(sorted
                ? "Forced the nearest Archivist to rearrange one relic"
                : "No sortable Archivist collection found"), false);
        return sorted ? 1 : 0;
    }

    private static int archivistPurgeLoaded(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int removed = ArchivistManager.purgeLoaded(player.serverLevel(),
                player.blockPosition(), 512.0D);
        context.getSource().sendSuccess(() -> Component.literal(
                "Purged " + removed + " loaded Archivist entities/relics and nearby sites"),
                true);
        return 1;
    }

    private static int rimeboundStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "Rimebound: " + RimeboundManager.statusLine(
                        context.getSource().getLevel())), false);
        return 1;
    }

    private static int rimeboundSpawn(
            CommandContext<CommandSourceStack> context, boolean dormant)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RimeboundEntity spawned = RimeboundManager.debugSpawn(player, dormant);
        context.getSource().sendSuccess(() -> Component.literal(spawned == null
                ? "Could not create a Rimebound on loaded terrain"
                : "Spawned debug Rimebound in " + spawned.activityState().name()), false);
        return spawned == null ? 0 : 1;
    }

    private static int rimeboundSetAge(CommandContext<CommandSourceStack> context) {
        int days = IntegerArgumentType.getInteger(context, "days");
        RimeboundManager.debugSetAgeDays(days);
        context.getSource().sendSuccess(() -> Component.literal(
                "Set debug Rimebound evolution age to " + days + " days"), false);
        return 1;
    }

    private static int rimeboundSetState(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        RimeboundEntity entity = RimeboundManager.nearest(player);
        if (entity == null) {
            context.getSource().sendFailure(Component.literal(
                    "No loaded Rimebound within 96 blocks"));
            return 0;
        }
        String raw = StringArgumentType.getString(context, "state");
        try {
            RimeboundState state = RimeboundState.valueOf(raw.toUpperCase(Locale.ROOT));
            entity.setActivityState(state);
            context.getSource().sendSuccess(() -> Component.literal(
                    "Set nearest Rimebound to " + state.name()), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(
                    "Unknown Rimebound state: " + raw));
            return 0;
        }
    }

    private static int rimeboundForceBurrow(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        RimeboundEntity entity = RimeboundManager.nearest(
                context.getSource().getPlayerOrException());
        if (entity == null) {
            return noRimebound(context);
        }
        entity.forceBurrow();
        context.getSource().sendSuccess(() -> Component.literal(
                "Forced nearest Rimebound burrow attempt"), false);
        return 1;
    }

    private static int rimeboundForceLance(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        RimeboundEntity entity = RimeboundManager.nearest(
                context.getSource().getPlayerOrException());
        if (entity == null) {
            return noRimebound(context);
        }
        entity.setTarget(context.getSource().getPlayerOrException());
        entity.forceLance();
        context.getSource().sendSuccess(() -> Component.literal(
                "Forced nearest Rimebound lance"), false);
        return 1;
    }

    private static int rimeboundForceFreeze(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        RimeboundEntity entity = RimeboundManager.nearest(
                context.getSource().getPlayerOrException());
        if (entity == null) {
            return noRimebound(context);
        }
        entity.forceFreeze();
        context.getSource().sendSuccess(() -> Component.literal(
                "Forced nearest Rimebound Flash Freeze"), false);
        return 1;
    }

    private static int rimeboundPurgeLoaded(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int removed = RimeboundManager.purgeLoaded(player.serverLevel(),
                player.blockPosition(), 512.0D);
        context.getSource().sendSuccess(() -> Component.literal(
                "Purged " + removed + " loaded Rimebound/lance entities"), true);
        return 1;
    }

    private static int noRimebound(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(Component.literal(
                "No loaded Rimebound within 96 blocks"));
        return 0;
    }

    private static int bloomStatus(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        String status = BloomGrowthManager.statusLine(
                server.overworld(), ApocalypseState.get(server));
        context.getSource().sendSuccess(() -> Component.literal(
                "--- Frozen Dawn Bloom ---\n" + status), false);
        return 1;
    }

    private static int bloomSeed(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        if (!PostMaeveWorldState.isErased(server)) {
            context.getSource().sendFailure(Component.literal(
                    "Bloom is dormant until Maeve is erased"));
            return 0;
        }
        int edits = BloomGrowthManager.debugSeed(server.overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Seeded loaded Hearth centers | edits=" + edits), true);
        return 1;
    }

    private static int bloomAdvance(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int days = IntegerArgumentType.getInteger(context, "days");
        BloomGrowthManager.debugAdvance(server.overworld(),
                days * HearthMaturationPolicy.MINECRAFT_DAY_TICKS);
        context.getSource().sendSuccess(() -> Component.literal(
                "Advanced loaded-time Bloom clocks by " + days + " day(s)"), true);
        return bloomStatus(context);
    }

    private static int bloomStart(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        if (!PostMaeveWorldState.isErased(server)) {
            context.getSource().sendFailure(Component.literal(
                    "Bloom is dormant until Maeve is erased"));
            return 0;
        }
        int edits = BloomGrowthManager.debugStartNormalProgression(server.overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Replayed ORSA biological warning | Bloom eruption in 10 seconds"
                        + " | initialEdits=" + edits), true);
        return bloomStatus(context);
    }

    private static int bloomSetRadius(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BloomGrowthManager.debugSetRadius(server.overworld(), radius);
        context.getSource().sendSuccess(() -> Component.literal(
                "Bloom debug radius set to " + radius + " blocks"), true);
        return bloomStatus(context);
    }

    private static int bloomProfile(CommandContext<CommandSourceStack> context) {
        int result = bloomStatus(context);
        BloomGrowthManager.resetProfile();
        context.getSource().sendSuccess(() -> Component.literal(
                "Bloom max profiler counter reset"), false);
        return result;
    }

    private static int bloomSpawnBloombound(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos probe = player.blockPosition().offset(5, 0, 3);
        BlockPos spawn = player.serverLevel().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                probe);
        boolean spawned = BloomboundUndoneSpawner.spawn(
                player.serverLevel(), spawn) != null;
        context.getSource().sendSuccess(() -> Component.literal(
                spawned ? "Spawned debug Bloombound at " + spawn.toShortString()
                        : "Could not create a Bloombound"), false);
        return spawned ? 1 : 0;
    }

    private static int bloomSpawnSpore(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var spore = BloomSporeManager.debugSpawn(player);
        context.getSource().sendSuccess(() -> Component.literal(spore == null
                ? "Could not create The Spore; a loaded Hearth source must be free"
                : "Spawned The Spore from source " + spore.getSourceId()), false);
        return spore == null ? 0 : 1;
    }

    private static int bloomSporeStatus(CommandContext<CommandSourceStack> context) {
        String status = BloomSporeManager.statusLine(
                context.getSource().getServer().overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "--- Bloom Spore Relays ---\n" + status), false);
        return 1;
    }

    private static int bloomSporeRootNearest(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean rooted = BloomSporeManager.debugRootNearest(player);
        context.getSource().sendSuccess(() -> Component.literal(rooted
                ? "Forced the nearest Spore to begin rooting"
                : "No traveling Spore found within 96 blocks"), false);
        return rooted ? 1 : 0;
    }

    private static int bloomSporeAdvance(CommandContext<CommandSourceStack> context) {
        int days = IntegerArgumentType.getInteger(context, "days");
        BloomSporeManager.debugAdvance(context.getSource().getServer().overworld(),
                days * HearthMaturationPolicy.MINECRAFT_DAY_TICKS);
        context.getSource().sendSuccess(() -> Component.literal(
                "Advanced loaded satellite clocks by " + days + " day(s)"), true);
        return bloomSporeStatus(context);
    }

    private static int bloomSporePurgeLoaded(
            CommandContext<CommandSourceStack> context) {
        int removed = BloomSporeManager.debugPurgeLoaded(
                context.getSource().getServer().overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Purged loaded Spore entities, corpses, satellite growth, and records"
                        + " | removed=" + removed), true);
        return 1;
    }

    private static int bloomPurgeLoaded(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int removed = BloomGrowthManager.debugPurgeLoaded(server.overworld());
        context.getSource().sendSuccess(() -> Component.literal(
                "Purged all loaded Bloom blocks and paused growth at radius 0 | removed="
                        + removed + " | use start to restart normal progression"), true);
        return 1;
    }

    private static int heartMaeveReset(CommandContext<CommandSourceStack> context) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        boolean changed = hearth != null
                && data.resetHeartMaeveErasureForDebug(hearth.id());
        if (changed) {
            BloomGrowthManager.debugResetEruptionForMaeveReplay(
                    context.getSource().getServer().overworld());
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset Maeve erasure" + (changed ? "" : " (unchanged)")), true);
        HearthHeartManager.tick(context.getSource().getLevel());
        return heartMaeveStatus(context);
    }

    private static int heartReset(CommandContext<CommandSourceStack> context) {
        int removed = HearthHeartManager.resetForDebug(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset Heart formation state; removed " + removed
                        + " Heart/display entity(s). Master state was not changed."), true);
        return 1;
    }

    private static int heartLoadStatus(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        context.getSource().sendSuccess(() -> Component.literal(
                "Cognitive Load: " + CognitiveLoadManager.describe(player)), false);
        return 1;
    }

    private static int heartLoadSet(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        float value = FloatArgumentType.getFloat(context, "value");
        CognitiveLoadManager.setLoadForDebug(player, value);
        context.getSource().sendSuccess(() -> Component.literal(
                "Set Cognitive Load to " + String.format(Locale.ROOT, "%.1f", value)), true);
        return heartLoadStatus(context);
    }

    private static int heartLoadClear(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CognitiveLoadManager.setLoadForDebug(player, 0.0F);
        context.getSource().sendSuccess(() -> Component.literal(
                "Cleared Cognitive Load"), true);
        return heartLoadStatus(context);
    }

    private static int heartNodesStatus(CommandContext<CommandSourceStack> context) {
        ReturnedHearthSavedData.HearthRecord hearth = ReturnedHearthSavedData
                .get(context.getSource().getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        if (hearth == null) {
            context.getSource().sendFailure(Component.literal(
                    "Major Hearth does not exist"));
            return 0;
        }
        int next = HeartLattice.nextNode(hearth.heartDestroyedNodeMask());
        context.getSource().sendSuccess(() -> Component.literal(
                "Heart nodes: destroyed="
                        + HeartLattice.destroyedCount(hearth.heartDestroyedNodeMask())
                        + "/" + HeartLattice.NODE_COUNT
                        + " mask=0x" + Integer.toHexString(
                        hearth.heartDestroyedNodeMask())
                        + " next=" + (next < 0 ? "none" : next + 1)
                        + " damage=" + hearth.heartActiveNodeDamage()
                        + "/" + HeartLattice.HITS_PER_NODE), false);
        return 1;
    }

    private static int heartNodeDestroy(CommandContext<CommandSourceStack> context) {
        int node = IntegerArgumentType.getInteger(context, "node") - 1;
        boolean changed = HeartMemoryNodeManager.damageNodeForDebug(
                context.getSource().getLevel(), node);
        if (!changed) {
            context.getSource().sendFailure(Component.literal(
                    "Node " + (node + 1)
                            + " is not the next active memory node or the Heart is not LIVE"));
            return 0;
        }
        HearthHeartManager.tick(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "Destroyed Heart memory node " + (node + 1)
                        + " for debugging"), true);
        return heartNodesStatus(context);
    }

    private static int heartNodesReset(CommandContext<CommandSourceStack> context) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(
                context.getSource().getServer());
        ReturnedHearthSavedData.HearthRecord hearth = data
                .hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        boolean changed = hearth != null
                && data.resetHeartMemoryNodesForDebug(hearth.id());
        HearthHeartManager.tick(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset Heart memory nodes" + (changed ? "" : " (unchanged)")),
                true);
        return heartNodesStatus(context);
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
        context.getSource().sendSuccess(() -> Component.literal(
                "Hearths do not force-load or build at a distance. Load a planned site naturally "
                        + "or use /frozendawn hearth locate major before testing reconciliation."), false);
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

    private static int masterArchitectWeather(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ApocalypseState apocalypse = ApocalypseState.get(
                context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(
                "Master Architect weather for "
                        + player.getGameProfile().getName() + ": "
                        + HearthMasterArchitectWeatherManager.describe(
                                player, apocalypse.getPhase(), apocalypse.getProgress())),
                false);
        return 1;
    }

    private static int masterArchitectPhase(
            CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "Master Architect combat: "
                        + HearthMasterArchitectManager.phaseStatus(
                                context.getSource().getServer().overworld())), false);
        return 1;
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
                        + " casualties=" + memory.congregationCasualties()
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
                    () -> {
                        if (binding.permanentlyVacant()) {
                            result.append("casualty");
                        } else {
                            result.append("waiting@").append(binding.respawnAfterGameTime());
                        }
                    });
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
