package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.RoofCollapseSnowTracker;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.network.ApocalypseDataPayload;
import com.frozendawn.network.OpenDifficultySelectionPayload;
import com.frozendawn.network.OpenOrsaAwakeningPayload;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.IcicleFormation;
import com.frozendawn.world.TemperatureManager;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.homo.CognitiveLoadManager;
import com.frozendawn.homo.HearthMaturationManager;
import com.frozendawn.homo.HearthArchitectManager;
import com.frozendawn.homo.HearthBoundaryManager;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMasterArchitectManager;
import com.frozendawn.homo.HearthMasterArchitectWeatherManager;
import com.frozendawn.homo.HearthHeartManager;
import com.frozendawn.homo.HeartMemoryNodeManager;
import com.frozendawn.homo.HeartMaeveErasureManager;
import com.frozendawn.homo.HeartScavengerWaveManager;
import com.frozendawn.homo.HeartMusicManager;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.HearthPopulationManager;
import com.frozendawn.homo.HearthTransmissionManager;
import com.frozendawn.homo.HearthViolationManager;
import com.frozendawn.homo.HearthReconciliationManager;
import com.frozendawn.homo.HearthSelectionManager;
import com.frozendawn.homo.HearthSurveySignalManager;
import com.frozendawn.homo.HearthWatcherManager;
import com.frozendawn.homo.HearthDarkeningManager;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.world.UndoneSpawner;
import com.frozendawn.world.UndoneArchitectSpawner;
import com.frozendawn.world.BloomboundUndoneSpawner;
import com.frozendawn.world.ArchivistManager;
import com.frozendawn.world.remnant.RemnantLureManager;
import com.frozendawn.phase.FrozenDawnPhaseTracker;
import com.frozendawn.world.AcheroniteGrowth;
import com.frozendawn.world.BlockFreezer;
import com.frozendawn.world.FrostbittenSpawner;
import com.frozendawn.world.FrostmiteSpawner;
import com.frozendawn.world.FrozenAtmosphereFormation;
import com.frozendawn.world.HollowSpawner;
import com.frozendawn.world.ArchitectSpawner;
import com.frozendawn.world.BlastPitPlanner;
import com.frozendawn.world.BlastPitPlacement;
import com.frozendawn.world.BlastPitWarmZoneRegistry;
import com.frozendawn.world.CargoDropPlacement;
import com.frozendawn.world.ChunkCatchUpManager;
import com.frozendawn.world.FrozenEvacVehiclePlacement;
import com.frozendawn.world.FrozenTownRuntime;
import com.frozendawn.world.MimicSpawner;
import com.frozendawn.world.MonitoringStationPlacement;
import com.frozendawn.world.ReturnedSpawner;
import com.frozendawn.world.RocketLaunchManager;
import com.frozendawn.world.SatellitePlacement;
import com.frozendawn.world.SnowAccumulator;
import com.frozendawn.world.StructureStressTracker;
import com.frozendawn.world.ThermalVentSystem;
import com.frozendawn.world.TowerEncounterController;
import com.frozendawn.world.CampPlacement;
import com.frozendawn.world.TowerPlanner;
import com.frozendawn.world.TowerPlacement;
import com.frozendawn.world.VegetationDecay;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drives the apocalypse forward each server tick.
 * Dispatches to PlayerTickHandler for per-player effects, then drives
 * world systems: WeatherHandler, BlockFreezer, VegetationDecay, SnowAccumulator.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class WorldTickHandler {

    private static int lastLoggedPhase = -1;
    private static int lastLoggedDay = -1;
    /**
     * BreakEvent fires before the block is actually removed. Queue a one-tick-later
     * notification so Architect D* Lite always sees final world state.
     */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> pendingArchitectBreakUpdates = new HashMap<>();
    private static final String ORSA_AWAKENING_SEEN_TAG = "frozendawn:orsa_awakening_seen";
    private static final String SKIP_ORSA_AWAKENING_PROPERTY = "frozendawn.dev.skipOrsaAwakening";
    private static final long ORSA_AWAKENING_FREEZE_TICKS = 340L;
    private static final Map<UUID, Long> orsaAwakeningFreezeUntil = new HashMap<>();
    private static final Map<UUID, Vec3> orsaAwakeningFreezeAnchor = new HashMap<>();

    private static final String[] PHASE_ADVANCEMENTS = {
            "root", "phase2", "phase3", "phase4", "phase5", "phase6"
    };

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        lastLoggedPhase = -1;
        lastLoggedDay = -1;
        pendingArchitectBreakUpdates.clear();
        PlayerTickHandler.reset();
        WeatherHandler.reset();
        NetherSeveranceHandler.reset();
        FrostbittenSpawner.reset();
        FrostmiteSpawner.reset();
        HollowSpawner.reset();
        ReturnedSpawner.reset();
        MimicSpawner.reset();
        ArchitectSpawner.reset();
        AcheroniteGrowth.reset();
        EasterEggHandler.reset();
        StructureStressTracker.reset();
        BlastPitWarmZoneRegistry.reset();
        ThermalVentSystem.reset();
        BlastPitPlanner.reset();
        TowerPlanner.reset();
        TowerPlacement.reset();
        CampPlacement.reset();
        ChunkCatchUpManager.reset();
        HearthReconciliationManager.reset();
        HearthArchitectManager.reset();
        HearthPopulationManager.reset();
        HearthMasterArchitectManager.reset();
        HearthMasterArchitectWeatherManager.reset();
        HearthHeartManager.reset();
        HeartMemoryNodeManager.reset();
        HeartMaeveErasureManager.reset();
        HeartScavengerWaveManager.reset();
        HeartMusicManager.reset();
        CognitiveLoadManager.reset();
        HearthMemoryManager.reset();
        HearthTransmissionManager.reset();
        HearthSurveySignalManager.reset();
        HearthBoundaryManager.reset();
        HearthCombatRosterManager.reset();
        HearthViolationManager.reset();
        HearthWatcherManager.reset();
        FrozenEvacVehiclePlacement.reset();
        CargoDropPlacement.reset();
        MonitoringStationPlacement.reset();
        FrozenTownRuntime.reset();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ApocalypseState state = ApocalypseState.get(server);

        state.tick(server);

        // Initialize satellite coordinates once (no-op if already chosen or disabled).
        WinConditionState winState = WinConditionState.get(server);
        winState.initSatellitePosition(server.overworld());
        BlastPitPlanner.ensurePlanned(server.overworld());
        TowerPlanner.ensurePlanned(server.overworld());

        int currentPhase = state.getPhase();
        int currentDay = state.getCurrentDay();
        FrozenDawnPhaseTracker.setPhase(currentPhase);

        // Log phase transitions and grant advancements
        if (currentPhase != lastLoggedPhase) {
            FrozenDawn.LOGGER.info("Apocalypse phase transition: Phase {} -> Phase {} (Day {})",
                    lastLoggedPhase == -1 ? "START" : lastLoggedPhase, currentPhase, currentDay);
            lastLoggedPhase = currentPhase;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                grantPhaseAdvancements(player, currentPhase);
            }
        }

        // Log day changes (every 10 days to avoid spam)
        if (currentDay != lastLoggedDay && currentDay % 10 == 0) {
            FrozenDawn.LOGGER.info("Apocalypse Day {}/{} | Phase {} | Temp offset: {}C | Sun scale: {}",
                    currentDay, state.getTotalDays(), currentPhase,
                    String.format("%.1f", state.getTemperatureOffset()),
                    String.format("%.2f", state.getSunScale()));
            lastLoggedDay = currentDay;
        }

        // Sync apocalypse data to all clients every 20 ticks (~1 second)
        if (state.getApocalypseTicks() % 20 == 0) {
            PacketDistributor.sendToAllPlayers(createPayload(state, winState));
        }

        ServerLevel overworld = server.overworld();
        float progress = state.getProgress();

        ThermalVentSystem.tick(overworld, currentPhase, progress, overworld.getGameTime());
        tickOrsaAwakeningFreeze(server);

        // Per-player effects: temperature, heat damage, wind chill, suffocation
        PlayerTickHandler.tick(server, state, currentPhase, currentDay, progress);

        // Drive world systems in the overworld
        long tick = overworld.getGameTime();
        PostMaeveWorldState.tick(overworld);
        BloomboundUndoneSpawner.tick(overworld);
        UndoneSpawner.tick(overworld);
        UndoneArchitectSpawner.tick(overworld);
        ArchivistManager.tick(overworld);
        RemnantLureManager.tick(overworld);
        HearthDarkeningManager.tick(overworld);
        if ((tick & 1L) == 0L) {
            BlastPitPlacement.tickPlacement(overworld);
        } else {
            TowerPlacement.tickPlacement(overworld);
        }
        CampPlacement.tickPlacement(overworld);
        FrozenEvacVehiclePlacement.tickPlacement(overworld);
        CargoDropPlacement.tickPlacement(overworld);
        MonitoringStationPlacement.tickPlacement(overworld);
        FrozenTownRuntime.tickProcessing(overworld);
        ChunkCatchUpManager.tick(overworld, state);
        BloomGrowthManager.tick(overworld, state);
        HearthSelectionManager.tick(overworld, state);
        HearthMaturationManager.tick(overworld, state);
        HearthReconciliationManager.tick(overworld, state);
        HearthArchitectManager.tick(overworld);
        HearthPopulationManager.tick(overworld);
        HearthMasterArchitectManager.tick(overworld);
        HearthMasterArchitectWeatherManager.tick(overworld, currentPhase, progress);
        HearthHeartManager.tick(overworld);
        HeartScavengerWaveManager.tick(overworld);
        HeartMusicManager.tick(server);
        CognitiveLoadManager.tick(overworld, state);
        HearthTransmissionManager.tick(overworld);
        HearthSurveySignalManager.tick(server);
        HearthBoundaryManager.tick(overworld);
        HearthMemoryManager.tick(overworld);
        HearthWatcherManager.tick(overworld);
        SatellitePlacement.tickPlacement(overworld);
        RocketLaunchManager.tick(overworld);
        WeatherHandler.tick(overworld, currentPhase, progress);
        NetherSeveranceHandler.tick(overworld, currentPhase);
        // Stagger heavy systems on alternating ticks to halve peak load
        if (tick % 2 == 0) {
            BlockFreezer.tick(overworld, currentPhase, progress);
        } else {
            VegetationDecay.tick(overworld, currentPhase);
            AcheroniteGrowth.tick(overworld, currentPhase, progress,
                    state.getCurrentDay(), state.getTotalDays());
            FrozenAtmosphereFormation.tick(overworld, currentPhase, progress,
                    state.getCurrentDay(), state.getTotalDays());
        }
        SnowAccumulator.tick(overworld, currentPhase, progress);
        IcicleFormation.tick(overworld, currentPhase, progress);
        FrostbittenSpawner.tick(overworld, currentPhase, progress);
        FrostmiteSpawner.tick(overworld, currentPhase, progress);
        HollowSpawner.tick(overworld, currentPhase, progress);
        ReturnedSpawner.tick(overworld, currentPhase, progress);
        MimicSpawner.tick(overworld, currentPhase, progress);
        TowerEncounterController.tick(overworld);
        ArchitectSpawner.tick(overworld, currentPhase, progress);
        StructureStressTracker.prune(overworld);
        RoofCollapseSnowTracker.get(server).prune(overworld);

        flushPendingArchitectBreakUpdates(server);

        // Easter egg delayed sounds (every tick)
        EasterEggHandler.tickDelayedSounds(server);
        // Easter egg Wilson drop tracking (every 20 ticks)
        if (tick % 20 == 0) {
            EasterEggHandler.tickWilsonDrops(server);
        }

        // Prune player-placed block tracker periodically
        PlayerPlacedBlockTracker.get(server).prune(overworld);
    }

    /**
     * Applies apocalypse natural-spawn suppression without affecting breeding, eggs,
     * scripted spawns, or structure-authored encounters.
     */
    @SubscribeEvent
    public static void onMobSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity().level() instanceof ServerLevel serverLevel
                && FrozenTownRuntime.shouldSuppressHostileSpawn(
                serverLevel,
                event.getEntity().blockPosition(),
                event.getSpawnType() == MobSpawnType.NATURAL,
                event.getEntity()
        )) {
            event.setSpawnCancelled(true);
            return;
        }

        if (shouldSuppressNaturalPassiveSpawn(event) || shouldSuppressNaturalHostileSpawn(event)) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean shouldSuppressNaturalPassiveSpawn(FinalizeSpawnEvent event) {
        if (!FrozenDawnConfig.ENABLE_NATURAL_PASSIVE_SPAWN_SUPPRESSION.get()) return false;
        if (FrozenDawnPhaseTracker.getPhase() < 4) return false;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return false;
        if (event.getEntity().level().dimension() != Level.OVERWORLD) return false;

        return isNaturalPassiveCategory(event.getEntity().getType().getCategory());
    }

    private static boolean shouldSuppressNaturalHostileSpawn(FinalizeSpawnEvent event) {
        if (!FrozenDawnConfig.ENABLE_NATURAL_HOSTILE_SPAWN_SUPPRESSION.get()) return false;
        if (FrozenDawnPhaseTracker.getPhase() < 4) return false;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return false;
        if (event.getEntity().level().dimension() != Level.OVERWORLD) return false;
        if (isFrozenDawnManagedHostile(event.getEntity())) return false;

        return event.getEntity().getType().getCategory() == MobCategory.MONSTER;
    }

    private static boolean isNaturalPassiveCategory(MobCategory category) {
        return category == MobCategory.CREATURE
                || category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.AXOLOTLS
                || category == MobCategory.UNDERGROUND_WATER_CREATURE;
    }

    private static boolean isFrozenDawnManagedHostile(net.minecraft.world.entity.Entity entity) {
        return entity instanceof FrostbittenEntity
                || entity instanceof RimeboundEntity
                || entity instanceof FrostmiteEntity
                || entity instanceof ReturnedEntity
                || entity instanceof MimicEntity
                || entity instanceof ArchitectEntity;
    }

    /**
     * Prevent crop growth when temperature is below 0C.
     */
    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (FrozenDawnPhaseTracker.getPhase() < 3) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        MinecraftServer server = serverLevel.getServer();
        ApocalypseState state = ApocalypseState.get(server);

        float temp = TemperatureManager.getTemperatureAt(
                serverLevel, event.getPos(), state.getCurrentDay(), state.getTotalDays());
        if (temp < 0f) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        invalidateNearbyShelterCaches(event.getLevel(), event.getPos());

        // Track player-placed blocks for Architect pathfinding
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel) {
                PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(serverLevel.getServer());
                tracker.markPlaced(event.getPos());
            }

            if (event.getPlacedBlock().is(ModBlocks.GEOTHERMAL_CORE.get())
                    && event.getPos().getY() < 0) {
                grantAdvancement(player, "last_light");
            }
        }

        notifyNearbyArchitects(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        invalidateNearbyShelterCaches(event.getLevel(), event.getPos());

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockState state = serverLevel.getBlockState(event.getPos());
            if (state.is(ModBlocks.ACHERONITE_CRYSTAL.get()) && state.getValue(AcheroniteCrystalBlock.BURIED)) {
                if (!AcheroniteCrystalBlock.hasSnowCover(serverLevel, event.getPos())) {
                    serverLevel.setBlock(event.getPos(), state.setValue(AcheroniteCrystalBlock.BURIED, false), 3);
                } else {
                    serverLevel.setBlock(event.getPos(), state.setValue(AcheroniteCrystalBlock.BURIED, false), 3);
                }
                event.setCanceled(true);
                return;
            }

            if (MonitoringStationPlacement.findLockedStationCovering(serverLevel, event.getPos()) != null) {
                event.setCanceled(true);
                if (event.getPlayer() instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.literal("The sealed archive is still locked by the ORSA terminal."), true);
                }
                return;
            }
        }

        // Remove from player-placed tracker
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            PlayerPlacedBlockTracker tracker = PlayerPlacedBlockTracker.get(serverLevel.getServer());
            tracker.markRemoved(event.getPos());
            queueArchitectBreakUpdate(serverLevel, event.getPos());
            if (event.getPlayer() instanceof ServerPlayer player) {
                FrostmiteSpawner.trySpawnInfestedBreak(serverLevel, event.getPos(), serverLevel.getBlockState(event.getPos()), player);
            }
        }
    }

    /** Notify Architect entities within 64 blocks of a block change so D* Lite can update costs. */
    private static void notifyNearbyArchitects(net.minecraft.world.level.LevelAccessor levelAccessor, net.minecraft.core.BlockPos pos) {
        if (!(levelAccessor instanceof ServerLevel serverLevel)) return;
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(pos).inflate(64);
        for (ArchitectEntity architect : serverLevel.getEntitiesOfClass(ArchitectEntity.class, searchBox)) {
            architect.getDStarPathfinder().onBlockChanged(pos, serverLevel);
        }
    }

    private static void queueArchitectBreakUpdate(ServerLevel level, BlockPos pos) {
        pendingArchitectBreakUpdates
                .computeIfAbsent(level.dimension(), key -> new HashSet<>())
                .add(pos.immutable());
    }

    private static void flushPendingArchitectBreakUpdates(MinecraftServer server) {
        if (pendingArchitectBreakUpdates.isEmpty()) return;
        for (ServerLevel level : server.getAllLevels()) {
            Set<BlockPos> queued = pendingArchitectBreakUpdates.remove(level.dimension());
            if (queued == null || queued.isEmpty()) continue;
            for (BlockPos pos : queued) {
                notifyNearbyArchitects(level, pos);
            }
        }
    }

    /** Invalidate shelter caches for any heaters within 4 blocks below the changed position. */
    private static void invalidateNearbyShelterCaches(net.minecraft.world.level.LevelAccessor levelAccessor, net.minecraft.core.BlockPos changedPos) {
        if (!(levelAccessor instanceof net.minecraft.world.level.Level level)) return;
        java.util.Set<net.minecraft.core.BlockPos> heaters = HeaterRegistry.getHeaters(level);
        if (heaters.isEmpty()) return;
        for (int dy = 1; dy <= 4; dy++) {
            net.minecraft.core.BlockPos below = changedPos.below(dy);
            if (heaters.contains(below)) {
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(below);
                if (be instanceof ThermalHeaterBlockEntity heater) {
                    heater.invalidateShelterCache();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            ApocalypseState state = ApocalypseState.get(player.getServer());
            WinConditionState winState = WinConditionState.get(player.getServer());
            PlayerTickHandler.onPlayerLogin(player);
            PacketDistributor.sendToPlayer(player, createPayload(state, winState));
            PlayerTickHandler.syncBreathableState(player);
            RocketLaunchManager.syncLaunchState(player);
            PostMaeveWorldState.sync(player);

            grantPhaseAdvancements(player, state.getPhase());
            SanityHandler.onPlayerLogin(player);

            net.minecraft.nbt.CompoundTag persistentData = player.getPersistentData();
            if (!persistentData.getBoolean("frozendawn:received_books")) {
                persistentData.putBoolean("frozendawn:received_books", true);
                net.minecraft.world.item.ItemStack guide = StarterBooks.createGuideBook();
                if (guide != null) player.getInventory().add(guide);
            }
            if (!state.isDifficultyLocked()) {
                PacketDistributor.sendToPlayer(player, new OpenDifficultySelectionPayload());
            } else {
                trySendOrsaAwakening(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            orsaAwakeningFreezeUntil.remove(playerId);
            orsaAwakeningFreezeAnchor.remove(playerId);
            PlayerTickHandler.onPlayerLogout(player);
            CognitiveLoadManager.onPlayerLogout(player);
        }
    }

    /** Returns the last-calculated temperature for a player (updated every 10 ticks). */
    public static float getLastTemperature(java.util.UUID playerId) {
        return PlayerTickHandler.getLastTemperature(playerId);
    }

    public static void trySendOrsaAwakening(ServerPlayer player) {
        if (Boolean.getBoolean(SKIP_ORSA_AWAKENING_PROPERTY)) {
            return;
        }
        net.minecraft.nbt.CompoundTag persistentData = player.getPersistentData();
        if (persistentData.getBoolean(ORSA_AWAKENING_SEEN_TAG)) {
            return;
        }
        persistentData.putBoolean(ORSA_AWAKENING_SEEN_TAG, true);
        UUID playerId = player.getUUID();
        orsaAwakeningFreezeUntil.put(playerId, player.level().getGameTime() + ORSA_AWAKENING_FREEZE_TICKS);
        orsaAwakeningFreezeAnchor.put(playerId, player.position());
        PacketDistributor.sendToPlayer(player, new OpenOrsaAwakeningPayload());
    }

    private static void tickOrsaAwakeningFreeze(MinecraftServer server) {
        if (orsaAwakeningFreezeUntil.isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            Long until = orsaAwakeningFreezeUntil.get(playerId);
            if (until == null) {
                continue;
            }
            long now = player.level().getGameTime();
            if (now >= until) {
                orsaAwakeningFreezeUntil.remove(playerId);
                orsaAwakeningFreezeAnchor.remove(playerId);
                continue;
            }
            Vec3 anchor = orsaAwakeningFreezeAnchor.get(playerId);
            if (anchor != null) {
                player.teleportTo(anchor.x, anchor.y, anchor.z);
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        }
    }

    public static void grantAdvancement(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name);
        AdvancementHolder holder = server.getAdvancements().get(loc);
        if (holder == null) return;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }

    private static void grantPhaseAdvancements(ServerPlayer player, int currentPhase) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        for (int i = 0; i < currentPhase && i < PHASE_ADVANCEMENTS.length; i++) {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, PHASE_ADVANCEMENTS[i]);
            AdvancementHolder holder = server.getAdvancements().get(loc);
            if (holder == null) continue;

            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            if (!progress.isDone()) {
                for (String criterion : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(holder, criterion);
                }
            }
        }
    }

    private static ApocalypseDataPayload createPayload(ApocalypseState state, WinConditionState winState) {
        return new ApocalypseDataPayload(
                state.getPhase(),
                state.getProgress(),
                state.getTemperatureOffset(),
                state.getSunScale(),
                state.getSunBrightness(),
                state.getSkyLight(),
                winState.isSchematicUnlocked()
        );
    }
}
