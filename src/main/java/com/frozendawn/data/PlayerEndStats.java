package com.frozendawn.data;

import com.frozendawn.world.CampDirectiveHelper;
import com.frozendawn.world.MonitoringStationPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Per-player narrative stats used by the finale credits.
 */
public final class PlayerEndStats {

    private static final String ROOT_TAG = "frozendawn:end_stats";
    private static final String TERMINALS_HACKED_TAG = "terminalsHacked";
    private static final String LOWEST_TEMPERATURE_TAG = "lowestTemperature";
    private static final String LAST_TEMPERATURE_TAG = "lastTemperature";
    private static final String PHASES_WITNESSED_MASK_TAG = "phasesWitnessedMask";
    private static final String STRUCTURES_DISCOVERED_TAG = "structuresDiscovered";
    private static final String ORSA_DOCUMENTS_READ_TAG = "orsaDocumentsRead";
    private static final String HEATERS_LIT_TAG = "heatersLit";
    private static final String FUEL_BURNED_TICKS_TAG = "fuelBurnedTicks";
    private static final String NIGHTS_UNDERGROUND_TAG = "nightsUnderground";
    private static final String LAST_UNDERGROUND_NIGHT_DAY_TAG = "lastUndergroundNightDay";
    private static final String LOWEST_HEALTH_TAG = "lowestHealth";
    private static final String ARCHITECT_OBSERVED_TAG = "architectObserved";
    private static final String ARCHITECT_RETREATED_TO_HEAL_TAG = "architectRetreatedToHeal";
    private static final String ARCHITECT_WALL_BREACHES_TAG = "architectWallBreaches";
    private static final String FUEL_CELLS_PROCESSED_TAG = "fuelCellsProcessed";
    private static final String SATELLITE_FOUND_DAY_TAG = "satelliteFoundDay";

    private static final double NEARBY_CREDIT_RADIUS_SQR = 96.0D * 96.0D;
    private static final double STRUCTURE_DISCOVERY_RADIUS_SQR = 64.0D * 64.0D;
    private static final double BLAST_PIT_DISCOVERY_RADIUS_SQR = 96.0D * 96.0D;

    private PlayerEndStats() {
    }

    public static int getTerminalsHacked(ServerPlayer player) {
        return getStatsTag(player).getInt(TERMINALS_HACKED_TAG);
    }

    public static void incrementTerminalsHacked(ServerPlayer player) {
        increment(player, TERMINALS_HACKED_TAG, 1);
    }

    public static void recordTemperature(ServerPlayer player, float temperature) {
        CompoundTag stats = getStatsTag(player);
        if (!stats.contains(LOWEST_TEMPERATURE_TAG) || temperature < stats.getFloat(LOWEST_TEMPERATURE_TAG)) {
            stats.putFloat(LOWEST_TEMPERATURE_TAG, temperature);
        }
        stats.putFloat(LAST_TEMPERATURE_TAG, temperature);
        putStatsTag(player, stats);
    }

    public static void tickJourneyStats(ServerPlayer player, ApocalypseState apocalypseState, int currentPhase) {
        if (player.level().dimension() != Level.OVERWORLD) {
            return;
        }

        recordPhaseWitnessed(player, currentPhase);
        recordClosestCall(player);

        if (player.getServer() == null || player.getServer().getTickCount() % 20 != 0) {
            return;
        }
        recordUndergroundNight(player, apocalypseState);
        recordNearbyStructures(player);
    }

    public static void incrementOrsaDocumentsRead(ServerPlayer player) {
        increment(player, ORSA_DOCUMENTS_READ_TAG, 1);
    }

    public static void markSatelliteFound(ServerPlayer player, int day) {
        CompoundTag stats = getStatsTag(player);
        if (!stats.contains(SATELLITE_FOUND_DAY_TAG)) {
            stats.putInt(SATELLITE_FOUND_DAY_TAG, Math.max(0, day));
            putStatsTag(player, stats);
        }
    }

    public static void incrementHeatersLit(ServerPlayer player) {
        increment(player, HEATERS_LIT_TAG, 1);
    }

    public static void addFuelBurned(ServerPlayer player, int fuelTicks) {
        if (fuelTicks > 0) {
            increment(player, FUEL_BURNED_TICKS_TAG, fuelTicks);
        }
    }

    public static void addFuelBurnedNearby(ServerLevel level, BlockPos pos, int fuelTicks) {
        ServerPlayer player = nearestPlayer(level, pos, NEARBY_CREDIT_RADIUS_SQR);
        if (player != null) {
            addFuelBurned(player, fuelTicks);
        }
    }

