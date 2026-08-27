package com.frozendawn.world.remnant;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.PostMaeveEvolutionDifficulty;
import com.frozendawn.aggregate.AggregatePressureHandler;
import com.frozendawn.aggregate.AggregateGrowthManager;
import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.data.RemnantLureSavedData;
import com.frozendawn.entity.RemnantEntity;
import com.frozendawn.entity.RemnantPolicy;
import com.frozendawn.entity.RemnantState;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.event.RemnantLureInteractionHandler;
import com.frozendawn.mixin.BlockDisplayAccessor;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import com.frozendawn.world.PostMaeveEncounterDirector;
import com.frozendawn.world.PostMaeveEncounterType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Loaded-only server authority for false refuges and their owned geometry. */
public final class RemnantLureManager {
    private RemnantLureManager() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        tickLoadedLures(level);
        if (level.getGameTime() % RemnantPolicy.CHECK_INTERVAL_TICKS != 0L
                || !PostMaeveWorldState.isUndoneSpawningReleased(level.getServer())) return;
        Set<Long> regions = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            long region = RemnantPolicy.regionKey(player.blockPosition());
            if (!regions.add(region)) continue;
            tryNaturalPlacement(level, player, region);
        }
    }

    private static void tryNaturalPlacement(ServerLevel level, ServerPlayer player, long region) {
        if (StillpointPolicy.isSuppressed(level, player.blockPosition())) return;
        RemnantLureSavedData data = RemnantLureSavedData.get(level.getServer());
        int loadedCount = (int) data.lures().stream().filter(record ->
                record.state() != RemnantState.RESOLVED && level.isLoaded(record.origin())).count();
        if (!RemnantPolicy.canNaturalPlace(PostMaeveWorldState.isErased(level),
                PostMaeveWorldState.isUndoneSpawningReleased(level.getServer()),
                data.unresolvedInRegion(region).isPresent(), loadedCount,
                level.getGameTime(), data.cooldown(region))) return;
        double chance = RemnantPolicy.SPAWN_CHANCE_PER_CHECK
                * PostMaeveEvolutionDifficulty.remnantMultiplier()
                * AggregateGrowthManager.evolvedWeightMultiplier(
                level, player.blockPosition());
        if (!PostMaeveEncounterDirector.rollRegion(level, region,
                PostMaeveEncounterType.REMNANT, chance)) return;

        RemnantLureTemplate.Kind[] kinds = RemnantLureTemplate.Kind.values();
        RemnantLureTemplate.Kind kind = kinds[level.random.nextInt(kinds.length)];
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            int distance = 80 + level.random.nextInt(65);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos origin = alignInsideChunkAtSurface(level, x, z,
                    RemnantLureTemplate.create(kind).radius());
            if (StillpointPolicy.isSuppressed(level, origin)) continue;
            long originRegion = RemnantPolicy.regionKey(origin);
            if (data.unresolvedInRegion(originRegion).isPresent()
                    || level.getGameTime() < data.cooldown(originRegion)) continue;
            if (!RemnantPolicy.hasSpacing(origin, data.lures().stream()
                    .filter(lure -> lure.state() != RemnantState.RESOLVED)
                    .map(RemnantLureSavedData.LureRecord::origin).toList())) continue;
            DummySightEntity sight = new DummySightEntity(level, origin);
            if (level.players().stream().filter(ServerPlayer::isAlive)
                    .anyMatch(observer -> observer.hasLineOfSight(sight))) continue;
            if (place(level, origin, kind, level.random.nextInt(4), originRegion) != null) {
                PostMaeveEncounterDirector.successRegion(level, region,
                        PostMaeveEncounterType.REMNANT);
                FrozenDawn.LOGGER.info(
                        "[Remnant] Placed hidden {} lure for {} at {}",
                        kind.id(), player.getName().getString(), origin.toShortString());
                return;
            }
        }
        PostMaeveEncounterDirector.blockedRegion(level, region,
                PostMaeveEncounterType.REMNANT,
                "no hidden protected shelter footprint");
    }

    public static RemnantLureSavedData.LureRecord place(ServerLevel level, BlockPos origin,
                                                         RemnantLureTemplate.Kind kind,
                                                         int rotation, long region) {
        RemnantLureTemplate template = RemnantLureTemplate.create(kind);
        RemnantPlacementPolicy.Result result = RemnantPlacementPolicy.validate(
                level, origin, template, rotation);
        if (!result.accepted()) return null;
        RemnantLureSavedData data = RemnantLureSavedData.get(level.getServer());
        RemnantLureSavedData.LureRecord record = data.create(region, kind.id(),
                origin, rotation, level.getSeed() ^ origin.asLong());
        populateRecord(record, template, rotation);
        data.changed();
        placeShallowFoundation(level, record, template, rotation);
        carveInterior(level, origin, template);
        for (RemnantLureTemplate.Cell cell : template.cells()) {
            BlockPos world = origin.offset(RemnantLureTemplate.rotate(cell.local(), rotation));
            if (cell.role() == RemnantLureTemplate.Role.ANCHOR
                    || cell.role() == RemnantLureTemplate.Role.TRIGGER) continue;
            BlockState state = switch (cell.role()) {
                case SEAM -> cell.state();
                case PROP -> ModBlocks.REMNANT_PROP.get().defaultBlockState();
                default -> cell.state();
            };
            level.setBlock(world, rotateState(state, rotation), 2);
        }
        record.markShellReconciled();
        seedLoot(level, record);
        BlockPos fold = origin.offset(RemnantLureTemplate.rotate(template.foldPoint(), rotation));
        RemnantEntity entity = spawn(level, fold, record, RemnantState.LURE_READY);
        if (entity == null) {
            beginCollapse(level, record.id());
            return record;
        }
        record.bindEntity(entity.getUUID());
        record.setState(RemnantState.LURE_READY);
        data.changed();
        return record;
    }

    private static void carveInterior(ServerLevel level, BlockPos origin,
                                      RemnantLureTemplate template) {
        for (int x = -template.radius() + 1; x < template.radius(); x++) {
            for (int z = -template.radius() + 1; z < template.radius(); z++) {
                for (int y = 1; y < template.height() - 1; y++) {
                    BlockPos interior = origin.offset(x, y, z);
                    if (!level.getBlockState(interior).isAir()) {
                        level.setBlock(interior, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void placeShallowFoundation(ServerLevel level,
                                               RemnantLureSavedData.LureRecord record,
                                               RemnantLureTemplate template, int rotation) {
        for (int x = -template.radius(); x <= template.radius(); x++) {
            for (int z = -template.radius(); z <= template.radius(); z++) {
                BlockPos local = RemnantLureTemplate.rotate(new BlockPos(x, 0, z), rotation);
                for (int depth = 1; depth <= 2; depth++) {
                    BlockPos foundation = record.origin().offset(
                            local.getX(), -depth, local.getZ());
                    BlockState existing = level.getBlockState(foundation);
                    if (!existing.getFluidState().isEmpty() || !existing.canBeReplaced()) break;
                    level.setBlock(foundation,
                            rotateState(template.floorState(), rotation), 2);
                    if (!record.foundationPositions().contains(foundation)) {
                        record.foundationPositions().add(foundation.immutable());
                    }
                }
            }
        }
    }

    private static void populateRecord(RemnantLureSavedData.LureRecord record,
                                       RemnantLureTemplate template, int rotation) {
        for (RemnantLureTemplate.Cell cell : template.cells()) {
            BlockPos world = record.origin().offset(RemnantLureTemplate.rotate(cell.local(), rotation));
            switch (cell.role()) {
                case TRIGGER, PROP, LOOT -> record.triggers().add(world);
                case SEAM -> record.seams().add(world);
                case ANCHOR -> record.wallAnchors().add(world);
                case OWNED -> record.ownedPositions().add(world);
                case MEMBRANE -> record.membranePositions().add(world);
                case PERMANENT -> record.rubblePositions().add(world);
            }
        }
    }

    private static RemnantEntity spawn(ServerLevel level, BlockPos pos,
                                       RemnantLureSavedData.LureRecord record,
                                       RemnantState state) {
        RemnantEntity entity = ModEntities.REMNANT.get().create(
                level, null, pos, MobSpawnType.EVENT, true, false);
        if (entity == null) return null;
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                record.rotation() * 90.0F, 0.0F);
        entity.bind(record.id(), record.origin(), state);
        if (!level.addFreshEntity(entity)) { entity.discard(); return null; }
        return entity;
    }

    private static void tickLoadedLures(ServerLevel level) {
        RemnantLureSavedData data = RemnantLureSavedData.get(level.getServer());
        boolean touched = false;
        for (RemnantLureSavedData.LureRecord record : data.lures()) {
            if (record.state() == RemnantState.RESOLVED || !level.isLoaded(record.origin())) continue;
            touched = true;
            if (!record.shellReconciled() && reconcileAuthoredShell(level, record)) {
                record.markShellReconciled();
            }
            record.tickState();
            tickFalseRadioTransmission(level, record);
            RemnantLureInteractionHandler.tickFireEscape(level, record);
            Entity entity = record.entityId().map(level::getEntity).orElse(null);
            if (record.state() == RemnantState.DYING && entity instanceof RemnantEntity remnant
                    && !remnant.isDeadOrDying()) {
                beginCollapse(level, record.id());
                entity = null;
            }
            if (entity == null && record.state() != RemnantState.COLLAPSING) {
                if (record.state().isCommitted()) beginCollapse(level, record.id());
                else beginCollapse(level, record.id());
            }
            if (record.state() == RemnantState.LURE_READY || record.state() == RemnantState.OBSERVING) {
                tickObservation(level, record);
            } else if (record.state() == RemnantState.COMMITTED) {
                startSealing(level, record);
            } else if (record.state() == RemnantState.SEALING) {
                tickSealing(level, record);
            } else if (record.state() == RemnantState.HUNTING) {
                tickAuthoredTricks(level, record);
            } else if (record.state() == RemnantState.COLLAPSING) {
                tickCollapse(level, data, record);
            }
        }
        if (touched) data.changed();
    }

    /**
     * One-time migration for lures created while reveal geometry removed real wall cells.
     * It only fills missing authored shell positions and never overwrites later solid edits.
     */
    private static boolean reconcileAuthoredShell(ServerLevel level,
                                                   RemnantLureSavedData.LureRecord record) {
        if (record.state() == RemnantState.COLLAPSING
                || record.state() == RemnantState.RESOLVED) return true;
        RemnantLureTemplate template = RemnantLureTemplate.create(
                RemnantLureTemplate.Kind.byId(record.templateId()));
        boolean complete = true;
        for (RemnantLureTemplate.Cell cell : template.cells()) {
            if (cell.role() != RemnantLureTemplate.Role.PERMANENT
                    && cell.role() != RemnantLureTemplate.Role.OWNED
                    && cell.role() != RemnantLureTemplate.Role.SEAM
                    && cell.role() != RemnantLureTemplate.Role.MEMBRANE) continue;
            BlockPos world = record.origin().offset(
                    RemnantLureTemplate.rotate(cell.local(), record.rotation()));
            if (!level.isLoaded(world)) {
                complete = false;
                continue;
            }
            BlockState actual = level.getBlockState(world);
            if (!actual.isAir() && !actual.canBeReplaced()) continue;
            if (!level.getEntities(null, new AABB(world)).isEmpty()) {
                complete = false;
                continue;
            }
            BlockState expected = expectedState(record, template, world);
            if (expected != null && !level.setBlock(world, expected, 2)) complete = false;
        }
        return complete;
    }

    private static void tickObservation(ServerLevel level, RemnantLureSavedData.LureRecord record) {
        record.entityId().map(level::getEntity)
                .filter(RemnantEntity.class::isInstance).map(RemnantEntity.class::cast)
                .ifPresent(entity -> entity.setInvisible(true));
        BlockPos origin = record.origin();
        RemnantLureTemplate template = RemnantLureTemplate.create(
                RemnantLureTemplate.Kind.byId(record.templateId()));
        int radius = template.radius();
        AABB interior = new AABB(origin.getX() - radius + 1, origin.getY(),
                origin.getZ() - radius + 1,
                origin.getX() + radius, origin.getY() + template.height(),
                origin.getZ() + radius);
        ServerPlayer occupant = level.getEntitiesOfClass(ServerPlayer.class, interior,
                player -> player.isAlive() && !player.isSpectator()).stream().findFirst().orElse(null);
        if (occupant == null) {
            if (record.state() == RemnantState.OBSERVING) record.setState(RemnantState.LURE_READY);
            return;
        }
        commit(level, record, occupant);
    }

    private static void tickFalseRadioTransmission(
            ServerLevel level, RemnantLureSavedData.LureRecord record) {
        if (!record.state().acceptsFalseRadio()) {
            if (record.radioSequenceTicks() >= 0 || record.radioCooldownTicks() > 0) {
                record.cancelRadioSequence();
            }
            return;
        }
        BlockPos radio = record.triggers().stream()
                .filter(level::isLoaded)
                .filter(pos -> level.getBlockState(pos).is(ModBlocks.REMNANT_PROP.get()))
                .findFirst()
                .orElseGet(() -> record.triggers().isEmpty()
                        ? record.origin() : record.triggers().get(record.triggers().size() - 1));
        boolean listenerNearby = level.players().stream().anyMatch(player ->
                player.isAlive() && !player.isSpectator()
                        && player.distanceToSqr(Vec3.atCenterOf(radio)) <= 32.0D * 32.0D);
        if (record.radioSequenceTicks() < 0) {
            if (!listenerNearby) return;
            if (record.radioCooldownTicks() > 0) {
                record.tickRadioCooldown();
                return;
            }
            record.startRadioSequence(RemnantPolicy.radioLine(
                    record.layoutSeed(), record.radioBroadcastCount()));
        }
        int radioTick = record.radioSequenceTicks();
        net.minecraft.sounds.SoundEvent sound = switch (radioTick) {
            case 0 -> ModSounds.REMNANT_FALSE_RADIO.get();
            case 18 -> radioVoice(record.radioLine());
            case 104 -> ModSounds.RADIO_CUTOFF.get();
            default -> null;
        };
        if (sound != null) {
            float pitch = radioTick == 18 ? 1.0F : radioTick == 0 ? 0.64F : 0.62F;
            float volume = radioTick == 18 ? 1.15F : radioTick == 0 ? 0.52F : 0.5F;
            level.playSound(null, radio, sound,
                    net.minecraft.sounds.SoundSource.BLOCKS, volume, pitch);
        }
        if (radioTick == 18) {
            for (ServerPlayer player : level.players()) {
                if (player.isAlive() && player.distanceToSqr(Vec3.atCenterOf(radio)) <= 32.0D * 32.0D) {
                    PacketDistributor.sendToPlayer(
                            player, HearthBoundaryEffectPayload.remnantRadioVoice(record.radioLine()));
                }
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    radio.getX() + 0.5D, radio.getY() + 0.55D, radio.getZ() + 0.5D,
                    7, 0.24D, 0.18D, 0.24D, 0.025D);
        }
        record.advanceRadioSequence();
        if (radioTick >= 120) {
            record.finishRadioSequence(RemnantPolicy.radioRepeatDelay(
                    record.layoutSeed(), record.radioBroadcastCount()));
        }
    }

    private static net.minecraft.sounds.SoundEvent radioVoice(int line) {
        return switch (line) {
            case 1 -> ModSounds.REMNANT_RADIO_WARM.get();
            case 2 -> ModSounds.REMNANT_RADIO_ALONE.get();
            case 3 -> ModSounds.REMNANT_RADIO_FORGIVE.get();
            default -> ModSounds.REMNANT_RADIO_ROOM.get();
        };
    }

    public static boolean commitAt(ServerLevel level, BlockPos pos, ServerPlayer player) {
        RemnantLureSavedData data = RemnantLureSavedData.get(level.getServer());
        Optional<RemnantLureSavedData.LureRecord> match = data.at(pos)
                .filter(record -> record.triggers().contains(pos)
                        || record.seams().contains(pos)
                        || record.ownedPositions().contains(pos)
                        || record.membranePositions().contains(pos));
        if (match.isEmpty()) return false;
        return commit(level, match.get(), player);
    }

    private static boolean commit(ServerLevel level, RemnantLureSavedData.LureRecord record,
                                  ServerPlayer player) {
        if (record.state().isCommitted()) return false;
        boolean interruptRadio = record.radioSequenceTicks() >= 0;
        record.cancelRadioSequence();
        if (interruptRadio) {
            level.playSound(null, record.origin(), ModSounds.RADIO_CUTOFF.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.62F, 0.72F);
            for (ServerPlayer listener : level.players()) {
                if (listener.isAlive()
                        && listener.distanceToSqr(Vec3.atCenterOf(record.origin()))
                        <= 32.0D * 32.0D) {
                    PacketDistributor.sendToPlayer(
                            listener, HearthBoundaryEffectPayload.remnantRadioCutoff());
                }
            }
        }
        record.commit(player.getUUID());
        Entity entity = record.entityId().map(level::getEntity).orElse(null);
        if (entity instanceof RemnantEntity remnant) remnant.awaken(player);
        closeAndSealEntrance(level, record);
        level.playSound(null, record.origin(), ModSounds.REMNANT_LATCH.get(),
                net.minecraft.sounds.SoundSource.HOSTILE, 1.1F, 0.85F);
        RemnantLureSavedData.get(level.getServer()).changed();
        return true;
    }

    private static void startSealing(ServerLevel level, RemnantLureSavedData.LureRecord record) {
        record.setState(RemnantState.SEALING);
        record.entityId().map(level::getEntity)
                .filter(RemnantEntity.class::isInstance).map(RemnantEntity.class::cast)
                .ifPresent(entity -> {
                    entity.setState(RemnantState.SEALING);
                    entity.setInvisible(true);
                });
        for (BlockPos trigger : record.triggers()) {
            BlockState state = level.getBlockState(trigger);
            if (state.is(ModBlocks.REMNANT_PROP.get())) {
                level.setBlock(trigger, state.setValue(
                        com.frozendawn.block.RemnantPropBlock.LIT, false), 2);
            }
        }
        for (BlockPos membrane : record.membranePositions()) {
            BlockState existing = level.getBlockState(membrane);
            if ((existing.canBeReplaced() || existing.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                    && level.getEntities(null, new AABB(membrane)).isEmpty()) {
                level.setBlock(membrane, ModBlocks.REMNANT_MEMBRANE.get().defaultBlockState(), 2);
            }
        }
        level.sendParticles(ParticleTypes.POOF, record.origin().getX() + 0.5D,
                record.origin().getY() + 2.0D, record.origin().getZ() + 0.5D,
                40, 4.0D, 2.0D, 4.0D, 0.03D);
    }

    private static void closeAndSealEntrance(
            ServerLevel level, RemnantLureSavedData.LureRecord record) {
        for (BlockPos entrance : record.membranePositions()) {
            if (!level.isLoaded(entrance)) continue;
            BlockState state = level.getBlockState(entrance);
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
                level.setBlock(entrance, state.setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, false), 2);
            }
            if (level.getEntities(null, new AABB(entrance)).isEmpty()) {
                level.setBlock(entrance, ModBlocks.REMNANT_MEMBRANE.get().defaultBlockState(), 2);
            }
        }
    }

    private static void tickSealing(ServerLevel level,
                                    RemnantLureSavedData.LureRecord record) {
        closeAndSealEntrance(level, record);
        if (record.stateTicks() >= 15) {
            record.entityId().map(level::getEntity)
                    .filter(RemnantEntity.class::isInstance).map(RemnantEntity.class::cast)
                    .ifPresent(entity -> entity.setInvisible(false));
        }
        if (record.stateTicks() >= RemnantPolicy.SEALING_TICKS) {
            record.setState(RemnantState.HUNTING);
            record.entityId().map(level::getEntity)
                    .filter(RemnantEntity.class::isInstance).map(RemnantEntity.class::cast)
                    .ifPresent(entity -> entity.setState(RemnantState.HUNTING));
        }
    }

    private static void tickAuthoredTricks(ServerLevel level,
                                           RemnantLureSavedData.LureRecord record) {
        if (!record.falseOpeningUsed() && record.stateTicks() >= 80) {
            record.markFalseOpeningUsed();
            BlockPos anchor = record.wallAnchors().isEmpty()
                    ? record.origin() : record.wallAnchors().get(0);
            createFalseOpeningDisplay(level, record, anchor);
            level.playSound(null, anchor, ModSounds.REMNANT_LATCH.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 1.2F);
        }
        if (record.falseOpeningUsed()) {
            BlockPos falseDoor = record.wallAnchors().isEmpty()
                    ? record.origin() : record.wallAnchors().get(0);
            boolean approached = !level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(falseDoor).inflate(2.5D), player -> player.isAlive()).isEmpty();
            if (approached || record.stateTicks() >= 140) {
                discardTaggedDisplays(level, record, falseDisplayTag(record.id()));
            }
        }
        if (!record.roomShiftUsed() && record.stateTicks() >= 160) {
            record.markRoomShiftUsed();
            int moved = 0;
            for (BlockPos trigger : record.triggers()) {
                if (moved >= 4 || !level.isLoaded(trigger)
                        || level.getBlockEntity(trigger) != null) continue;
                BlockState state = level.getBlockState(trigger);
                if (!state.is(ModBlocks.REMNANT_PROP.get())) continue;
                BlockPos destination = trigger.relative(
                        trigger.getX() < record.origin().getX()
                                ? net.minecraft.core.Direction.EAST
                                : net.minecraft.core.Direction.WEST);
                if (!level.getBlockState(destination).isAir()
                        || !level.getEntities(null, new AABB(destination)).isEmpty()) continue;
                level.setBlock(destination, state, 2);
                level.removeBlock(trigger, false);
                record.triggers().set(record.triggers().indexOf(trigger), destination);
                moved += 2;
            }
            level.playSound(null, record.origin(), ModSounds.REMNANT_WALL_SHIFT.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.65F);
        }
    }

    private static void createFalseOpeningDisplay(ServerLevel level,
                                                  RemnantLureSavedData.LureRecord record,
                                                  BlockPos anchor) {
        for (int y = 0; y < 2; y++) {
            Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(
                    net.minecraft.world.level.block.DoorBlock.HALF,
                    y == 0
                            ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                            : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
            ((BlockDisplayAccessor) (Object) display).frozendawn$setBlockState(door);
            display.setPos(anchor.getX() + 0.5D, anchor.getY() + y, anchor.getZ() + 0.5D);
            display.setNoGravity(true);
            display.setInvulnerable(true);
            display.addTag(displayTag(record.id()));
            display.addTag(falseDisplayTag(record.id()));
            level.addFreshEntity(display);
        }
    }

    private static void discardTaggedDisplays(ServerLevel level,
                                               RemnantLureSavedData.LureRecord record,
                                               String tag) {
        level.getEntities((Entity) null, new AABB(record.origin()).inflate(24),
                entity -> entity.getTags().contains(tag)).forEach(Entity::discard);
    }

    public static void beginDeathPresentation(ServerLevel level, UUID lureId) {
        RemnantLureSavedData data = RemnantLureSavedData.get(level.getServer());
        data.lure(lureId).ifPresent(record -> {
            if (record.state() == RemnantState.RESOLVED) return;
            record.setState(RemnantState.DYING);
            for (BlockPos trigger : record.triggers()) {
                BlockState state = level.getBlockState(trigger);
                if (state.is(ModBlocks.REMNANT_PROP.get())) {
                    level.setBlock(trigger, state.setValue(
                            com.frozendawn.block.RemnantPropBlock.LIT, false), 2);
                }
            }
            data.changed();
        });
    }

    public static void beginCollapse(ServerLevel level, UUID lureId) {
        RemnantLureSavedData data = RemnantLureSavedData.get(level.getServer());
        data.lure(lureId).ifPresent(record -> {
            if (record.state() == RemnantState.RESOLVED) return;
            record.setState(RemnantState.COLLAPSING);
            spillContainers(level, record);
            level.getEntities((Entity) null, new AABB(record.origin()).inflate(24),
                    entity -> entity.getTags().contains(displayTag(record.id()))).forEach(Entity::discard);
            level.playSound(null, record.origin(), ModSounds.REMNANT_COLLAPSE.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.35F, 0.72F);
            data.changed();
        });
    }

    private static void tickCollapse(ServerLevel level, RemnantLureSavedData data,
                                     RemnantLureSavedData.LureRecord record) {
        List<BlockPos> removable = new ArrayList<>();
        Map<BlockPos, BlockState> expectedStates = new HashMap<>();
        Set<BlockPos> orderedPositions = new LinkedHashSet<>();
        RemnantLureTemplate template = RemnantLureTemplate.create(
                RemnantLureTemplate.Kind.byId(record.templateId()));
        int radius = template.radius();
        for (RemnantLureTemplate.Cell cell : template.cells()) {
            BlockPos world = record.origin().offset(
                    RemnantLureTemplate.rotate(cell.local(), record.rotation()));
            orderedPositions.add(world);
            BlockState expected = expectedState(record, template, world);
            if (expected != null) expectedStates.put(world, expected);
            if (cell.local().getY() >= template.height() - 1) {
                for (int height = 1; height <= 3; height++) {
                    orderedPositions.add(world.above(height));
                }
            }
        }
        for (BlockPos foundation : record.foundationPositions()) {
            orderedPositions.add(foundation);
            expectedStates.put(foundation,
                    rotateState(template.floorState(), record.rotation()));
        }
        for (BlockPos tracked : trackedLurePositions(record)) {
            orderedPositions.add(tracked);
            BlockState expected = expectedState(record, template, tracked);
            if (expected != null) expectedStates.put(tracked, expected);
        }
        removable.addAll(orderedPositions);
        removable.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed()
                .thenComparingDouble(pos -> pos.distSqr(record.origin())));
        int changed = 0;
        while (record.collapseCursor() < removable.size()
                && changed < RemnantPolicy.COLLAPSE_EDITS_PER_TICK) {
            BlockPos pos = removable.get(record.collapseCursor());
            record.advanceCollapse(1);
            if (!level.isLoaded(pos)) break;
            BlockState state = level.getBlockState(pos);
            BlockState expected = expectedStates.get(pos);
            boolean authoredRadio = record.triggers().contains(pos)
                    && state.is(ModBlocks.REMNANT_PROP.get());
            boolean removableResidue = expected == null && isLureWeatherResidue(state);
            if (authoredRadio
                    || (expected != null && isLureCollapseState(state, expected))
                    || removableResidue) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        8, 0.35D, 0.35D, 0.35D, 0.05D);
                level.removeBlock(pos, false);
                changed++;
            }
        }
        if (record.collapseCursor() >= removable.size()
                && record.stateTicks() >= RemnantPolicy.COLLAPSE_TICKS
                && removeRemainingAuthoredRadios(level, record)) {
            data.resolve(record, level.getGameTime() + RemnantPolicy.REPLACEMENT_COOLDOWN);
        }
    }

    private static boolean removeRemainingAuthoredRadios(
            ServerLevel level, RemnantLureSavedData.LureRecord record) {
        boolean complete = true;
        for (BlockPos trigger : record.triggers()) {
            if (!level.isLoaded(trigger)) {
                complete = false;
            } else if (level.getBlockState(trigger).is(ModBlocks.REMNANT_PROP.get())) {
                level.removeBlock(trigger, false);
            }
        }
        return complete;
    }

    private static boolean isLureCollapseState(BlockState actual, BlockState expected) {
        if (actual.equals(expected)) return true;
        if (expected.is(ModBlocks.REMNANT_MEMBRANE.get())
                && actual.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) return true;
        if (expected.is(net.minecraft.tags.BlockTags.PLANKS)
                && actual.is(ModBlocks.FROZEN_PLANKS.get())) return true;
        if (expected.is(net.minecraft.tags.BlockTags.LOGS)
                && (actual.is(ModBlocks.DEAD_LOG.get())
                || actual.is(ModBlocks.FROZEN_LOG.get()))) return true;
        if ((expected.is(Blocks.STONE_BRICKS)
                || expected.is(Blocks.MOSSY_STONE_BRICKS)
                || expected.is(Blocks.CRACKED_STONE_BRICKS)
                || expected.is(Blocks.CHISELED_STONE_BRICKS))
                && actual.is(ModBlocks.FROZEN_STONE_BRICKS.get())) return true;
        return expected.is(Blocks.COBBLESTONE)
                && actual.is(ModBlocks.FROZEN_COBBLESTONE.get());
    }

    private static boolean isLureWeatherResidue(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())
                || state.is(ModBlocks.ICICLE.get());
    }

    private static List<BlockPos> trackedLurePositions(
            RemnantLureSavedData.LureRecord record) {
        List<BlockPos> tracked = new ArrayList<>();
        tracked.addAll(record.triggers());
        tracked.addAll(record.seams());
        tracked.addAll(record.wallAnchors());
        tracked.addAll(record.ownedPositions());
        tracked.addAll(record.membranePositions());
        tracked.addAll(record.rubblePositions());
        tracked.addAll(record.foundationPositions());
        return tracked;
    }

    private static BlockState expectedState(RemnantLureSavedData.LureRecord record,
                                            RemnantLureTemplate template, BlockPos world) {
        for (RemnantLureTemplate.Cell cell : template.cells()) {
            BlockPos expectedPos = record.origin().offset(
                    RemnantLureTemplate.rotate(cell.local(), record.rotation()));
            if (!expectedPos.equals(world)) continue;
            BlockState state = switch (cell.role()) {
                case SEAM -> cell.state();
                case PROP -> ModBlocks.REMNANT_PROP.get().defaultBlockState().setValue(
                        com.frozendawn.block.RemnantPropBlock.LIT,
                        record.committedPlayer().isEmpty());
                case MEMBRANE -> record.committedPlayer().isPresent()
                        ? ModBlocks.REMNANT_MEMBRANE.get().defaultBlockState() : cell.state();
                default -> cell.state();
            };
            return rotateState(state, record.rotation());
        }
        if (record.triggers().contains(world)) {
            return ModBlocks.REMNANT_PROP.get().defaultBlockState().setValue(
                    com.frozendawn.block.RemnantPropBlock.LIT,
                    record.committedPlayer().isEmpty());
        }
        if (record.foundationPositions().contains(world)) {
            return rotateState(template.floorState(), record.rotation());
        }
        return null;
    }

    private static void spillContainers(ServerLevel level, RemnantLureSavedData.LureRecord record) {
        for (BlockPos trigger : record.triggers()) {
            if (level.getBlockEntity(trigger) instanceof Container container) {
                Containers.dropContents(level, trigger, container);
                container.clearContent();
            }
        }
    }

    private static void seedLoot(ServerLevel level, RemnantLureSavedData.LureRecord record) {
        for (BlockPos trigger : record.triggers()) {
            if (!(level.getBlockEntity(trigger) instanceof BarrelBlockEntity barrel)) continue;
            RandomSource random = RandomSource.create(record.layoutSeed());
            List<ItemStack> loot = List.of(
                    new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(3)),
                    new ItemStack(Items.COOKED_BEEF, 1 + random.nextInt(2)),
                    new ItemStack(ModItems.ICE_SHARD.get(), 2 + random.nextInt(4)));
            for (int i = 0; i < loot.size(); i++) barrel.setItem(i, loot.get(i));
            barrel.setChanged();
        }
    }

    public static RemnantLureSavedData.LureRecord debugPlace(ServerPlayer player,
                                                              RemnantLureTemplate.Kind kind) {
        ServerLevel level = player.serverLevel();
        for (int distance = 12; distance <= 30; distance += 3) {
            BlockPos probe = player.blockPosition().relative(player.getDirection(), distance);
            BlockPos origin = alignInsideChunkAtSurface(level, probe.getX(), probe.getZ(),
                    RemnantLureTemplate.create(kind).radius());
            RemnantLureSavedData.LureRecord placed = place(level, origin, kind,
                    player.getDirection().get2DDataValue(), RemnantPolicy.regionKey(origin));
            if (placed != null) return placed;
        }
        return null;
    }

    public static RemnantPlacementPolicy.Result debugDryRun(ServerPlayer player,
                                                             RemnantLureTemplate.Kind kind) {
        ServerLevel level = player.serverLevel();
        BlockPos probe = player.blockPosition().relative(player.getDirection(), 16);
        RemnantLureTemplate template = RemnantLureTemplate.create(kind);
        int rotation = player.getDirection().get2DDataValue();
        return RemnantPlacementPolicy.validate(level,
                alignInsideChunkAtSurface(level, probe.getX(), probe.getZ(), template.radius()),
                template, rotation);
    }

    public static RemnantEntity debugSpawnExposed(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = findDebugSpawnPosition(level,
                player.blockPosition().relative(player.getDirection(), 6));
        RemnantEntity entity = ModEntities.REMNANT.get().create(level, null, pos,
                MobSpawnType.COMMAND, true, false);
        if (entity == null) return null;
        AggregatePressureHandler.markIgnored(entity);
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                player.getYRot() + 180.0F, 0.0F);
        entity.exposeWithoutLure(player);
        return level.addFreshEntity(entity) ? entity : null;
    }

    private static BlockPos findDebugSpawnPosition(ServerLevel level, BlockPos probe) {
        for (int offset = 0; offset <= 8; offset++) {
            int[] candidates = offset == 0 ? new int[]{0} : new int[]{offset, -offset};
            for (int vertical : candidates) {
                BlockPos feet = probe.offset(0, vertical, 0);
                if (safeDebugSpawn(level, feet)) return feet;
            }
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                probe.getX(), probe.getZ());
        return new BlockPos(probe.getX(), surfaceY, probe.getZ());
    }

    private static boolean safeDebugSpawn(ServerLevel level, BlockPos feet) {
        return level.isLoaded(feet)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getBlockState(feet.below()).isFaceSturdy(
                        level, feet.below(), net.minecraft.core.Direction.UP);
    }

    public static Optional<RemnantLureSavedData.LureRecord> nearest(ServerPlayer player) {
        return RemnantLureSavedData.get(player.server).lures().stream()
                .filter(record -> record.state() != RemnantState.RESOLVED)
                .min(java.util.Comparator.comparingDouble(record ->
                        record.origin().distSqr(player.blockPosition())));
    }

    public static String statusLine(ServerLevel level) {
        RemnantLureSavedData data = RemnantLureSavedData.get(level.getServer());
        var loadedRecords = data.lures().stream().filter(record ->
                level.isLoaded(record.origin())
                        && record.state() != RemnantState.RESOLVED).toList();
        String anchor = loadedRecords.isEmpty() ? ""
                : ", loadedAnchor=" + loadedRecords.getFirst().origin().toShortString();
        return "records=" + data.lures().size() + ", loaded="
                + loadedRecords.size() + anchor;
    }

    public static boolean debugCommit(ServerPlayer player) {
        return nearest(player).map(record -> commit(player.serverLevel(), record, player)).orElse(false);
    }

    public static boolean debugSetState(ServerPlayer player, RemnantState state) {
        Optional<RemnantLureSavedData.LureRecord> nearest = nearest(player);
        if (nearest.isEmpty()) return false;
        RemnantLureSavedData.LureRecord record = nearest.get();
        record.setState(state);
        record.entityId().map(player.serverLevel()::getEntity)
                .filter(RemnantEntity.class::isInstance).map(RemnantEntity.class::cast)
                .ifPresent(entity -> entity.setState(state));
        RemnantLureSavedData.get(player.server).changed();
        return true;
    }

    public static boolean debugForceSlip(ServerPlayer player) {
        RemnantEntity entity = nearestEntity(player);
        if (entity == null) return false;
        return entity.forceSlip();
    }

    public static boolean debugForceGrab(ServerPlayer player) {
        RemnantEntity entity = nearestEntity(player);
        if (entity == null) return false;
        entity.forceGrab(player);
        return true;
    }

    public static boolean interruptWallLatch(ServerLevel level, UUID lureId) {
        return RemnantLureSavedData.get(level.getServer()).lure(lureId)
                .flatMap(RemnantLureSavedData.LureRecord::entityId)
                .map(level::getEntity)
                .filter(RemnantEntity.class::isInstance)
                .map(RemnantEntity.class::cast)
                .map(RemnantEntity::interruptWallLatch)
                .orElse(false);
    }

    public static RemnantEntity nearestEntity(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(RemnantEntity.class,
                player.getBoundingBox().inflate(96.0D), Entity::isAlive).stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    public static int purgeLoaded(ServerLevel level, BlockPos center, int radius) {
        RemnantLureSavedData data = RemnantLureSavedData.get(level.getServer());
        int removed = 0;
        for (RemnantLureSavedData.LureRecord record : List.copyOf(data.lures())) {
            if (record.origin().distSqr(center) > (long) radius * radius
                    || !level.isLoaded(record.origin())) continue;
            record.entityId().map(level::getEntity).ifPresent(Entity::discard);
            RemnantLureTemplate template = RemnantLureTemplate.create(
                    RemnantLureTemplate.Kind.byId(record.templateId()));
            for (RemnantLureTemplate.Cell cell : template.cells()) {
                BlockPos pos = record.origin().offset(
                        RemnantLureTemplate.rotate(cell.local(), record.rotation()));
                BlockState expected = expectedState(record, template, pos);
                if (level.isLoaded(pos) && expected != null
                        && level.getBlockState(pos).equals(expected)) level.removeBlock(pos, false);
            }
            for (BlockPos pos : record.triggers()) {
                BlockState expected = expectedState(record, template, pos);
                if (level.isLoaded(pos) && expected != null
                        && level.getBlockState(pos).equals(expected)) level.removeBlock(pos, false);
            }
            for (BlockPos pos : record.foundationPositions()) {
                BlockState expected = rotateState(template.floorState(), record.rotation());
                if (level.isLoaded(pos) && level.getBlockState(pos).equals(expected)) {
                    level.removeBlock(pos, false);
                }
            }
            level.getEntities((Entity) null, new AABB(record.origin()).inflate(24),
                    entity -> entity.getTags().contains(displayTag(record.id()))).forEach(Entity::discard);
            data.remove(record.id());
            removed++;
        }
        return removed;
    }

    private static BlockPos alignInsideChunk(BlockPos pos, int radius) {
        int minX = (pos.getX() >> 4 << 4) + radius + 1;
        int maxX = (pos.getX() >> 4 << 4) + 14 - radius;
        int minZ = (pos.getZ() >> 4 << 4) + radius + 1;
        int maxZ = (pos.getZ() >> 4 << 4) + 14 - radius;
        return new BlockPos(net.minecraft.util.Mth.clamp(pos.getX(), minX, maxX),
                pos.getY(), net.minecraft.util.Mth.clamp(pos.getZ(), minZ, maxZ));
    }

    private static BlockPos alignInsideChunkAtSurface(ServerLevel level, int x, int z, int radius) {
        BlockPos aligned = alignInsideChunk(new BlockPos(x, 0, z), radius);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                aligned.getX(), aligned.getZ());
        return new BlockPos(aligned.getX(), y, aligned.getZ());
    }

    private static String displayTag(UUID lureId) { return "fd_remnant_" + lureId; }
    private static String falseDisplayTag(UUID lureId) { return "fd_remnant_false_" + lureId; }
    private static int crackId(RemnantLureSavedData.LureRecord record, BlockPos pos) {
        return 31 * record.id().hashCode() + pos.hashCode();
    }

    private static BlockState rotateState(BlockState state, int quarterTurns) {
        net.minecraft.world.level.block.Rotation rotation = switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case 2 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case 3 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            default -> net.minecraft.world.level.block.Rotation.NONE;
        };
        return state.rotate(rotation);
    }

    /** Tiny no-save entity used only for a vanilla LOS ray in a rare placement check. */
    private static final class DummySightEntity extends Entity {
        private DummySightEntity(ServerLevel level, BlockPos pos) {
            super(EntityType.MARKER, level); setPos(Vec3.atCenterOf(pos));
        }
        @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}
        @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
        @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    }
}
