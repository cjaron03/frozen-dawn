package com.frozendawn.command;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthSelectionManager;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;

final class FrozenDawnHearthCommand {

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
                        .executes(FrozenDawnHearthCommand::forceSelect));
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

        context.getSource().sendSuccess(() -> Component.literal("--- Homo Reliquus Hearths ---"), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Schema: " + hearthState.dataVersion()
                        + " | Records: " + hearthState.hearths().size()), false);
        context.getSource().sendSuccess(() -> Component.literal("  Transponder anchor: " + anchor), false);
        context.getSource().sendSuccess(() -> Component.literal("  Selection: " + selectionGate), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Hive: " + hearthState.globalDisposition().name().toLowerCase(Locale.ROOT)
                        + " | Permanent Orsathae: " + yesNo(hearthState.permanentOrsathae())), false);
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
                            + " maturity=" + hearth.maturityTicks() + "t"
                            + " resolved=" + yesNo(hearth.surfaceResolved())
                            + " violation=" + hearth.violationState().name().toLowerCase(Locale.ROOT)), false);
        }
        return state.hearths().size();
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
