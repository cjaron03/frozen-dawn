package com.frozendawn.command;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.flora.ColdFloraSpawner;
import com.frozendawn.flora.ColdFloraSpawner.Attempt;
import com.frozendawn.gpr.PlumbLinePolicy;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Development only: runs the natural Cold Flora spawn rules near the player right away, repeating up to N tries until
 * one grows, and reports where it grew or why each try failed. Phase is reported but not required.
 */
final class FloraDebugCommand {
    private FloraDebugCommand() { }
    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("flora").requires(source -> source.hasPermission(2))
                .then(kind("grove", "Rimewood grove", ColdFloraSpawner::tryGrove))
                .then(kind("frostcap", "Frostcap", ColdFloraSpawner::tryFrostcap));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kind(String name, String label, BiFunction<ServerLevel, BlockPos, Attempt> spawn) {
        return Commands.literal(name).executes(c -> run(c, label, spawn, 20))
                .then(Commands.argument("tries", IntegerArgumentType.integer(1, 200)).executes(c -> run(c, label, spawn, IntegerArgumentType.getInteger(c, "tries"))));
    }

    private static int run(CommandContext<CommandSourceStack> c, String label, BiFunction<ServerLevel, BlockPos, Attempt> spawn, int tries) throws CommandSyntaxException {
        var source = c.getSource();
        var player = source.getPlayerOrException();
        if (player.level().dimension() != Level.OVERWORLD) { FrozenDawnCommandOutput.item(source, "Natural Cold Flora only grows in the overworld."); return 0; }
        var level = (ServerLevel) player.level();
        FrozenDawnCommandOutput.heading(source, label + " spawn rules");
        int phase = ApocalypseState.get(source.getServer()).getPhase();
        if (phase < 3) FrozenDawnCommandOutput.item(source, "Phase " + phase + ": natural spawning starts in Phase 3. Running the other rules anyway.");
        var failures = new LinkedHashMap<String, Integer>();
        for (int i = 1; i <= tries; i++) {
            var attempt = spawn.apply(level, player.blockPosition());
            if (attempt.grew()) {
                var at = attempt.at();
                double dx = at.getX() - player.getX(), dz = at.getZ() - player.getZ();
                FrozenDawnCommandOutput.item(source, "Grew " + attempt.placed() + (label.startsWith("Rimewood") ? " trees" : " caps") + " at "
                        + at.toShortString() + ", " + Math.round(Math.hypot(dx, dz)) + " blocks " + COMPASS[PlumbLinePolicy.compass(dx, dz)] + " (try " + i + " of " + tries + ")");
                report(source, failures);
                return 1;
            }
            failures.merge(attempt.failure(), 1, Integer::sum);
        }
        FrozenDawnCommandOutput.item(source, "Nothing grew in " + tries + " tries.");
        report(source, failures);
        return 0;
    }

    private static void report(CommandSourceStack source, LinkedHashMap<String, Integer> failures) {
        failures.forEach((why, n) -> FrozenDawnCommandOutput.detail(source, "Failed x" + n, why));
    }
}
