package com.frozendawn.command;

import com.frozendawn.FrozenDawn;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Public diagnostics plus the operator-only Frozen Dawn debug surface. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class FrozenDawnCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> debug = FrozenDawnDebugCommand.commands().build();
        LiteralCommandNode<CommandSourceStack> world = FrozenDawnWorldCommand.worldCommands().build();
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("frozendawn")
                .executes(FrozenDawnCommand::help)
                .then(Commands.literal("help")
                        .executes(FrozenDawnCommand::help)
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        FrozenDawnDebugCommand.HELP_CATEGORIES, builder))
                                .executes(FrozenDawnCommand::categoryHelp)))
                .then(world.getChild("status"))
                .then(FrozenDawnLocateCommand.locateCommands())
                .then(FrozenDawnWinCommand.winCommands())
                .then(debug)
        );
        dispatcher.register(Commands.literal("fd")
                .requires(source -> source.hasPermission(2))
                .executes(context -> FrozenDawnDebugCommand.sendHelp(
                        context.getSource(), "debug"))
                .redirect(root.getChild("debug")));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        FrozenDawnCommandOutput.heading(context.getSource(), "Commands");
        FrozenDawnCommandOutput.item(context.getSource(), "/frozendawn status");
        FrozenDawnCommandOutput.item(context.getSource(),
                "/frozendawn locate <all|orsa|towns|vents>");
        FrozenDawnCommandOutput.item(context.getSource(),
                "/frozendawn win <status|satellite>");
        FrozenDawnCommandOutput.item(context.getSource(),
                "/frozendawn help <category>");
        FrozenDawnCommandOutput.line(context.getSource(), "Operator debug",
                "/fd <category> ...");
        FrozenDawnCommandOutput.detail(context.getSource(), "Categories",
                String.join(", ", FrozenDawnDebugCommand.HELP_CATEGORIES));
        return 1;
    }

    private static int categoryHelp(CommandContext<CommandSourceStack> context) {
        return FrozenDawnDebugCommand.sendHelp(
                context.getSource(), StringArgumentType.getString(context, "category"));
    }
}
