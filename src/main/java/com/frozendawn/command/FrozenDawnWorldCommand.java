package com.frozendawn.command;

import com.frozendawn.config.ConfigPresets;
import com.frozendawn.config.DifficultyPresetManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.phase.PhaseManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Locale;

final class FrozenDawnWorldCommand {

    private FrozenDawnWorldCommand() {
    }

    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(ConfigPresets.values()).map(p -> p.name().toLowerCase(Locale.ROOT)),
                    builder);

    private static final SuggestionProvider<CommandSourceStack> SUBSTAGE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.asList("early", "mid", "late"), builder);

    static LiteralArgumentBuilder<CommandSourceStack> worldCommands() {
        return Commands.literal("world")
                .then(Commands.literal("status").executes(FrozenDawnWorldCommand::status))
                .then(Commands.literal("setday")
                        .then(Commands.argument("day", IntegerArgumentType.integer(0, 10000))
                                .executes(FrozenDawnWorldCommand::setDay)))
                .then(Commands.literal("setphase")
                        .then(Commands.argument("phase", IntegerArgumentType.integer(0, 6))
                                .executes(FrozenDawnWorldCommand::setPhase)
                                .then(Commands.argument("substage", StringArgumentType.word())
                                        .suggests(SUBSTAGE_SUGGESTIONS)
                                        .executes(FrozenDawnWorldCommand::setPhaseSubstage))))
                .then(Commands.literal("settotaldays")
                        .then(Commands.argument("days", IntegerArgumentType.integer(7, 10000))
                                .executes(FrozenDawnWorldCommand::setTotalDays)))
                .then(Commands.literal("pause").executes(FrozenDawnWorldCommand::togglePause))
                .then(Commands.literal("reset").executes(FrozenDawnWorldCommand::reset))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(PRESET_SUGGESTIONS)
                                .executes(FrozenDawnWorldCommand::applyPreset)));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);
        ConfigPresets activePreset = ConfigPresets.detectCurrentPreset();

        String[] phaseNames = {"Normal", "Twilight", "Cooling", "The Long Night", "Deep Freeze", "Eternal Winter", "Atmospheric Collapse"};
        int phase = state.getPhase();
        String phaseName = phase >= 0 && phase <= 6 ? phaseNames[phase] : "Unknown";
        boolean paused = FrozenDawnConfig.PAUSE_PROGRESSION.get();

        context.getSource().sendSuccess(() -> Component.literal("--- Frozen Dawn Status ---"), false);
        context.getSource().sendSuccess(() -> Component.literal("  Day: " + state.getCurrentDay() + " / " + state.getTotalDays()), false);
        context.getSource().sendSuccess(() -> Component.literal("  Phase: " + phase + " (" + phaseName + ")"), false);
        context.getSource().sendSuccess(() -> Component.literal("  Preset: " + formatPresetName(activePreset)), false);
        context.getSource().sendSuccess(() -> Component.literal("  Temperature: " + String.format(Locale.ROOT, "%.1f", state.getTemperatureOffset()) + "C"), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Sun Scale: " + String.format(Locale.ROOT, "%.2f", state.getSunScale())
                        + " | Sky Light: " + String.format(Locale.ROOT, "%.0f%%", state.getSkyLight() * 100)), false);
        context.getSource().sendSuccess(() -> Component.literal("  Progression: " + (paused ? "Paused" : "Running")), false);

        boolean winEnabled = FrozenDawnConfig.ENABLE_WIN_CONDITION.get();
        context.getSource().sendSuccess(() -> Component.literal("  Win Condition: " + (winEnabled ? "Enabled" : "Disabled")), false);
        if (winEnabled) {
            WinConditionState winState = WinConditionState.get(server);
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Satellite Placed: " + yesNo(winState.isSatellitePlaced())
                            + " | Schematic: " + yesNo(winState.isSchematicUnlocked())), false);
        }

        OrsaStructureState orsaState = OrsaStructureState.get(server);
        BlockPos blastPitPos = orsaState.getBlastPitPos();
        if (blastPitPos != null) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Blast Pit: final (" + blastPitPos.getX() + ", " + blastPitPos.getY() + ", " + blastPitPos.getZ() + ")"
                            + " | Placed: " + yesNo(orsaState.isBlastPitPlaced())), false);
        } else if (orsaState.getBlastPitTargetPos() != null) {
            BlockPos anchor = orsaState.getBlastPitTargetPos();
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Blast Pit: final anchor (" + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ") | awaiting chunk load"), false);
        }
        context.getSource().sendSuccess(() -> Component.literal("  Towers: " + orsaState.getTowers().size()), false);
        return 1;
    }

    private static String formatPresetName(ConfigPresets preset) {
        if (preset == null) {
            return "Custom";
        }
        String lower = preset.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static int setDay(CommandContext<CommandSourceStack> context) {
        int day = IntegerArgumentType.getInteger(context, "day");
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        state.setApocalypseTicks((long) day * 24000L, server);
        DifficultyPresetManager.syncToClients(server, state);

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.setday",
                day, state.getPhase()), true);
        return 1;
    }

    private static int setPhase(CommandContext<CommandSourceStack> context) {
        int phase = IntegerArgumentType.getInteger(context, "phase");
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        int targetDay = PhaseManager.getPhaseStartDay(phase, state.getTotalDays());
        state.setApocalypseTicks((long) targetDay * 24000L, server);
        DifficultyPresetManager.syncToClients(server, state);

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.setphase",
                phase, targetDay), true);
        return 1;
    }

    private static int setPhaseSubstage(CommandContext<CommandSourceStack> context) {
        int phase = IntegerArgumentType.getInteger(context, "phase");
        String substage = StringArgumentType.getString(context, "substage").toLowerCase(Locale.ROOT);
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        if (phase != 6) {
            context.getSource().sendFailure(Component.literal("Sub-stages are only available for phase 6"));
            return 0;
        }

        float targetProgress = switch (substage) {
            case "early" -> PhaseManager.PHASE6_START;
            case "mid" -> PhaseManager.PHASE6_MID_START;
            case "late" -> PhaseManager.PHASE6_VACUUM_START;
            default -> {
                context.getSource().sendFailure(Component.literal("Unknown sub-stage: " + substage + " (valid: early, mid, late)"));
                yield -1f;
            }
        };
        if (targetProgress < 0) {
            return 0;
        }

        int targetDay = Math.min(state.getTotalDays(), Mth.ceil(targetProgress * state.getTotalDays()));
        state.setApocalypseTicks((long) targetDay * 24000L, server);
        DifficultyPresetManager.syncToClients(server, state);

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.setphase.substage",
                substage, targetDay), true);
        return 1;
    }

    private static int setTotalDays(CommandContext<CommandSourceStack> context) {
        int totalDays = IntegerArgumentType.getInteger(context, "days");
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        FrozenDawnConfig.TOTAL_DAYS.set(totalDays);
        state.setPresetName("custom");
        DifficultyPresetManager.persistConfigOverrides();
        DifficultyPresetManager.syncToClients(server, state);

        context.getSource().sendSuccess(() -> Component.literal(
                "Set apocalypse duration to " + totalDays + " total days (preset now custom, current phase "
                        + state.getPhase() + ")"), true);
        return 1;
    }

    private static int togglePause(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        boolean newValue = !FrozenDawnConfig.PAUSE_PROGRESSION.get();
        FrozenDawnConfig.PAUSE_PROGRESSION.set(newValue);
        DifficultyPresetManager.persistConfigOverrides();
        DifficultyPresetManager.syncToClients(server, ApocalypseState.get(server));

        context.getSource().sendSuccess(() -> Component.translatable(
                newValue ? "command.frozendawn.paused" : "command.frozendawn.resumed"), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        state.setApocalypseTicks(0, server);
        DifficultyPresetManager.syncToClients(server, state);

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.reset"), true);
        return 1;
    }

    private static int applyPreset(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        String name = StringArgumentType.getString(context, "name").toUpperCase(Locale.ROOT);
        ConfigPresets preset;
        try {
            preset = ConfigPresets.valueOf(name);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.translatable("command.frozendawn.preset.unknown", name));
            return 0;
        }

        boolean applied = DifficultyPresetManager.applyPreset(server, preset, false, context.getSource().hasPermission(2));
        if (!applied) {
            context.getSource().sendFailure(Component.translatable("command.frozendawn.preset.locked"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.preset.applied",
                preset.name().toLowerCase(Locale.ROOT), preset.totalDays, preset.basePhase5Temp), true);
        return 1;
    }
}