    public static void addFuelCellsProcessedNearby(ServerLevel level, BlockPos pos, int fuelCells) {
        if (fuelCells <= 0) {
            return;
        }
        ServerPlayer player = nearestPlayer(level, pos, NEARBY_CREDIT_RADIUS_SQR);
        if (player != null) {
            increment(player, FUEL_CELLS_PROCESSED_TAG, fuelCells);
        }
    }

    public static void incrementArchitectObserved(ServerPlayer player) {
        increment(player, ARCHITECT_OBSERVED_TAG, 1);
    }

    public static void incrementArchitectRetreatedToHealNearby(ServerLevel level, BlockPos pos) {
        incrementNearby(level, pos, ARCHITECT_RETREATED_TO_HEAL_TAG, 1, NEARBY_CREDIT_RADIUS_SQR);
    }

    public static void incrementArchitectWallBreachesNearby(ServerLevel level, BlockPos pos) {
        incrementNearby(level, pos, ARCHITECT_WALL_BREACHES_TAG, 1, NEARBY_CREDIT_RADIUS_SQR);
    }

    public static int getOrsaDocumentsRead(ServerPlayer player) {
        return getStatsTag(player).getInt(ORSA_DOCUMENTS_READ_TAG);
    }

    public static int getFuelCellsProcessed(ServerPlayer player) {
        return getStatsTag(player).getInt(FUEL_CELLS_PROCESSED_TAG);
    }

    public static int getDaysBetweenSatelliteAndLaunch(ServerPlayer player, int launchDay) {
        CompoundTag stats = getStatsTag(player);
        if (!stats.contains(SATELLITE_FOUND_DAY_TAG)) {
            return -1;
        }
        return Math.max(0, launchDay - stats.getInt(SATELLITE_FOUND_DAY_TAG));
    }

    public static Snapshot snapshot(ServerPlayer player) {
        CompoundTag stats = getStatsTag(player);
        return new Snapshot(
                getTemperatureOrDefault(stats, LOWEST_TEMPERATURE_TAG),
                stats.getInt(PHASES_WITNESSED_MASK_TAG),
                countDiscoveredStructures(stats),
                stats.getInt(ORSA_DOCUMENTS_READ_TAG),
                stats.getInt(HEATERS_LIT_TAG),
                stats.getInt(FUEL_BURNED_TICKS_TAG),
                stats.getInt(NIGHTS_UNDERGROUND_TAG),
                getHealthOrDefault(stats),
                stats.getInt(ARCHITECT_OBSERVED_TAG),
                stats.getInt(ARCHITECT_RETREATED_TO_HEAL_TAG),
                stats.getInt(ARCHITECT_WALL_BREACHES_TAG),
                stats.getInt(FUEL_CELLS_PROCESSED_TAG),
                getTemperatureOrDefault(stats, LAST_TEMPERATURE_TAG));
    }

    private static void recordPhaseWitnessed(ServerPlayer player, int phase) {
        if (phase < 0 || phase > 30) {
            return;
        }
        CompoundTag stats = getStatsTag(player);
        int mask = stats.getInt(PHASES_WITNESSED_MASK_TAG);
        int updated = mask | (1 << phase);
        if (updated != mask) {
            stats.putInt(PHASES_WITNESSED_MASK_TAG, updated);
            putStatsTag(player, stats);
        }
    }

