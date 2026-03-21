package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places rare ORSA Communication Towers at a stored final anchor chosen by
 * OrsaStructureState. Once the surrounding area is loaded, the structure builder
 * handles terrain preparation directly.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class TowerPlacement {

    private static final int FOOTPRINT_RADIUS = 18;
    private static final int DRY_BUFFER = 12;
    private static final Set<Long> pendingTowerPlacements = ConcurrentHashMap.newKeySet();

    private TowerPlacement() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        OrsaStructureState state = OrsaStructureState.get(level.getServer());
        for (OrsaStructureState.TowerRecord tower : state.getTowers()) {
            if (tower.placed() || tower.plannedPos() == null) {
                continue;
            }
            int chunkX = tower.plannedPos().getX() >> 4;
            int chunkZ = tower.plannedPos().getZ() >> 4;
            if (event.getChunk().getPos().x == chunkX && event.getChunk().getPos().z == chunkZ) {
                pendingTowerPlacements.add(tower.id());
            }
        }
    }

    public static void tickPlacement(ServerLevel overworld) {
        if (overworld.players().isEmpty()) {
            return;
        }
        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());
        for (OrsaStructureState.TowerRecord tower : state.getTowers()) {
            if (!tower.placed() && tower.plannedPos() != null && isPlacementAreaLoaded(overworld, tower.plannedPos())) {
                pendingTowerPlacements.add(tower.id());
            }
        }
        if (pendingTowerPlacements.isEmpty()) {
            return;
        }

        for (Long towerId : Set.copyOf(pendingTowerPlacements)) {
            OrsaStructureState.TowerRecord tower = state.getTowerById(towerId);
            if (tower == null || tower.placed()) {
                pendingTowerPlacements.remove(towerId);
                continue;
            }
            if (tower.placed()) {
                pendingTowerPlacements.remove(tower.id());
                continue;
            }

            BlockPos resolvedPos = ensureTowerResolved(overworld, tower.id());
            if (resolvedPos == null) {
                continue;
            }

            if (!isPlacementAreaLoaded(overworld, resolvedPos)) {
                continue;
            }
            TowerStructureBuilder.place(overworld, resolvedPos, tower.id());
            state.setTowerPlaced(tower.id(), resolvedPos);
            pendingTowerPlacements.remove(tower.id());
            FrozenDawn.LOGGER.info("ORSA Communication Tower placed at ({}, {}, {})",
                    resolvedPos.getX(), resolvedPos.getY(), resolvedPos.getZ());
        }
    }

    public static BlockPos ensureTowerResolved(ServerLevel overworld, long towerId) {
        OrsaStructureState state = OrsaStructureState.get(overworld.getServer());
        OrsaStructureState.TowerRecord tower = state.getTowerById(towerId);
        if (tower == null) {
            return null;
        }
        return tower.pos() != null ? tower.pos() : tower.plannedPos();
    }

    private static boolean isPlacementAreaLoaded(ServerLevel level, BlockPos target) {
        return isFootprintLoaded(level, target.getX(), target.getZ(), FOOTPRINT_RADIUS + DRY_BUFFER + 4);
    }

    private static boolean isFootprintLoaded(ServerLevel level, int centerX, int centerZ, int radius) {
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;

        for (int x = minX; x <= maxX; x += 16) {
            for (int z = minZ; z <= maxZ; z += 16) {
                if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight(), z))) {
                    return false;
                }
            }
        }

        return level.isLoaded(new BlockPos(maxX, level.getMinBuildHeight(), maxZ));
    }

    public static void completeAlignment(ServerLevel level, long towerId, ServerPlayer player) {
        OrsaStructureState state = OrsaStructureState.get(level.getServer());
        OrsaStructureState.TowerRecord tower = state.getTowerById(towerId);
        if (tower == null) {
            tower = state.findTowerNear(player.blockPosition(), 24);
            if (tower == null) {
                return;
            }
            towerId = tower.id();
        }

        state.setTowerAligned(towerId, true);
        boolean grantReward = !tower.rewardGranted();
        if (grantReward) {
            ItemStack compass = new ItemStack(ModItems.ACHERONITE_COMPASS.get());
            if (!player.addItem(compass)) {
                player.drop(compass, false);
            }
            state.setTowerRewardGranted(towerId, true);
        }

        BlastPitPlacement.ensureBlastPitResolved(level);
        sendAlignmentResults(level, towerId, player, grantReward);
    }

    public static void sendAlignmentResults(ServerLevel level, long towerId, ServerPlayer player, boolean includeRewardMessage) {
        OrsaStructureState state = OrsaStructureState.get(level.getServer());
        OrsaStructureState.TowerRecord tower = state.getTowerById(towerId);
        if (tower == null) {
            return;
        }
        BlockPos blastPit = BlastPitPlacement.ensureBlastPitResolved(level);
        player.sendSystemMessage(Component.literal("ORSA uplink stabilized. Satellite locator recovered."));
        if (blastPit != null) {
            player.sendSystemMessage(Component.literal(
                    "Nearest ORSA Blast Pit: X " + blastPit.getX() + " Y " + blastPit.getY() + " Z " + blastPit.getZ()));
        } else {
            player.sendSystemMessage(Component.literal("Nearest ORSA Blast Pit: resolving final anchor..."));
        }
        if (includeRewardMessage) {
            player.sendSystemMessage(Component.literal("You recover an Acheronite Compass from the tower cache."));
        }
    }

    public static void reset() {
        pendingTowerPlacements.clear();
    }
}
