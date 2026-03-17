package com.frozendawn.command;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.ConfigPresets;
import com.frozendawn.config.DifficultyPresetManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.data.WinConditionState;
import net.minecraft.core.BlockPos;
import com.frozendawn.phase.PhaseManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.Arrays;
import java.util.Locale;

/**
 * Admin commands for controlling the apocalypse.
 *
 * /frozendawn status     — show current state
 * /frozendawn setday <n> — jump to a specific day
 * /frozendawn setphase <1-6> [early|mid|late] — jump to the start of a phase (sub-stages for phase 6)
 * /frozendawn pause      — toggle progression pause
 * /frozendawn reset      — reset to day 0
 * /frozendawn preset <name> — apply a config preset (default/cinematic/brutal)
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class FrozenDawnCommand {

    private static final SuggestionProvider<CommandSourceStack> PRESET_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(ConfigPresets.values()).map(p -> p.name().toLowerCase(Locale.ROOT)),
                    builder);

    private static final SuggestionProvider<CommandSourceStack> SUBSTAGE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.asList("early", "mid", "late"), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("frozendawn")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(FrozenDawnCommand::status))
                .then(Commands.literal("setday")
                        .then(Commands.argument("day", IntegerArgumentType.integer(0, 10000))
                                .executes(FrozenDawnCommand::setDay)))
                .then(Commands.literal("setphase")
                        .then(Commands.argument("phase", IntegerArgumentType.integer(0, 6))
                                .executes(FrozenDawnCommand::setPhase)
                                .then(Commands.argument("substage", StringArgumentType.word())
                                        .suggests(SUBSTAGE_SUGGESTIONS)
                                        .executes(FrozenDawnCommand::setPhaseSubstage))))
                .then(Commands.literal("pause")
                        .executes(FrozenDawnCommand::togglePause))
                .then(Commands.literal("reset")
                        .executes(FrozenDawnCommand::reset))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(PRESET_SUGGESTIONS)
                                .executes(FrozenDawnCommand::applyPreset)))
                .then(Commands.literal("blastpit")
                        .executes(FrozenDawnCommand::blastPit))
                .then(Commands.literal("satellite")
                        .executes(FrozenDawnCommand::satellite))
        );
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

        // Win condition info
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
                    "  Blast Pit: (" + blastPitPos.getX() + ", " + blastPitPos.getY() + ", " + blastPitPos.getZ() + ")"
                            + " | Placed: " + yesNo(orsaState.isBlastPitPlaced())), false);
        }
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

        // Phase 6 sub-stages: early=0.60, mid=0.72, late=0.85
        float targetProgress = switch (substage) {
            case "early" -> 0.60f;
            case "mid" -> 0.72f;
            case "late" -> 0.85f;
            default -> {
                context.getSource().sendFailure(Component.literal("Unknown sub-stage: " + substage + " (valid: early, mid, late)"));
                yield -1f;
            }
        };
        if (targetProgress < 0) return 0;

        int targetDay = (int) (targetProgress * state.getTotalDays());
        state.setApocalypseTicks((long) targetDay * 24000L, server);
        DifficultyPresetManager.syncToClients(server, state);

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.setphase.substage",
                substage, targetDay), true);
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

    private static int satellite(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        WinConditionState winState = WinConditionState.get(server);
        BlockPos pos = winState.getSatellitePos();
        if (pos == null) {
            context.getSource().sendSuccess(() -> Component.literal("  Satellite: not yet initialized"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Satellite: (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                    + " | Placed: " + winState.isSatellitePlaced()
                    + " | Schematic: " + winState.isSchematicUnlocked()), false);
        }
        return 1;
    }

    private static int blastPit(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        OrsaStructureState state = OrsaStructureState.get(server);
        BlockPos pos = state.getBlastPitPos();
        if (pos == null) {
            context.getSource().sendSuccess(() -> Component.literal("  Blast Pit: not yet initialized"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Blast Pit: (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                            + " | Placed: " + state.isBlastPitPlaced()), false);
        }
        return 1;
    }

}
