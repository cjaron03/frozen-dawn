package com.frozendawn.command;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.ConfigPresets;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.network.ApocalypseDataPayload;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.Locale;

/**
 * Admin commands for controlling the apocalypse.
 *
 * /frozendawn status     — show current state
 * /frozendawn setday <n> — jump to a specific day
 * /frozendawn setphase <1-5> — jump to the start of a phase
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
                        .then(Commands.argument("phase", IntegerArgumentType.integer(1, 5))
                                .executes(FrozenDawnCommand::setPhase)))
                .then(Commands.literal("pause")
                        .executes(FrozenDawnCommand::togglePause))
                .then(Commands.literal("reset")
                        .executes(FrozenDawnCommand::reset))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(PRESET_SUGGESTIONS)
                                .executes(FrozenDawnCommand::applyPreset)))
        );
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        String[] phaseNames = {"", "Twilight", "Cooling", "The Long Night", "Deep Freeze", "Eternal Winter"};
        int phase = state.getPhase();
        String phaseName = phase >= 1 && phase <= 5 ? phaseNames[phase] : "Unknown";

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.status.header"), false);
        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.status.day",
                state.getCurrentDay(), state.getTotalDays()), false);
        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.status.phase",
                phase, phaseName), false);
        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.status.temp",
                String.format("%.1f", state.getTemperatureOffset())), false);
        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.status.sun",
                String.format("%.2f", state.getSunScale()),
                String.format("%.0f%%", state.getSkyLight() * 100)), false);
        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.status.paused",
                FrozenDawnConfig.PAUSE_PROGRESSION.get() ? "Yes" : "No"), false);
        return 1;
    }

    private static int setDay(CommandContext<CommandSourceStack> context) {
        int day = IntegerArgumentType.getInteger(context, "day");
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        state.setApocalypseTicks((long) day * 24000L);
        syncToClients(state);

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.setday",
                day, state.getPhase()), true);
        return 1;
    }

    private static int setPhase(CommandContext<CommandSourceStack> context) {
        int phase = IntegerArgumentType.getInteger(context, "phase");
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        int targetDay = PhaseManager.getPhaseStartDay(phase, state.getTotalDays());
        state.setApocalypseTicks((long) targetDay * 24000L);
        syncToClients(state);

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.setphase",
                phase, targetDay), true);
        return 1;
    }

    private static int togglePause(CommandContext<CommandSourceStack> context) {
        boolean newValue = !FrozenDawnConfig.PAUSE_PROGRESSION.get();
        FrozenDawnConfig.PAUSE_PROGRESSION.set(newValue);

        context.getSource().sendSuccess(() -> Component.translatable(
                newValue ? "command.frozendawn.paused" : "command.frozendawn.resumed"), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ApocalypseState state = ApocalypseState.get(server);

        state.setApocalypseTicks(0);
        syncToClients(state);

        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.reset"), true);
        return 1;
    }

    private static int applyPreset(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name").toUpperCase(Locale.ROOT);
        ConfigPresets preset;
        try {
            preset = ConfigPresets.valueOf(name);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.translatable("command.frozendawn.preset.unknown", name));
            return 0;
        }

        preset.apply();
        context.getSource().sendSuccess(() -> Component.translatable("command.frozendawn.preset.applied",
                preset.name().toLowerCase(Locale.ROOT), preset.totalDays, preset.basePhase5Temp), true);
        return 1;
    }

    private static void syncToClients(ApocalypseState state) {
        PacketDistributor.sendToAllPlayers(new ApocalypseDataPayload(
                state.getPhase(),
                state.getProgress(),
                state.getTemperatureOffset(),
                state.getSunScale(),
                state.getSunBrightness(),
                state.getSkyLight()
        ));
    }
}
