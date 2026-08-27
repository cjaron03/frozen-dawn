package com.frozendawn.command;

import com.frozendawn.aggregate.AggregateAction;
import com.frozendawn.aggregate.AggregateEncounterManager;
import com.frozendawn.aggregate.AggregateLineage;
import com.frozendawn.aggregate.AggregateReinforcementManager;
import com.frozendawn.aggregate.AggregateSavedData;
import com.frozendawn.aggregate.AggregateStage;
import com.frozendawn.aggregate.AggregatePressurePolicy;
import com.frozendawn.aggregate.AggregateOssuaryBuilder;
import com.frozendawn.entity.AggregateEntity;
import com.frozendawn.entity.AggregateFragmentEntity;
import com.frozendawn.init.ModBlocks;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

final class AggregateCommand {
    private AggregateCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("aggregate")
                .executes(context -> status(context, false))
                .then(Commands.literal("status")
                        .executes(context -> status(context, false))
                        .then(Commands.literal("verbose")
                                .executes(context -> status(context, true))))
                .then(Commands.literal("pressure")
                        .executes(context -> status(context, false))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value",
                                                DoubleArgumentType.doubleArg(0.0D, 100_000.0D))
                                        .executes(AggregateCommand::setPressure)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("value",
                                                DoubleArgumentType.doubleArg(0.0D, 100_000.0D))
                                        .executes(AggregateCommand::addPressure))))
                .then(Commands.literal("stage")
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .suggests(FrozenDawnCommandSuggestions.enums(AggregateStage.class))
                                .executes(AggregateCommand::setStage)))
                .then(Commands.literal("spawn").executes(AggregateCommand::spawn))
                .then(Commands.literal("trait")
                        .then(Commands.argument("lineage", StringArgumentType.word())
                                .suggests(FrozenDawnCommandSuggestions.enums(AggregateLineage.class))
                                .then(Commands.argument("value",
                                                DoubleArgumentType.doubleArg(0.0D, 100_000.0D))
                                        .executes(AggregateCommand::setTrait))))
                .then(Commands.literal("force")
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests(FrozenDawnCommandSuggestions.enums(AggregateAction.class))
                                .executes(AggregateCommand::forceAction)))
                .then(Commands.literal("resolve")
                        .then(Commands.literal("confirm")
                                .executes(AggregateCommand::resolve)))
                .then(Commands.literal("stillpoint")
                        .executes(context -> stillpointStatus(context, false))
                        .then(Commands.literal("status")
                                .executes(context -> stillpointStatus(context, false))
                                .then(Commands.literal("verbose")
                                        .executes(context -> stillpointStatus(context, true))))
                        .then(Commands.literal("place")
                                .executes(AggregateCommand::stillpointPlace))
                        .then(Commands.literal("activate")
                                .executes(AggregateCommand::stillpointActivate))
                        .then(Commands.literal("pulse")
                                .executes(AggregateCommand::stillpointPulse))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("confirm")
                                        .executes(AggregateCommand::stillpointReset))))
                .then(Commands.literal("reset")
                        .then(Commands.literal("confirm")
                                .executes(AggregateCommand::reset)));
    }

    private static int status(CommandContext<CommandSourceStack> context, boolean verbose) {
        AggregateSavedData data = data(context);
        String traits = data.lineagePressure().entrySet().stream()
                .map(entry -> entry.getKey().name().toLowerCase(Locale.ROOT)
                        + "=" + String.format(Locale.ROOT, "%.2f", entry.getValue()))
                .reduce((left, right) -> left + ", " + right).orElse("none");
        FrozenDawnCommandOutput.heading(context.getSource(), "Aggregate");
        FrozenDawnCommandOutput.line(context.getSource(), "State",
                data.stage().name().toLowerCase(Locale.ROOT)
                        + " - pressure " + String.format(Locale.ROOT, "%.1f", data.pressure())
                        + (data.resolved() ? " - resolved" : ""));
        FrozenDawnCommandOutput.line(context.getSource(), "Encounter",
                data.fightStarted()
                        ? String.format(Locale.ROOT, "active - %.1f/%.1f HP",
                        data.fightHealth(), data.fightMaxHealth())
                        : "inactive");
        FrozenDawnCommandOutput.line(context.getSource(), "Ossuary",
                data.ossuaryPos().map(BlockPos::toShortString).orElse("unselected"));
        if (!verbose) {
            FrozenDawnCommandOutput.hint(context.getSource(), "/fd aggregate status verbose");
            return 1;
        }
        FrozenDawnCommandOutput.detail(context.getSource(), "Lineages", traits);
        FrozenDawnCommandOutput.detail(context.getSource(), "Discharge",
                "scars=" + data.dischargeScars()
                        + " reinforcements=" + data.reinforcements().size());
        return 1;
    }

    private static int setPressure(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        double value = DoubleArgumentType.getDouble(context, "value");
        data.debugSetPressure(value);
        FrozenDawnCommandOutput.success(context.getSource(),
                String.format(Locale.ROOT, "Aggregate pressure set to %.1f.", value), false);
        return 1;
    }

    private static int addPressure(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        double amount = DoubleArgumentType.getDouble(context, "value");
        data.addPressure(new AggregatePressurePolicy.Contribution(
                amount, AggregateLineage.NORMAL));
        FrozenDawnCommandOutput.success(context.getSource(),
                String.format(Locale.ROOT, "Added %.1f Aggregate pressure (now %.1f).",
                        amount, data.pressure()), false);
        return 1;
    }

    private static int setStage(CommandContext<CommandSourceStack> context) {
        try {
            AggregateStage stage = AggregateStage.valueOf(
                    StringArgumentType.getString(context, "stage").toUpperCase(Locale.ROOT));
            ServerLevel level = context.getSource().getLevel();
            AggregateSavedData data = data(context);
            if (stage.ordinal() >= AggregateStage.DEPOSIT.ordinal()
                    && data.ossuaryPos().isEmpty()) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                BlockPos anchor = player.blockPosition();
                data.setOssuary(anchor, level.getSeed() ^ anchor.asLong());
            }
            data.debugSetStage(stage, Math.floorDiv(level.getDayTime(), 24_000L));
            AggregateOssuaryBuilder.buildStage(level, data, stage);
            com.frozendawn.aggregate.AggregateGrowthManager.playStageDiagnostic(
                    level, data, stage);
            FrozenDawnCommandOutput.success(context.getSource(),
                    "Aggregate stage set to " + stage.name().toLowerCase(Locale.ROOT) + ".",
                    false);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(
                    "Unknown Aggregate stage. Use dormant, residue, deposit, ossuary, gestation, awakening_eligible, active, or resolved."));
            return 0;
        }
    }

    private static int spawn(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        AggregateSavedData data = data(context);
        AggregateReinforcementManager.cleanupLoaded(level, data);
        AggregateEncounterManager.cleanupTemporary(level, data);
        data.activeAggregateId().map(level::getEntity)
                .filter(AggregateEntity.class::isInstance)
                .map(AggregateEntity.class::cast)
                .ifPresent(AggregateEntity::discard);
        AggregateEntity stale = nearest(level, player.getX(), player.getY(), player.getZ());
        if (stale != null) stale.discard();
        data.debugRearmFight();
        if (data.ossuaryPos().isEmpty()) {
            BlockPos anchor = BlockPos.containing(player.position().add(
                    player.getLookAngle().multiply(10.0D, 0.0D, 10.0D)));
            data.debugRelocateOssuary(anchor, level.getSeed() ^ anchor.asLong());
        }
        data.debugSetPressure(Math.max(400.0D, data.pressure()));
        if (AggregatePressurePolicy.lockTraits(
                data.lineagePressure(), data.ossuarySeed()).size() < 2) {
            data.debugSetLineage(AggregateLineage.RIMEBOUND, 160.0D);
            data.debugSetLineage(AggregateLineage.RESONANT, 130.0D);
            data.debugSetLineage(AggregateLineage.REMNANT, 110.0D);
        }
        data.debugSetStage(AggregateStage.AWAKENING_ELIGIBLE,
                Math.floorDiv(level.getDayTime(), 24_000L));
        AggregateEncounterManager.awaken(level, data, player);
        if (data.activeAggregateId().isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "Could not force Aggregate awakening in the loaded area."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Forced Aggregate awakening."), true);
        return 1;
    }

    private static int setTrait(CommandContext<CommandSourceStack> context) {
        try {
            AggregateLineage lineage = AggregateLineage.valueOf(
                    StringArgumentType.getString(context, "lineage").toUpperCase(Locale.ROOT));
            double value = DoubleArgumentType.getDouble(context, "value");
            data(context).debugSetLineage(lineage, value);
            FrozenDawnCommandOutput.success(context.getSource(),
                    String.format(Locale.ROOT, "%s lineage set to %.1f.",
                            lineage.name().toLowerCase(Locale.ROOT), value), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Unknown Aggregate lineage."));
            return 0;
        }
    }

    private static int forceAction(CommandContext<CommandSourceStack> context) {
        try {
            AggregateAction action = AggregateAction.valueOf(
                    StringArgumentType.getString(context, "action").toUpperCase(Locale.ROOT));
            AggregateEntity aggregate = nearest(context.getSource().getLevel(),
                    context.getSource().getPosition().x,
                    context.getSource().getPosition().y,
                    context.getSource().getPosition().z);
            if (aggregate == null) {
                context.getSource().sendFailure(Component.literal("No loaded Aggregate nearby."));
                return 0;
            }
            if (!aggregate.debugForceAction(action)) {
                context.getSource().sendFailure(Component.literal(
                        action == AggregateAction.CONVERGENCE_DISCHARGE
                                ? "Could not start convergence discharge: no valid loaded landing positions."
                                : "Could not start Aggregate action "
                                        + action.name().toLowerCase(Locale.ROOT) + "."));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal(
                    "Forced Aggregate action " + action.name().toLowerCase(Locale.ROOT) + "."), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Unknown Aggregate action."));
            return 0;
        }
    }

    private static int resolve(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        AggregateEntity aggregate = nearest(level, context.getSource().getPosition().x,
                context.getSource().getPosition().y, context.getSource().getPosition().z);
        if (aggregate == null) {
            context.getSource().sendFailure(Component.literal("No loaded Aggregate nearby."));
            return 0;
        }
        AggregateEncounterManager.resolve(level, aggregate);
        aggregate.discard();
        return 1;
    }

    private static int stillpointStatus(
            CommandContext<CommandSourceStack> context, boolean verbose) {
        AggregateSavedData data = data(context);
        long elapsed = data.stillpointChargeStart() < 0L ? 0L
                : Math.max(0L, context.getSource().getLevel().getGameTime()
                - data.stillpointChargeStart());
        long charge = Math.min(
                com.frozendawn.aggregate.StillpointFieldManager.CHARGE_TICKS, elapsed);
        FrozenDawnCommandOutput.heading(context.getSource(), "Stillpoint");
        FrozenDawnCommandOutput.line(context.getSource(), "Field",
                data.stillpointActive() ? "active"
                        : data.stillpointPos().isPresent() ? "charging" : "absent");
        FrozenDawnCommandOutput.line(context.getSource(), "Core",
                data.stillpointPos().map(BlockPos::toShortString).orElse("none"));
        if (verbose) {
            FrozenDawnCommandOutput.detail(context.getSource(), "Charge",
                    charge + "/"
                            + com.frozendawn.aggregate.StillpointFieldManager.CHARGE_TICKS
                            + " ticks");
            FrozenDawnCommandOutput.detail(context.getSource(), "Activation processed",
                    data.stillpointActivationProcessed());
        } else {
            FrozenDawnCommandOutput.hint(context.getSource(),
                    "/fd aggregate stillpoint status verbose");
        }
        return 1;
    }

    private static int stillpointPlace(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition().relative(player.getDirection(), 3);
            level.setBlock(pos, ModBlocks.INERT_CONVERGENCE_CORE.get()
                    .defaultBlockState()
                    .setValue(com.frozendawn.block.StillpointCoreBlock.DEPLOYED, true), 3);
            AggregateSavedData.get(level.getServer()).armStillpoint(
                    level, pos, player.getUUID());
            com.frozendawn.aggregate.StillpointFieldManager.announceCharge(level, pos);
            FrozenDawnCommandOutput.success(context.getSource(),
                    "Stillpoint Core placed at " + pos.toShortString() + ".", false);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(
                    "Run this command as a player in a loaded dimension."));
            return 0;
        }
    }

    private static int stillpointActivate(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        if (data.stillpointPos().isEmpty()) {
            context.getSource().sendFailure(Component.literal("No Stillpoint Core is armed."));
            return 0;
        }
        data.debugActivateStillpoint(context.getSource().getLevel().getGameTime());
        com.frozendawn.aggregate.StillpointFieldManager.tick(context.getSource().getServer());
        FrozenDawnCommandOutput.success(context.getSource(),
                "Stillpoint field activated.", false);
        return 1;
    }

    private static int stillpointPulse(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        BlockPos pos = data.stillpointPos().orElse(null);
        if (pos == null || data.stillpointDimension()
                .filter(context.getSource().getLevel().dimension().location()::equals)
                .isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "No Stillpoint Core is loaded in this dimension."));
            return 0;
        }
        com.frozendawn.aggregate.StillpointFieldManager.broadcastPulse(
                context.getSource().getLevel(), pos.getCenter(), 1.0F);
        return 1;
    }

    private static int stillpointReset(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        BlockPos pos = data.stillpointPos().orElse(null);
        ServerLevel level = data.stillpointDimension()
                .map(id -> context.getSource().getServer().getLevel(
                        ResourceKey.create(Registries.DIMENSION, id)))
                .orElse(null);
        if (pos != null && level != null) {
            if (level.hasChunkAt(pos)
                    && level.getBlockState(pos).is(ModBlocks.INERT_CONVERGENCE_CORE.get())) {
                level.destroyBlock(pos, false);
            } else {
                data.clearStillpoint(level, pos);
            }
        }
        com.frozendawn.aggregate.StillpointFieldManager.syncAll(
                context.getSource().getServer());
        FrozenDawnCommandOutput.success(context.getSource(),
                "Stillpoint authority reset.", true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        AggregateSavedData data = data(context);
        com.frozendawn.aggregate.AggregateReinforcementManager.cleanupLoaded(level, data);
        AggregateEncounterManager.cleanupTemporary(level, data);
        data.activeAggregateId().map(level::getEntity)
                .filter(AggregateEntity.class::isInstance)
                .map(AggregateEntity.class::cast).ifPresent(AggregateEntity::discard);
        data.ossuaryPos().ifPresent(anchor -> level.getEntitiesOfClass(
                AggregateFragmentEntity.class,
                new net.minecraft.world.phys.AABB(anchor).inflate(96.0D),
                entity -> true).forEach(AggregateFragmentEntity::discard));
        data.stillpointPos().ifPresent(pos -> {
            if (data.stillpointDimension().filter(level.dimension().location()::equals).isPresent()
                    && level.isLoaded(pos)
                    && level.getBlockState(pos).is(ModBlocks.INERT_CONVERGENCE_CORE.get())) {
                level.destroyBlock(pos, false);
            }
        });
        for (long packed : data.ossuaryBlocks()) {
            BlockPos pos = BlockPos.of(packed);
            if (level.isLoaded(pos) && (level.getBlockState(pos).is(ModBlocks.AGGREGATE_MASS.get())
                    || level.getBlockState(pos).is(ModBlocks.AGGREGATE_RIB.get())
                    || level.getBlockState(pos).is(ModBlocks.AGGREGATE_RESIDUE.get()))) {
                level.destroyBlock(pos, false);
            }
        }
        data.debugReset();
        context.getSource().sendSuccess(() -> Component.literal(
                "Reset Aggregate authority and loaded owned geometry."), true);
        return 1;
    }

    private static AggregateEntity nearest(ServerLevel level, double x, double y, double z) {
        return level.getEntitiesOfClass(AggregateEntity.class,
                        new net.minecraft.world.phys.AABB(x - 256, y - 128, z - 256,
                                x + 256, y + 128, z + 256), entity -> true)
                .stream().min(java.util.Comparator.comparingDouble(entity ->
                        entity.distanceToSqr(x, y, z))).orElse(null);
    }

    private static AggregateSavedData data(CommandContext<CommandSourceStack> context) {
        return AggregateSavedData.get(context.getSource().getServer());
    }
}
