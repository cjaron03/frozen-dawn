package com.frozendawn.command;

import com.frozendawn.FrozenDawn;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Admin commands for controlling the apocalypse.
 *
 * /frozendawn world status     — show current state
 * /frozendawn world setday <n> — jump to a specific day
 * /frozendawn world setphase <1-6> [early|mid|late] — jump to the start of a phase (sub-stages for phase 6)
 * /frozendawn world settotaldays <n> — set total apocalypse duration for fast testing
 * /frozendawn world pause      — toggle progression pause
 * /frozendawn world reset      — reset to day 0
 * /frozendawn world preset <name> — apply a config preset (default/cinematic/brutal)
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class FrozenDawnCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("frozendawn")
                .requires(source -> source.hasPermission(2))
                .executes(FrozenDawnCommand::help)
                .then(Commands.literal("help")
                        .executes(FrozenDawnCommand::help))
                .then(FrozenDawnWorldCommand.worldCommands())
                .then(FrozenDawnHearthCommand.hearthCommands())
                .then(FrozenDawnLocateCommand.locateCommands())
                .then(FrozenDawnWinCommand.winCommands())
        );
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("--- Frozen Dawn Commands ---"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn world status"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn world catchup"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn world setday <day>"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn world setphase <phase> [early|mid|late]"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn world settotaldays <days>"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn world pause | reset | preset <name>"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn hearth status | list"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn hearth locate <major|minor>"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn hearth force-select"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn hearth watcher [respawn <major|minor>]"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn hearth architect [respawn|assessment [reset]]"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn hearth relationship [set <neutral|suspicious|orsathae>]"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn hearth mood set <major|minor|all> <mood>"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn hearth advance <ticks|days> <amount>"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn locate orsa"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn locate vents"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn locate vents rupture"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn locate towns"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn locate all"), false);
        context.getSource().sendSuccess(() -> Component.literal("  /frozendawn win satellite"), false);
        return 1;
    }
}