    private static void recordClosestCall(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return;
        }
        CompoundTag stats = getStatsTag(player);
        float health = Math.max(0.0F, player.getHealth());
        if (!stats.contains(LOWEST_HEALTH_TAG) || health < stats.getFloat(LOWEST_HEALTH_TAG)) {
            stats.putFloat(LOWEST_HEALTH_TAG, health);
            putStatsTag(player, stats);
        }
    }

    private static void recordUndergroundNight(ServerPlayer player, ApocalypseState apocalypseState) {
        long dayTime = player.level().getDayTime() % 24000L;
        boolean night = dayTime >= 13000L && dayTime <= 23000L;
        if (!night || player.blockPosition().getY() >= 50 || player.level().canSeeSky(player.blockPosition().above())) {
            return;
        }

        int currentDay = apocalypseState.getCurrentDay();
        CompoundTag stats = getStatsTag(player);
        if (stats.contains(LAST_UNDERGROUND_NIGHT_DAY_TAG)
                && stats.getInt(LAST_UNDERGROUND_NIGHT_DAY_TAG) == currentDay) {
            return;
        }
        stats.putInt(LAST_UNDERGROUND_NIGHT_DAY_TAG, currentDay);
        stats.putInt(NIGHTS_UNDERGROUND_TAG, Math.max(0, stats.getInt(NIGHTS_UNDERGROUND_TAG)) + 1);
        putStatsTag(player, stats);
    }

    private static void recordNearbyStructures(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos playerPos = player.blockPosition();
        WinConditionState winState = WinConditionState.get(level.getServer());
        OrsaStructureState structureState = OrsaStructureState.get(level.getServer());

        if (winState.isSatellitePlaced() && isNear(playerPos, winState.getSatellitePos(), STRUCTURE_DISCOVERY_RADIUS_SQR)) {
            markStructureDiscovered(player, "satellite");
        }
        if (isNear(playerPos, structureState.getBlastPitPos(), BLAST_PIT_DISCOVERY_RADIUS_SQR)) {
            markStructureDiscovered(player, "blast_pit");
        }

        OrsaStructureState.TowerRecord tower = structureState.findTowerNear(playerPos, 64);
        if (tower != null) {
            markStructureDiscovered(player, "tower_" + tower.id());
        }

        BlockPos station = MonitoringStationPlacement.findBuiltStationNear(level, playerPos, 48);
        if (station != null) {
            markStructureDiscovered(player, keyForPos("monitoring_station", station));
        }

        for (BlockPos center : CargoDropState.get(level.getServer()).getBuiltDropCenters()) {
            if (isNear(playerPos, center, STRUCTURE_DISCOVERY_RADIUS_SQR)) {
                markStructureDiscovered(player, keyForPos("cargo_drop", center));
                break;
            }
        }

        CampDirectiveHelper.CampDirective camp = CampDirectiveHelper.findNearestCamp(level, playerPos);
        if (camp != null && isNear(playerPos, camp.pos(), STRUCTURE_DISCOVERY_RADIUS_SQR)) {
            int chunkX = camp.pos().getX() >> 4;
            int chunkZ = camp.pos().getZ() >> 4;
            if (structureState.isCampBuilt(chunkX, chunkZ)) {
                markStructureDiscovered(player, "camp_" + chunkX + "_" + chunkZ);
            }
        }
    }

    private static void markStructureDiscovered(ServerPlayer player, String key) {
        CompoundTag stats = getStatsTag(player);
        CompoundTag structures = stats.getCompound(STRUCTURES_DISCOVERED_TAG);
        if (structures.getBoolean(key)) {
            return;
        }
        structures.putBoolean(key, true);
        stats.put(STRUCTURES_DISCOVERED_TAG, structures);
        putStatsTag(player, stats);
    }

    private static boolean isNear(BlockPos from, BlockPos to, double radiusSqr) {
        return to != null && from.distSqr(to) <= radiusSqr;
    }

    private static String keyForPos(String prefix, BlockPos pos) {
        return prefix + '_' + pos.getX() + '_' + pos.getY() + '_' + pos.getZ();
    }

    private static void incrementNearby(ServerLevel level, BlockPos pos, String tag, int amount, double radiusSqr) {
        ServerPlayer player = nearestPlayer(level, pos, radiusSqr);
        if (player != null) {
            increment(player, tag, amount);
        }
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos, double radiusSqr) {
        ServerPlayer nearest = null;
        double best = radiusSqr;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double dist = player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (dist <= best) {
                best = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void increment(ServerPlayer player, String tag, int amount) {
        if (amount <= 0) {
            return;
        }
        CompoundTag stats = getStatsTag(player);
        stats.putInt(tag, Math.max(0, stats.getInt(tag)) + amount);
        putStatsTag(player, stats);
    }

    private static int countDiscoveredStructures(CompoundTag stats) {
        CompoundTag structures = stats.getCompound(STRUCTURES_DISCOVERED_TAG);
        return structures.getAllKeys().size();
    }

    private static float getTemperatureOrDefault(CompoundTag stats, String tag) {
        return stats.contains(tag) ? stats.getFloat(tag) : 20.0F;
    }

    private static float getHealthOrDefault(CompoundTag stats) {
        return stats.contains(LOWEST_HEALTH_TAG) ? stats.getFloat(LOWEST_HEALTH_TAG) : 20.0F;
    }

    private static CompoundTag getStatsTag(ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT_TAG).copy();
    }

    private static void putStatsTag(ServerPlayer player, CompoundTag stats) {
        player.getPersistentData().put(ROOT_TAG, stats);
    }

    public record Snapshot(
            float lowestTemperatureSurvived,
            int phasesWitnessedMask,
            int structuresDiscovered,
            int orsaDocumentsRead,
            int heatersLit,
            int fuelBurnedTicks,
            int nightsUnderground,
            float lowestHealth,
            int architectObserved,
            int architectRetreatedToHeal,
            int architectWallBreaches,
            int fuelCellsProcessed,
            float lastTemperatureRecorded) {
    }
}
