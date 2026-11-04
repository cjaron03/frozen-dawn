package com.frozendawn.command;

import com.frozendawn.data.HelionSiteState;
import com.frozendawn.defense.Terminus;
import com.frozendawn.defense.TerminusToll;
import com.frozendawn.defense.TerminusLedger;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.vault.ArgusFight;
import com.frozendawn.vault.TerminusVaultBuilder;
import com.frozendawn.vault.VaultArgusBlockEntity;
import com.frozendawn.vault.VaultMechanismBlockEntity;
import com.frozendawn.world.HelionSitePlacement;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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
                    var toll = player.getData(ModAttachments.TERMINUS_TAKEN.get());
                    long wait = Math.max(0, toll.returnsAt() - c.getSource().getServer().overworld().getGameTime());
                    player.sendSystemMessage(Component.literal("Terminus: " + toll.taken() + " half hearts taken"
                            + (toll.taken() > 0 ? ", the next back in " + wait / 20 + " s" : "")
                            + ", max health " + player.getMaxHealth() + "; world log "
                            + (TerminusLedger.turned(c.getSource().getServer()) ? "terminations" : "transfers")));
                    return 1;
                }))
                .then(Commands.literal("restore").executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    player.setData(ModAttachments.TERMINUS_TAKEN.get(), TerminusToll.NONE);
                    Terminus.applyToll(player);
                    player.setHealth(player.getMaxHealth());
                    player.sendSystemMessage(Component.literal("Terminus toll cleared: max health " + player.getMaxHealth()));
                    return 1;
                }))
                .then(Commands.literal("due").executes(c -> {
                    // The next half heart comes back on the next tick, so a playtest need not wait half a day.
                    var player = c.getSource().getPlayerOrException();
                    var toll = player.getData(ModAttachments.TERMINUS_TAKEN.get());
                    player.setData(ModAttachments.TERMINUS_TAKEN.get(),
                            new TerminusToll(toll.taken(), c.getSource().getServer().overworld().getGameTime()));
                    player.sendSystemMessage(Component.literal("Terminus: the next half heart is due now"));
                    return 1;
                }))
                .then(Commands.literal("vault")
                        .then(Commands.literal("place").executes(c -> {
                            // A new site 32 blocks ahead, buried as usual, with the vault under it; the world's flag moves to it.
                            var player = c.getSource().getPlayerOrException();
                            var level = c.getSource().getLevel();
                            var ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(32));
                            var hall = HelionSitePlacement.placeNow(level, (int) Math.floor(ahead.x), (int) Math.floor(ahead.z));
                            TerminusVaultBuilder.place(level, hall);
                            HelionSiteState.get(level.getServer()).markVault(hall);
                            player.sendSystemMessage(Component.literal("Terminus Vault under the hall at " + hall.toShortString()));
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(c -> {
                            var player = c.getSource().getPlayerOrException();
                            var hall = HelionSiteState.get(c.getSource().getServer()).vault();
                            if (hall != null) TerminusVaultBuilder.reset(c.getSource().getServer().overworld(), hall);
                            player.sendSystemMessage(Component.literal(hall == null ? "No Terminus Vault in this world" : "Terminus Vault reset: doors shut, bridge in, wand back"));
                            return 1;
                        }))
                        .then(Commands.literal("fight")
                                .then(Commands.literal("start").executes(c -> fight(c.getSource(), f -> f.jump(0, ArgusFight.Phase.WAITING, ArgusFight.CLOCK_TICKS))))
                                .then(Commands.literal("round").then(Commands.argument("round", IntegerArgumentType.integer(1, 3)).executes(c -> {
                                    int won = IntegerArgumentType.getInteger(c, "round") - 1;
                                    return fight(c.getSource(), f -> f.jump(won, ArgusFight.Phase.WAITING, ArgusFight.CLOCK_TICKS));
                                })))
                                .then(Commands.literal("pull").then(Commands.argument("round", IntegerArgumentType.integer(1, 3)).executes(c -> {
                                    int won = IntegerArgumentType.getInteger(c, "round") - 1;
                                    return fight(c.getSource(), f -> f.jump(won, ArgusFight.Phase.PULL, ArgusFight.CLOCK_TICKS));
                                })))
                                .then(Commands.literal("burn").then(Commands.argument("round", IntegerArgumentType.integer(1, 3)).executes(c -> {
                                    int won = IntegerArgumentType.getInteger(c, "round") - 1;
                                    return fight(c.getSource(), f -> f.jump(won, ArgusFight.Phase.BURN, ArgusFight.CLOCK_TICKS));
                                })))
                                .then(Commands.literal("clock").then(Commands.argument("seconds", IntegerArgumentType.integer(1, ArgusFight.CLOCK_TICKS / 20)).executes(c -> {
                                    int seconds = IntegerArgumentType.getInteger(c, "seconds");
                                    return fight(c.getSource(), f -> f.setClock(seconds * 20));
                                }))))
                        .then(Commands.literal("status").executes(c -> {
                            var server = c.getSource().getServer();
                            var hall = HelionSiteState.get(server).vault();
                            String text = "No Terminus Vault in this world";
                            if (hall != null) {
                                var level = server.overworld();
                                text = "Terminus Vault at " + hall.toShortString() + ":";
                                for (var at : new BlockPos[] {TerminusVaultBuilder.DOOR, TerminusVaultBuilder.BRIDGE, TerminusVaultBuilder.LAST_DOOR})
                                    if (level.getBlockEntity(hall.offset(at)) instanceof VaultMechanismBlockEntity m) text += " " + m.kind().getSerializedName() + " " + m.progress();
                                if (level.getBlockEntity(hall.offset(TerminusVaultBuilder.ARGUS)) instanceof VaultArgusBlockEntity argus) {
                                    var fight = argus.fight();
                                    text += " | fight " + fight.phase() + ", " + ArgusFight.text(fight.seconds()) + ", openings " + fight.won() + ", clean " + fight.clean()
                                            + ", wanted " + java.util.Arrays.toString(fight.wanted()) + ", fuses " + java.util.Arrays.toString(fight.fuses());
                                    if (argus.custodian() != null) text += ", Custodian " + Math.round(argus.custodian().getHealth()) + "/" + Math.round(argus.custodian().getMaxHealth());
                                }
                                if (level.getBlockEntity(hall.offset(TerminusVaultBuilder.RECORDER)) instanceof com.frozendawn.vault.VaultRecorderBlockEntity recorder)
                                    text += " | drill " + recorder.rule().phase() + ", " + recorder.rule().ticksLeft() / 20 + " s left, door " + (recorder.rule().doorOpen() ? "open" : "shut");
                                if (level.getBlockEntity(hall.offset(TerminusVaultBuilder.VITALS)) instanceof com.frozendawn.vault.VaultVitalsBlockEntity vitals)
                                    text += " | morgue reads " + vitals.gate().reading() + ", door " + (vitals.gate().open() ? "open" : "shut");
                            }
                            c.getSource().getPlayerOrException().sendSystemMessage(Component.literal(text));
                            return 1;
                        })))
                .then(Commands.literal("turned").then(Commands.argument("turned", BoolArgumentType.bool()).executes(c -> {
                    boolean turned = BoolArgumentType.getBool(c, "turned");
                    TerminusLedger.setForTest(c.getSource().getServer(), turned);
                    c.getSource().getPlayerOrException().sendSystemMessage(Component.literal(
                            "Terminus log set to " + (turned ? "terminations (after the Heart)" : "transfers (before the Heart)")));
                    return 1;
                })));
    }

    /** Playtest only: a stage of the Watch Floor's fight, on the world's own Argus. */
    private static int fight(CommandSourceStack source, java.util.function.Consumer<ArgusFight> stage) {
        var hall = HelionSiteState.get(source.getServer()).vault();
        if (hall == null || !(source.getServer().overworld().getBlockEntity(hall.offset(TerminusVaultBuilder.ARGUS)) instanceof VaultArgusBlockEntity argus)) return 0;
        stage.accept(argus.fight());
        return 1;
    }
}
