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
                .executes(AggregateCommand::status)
                .then(Commands.literal("status").executes(AggregateCommand::status))
                .then(Commands.literal("pressure")
                        .executes(AggregateCommand::status)
                        .then(Commands.argument("value",
                                        DoubleArgumentType.doubleArg(0.0D, 100_000.0D))
                                .executes(AggregateCommand::setPressure))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value",
                                                DoubleArgumentType.doubleArg(0.0D, 100_000.0D))
                                        .executes(AggregateCommand::setPressure)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("value",
                                                DoubleArgumentType.doubleArg(0.0D, 100_000.0D))
                                        .executes(AggregateCommand::addPressure))))
                .then(Commands.literal("addpressure")
                        .then(Commands.argument("value",
                                        DoubleArgumentType.doubleArg(0.0D, 100_000.0D))
                                .executes(AggregateCommand::addPressure)))
                .then(Commands.literal("stage")
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .executes(AggregateCommand::setStage)))
                .then(Commands.literal("spawn").executes(AggregateCommand::spawn))
                .then(Commands.literal("trait")
                        .then(Commands.argument("lineage", StringArgumentType.word())
                                .then(Commands.argument("value",
                                                DoubleArgumentType.doubleArg(0.0D, 100_000.0D))
                                        .executes(AggregateCommand::setTrait))))
                .then(Commands.literal("visual")
                        .then(Commands.argument("action", StringArgumentType.word())
                                .executes(AggregateCommand::forceAction)))
                .then(Commands.literal("animation")
                        .then(Commands.argument("action", StringArgumentType.word())
                                .executes(AggregateCommand::forceAction)))
                .then(Commands.literal("resolve").executes(AggregateCommand::resolve))
                .then(Commands.literal("stillpoint")
                        .executes(AggregateCommand::stillpointStatus)
                        .then(Commands.literal("status")
                                .executes(AggregateCommand::stillpointStatus))
                        .then(Commands.literal("debug-place")
                                .executes(AggregateCommand::stillpointPlace))
                        .then(Commands.literal("debug-activate")
                                .executes(AggregateCommand::stillpointActivate))
                        .then(Commands.literal("debug-pulse")
                                .executes(AggregateCommand::stillpointPulse))
                        .then(Commands.literal("debug-reset")
                                .executes(AggregateCommand::stillpointReset)))
                .then(Commands.literal("reset").executes(AggregateCommand::reset));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        String traits = data.lineagePressure().entrySet().stream()
                .map(entry -> entry.getKey().name().toLowerCase(Locale.ROOT)
                        + "=" + String.format(Locale.ROOT, "%.2f", entry.getValue()))
                .reduce((left, right) -> left + ", " + right).orElse("none");
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Aggregate: stage=%s pressure=%.2f fight=%s resolved=%s health=%.1f/%.1f anchor=%s",
                data.stage().name().toLowerCase(Locale.ROOT), data.pressure(),
                data.fightStarted(), data.resolved(), data.fightHealth(), data.fightMaxHealth(),
                data.ossuaryPos().map(BlockPos::toShortString).orElse("unselected"))), false);
        context.getSource().sendSuccess(() -> Component.literal("Lineages: " + traits), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "Discharge: scars=" + data.dischargeScars()
                        + " reinforcements=" + data.reinforcements().size()), false);
        return 1;
    }

    private static int setPressure(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        data.debugSetPressure(DoubleArgumentType.getDouble(context, "value"));
        return status(context);
    }

    private static int addPressure(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        double amount = DoubleArgumentType.getDouble(context, "value");
        data.addPressure(new AggregatePressurePolicy.Contribution(
                amount, AggregateLineage.NORMAL));
        return status(context);
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
            return status(context);
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
            data(context).debugSetLineage(lineage,
                    DoubleArgumentType.getDouble(context, "value"));
            return status(context);
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

    private static int stillpointStatus(CommandContext<CommandSourceStack> context) {
        AggregateSavedData data = data(context);
        long elapsed = data.stillpointChargeStart() < 0L ? 0L
                : Math.max(0L, context.getSource().getLevel().getGameTime()
                - data.stillpointChargeStart());
        context.getSource().sendSuccess(() -> Component.literal(
                "Stillpoint: present=" + data.stillpointPos().isPresent()
                        + " active=" + data.stillpointActive()
                        + " activationProcessed=" + data.stillpointActivationProcessed()
                        + " charge=" + Math.min(
                        com.frozendawn.aggregate.StillpointFieldManager.CHARGE_TICKS, elapsed)
                        + "/" + com.frozendawn.aggregate.StillpointFieldManager.CHARGE_TICKS
                        + " pos=" + data.stillpointPos().map(BlockPos::toShortString)
                        .orElse("none")), false);
        return 1;
    }

    private static int stillpointPlace(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition().relative(player.getDirection(), 3);
            level.setBlock(pos, ModBlocks.INERT_CONVERGENCE_CORE.get()
                    .defaultBlockState(), 3);
            AggregateSavedData.get(level.getServer()).armStillpoint(
                    level, pos, player.getUUID());
            return stillpointStatus(context);
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
        return stillpointStatus(context);
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
        return stillpointStatus(context);
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
