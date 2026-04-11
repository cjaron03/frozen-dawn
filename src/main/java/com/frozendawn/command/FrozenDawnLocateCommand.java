package com.frozendawn.command;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.FrozenDawn;
import com.frozendawn.data.CampSatelliteState;
import com.frozendawn.data.CargoDropState;
import com.frozendawn.data.MonitoringStationState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.world.BlastPitPlanner;
import com.frozendawn.world.CampPlacement;
import com.frozendawn.world.CargoDropPlacement;
import com.frozendawn.world.FrozenEvacVehiclePlacement;
import com.frozendawn.world.MonitoringStationPlacement;
import com.frozendawn.world.ThermalVentSavedData;
import com.frozendawn.world.ThermalVentState;
import com.frozendawn.world.ThermalVentSystem;
import com.frozendawn.world.TowerPlanner;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Comparator;
import java.util.List;

final class FrozenDawnLocateCommand {

    private FrozenDawnLocateCommand() {
    }

    private static final ResourceKey<Structure> FROZEN_TOWN = ResourceKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frozen_town")
    );

    private static final double CAMP_SKIP_RADIUS_SQ = 5.0 * 5.0;
    private static final double CARGO_SKIP_RADIUS_SQ = 20.0 * 20.0;
    private static final double STATION_SKIP_RADIUS_SQ = 5.0 * 5.0;

    static LiteralArgumentBuilder<CommandSourceStack> locateCommands() {
        return Commands.literal("locate")
                .then(Commands.literal("all").executes(FrozenDawnLocateCommand::locateAll))
                .then(Commands.literal("orsa").executes(FrozenDawnLocateCommand::locateOrsa))
                .then(Commands.literal("vents").executes(FrozenDawnLocateCommand::vents))
                .then(Commands.literal("towns").executes(FrozenDawnLocateCommand::towns));
    }

    private static int blastPit(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        OrsaStructureState state = OrsaStructureState.get(server);
        BlockPos pos = state.getBlastPitPos();
        if (pos == null) {
            if (state.getBlastPitTargetPos() != null) {
                BlockPos anchor = state.getBlastPitTargetPos();
                context.getSource().sendSuccess(() -> Component.literal(
                        "  Blast Pit: final anchor (" + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ") | awaiting chunk load"), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("  Blast Pit: not yet initialized"), false);
            }
        } else {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Blast Pit: final (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                            + " | Placed: " + state.isBlastPitPlaced()), false);
        }
        return 1;
    }

    private static int towers(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        OrsaStructureState state = OrsaStructureState.get(server);
        if (state.getTowers().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("  Towers: not yet initialized"), false);
            return 1;
        }

        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        OrsaStructureState.TowerRecord nearest = state.getNearestTower(origin);
        context.getSource().sendSuccess(() -> Component.literal("  Towers: " + state.getTowers().size()), false);
        if (nearest != null && nearest.pos() != null) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Nearest Tower: final (" + nearest.pos().getX() + ", " + nearest.pos().getY() + ", " + nearest.pos().getZ() + ")"
                            + " | Placed: " + nearest.placed()
                            + " | Architect: " + yesNo(nearest.architectTriggered())
                            + " | Aligned: " + yesNo(nearest.aligned())
                            + " | Reward: " + yesNo(nearest.rewardGranted())), false);
        } else if (nearest != null) {
            BlockPos anchor = nearest.plannedPos();
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Nearest Tower: final anchor (" + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ") | awaiting chunk load"
                            + " | Architect: " + yesNo(nearest.architectTriggered())
                            + " | Aligned: " + yesNo(nearest.aligned())
                            + " | Reward: " + yesNo(nearest.rewardGranted())), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  Nearest Tower: not yet initialized"), false);
        }
        return 1;
    }

    private static int locateOrsa(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        refreshLandmarks(server);
        context.getSource().sendSuccess(() -> Component.literal("--- ORSA Locate ---"), false);
        blastPit(context);
        towers(context);
        camps(context);
        cargoDrops(context);
        stations(context);
        return 1;
    }

    private static int locateAll(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("--- Locate Summary ---"), false);
        locateOrsa(context);
        vents(context);
        towns(context);
        return 1;
    }

    private static int vents(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        ThermalVentSavedData.VentRecord record = ThermalVentSystem.findNearestVent(overworld, origin);
        if (record == null) {
            context.getSource().sendSuccess(() -> Component.literal("  Thermal Vents: none resolved nearby"), false);
            return 1;
        }

        ApocalypseState apocalypseState = ApocalypseState.get(server);
        ThermalVentState state = describeVentState(record, apocalypseState.getPhase(), apocalypseState.getPreciseProgress(),
                overworld.getGameTime());
        int y = record.hasResolvedSurface() ? record.y() : overworld.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                record.x(), record.z()) - 1;

        context.getSource().sendSuccess(() -> Component.literal(
                "  Nearest Vent: (" + record.x() + ", " + y + ", " + record.z() + ")"
                        + " | Archetype: " + record.archetype().getSerializedName()
                        + " | Surfaced: " + yesNo(record.surfaced())
                        + " | State: " + prettyVentState(state)
                        + " | Spent: " + yesNo(record.spent())
        ), false);
        return 1;
    }

    private static ThermalVentState describeVentState(ThermalVentSavedData.VentRecord record, int phase, float progress, long worldTime) {
        return switch (record.archetype()) {
            case WARM -> {
                if (record.spent()) {
                    yield ThermalVentState.SPENT;
                }
                if (record.activatedAt() >= 0L && worldTime - record.activatedAt() < 12L * 60L * 20L) {
                    yield ThermalVentState.ACTIVE;
                }
                yield ThermalVentState.DORMANT;
            }
            case ACTIVE -> {
                if (!PhaseManager.isPhase6MidOrLater(phase, progress)) {
                    yield ThermalVentState.DORMANT;
                }
                yield record.eruptionEndTick() > worldTime ? ThermalVentState.ERUPTING : ThermalVentState.ACTIVE;
            }
            case RUPTURE -> {
                if (!PhaseManager.isVacuumActive(phase, progress)) {
                    yield ThermalVentState.DORMANT;
                }
                if (record.eruptionEndTick() > worldTime) {
                    yield ThermalVentState.ERUPTING;
                }
                if (record.nextEventTick() > 0L && worldTime >= record.nextEventTick() - (5L * 20L)) {
                    yield ThermalVentState.WARNING;
                }
                yield ThermalVentState.ACTIVE;
            }
        };
    }

    private static String prettyVentState(ThermalVentState state) {
        return switch (state) {
            case ACTIVE -> "active";
            case WARNING -> "warning";
            case ERUPTING -> "erupting";
            case SPENT -> "spent";
            default -> "dormant";
        };
    }

    private static int camps(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        OrsaStructureState state = OrsaStructureState.get(server);
        ServerLevel overworld = server.overworld();
        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        long seed = overworld.getSeed();

        int originRegionX = Math.floorDiv(origin.getX() >> 4, 24);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, 24);

        record CampCandidate(BlockPos pos, double distSq) {}
        List<CampCandidate> candidates = new java.util.ArrayList<>();

        for (int drx = -3; drx <= 3; drx++) {
            for (int drz = -3; drz <= 3; drz++) {
                int regionX = originRegionX + drx;
                int regionZ = originRegionZ + drz;

                int[] pos = CampPlacement.getCampBlockPos(seed, regionX, regionZ);
                if (pos == null) {
                    continue;
                }

                if (!CampPlacement.isEligibleCampSite(overworld, pos[0], pos[1])) {
                    continue;
                }

                double distSq = (pos[0] - origin.getX()) * (long) (pos[0] - origin.getX())
                        + (pos[1] - origin.getZ()) * (long) (pos[1] - origin.getZ());
                candidates.add(new CampCandidate(new BlockPos(pos[0], 0, pos[1]), distSq));
            }
        }

        candidates.sort(Comparator.comparingDouble(CampCandidate::distSq));
        CampCandidate chosen = null;
        for (CampCandidate c : candidates) {
            if (c.distSq() > CAMP_SKIP_RADIUS_SQ) {
                chosen = c;
                break;
            }
        }

        if (chosen != null) {
            int dist = (int) Math.sqrt(chosen.distSq());
            final BlockPos camp = chosen.pos();
            int cx = camp.getX() >> 4;
            int cz = camp.getZ() >> 4;
            boolean built = state.isCampBuilt(cx, cz);
            CampSatelliteState satelliteState = CampSatelliteState.get(server);
            boolean hasLinkedVehicle;
            BlockPos vehiclePos = null;
            if (built) {
                if (!satelliteState.hasDecision(cx, cz)) {
                    BlockPos resolvedCampCenter = FrozenEvacVehiclePlacement.resolveCampCenter(
                            overworld,
                            camp.getX(),
                            camp.getZ()
                    );
                    FrozenEvacVehiclePlacement.ensureCampSatellite(overworld, resolvedCampCenter);
                }
                hasLinkedVehicle = satelliteState.hasLinkedVehicle(cx, cz);
                vehiclePos = satelliteState.getVehicleCenter(cx, cz);
            } else {
                int previewY = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        camp.getX(), camp.getZ());
                FrozenEvacVehiclePlacement.VehiclePlan vehiclePlan = FrozenEvacVehiclePlacement.createVehiclePlan(
                        overworld,
                        new BlockPos(camp.getX(), previewY, camp.getZ())
                );
                hasLinkedVehicle = vehiclePlan != null;
                if (vehiclePlan != null) {
                    vehiclePos = vehiclePlan.center();
                }
            }
            final BlockPos finalVehiclePos = vehiclePos;
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Nearest Camp: (" + camp.getX() + ", " + camp.getZ() + ")"
                            + " | ~" + dist + " blocks"
                            + (built ? " | Built" : " | Awaiting chunk load")
                            + (hasLinkedVehicle ? " | Adjacent evac vehicle"
                            + (finalVehiclePos != null
                            ? " at (" + finalVehiclePos.getX() + ", " + finalVehiclePos.getZ() + ")"
                            : "")
                            : "")), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  Camps: none eligible in nearby regions"), false);
        }
        return 1;
    }

    private static int cargoDrops(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel overworld = server.overworld();
        CargoDropState state = CargoDropState.get(server);
        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        long seed = overworld.getSeed();

        int originRegionX = Math.floorDiv(origin.getX() >> 4, 28);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, 28);

        record CargoCandidate(BlockPos pos, double distSq, boolean built) {}
        List<CargoCandidate> candidates = new java.util.ArrayList<>();

        for (int drx = -4; drx <= 4; drx++) {
            for (int drz = -4; drz <= 4; drz++) {
                int regionX = originRegionX + drx;
                int regionZ = originRegionZ + drz;

                int[] pos = CargoDropPlacement.getCargoDropBlockPos(seed, regionX, regionZ);
                if (pos == null) {
                    continue;
                }
                if (!CargoDropPlacement.isEligibleCargoDropSite(overworld, pos[0], pos[1])) {
                    continue;
                }
                if (!CargoDropPlacement.isOutsideSpawnBuffer(overworld, pos[0], pos[1])) {
                    continue;
                }

                int chunkX = pos[0] >> 4;
                int chunkZ = pos[1] >> 4;
                boolean built = state.isCargoDropBuilt(chunkX, chunkZ);
                if (!built && state.isCargoDropEvaluated(chunkX, chunkZ)) {
                    continue;
                }

                BlockPos displayPos = built
                        ? state.getCargoDropCenter(chunkX, chunkZ)
                        : CargoDropPlacement.getCargoDropDisplayPos(seed, new BlockPos(pos[0], 0, pos[1]));
                if (displayPos == null) {
                    displayPos = new BlockPos(pos[0], 0, pos[1]);
                }
                double distSq = (displayPos.getX() - origin.getX()) * (long) (displayPos.getX() - origin.getX())
                        + (displayPos.getZ() - origin.getZ()) * (long) (displayPos.getZ() - origin.getZ());
                candidates.add(new CargoCandidate(displayPos, distSq, built));
            }
        }

        candidates.sort(Comparator.comparingDouble(CargoCandidate::distSq));
        CargoCandidate chosen = null;
        for (CargoCandidate candidate : candidates) {
            if (candidate.distSq() > CARGO_SKIP_RADIUS_SQ) {
                chosen = candidate;
                break;
            }
        }

        if (chosen != null) {
            int dist = (int) Math.sqrt(chosen.distSq());
            final BlockPos cargo = chosen.pos();
            final boolean built = chosen.built();
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Nearest Cargo Drop: (" + cargo.getX() + ", " + cargo.getZ() + ")"
                            + " | ~" + dist + " blocks"
                            + (built ? " | Built" : " | Awaiting chunk load")), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  Cargo Drops: none eligible in nearby regions"), false);
        }
        return 1;
    }

    private static int stations(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ServerLevel overworld = server.overworld();
        MonitoringStationState state = MonitoringStationState.get(server);
        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        long seed = overworld.getSeed();

        int originRegionX = Math.floorDiv(origin.getX() >> 4, 32);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, 32);

        record StationCandidate(BlockPos pos, double distSq, boolean built, int chunkX, int chunkZ) {}
        List<StationCandidate> candidates = new java.util.ArrayList<>();

        for (int drx = -3; drx <= 3; drx++) {
            for (int drz = -3; drz <= 3; drz++) {
                int regionX = originRegionX + drx;
                int regionZ = originRegionZ + drz;

                int[] pos = MonitoringStationPlacement.getStationBlockPos(seed, regionX, regionZ);
                if (pos == null) {
                    continue;
                }

                if (!MonitoringStationPlacement.isEligibleStationSite(overworld, pos[0], pos[1])) {
                    continue;
                }
                if (!MonitoringStationPlacement.isOutsideSpawnBuffer(overworld, pos[0], pos[1])) {
                    continue;
                }

                int chunkX = pos[0] >> 4;
                int chunkZ = pos[1] >> 4;
                boolean built = state.isStationBuilt(chunkX, chunkZ);
                if (!built && state.isStationEvaluated(chunkX, chunkZ)) {
                    continue;
                }
                BlockPos displayPos = new BlockPos(pos[0], 0, pos[1]);

                double distSq = (displayPos.getX() - origin.getX()) * (long) (displayPos.getX() - origin.getX())
                        + (displayPos.getZ() - origin.getZ()) * (long) (displayPos.getZ() - origin.getZ());
                candidates.add(new StationCandidate(displayPos, distSq, built, chunkX, chunkZ));
            }
        }

        candidates.sort(Comparator.comparingDouble(StationCandidate::distSq));
        StationCandidate chosen = null;
        for (StationCandidate candidate : candidates) {
            if (candidate.distSq() > STATION_SKIP_RADIUS_SQ) {
                chosen = candidate;
                break;
            }
        }

        if (chosen != null) {
            if (!chosen.built()) {
                MonitoringStationPlacement.queueStationPlacement(overworld, chosen.chunkX(), chosen.chunkZ());
            }
            int dist = (int) Math.sqrt(chosen.distSq());
            final BlockPos station = chosen.pos();
            final boolean built = chosen.built();
            context.getSource().sendSuccess(() -> Component.literal(
                    "  Nearest Monitoring Station: (" + station.getX() + ", " + station.getZ() + ")"
                            + " | ~" + dist + " blocks"
                            + (built ? " | Built" : " | Awaiting chunk load")), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  Monitoring Stations: none eligible in nearby regions"), false);
        }
        return 1;
    }

    private static int towns(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        Holder<Structure> holder = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolder(FROZEN_TOWN)
                .orElse(null);
        if (holder == null) {
            context.getSource().sendFailure(Component.literal("  Frozen Town structure is not registered"));
            return 0;
        }

        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        Pair<BlockPos, Holder<Structure>> result = level.getChunkSource()
                .getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(holder), origin, 200, false);

        if (result == null) {
            context.getSource().sendSuccess(() -> Component.literal("  Frozen Towns: none found within search radius"), false);
            return 1;
        }

        BlockPos town = result.getFirst();
        int dx = town.getX() - origin.getX();
        int dz = town.getZ() - origin.getZ();
        int dist = (int) Math.sqrt(dx * (long) dx + dz * (long) dz);
        context.getSource().sendSuccess(() -> Component.literal(
                "  Nearest Frozen Town: (" + town.getX() + ", " + town.getZ() + ")"
                        + " | ~" + dist + " blocks"), false);
        return 1;
    }

    private static void refreshLandmarks(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        for (int i = 0; i < 2; i++) {
            BlastPitPlanner.ensurePlanned(overworld);
            TowerPlanner.ensurePlanned(overworld);
        }
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }
}
