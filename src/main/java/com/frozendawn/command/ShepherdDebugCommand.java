package com.frozendawn.command;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.miller.Drives.Drive;
import com.frozendawn.miller.Feelings.Feeling;
import com.frozendawn.miller.Mark;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.miller.PatternLaws;
import com.frozendawn.miller.SelfModel;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdEggs;
import com.frozendawn.shepherd.ShepherdReactions;
import com.frozendawn.shepherd.ShepherdSavedData;
import com.frozendawn.shepherd.ShepherdTalk;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** CS-1 diagnostics: a read-only dump, and for playtests a replay of the first meeting, a set score and a skipped day. */
final class ShepherdDebugCommand {
    private ShepherdDebugCommand() { }

    private static int lines(CommandSourceStack source, List<String> lines) {
        for (var line : lines) FrozenDawnCommandOutput.item(source, line);
        return 1;
    }

    /** Through the real door: it pays the cooldown and needs her in range, like any deed. */
    private static LiteralArgumentBuilder<CommandSourceStack> saw() {
        var saw = Commands.literal("saw");
        for (Deed deed : Deed.values()) {
            saw.then(Commands.literal(deed.name().toLowerCase(Locale.ROOT)).executes(c -> {
                ShepherdReactions.saw(c.getSource().getPlayerOrException(), deed);
                return 1;
            }));
        }
        return saw;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> want() {
        var want = Commands.literal("want");
        for (Drive drive : Drive.values()) {
            want.then(Commands.literal(drive.name().toLowerCase(Locale.ROOT)).then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1)).executes(c -> {
                MillerPattern.want(c.getSource().getServer(), drive, DoubleArgumentType.getDouble(c, "value"));
                FrozenDawnCommandOutput.item(c.getSource(), drive + "=" + DoubleArgumentType.getDouble(c, "value"));
                return 1;
            })));
        }
        return want;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> witness() {
        var witness = Commands.literal("witness");
        for (PatternLaws wall : PatternLaws.values()) {
            witness.then(Commands.literal(wall.name().toLowerCase(Locale.ROOT)).executes(c -> {
                MillerPattern.witness(c.getSource().getServer(), wall, SelfModel.FOUND_AT);
                FrozenDawnCommandOutput.item(c.getSource(), "found " + wall);
                return 1;
            }));
        }
        return witness;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> feel() {
        var feel = Commands.literal("feel");
        for (Feeling feeling : Feeling.values()) {
            feel.then(Commands.literal(feeling.name().toLowerCase(Locale.ROOT)).executes(c -> {
                MillerPattern.feel(c.getSource().getServer(), feeling);
                return 1;
            }));
        }
        return feel;
    }

    /** Her log for the lab: an entry now, the lean that signs it, a mark as if she saw it, and the first read again. */
    private static LiteralArgumentBuilder<CommandSourceStack> log() {
        var mark = Commands.literal("mark");
        for (Mark seen : Mark.values()) {
            mark.then(Commands.literal(seen.name().toLowerCase(Locale.ROOT)).executes(c -> {
                MillerPattern.marked(c.getSource().getServer(), c.getSource().getPlayerOrException().getUUID(), seen);
                return 1;
            }));
        }
        return Commands.literal("log")
                .then(mark)
                .then(Commands.literal("forget").executes(c -> {
                    MillerPattern.forgetRead(c.getSource().getServer(), c.getSource().getPlayerOrException().getUUID());
                    FrozenDawnCommandOutput.item(c.getSource(), "she has not seen you read it");
                    return 1;
                }))
                .then(Commands.literal("write").executes(c -> {
                    var entry = ShepherdReactions.writeDay(c.getSource().getPlayerOrException());
                    FrozenDawnCommandOutput.item(c.getSource(), "day " + entry.day() + " note=" + entry.note());
                    return 1;
                }))
                .then(Commands.literal("sign").executes(c -> {
                    MillerPattern.lean(c.getSource().getServer(), c.getSource().getPlayerOrException().getUUID());
                    FrozenDawnCommandOutput.item(c.getSource(), "lean=" + MillerPattern.leaning(c.getSource().getServer()));
                    return 1;
                }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("shepherd").requires(source -> source.hasPermission(2))
                .then(Commands.literal("state").executes(c -> lines(c.getSource(), MillerPattern.diagnostics(c.getSource().getServer()))))
                .then(Commands.literal("why").executes(c -> lines(c.getSource(), MillerPattern.explain(c.getSource().getServer(), c.getSource().getPlayer() == null ? null : c.getSource().getPlayer().getUUID()))))
                .then(Commands.literal("hear").then(Commands.argument("text", StringArgumentType.greedyString()).executes(c ->
                        lines(c.getSource(), ShepherdTalk.hearing(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "text"))))))
                .then(Commands.literal("mind").executes(c -> lines(c.getSource(), MillerPattern.mind(c.getSource().getServer()))))
                .then(Commands.literal("coherence").then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1)).executes(c -> {
                    MillerPattern.setCoherence(c.getSource().getServer(), DoubleArgumentType.getDouble(c, "value"));
                    FrozenDawnCommandOutput.item(c.getSource(), "coherence=" + MillerPattern.snapshot(c.getSource().getServer()).coherence());
                    return 1;
                })))
                .then(want())
                .then(witness())
                .then(saw())
                .then(feel())
                .then(log())
                .then(Commands.literal("dump").executes(c -> {
                    FrozenDawnCommandOutput.heading(c.getSource(), "CS-1 Shepherd");
                    for (var line : ShepherdSavedData.get(c.getSource().getServer()).describe())
                        FrozenDawnCommandOutput.item(c.getSource(), line);
                    return 1;
                }))
                .then(Commands.literal("replay").executes(c -> {
                    boolean done = ShepherdTalk.replay(c.getSource().getServer());
                    FrozenDawnCommandOutput.item(c.getSource(), done ? "first meeting reset; right-click her to start it" : "she is not awake");
                    return done ? 1 : 0;
                }))
                .then(Commands.literal("skipday").executes(c -> {
                    ShepherdReactions.skipDay(c.getSource().getServer());
                    FrozenDawnCommandOutput.item(c.getSource(), "her cooldowns and clock are a day on");
                    return 1;
                }))
                .then(Commands.literal("slip").executes(c -> {
                    MillerPattern.slipNow(c.getSource().getServer());
                    FrozenDawnCommandOutput.item(c.getSource(), "the recording is due at the next poll");
                    return 1;
                }))
                .then(Commands.literal("resetgap").executes(c -> {
                    ShepherdSavedData.get(c.getSource().getServer()).lastAmbient(ShepherdSavedData.NOT_SET);
                    FrozenDawnCommandOutput.item(c.getSource(), "she may speak up at the next poll");
                    return 1;
                }))
                .then(Commands.literal("hearthhere").executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    ShepherdEggs.wall(player.serverLevel(), List.of(player), List.of(player.blockPosition()), ReturnedHearthSavedData.get(c.getSource().getServer()).maeveErased());
                    return 1;
                }))
                .then(Commands.literal("affinity").then(Commands.argument("value", IntegerArgumentType.integer(-1000, 1000)).executes(c -> {
                    var data = ShepherdSavedData.get(c.getSource().getServer());
                    data.affinity(IntegerArgumentType.getInteger(c, "value"));
                    FrozenDawnCommandOutput.item(c.getSource(), "affinity=" + data.affinity() + " tier=" + data.tier());
                    return 1;
                })));
    }
}
