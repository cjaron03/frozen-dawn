package com.frozendawn.command;

import com.frozendawn.defense.Terminus;
import com.frozendawn.defense.TerminusLedger;
import com.frozendawn.init.ModAttachments;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Terminus playtest controls: read the operator's toll and the world's log, give the toll back, and set the log's
 * turn by hand. In play neither the toll nor the turn ever reverses; these are for repeat playtests only.
 */
final class TerminusDebugCommand {
    private TerminusDebugCommand() { }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("terminus").requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    // Playtest worlds turn command feedback off, so this speaks to the player directly.
                    player.sendSystemMessage(Component.literal("Terminus: " + player.getData(ModAttachments.TERMINUS_TOLL.get())
                            + " discharges on record, max health " + player.getMaxHealth() + "; world log "
                            + (TerminusLedger.turned(c.getSource().getServer()) ? "terminations" : "transfers")));
                    return 1;
                }))
                .then(Commands.literal("restore").executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    player.setData(ModAttachments.TERMINUS_TOLL.get(), 0);
                    Terminus.applyToll(player);
                    player.setHealth(player.getMaxHealth());
                    player.sendSystemMessage(Component.literal("Terminus toll cleared: max health " + player.getMaxHealth()));
                    return 1;
                }))
                .then(Commands.literal("turned").then(Commands.argument("turned", BoolArgumentType.bool()).executes(c -> {
                    boolean turned = BoolArgumentType.getBool(c, "turned");
                    TerminusLedger.setForTest(c.getSource().getServer(), turned);
                    c.getSource().getPlayerOrException().sendSystemMessage(Component.literal(
                            "Terminus log set to " + (turned ? "terminations (after the Heart)" : "transfers (before the Heart)")));
                    return 1;
                })));
    }
}
