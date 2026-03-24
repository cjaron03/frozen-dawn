package com.frozendawn.command;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.ConfigPresets;
import com.frozendawn.config.DifficultyPresetManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.world.BlastPitPlacement;
import com.frozendawn.world.CampPlacement;
import com.frozendawn.world.TowerPlacement;
import net.minecraft.server.level.ServerLevel;
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
import java.util.Comparator;
import java.util.List;
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
                .then(Commands.literal("towers")
                        .executes(FrozenDawnCommand::towers))
                .then(Commands.literal("landmarks")
                        .executes(FrozenDawnCommand::landmarks))
                .then(Commands.literal("camps")
                        .executes(FrozenDawnCommand::camps))
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
            if (state.getBlastPitTargetPos() != null) {
                BlockPos anchor = state.getBlastPitTargetPos();
                context.getSource().sendSuccess(() -> Component.literal(
                        "  Blast Pit: final anchor (" + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ") | awaiting chunk load"), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("  Blast Pit: not yet initialized"), false);
            }
        } else {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Blast Pit: final (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                            + " | Placed: " + state.isBlastPitPlaced()), false);
        }
        return 1;
    }

    private static int towers(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        OrsaStructureState state = OrsaStructureState.get(server);
        if (state.getTowers().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("  Towers: not yet initialized"), false);
            return 1;
        }

        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        OrsaStructureState.TowerRecord nearest = state.getNearestTower(origin);
        context.getSource().sendSuccess(() -> Component.literal("  Towers: " + state.getTowers().size()), false);
        if (nearest != null && nearest.pos() != null) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Nearest Tower: final (" + nearest.pos().getX() + ", " + nearest.pos().getY() + ", " + nearest.pos().getZ() + ")"
                            + " | Placed: " + nearest.placed()
                            + " | Architect: " + yesNo(nearest.architectTriggered())
                            + " | Aligned: " + yesNo(nearest.aligned())
                            + " | Reward: " + yesNo(nearest.rewardGranted())), false);
        } else if (nearest != null) {
            BlockPos anchor = nearest.plannedPos();
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Nearest Tower: final anchor (" + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ") | awaiting chunk load"
                            + " | Architect: " + yesNo(nearest.architectTriggered())
                            + " | Aligned: " + yesNo(nearest.aligned())
                            + " | Reward: " + yesNo(nearest.rewardGranted())), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  Nearest Tower: not yet initialized"), false);
        }
        return 1;
    }

    private static int landmarks(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        refreshLandmarks(server);
        context.getSource().sendSuccess(() -> Component.literal("--- Landmark Refresh ---"), false);
        blastPit(context);
        towers(context);
        camps(context);
        return 1;
    }

    private static final double CAMP_SKIP_RADIUS_SQ = 5.0 * 5.0;

    private static int camps(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        OrsaStructureState state = OrsaStructureState.get(server);
        ServerLevel overworld = server.overworld();
        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        long seed = overworld.getSeed();

        int originRegionX = Math.floorDiv(origin.getX() >> 4, 24);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, 24);

        // Collect all eligible camps with distances
        record CampCandidate(BlockPos pos, double distSq) {}
        List<CampCandidate> candidates = new java.util.ArrayList<>();

        // Scan 7x7 region grid using exact same logic as CampPlacement
        for (int drx = -3; drx <= 3; drx++) {
            for (int drz = -3; drz <= 3; drz++) {
                int regionX = originRegionX + drx;
                int regionZ = originRegionZ + drz;

                int[] pos = CampPlacement.getCampBlockPos(seed, regionX, regionZ);
                if (pos == null) {
                    continue;
                }

                // Same biome + footprint check as placement uses
                if (!CampPlacement.isEligibleCampSite(overworld, pos[0], pos[1])) {
                    continue;
                }

                double distSq = (pos[0] - origin.getX()) * (long) (pos[0] - origin.getX())
                        + (pos[1] - origin.getZ()) * (long) (pos[1] - origin.getZ());
                candidates.add(new CampCandidate(new BlockPos(pos[0], 0, pos[1]), distSq));
            }
        }

        // Sort by distance, skip camps within 5 blocks
        candidates.sort(Comparator.comparingDouble(CampCandidate::distSq));
        CampCandidate chosen = null;
        for (CampCandidate c : candidates) {
            if (c.distSq() > CAMP_SKIP_RADIUS_SQ) {
                chosen = c;
                break;
            }
        }

        if (chosen != null) {
            int dist = (int) Math.sqrt(chosen.distSq());
            final BlockPos camp = chosen.pos();
            int cx = camp.getX() >> 4;
            int cz = camp.getZ() >> 4;
            boolean built = state.isCampBuilt(cx, cz);
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Nearest Camp: (" + camp.getX() + ", " + camp.getZ() + ")"
                            + " | ~" + dist + " blocks"
                            + (built ? " | Built" : " | Awaiting chunk load")), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  Camps: none eligible in nearby regions"), false);
        }
        return 1;
    }

    private static void refreshLandmarks(MinecraftServer server) {
        var overworld = server.overworld();
        OrsaStructureState state = OrsaStructureState.get(server);
        for (int i = 0; i < 2; i++) {
            if (state.getBlastPitTargetPos() == null) {
                state.initBlastPitPosition(overworld);
            }
            if (state.getTowers().size() < 6) {
                state.initTowerPositions(overworld);
            }
        }
    }

}
