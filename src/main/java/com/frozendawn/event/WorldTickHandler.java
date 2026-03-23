package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.data.RoofCollapseSnowTracker;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.FrozenDawnPhaseTracker;
import com.frozendawn.network.ApocalypseDataPayload;
import com.frozendawn.network.OpenDifficultySelectionPayload;
import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.IcicleFormation;
import com.frozendawn.world.TemperatureManager;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.world.AcheroniteGrowth;
import com.frozendawn.world.BlockFreezer;
import com.frozendawn.world.FrostbittenSpawner;
import com.frozendawn.world.FrostmiteSpawner;
import com.frozendawn.world.FrozenAtmosphereFormation;
import com.frozendawn.world.HollowSpawner;
import com.frozendawn.world.ArchitectSpawner;
import com.frozendawn.world.BlastPitPlacement;
import com.frozendawn.world.BlastPitWarmZoneRegistry;
import com.frozendawn.world.MimicSpawner;
import com.frozendawn.world.ReturnedSpawner;
import com.frozendawn.world.SatellitePlacement;
import com.frozendawn.world.SnowAccumulator;
import com.frozendawn.world.StructureStressTracker;
import com.frozendawn.world.TowerEncounterController;
import com.frozendawn.world.CampPlacement;
import com.frozendawn.world.TowerPlacement;
import com.frozendawn.world.VegetationDecay;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
        EasterEggHandler.reset();
        StructureStressTracker.reset();
        BlastPitWarmZoneRegistry.reset();
        TowerPlacement.reset();
        CampPlacement.reset();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ApocalypseState state = ApocalypseState.get(server);

        state.tick(server);

        // Initialize satellite coordinates once (no-op if already chosen or disabled).
        WinConditionState winState = WinConditionState.get(server);
        winState.initSatellitePosition(server.overworld());
        OrsaStructureState orsaStructureState = OrsaStructureState.get(server);
        orsaStructureState.initBlastPitPosition(server.overworld());
        orsaStructureState.initTowerPositions(server.overworld());

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

        // Sync apocalypse data to all clients every 100 ticks (~5 seconds)
        if (state.getApocalypseTicks() % 100 == 0) {
            PacketDistributor.sendToAllPlayers(createPayload(state, winState));
        }

        float progress = state.getProgress();

        // Per-player effects: temperature, heat damage, wind chill, suffocation
        PlayerTickHandler.tick(server, state, currentPhase, currentDay, progress);

        // Drive world systems in the overworld
        ServerLevel overworld = server.overworld();
        long tick = overworld.getGameTime();
        if ((tick & 1L) == 0L) {
            BlastPitPlacement.tickPlacement(overworld);
        } else {
            TowerPlacement.tickPlacement(overworld);
        }
        CampPlacement.tickPlacement(overworld);
        SatellitePlacement.tickPlacement(overworld);
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
     * Suppress all mob spawning in the Overworld at phase 4+.
     */
    @SubscribeEvent
    public static void onMobSpawn(FinalizeSpawnEvent event) {
        if (FrozenDawnPhaseTracker.getPhase() < 4) return;
        if (event.getEntity().level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (event.getEntity() instanceof FrostbittenEntity) return;
        if (event.getEntity() instanceof FrostmiteEntity) return;
        if (event.getEntity() instanceof HollowEntity) return;
        if (event.getEntity() instanceof ReturnedEntity) return;
        if (event.getEntity() instanceof MimicEntity) return;
        if (event.getEntity() instanceof ArchitectEntity) return;
        event.setSpawnCancelled(true);
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
            PacketDistributor.sendToPlayer(player, createPayload(state, winState));

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
            }
        }
    }

    /** Returns the last-calculated temperature for a player (updated every 40 ticks). */
    public static float getLastTemperature(java.util.UUID playerId) {
        return PlayerTickHandler.getLastTemperature(playerId);
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
