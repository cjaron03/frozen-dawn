package com.frozendawn.command;

import com.frozendawn.data.ThaevenLoreSavedData;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthSelectionPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.lore.ThaevenLoreManager;
import com.frozendawn.lore.ThaevenRecordId;
import com.frozendawn.lore.ThaevenSemanticKey;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Focused debug surface for player archives and world semantics. */
final class ThaevenLoreCommand {
    private ThaevenLoreCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("lore")
                .executes(ThaevenLoreCommand::status)
                .then(Commands.literal("status")
                        .executes(ThaevenLoreCommand::status))
                .then(Commands.literal("grant")
                        .then(Commands.argument("record", StringArgumentType.word())
                                .executes(context -> grant(context,
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> grant(context,
                                                EntityArgument.getPlayer(
                                                        context, "player"))))))
                .then(Commands.literal("spawn-carrier")
                        .then(Commands.argument("record", StringArgumentType.word())
                                .executes(ThaevenLoreCommand::spawnCarrier)))
                .then(Commands.literal("unlock-semantic")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(ThaevenLoreCommand::unlockSemantic)))
                .then(Commands.literal("assemble-record3")
                        .executes(ThaevenLoreCommand::assembleRecordThree))
                .then(Commands.literal("reset-player")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ThaevenLoreCommand::resetPlayer)))
                .then(Commands.literal("debug-reset-world-semantic")
                        .executes(ThaevenLoreCommand::resetWorldSemantics))
                .then(Commands.literal("explain")
                        .then(Commands.argument("record", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ThaevenLoreCommand::explain))));
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(
                context.getSource().getServer());
        ThaevenLoreSavedData.ArchiveSnapshot snapshot =
                data.snapshot(player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal(
                "Lore archive " + player.getGameProfile().getName()
                        + " | records=" + Long.bitCount(snapshot.discoveredMask())
                        + "/" + ThaevenRecordId.values().length
                        + " | recipe=" + snapshot.recipeDiscovered()
                        + " | architectRevision="
                        + snapshot.architectLidRevision()), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "Heart Scar: " + data.heartScarAnchor()
                        .map(anchor -> anchor.dimension().location() + " "
                                + formatPos(anchor.pos()))
                        .orElse("unresolved")), false);
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> context,
                             ServerPlayer player) {
        ThaevenRecordId record = recordArgument(context);
        if (record == null) {
            return 0;
        }
        boolean changed = ThaevenLoreManager.grantRecord(player, record);
        context.getSource().sendSuccess(() -> Component.literal(
                (changed ? "Granted " : "Already discovered ")
                        + record.serializedName() + " for "
                        + player.getGameProfile().getName()), false);
        return 1;
    }

    private static int spawnCarrier(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ThaevenRecordId record = recordArgument(context);
        if (record == null) {
            return 0;
        }
        ItemStack carrier = switch (record) {
            case THE_PASSAGE -> new ItemStack(ModItems.HUMAN_CARRIER.get());
            case PATTERN_RESIDUE ->
                    new ItemStack(ModItems.PATTERN_RESIDUE_RECORD.get());
            case THE_HEART_BENEATH ->
                    new ItemStack(ModItems.ACCRETED_REMNANT.get());
            default -> ItemStack.EMPTY;
        };
        if (!carrier.isEmpty()) {
            if (!player.getInventory().add(carrier)) {
                player.drop(carrier, false);
            }
        } else {
            BlockPos pos = player.blockPosition();
            var block = switch (record) {
                case VEL_AN -> ModBlocks.VEL_AN_RELIC.get();
                case THE_FIRST_CROSSING -> ModBlocks.VEL_AN_MEMORY_WALL.get();
                case THE_UNTHREADING -> ModBlocks.UNTHREADING_VESSEL.get();
                default -> throw new IllegalStateException();
            };
            player.serverLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned carrier for " + record.serializedName()), false);
        return 1;
    }

    private static int unlockSemantic(CommandContext<CommandSourceStack> context) {
        String keyName = StringArgumentType.getString(context, "key");
        ThaevenSemanticKey key;
        try {
            key = ThaevenSemanticKey.valueOf(keyName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(
                    "Unknown semantic key: " + keyName));
            return 0;
        }
        ThaevenLoreManager.unlockSemantic(context.getSource().getServer(), key);
        context.getSource().sendSuccess(() -> Component.literal(
                "Semantic key active: " + key), true);
        return 1;
    }

    private static int assembleRecordThree(
            CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack output = new ItemStack(ModItems.PATTERN_RESIDUE_RECORD.get());
        if (!player.getInventory().add(output)) {
            player.drop(output, false);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Created Record III carrier without modifying archive state"), false);
        return 1;
    }

    private static int resetPlayer(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ThaevenLoreSavedData.get(context.getSource().getServer())
                .resetPlayer(player.getUUID());
        ThaevenLoreManager.sync(player);
        context.getSource().sendSuccess(() -> Component.literal(
                "[debug] Reset lore archive for "
                        + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int resetWorldSemantics(
            CommandContext<CommandSourceStack> context) {
        ThaevenLoreSavedData.get(context.getSource().getServer())
                .resetSemanticsForDebug();
        for (ServerPlayer player : context.getSource().getServer()
                .getPlayerList().getPlayers()) {
            ThaevenLoreManager.sync(player);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "[debug] Reset all world semantic revisions"), true);
        return 1;
    }

    private static int explain(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ThaevenRecordId record = recordArgument(context);
        if (record == null) {
            return 0;
        }
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(
                context.getSource().getServer());
        ThaevenLoreSavedData.ArchiveSnapshot snapshot =
                data.snapshot(player.getUUID());
        int seen = snapshot.seenRevisions()[record.ordinal()];
        int current = data.currentRevision(record);
        context.getSource().sendSuccess(() -> Component.literal(
                record.serializedName().toUpperCase(java.util.Locale.ROOT)
                        + " | discovered=" + data.hasRecord(player.getUUID(), record)
                        + " | seenRevision=" + seen
                        + " | currentRevision=" + current), false);
        if (record == ThaevenRecordId.THE_PASSAGE) {
            boolean permanentDefeat = ReturnedHearthSavedData.get(
                    context.getSource().getServer())
                    .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                    .map(ReturnedHearthSavedData.HearthRecord::decoherenceGranted)
                    .orElse(false);
            context.getSource().sendSuccess(() -> Component.literal(
                    "permanentMasterDefeat=" + permanentDefeat
                            + " | ARCHITECT_LID_REVEAL="
                            + data.semanticRevision(
                            ThaevenSemanticKey.ARCHITECT_LID_REVEAL)), false);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "carrier=" + carrierState(data, record, player)
                        + " | heartScar=" + data.heartScarAnchor()
                        .map(anchor -> anchor.dimension().location() + " "
                                + formatPos(anchor.pos()))
                        .orElse("unresolved")), false);
        return 1;
    }

    private static String carrierState(
            ThaevenLoreSavedData data, ThaevenRecordId record,
            ServerPlayer player) {
        return switch (record) {
            case VEL_AN -> data.velAnRelicPositions().isEmpty()
                    ? "unbound"
                    : data.velAnRelicPositions().stream()
                    .map(ThaevenLoreCommand::formatPos)
                    .collect(java.util.stream.Collectors.joining(", "));
            case THE_PASSAGE -> "reconciled="
                    + data.humanCarrierReconciled() + "; crate="
                    + data.humanCarrierCratePos()
                    .map(ThaevenLoreCommand::formatPos).orElse("unbound");
            case PATTERN_RESIDUE -> "fragments=" + fragmentCount(player)
                    + "/4; assembles at Heart Scar";
            case THE_HEART_BENEATH -> "heartExposed=" + heartExposed(player)
                    + "; guaranteed eligible Undone Architect drop";
            case THE_FIRST_CROSSING -> data.firstCrossingVesselPos()
                    .map(ThaevenLoreCommand::formatPos).orElse("unbound");
            case THE_UNTHREADING -> data.unthreadingVesselPos()
                    .map(ThaevenLoreCommand::formatPos).orElse("unbound");
        };
    }

    private static int fragmentCount(ServerPlayer player) {
        int count = 0;
        count += player.getInventory().contains(new ItemStack(
                ModItems.RIMEBOUND_PATTERN_FRAGMENT.get())) ? 1 : 0;
        count += player.getInventory().contains(new ItemStack(
                ModItems.RESONANT_PATTERN_FRAGMENT.get())) ? 1 : 0;
        count += player.getInventory().contains(new ItemStack(
                ModItems.REMNANT_PATTERN_FRAGMENT.get())) ? 1 : 0;
        count += player.getInventory().contains(new ItemStack(
                ModItems.FROSTWRITHE_PATTERN_FRAGMENT.get())) ? 1 : 0;
        return count;
    }

    private static boolean heartExposed(ServerPlayer player) {
        return ReturnedHearthSavedData.get(player.getServer())
                .hearth(HearthSelectionPolicy.HearthType.MAJOR)
                .map(hearth -> hearth.heartLive()
                        || hearth.heartCollapseStartGameTime() >= 0L
                        || hearth.heartCollapseComplete())
                .orElse(false);
    }

    private static ThaevenRecordId recordArgument(
            CommandContext<CommandSourceStack> context) {
        String value = StringArgumentType.getString(context, "record");
        ThaevenRecordId record = ThaevenRecordId.parse(value).orElse(null);
        if (record == null) {
            context.getSource().sendFailure(Component.literal(
                    "Unknown record: " + value));
        }
        return record;
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", "
                + pos.getZ() + ")";
    }
}
