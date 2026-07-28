package com.frozendawn.command;

import com.frozendawn.data.SuitIntegrity;
import com.frozendawn.event.SuitIntegrityHandler;
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
                                        IntegerArgumentType.getInteger(context, "count")))));
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
}
