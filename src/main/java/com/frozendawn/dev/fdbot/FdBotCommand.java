package com.frozendawn.dev.fdbot;

import com.frozendawn.FrozenDawn;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Brigadier tree for {@code /fdbot}. Actions live in {@link FdBotActions}. */
final class FdBotCommand {
    private FdBotCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("fdbot")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx, "help", player -> FdBotActions.help()))
                .then(gather())
                .then(craft())
                .then(place())
                .then(use())
                .then(goTo())
                .then(face())
                .then(Commands.literal("status")
                        .executes(ctx -> run(ctx, "status", FdBotActions::status)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gather() {
        return Commands.literal("gather")
                .then(idArgument("target", Registries.BLOCK, blockSuggestions())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, FdBotActions.MAX_COUNT))
                                .executes(ctx -> gather(ctx, FdBotActions.DEFAULT_GATHER_RADIUS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, FdBotActions.MAX_RADIUS))
                                        .executes(ctx -> gather(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int gather(CommandContext<CommandSourceStack> ctx, int radius) {
        String raw = idToken(ctx, "target");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        String args = "gather " + raw + " " + count + " " + radius;
        return run(ctx, args, player -> {
            FdBotIds.Result<FdBotIds.BlockMatch> parsed = FdBotIds.block(player.registryAccess(), raw);
            if (!parsed.ok()) {
                return FdBotActions.Outcome.fail(parsed.error());
            }
            return FdBotActions.gather(player, parsed.value(), count, radius);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> craft() {
        return Commands.literal("craft")
                .then(idArgument("item", Registries.ITEM, itemSuggestions())
                        .executes(ctx -> craft(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, FdBotActions.MAX_COUNT))
                                .executes(ctx -> craft(ctx, IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int craft(CommandContext<CommandSourceStack> ctx, int count) {
        String raw = idToken(ctx, "item");
        return run(ctx, "craft " + raw + " " + count, player -> {
            FdBotIds.Result<Item> parsed = FdBotIds.item(player.registryAccess(), raw);
            if (!parsed.ok()) {
                return FdBotActions.Outcome.fail(parsed.error());
            }
            return FdBotActions.craft(player, parsed.value(), count);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> place() {
        return Commands.literal("place")
                .then(idArgument("item", Registries.ITEM, itemSuggestions())
                        .executes(ctx -> place(ctx, "front", null))
                        .then(Commands.literal("here").executes(ctx -> place(ctx, "here", null)))
                        .then(Commands.literal("front").executes(ctx -> place(ctx, "front", null)))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> place(ctx, "pos", BlockPosArgument.getBlockPos(ctx, "pos")))));
    }

    private static int place(CommandContext<CommandSourceStack> ctx, String where, BlockPos pos)
            throws CommandSyntaxException {
        String raw = idToken(ctx, "item");
        String args = pos == null ? "place " + raw + " " + where : "place " + raw + " " + pos.toShortString();
        return run(ctx, args, player -> {
            FdBotIds.Result<Item> parsed = FdBotIds.item(player.registryAccess(), raw);
            if (!parsed.ok()) {
                return FdBotActions.Outcome.fail(parsed.error());
            }
            BlockPos target = pos == null ? FdBotActions.feetOrFront(player, where) : pos;
            return FdBotActions.place(player, parsed.value(), target);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> use() {
        return Commands.literal("use")
                .then(Commands.literal("here").executes(ctx -> use(ctx, "here", null)))
                .then(Commands.literal("front").executes(ctx -> use(ctx, "front", null)))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> use(ctx, "pos", BlockPosArgument.getBlockPos(ctx, "pos"))));
    }

    private static int use(CommandContext<CommandSourceStack> ctx, String where, BlockPos pos)
            throws CommandSyntaxException {
        String args = pos == null ? "use " + where : "use " + pos.toShortString();
        return run(ctx, args, player -> FdBotActions.use(
                player, pos == null ? FdBotActions.feetOrFront(player, where) : pos));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> goTo() {
        return Commands.literal("goto")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> {
                            BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
                            return run(ctx, "goto " + pos.toShortString(), player -> FdBotActions.goTo(player, pos));
                        }))
                .then(Commands.literal("nearest")
                        .then(idArgument("target", Registries.BLOCK, blockSuggestions())
                                .executes(ctx -> nearest(ctx, true))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> face() {
        return Commands.literal("face")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> {
                            BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
                            return run(ctx, "face " + pos.toShortString(), player -> FdBotActions.face(player, pos));
                        }))
                .then(Commands.literal("nearest")
                        .then(idArgument("target", Registries.BLOCK, blockSuggestions())
                                .executes(ctx -> nearest(ctx, false))));
    }

    private static int nearest(CommandContext<CommandSourceStack> ctx, boolean teleport) {
        String raw = idToken(ctx, "target");
        String args = (teleport ? "goto nearest " : "face nearest ") + raw;
        return run(ctx, args, player -> {
            FdBotIds.Result<FdBotIds.BlockMatch> parsed = FdBotIds.block(player.registryAccess(), raw);
            if (!parsed.ok()) {
                return FdBotActions.Outcome.fail(parsed.error());
            }
            return teleport
                    ? FdBotActions.goToNearest(player, parsed.value())
                    : FdBotActions.faceNearest(player, parsed.value());
        });
    }

    @FunctionalInterface
    private interface Action {
        FdBotActions.Outcome run(ServerPlayer player) throws CommandSyntaxException;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String args, Action action) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException ex) {
            String result = "must be run by a player";
            FdBotAudit.logConsole(args, result);
            source.sendFailure(Component.literal("FDBOT " + result));
            return 0;
        }
        FdBotAudit.mark(player.getUUID());
        FdBotActions.Outcome outcome;
        try {
            outcome = action.run(player);
        } catch (CommandSyntaxException ex) {
            outcome = FdBotActions.Outcome.fail(ex.getMessage());
        } catch (RuntimeException ex) {
            FrozenDawn.LOGGER.warn("[FDBOT] internal error during {}", args, ex);
            outcome = FdBotActions.Outcome.fail("internal error: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
        FdBotAudit.log(player, args, outcome.success(), outcome.message());
        String line = "FDBOT " + outcome.message();
        if (outcome.success()) {
            source.sendSuccess(() -> Component.literal(line), false);
            return Math.max(1, outcome.count());
        }
        source.sendFailure(Component.literal(line));
        return 0;
    }

    private static SuggestionProvider<CommandSourceStack> blockSuggestions() {
        return (context, builder) -> {
            Registry<Block> registry = context.getSource().registryAccess().registryOrThrow(Registries.BLOCK);
            List<String> options = new ArrayList<>();
            for (ResourceLocation id : registry.keySet()) {
                options.add(id.toString());
            }
            registry.getTagNames().forEach(tag -> options.add("#" + tag.location()));
            return SharedSuggestionProvider.suggest(options, builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> itemSuggestions() {
        return (context, builder) -> {
            Registry<Item> registry = context.getSource().registryAccess().registryOrThrow(Registries.ITEM);
            List<String> options = new ArrayList<>();
            for (ResourceLocation id : registry.keySet()) {
                options.add(id.toString());
            }
            return SharedSuggestionProvider.suggest(options, builder);
        };
    }

    /**
     * Vanilla resource-or-tag argument. It parses {@code minecraft:oak_log} and {@code #minecraft:logs}
     * and is already in the command-argument registry, so {@code ClientboundCommandsPacket} can send
     * the tree. A custom argument type is not: Open to LAN crashes in {@code ArgumentTypeInfos.byClass}
     * before any command runs. {@code StringArgumentType.word()} cannot take its place, because that
     * reader stops at {@code :} and {@code #}, and a greedy string would swallow the following count
     * or coordinates.
     */
    private static <T> RequiredArgumentBuilder<CommandSourceStack, ResourceOrTagKeyArgument.Result<T>> idArgument(
            String name, ResourceKey<Registry<T>> registry, SuggestionProvider<CommandSourceStack> suggestions) {
        return Commands.argument(name, ResourceOrTagKeyArgument.resourceOrTagKey(registry)).suggests(suggestions);
    }

    private static String idToken(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, ResourceOrTagKeyArgument.Result.class).asPrintable();
    }
}
