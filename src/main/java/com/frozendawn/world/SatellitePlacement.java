package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Deferred placement of the crashed ORSA Relay Satellite.
 * Listens for chunk loads; when the target chunk loads, flags for placement
 * on the next server tick (ChunkEvent.Load fires before chunk is fully promoted,
 * so direct setBlock calls can cause deadlocks).
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public class SatellitePlacement {

    private static volatile boolean pendingPlacement = false;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (pendingPlacement) return;
        if (!FrozenDawnConfig.ENABLE_WIN_CONDITION.get()) return;

        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != ServerLevel.OVERWORLD) return;

        MinecraftServer server = level.getServer();
        WinConditionState winState = WinConditionState.get(server);

        if (winState.isSatellitePlaced()) return;

        BlockPos targetPos = winState.getSatellitePos();
        if (targetPos == null) return;

        int chunkX = targetPos.getX() >> 4;
        int chunkZ = targetPos.getZ() >> 4;
        if (event.getChunk().getPos().x != chunkX
                || event.getChunk().getPos().z != chunkZ) return;

        pendingPlacement = true;
    }

    /**
     * Called from WorldTickHandler each server tick.
     * If a chunk load flagged pending placement, execute it now (safe context).
     */
    public static void tickPlacement(ServerLevel overworld) {
        if (!pendingPlacement) return;
        pendingPlacement = false;

        MinecraftServer server = overworld.getServer();
        WinConditionState winState = WinConditionState.get(server);
        if (winState.isSatellitePlaced()) return;

        BlockPos targetPos = winState.getSatellitePos();
        if (targetPos == null) return;

        if (!overworld.isLoaded(targetPos)) return;

        int surfaceY = overworld.getHeight(Heightmap.Types.WORLD_SURFACE,
                targetPos.getX(), targetPos.getZ());
        winState.resolveSatelliteY(overworld);

        BlockPos placePos = new BlockPos(targetPos.getX(), surfaceY, targetPos.getZ());
        placeStructure(overworld, placePos);
        winState.setSatellitePlaced(true);
        FrozenDawn.LOGGER.info("Crashed ORSA satellite placed at ({}, {}, {})",
                placePos.getX(), placePos.getY(), placePos.getZ());
    }

    /**
     * Places the crashed satellite structure at the given surface position.
     * ~11x6x9 debris field: main hull, wing sections, scorched plating, antenna,
     * impact crater, and black box chest with loot.
     */
    private static void placeStructure(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        // === IMPACT CRATER — depression around crash site ===
        for (int dx = -3; dx <= 4; dx++) {
            for (int dz = -2; dz <= 3; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= 3.5) {
                    // Dig 1-2 blocks down for crater effect
                    setBlock(level, cx + dx, cy - 1, cz + dz, Blocks.COARSE_DIRT.defaultBlockState());
                    if (dist <= 2.0) {
                        setBlock(level, cx + dx, cy - 2, cz + dz, Blocks.COARSE_DIRT.defaultBlockState());
                    }
                }
            }
        }

        // === MAIN HULL — central iron block mass (partially buried) ===
        // Lower hull (buried)
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                setBlock(level, cx + dx, cy - 2, cz + dz, Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
        // Upper hull (visible)
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                setBlock(level, cx + dx, cy - 1, cz + dz, Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
        // Hull top (partially exposed)
        setBlock(level, cx, cy, cz, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx + 1, cy, cz, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx, cy, cz + 1, Blocks.IRON_BLOCK.defaultBlockState());

        // === NOSE SECTION — tilted upward from impact ===
        setBlock(level, cx - 2, cy - 1, cz, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx - 2, cy, cz, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx - 2, cy + 1, cz, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx - 3, cy + 1, cz, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx - 3, cy + 2, cz, Blocks.IRON_BLOCK.defaultBlockState());
        // Nose tip
        setBlock(level, cx - 4, cy + 2, cz, Blocks.SMOOTH_STONE.defaultBlockState());

        // === WING STUBS — broken off, scattered ===
        // Left wing stub
        setBlock(level, cx, cy - 1, cz - 2, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx + 1, cy - 1, cz - 2, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx, cy - 1, cz - 3, Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());
        // Right wing stub
        setBlock(level, cx, cy - 1, cz + 3, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx + 1, cy - 1, cz + 3, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx + 1, cy - 1, cz + 4, Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState());

        // === TAIL SECTION — broken, trailing debris ===
        setBlock(level, cx + 3, cy - 2, cz, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx + 3, cy - 2, cz + 1, Blocks.IRON_BLOCK.defaultBlockState());
        setBlock(level, cx + 4, cy - 2, cz, Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState());
        setBlock(level, cx + 4, cy - 2, cz + 1, Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState());
        setBlock(level, cx + 5, cy - 2, cz, Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());

        // === SCORCHED PLATING — heat-damaged panels scattered around ===
        setBlock(level, cx + 2, cy - 1, cz + 2, Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());
        setBlock(level, cx + 3, cy - 1, cz - 1, Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState());
        setBlock(level, cx - 1, cy - 1, cz + 2, Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState());
        setBlock(level, cx - 1, cy - 1, cz - 1, Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState());
        setBlock(level, cx + 2, cy, cz - 1, Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState());
        // Loose panels on surface
        setBlock(level, cx - 3, cy, cz + 2, Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState());
        setBlock(level, cx + 4, cy - 1, cz + 2, Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());

        // === ANTENNA ARRAY — iron bars sticking up ===
        setBlock(level, cx + 1, cy + 1, cz + 1, Blocks.IRON_BARS.defaultBlockState());
        setBlock(level, cx + 1, cy + 2, cz + 1, Blocks.IRON_BARS.defaultBlockState());
        setBlock(level, cx + 1, cy + 3, cz + 1, Blocks.IRON_BARS.defaultBlockState());
        // Second antenna stub
        setBlock(level, cx - 1, cy + 1, cz + 1, Blocks.IRON_BARS.defaultBlockState());
        setBlock(level, cx - 1, cy + 2, cz + 1, Blocks.IRON_BARS.defaultBlockState());

        // === SOLAR PANEL DEBRIS — anvils as heavy wreckage ===
        setBlock(level, cx - 3, cy, cz - 1, Blocks.ANVIL.defaultBlockState());
        setBlock(level, cx + 3, cy - 1, cz + 2, Blocks.DAMAGED_ANVIL.defaultBlockState());

        // === WIRING — tripwire hooks as exposed circuitry ===
        setBlock(level, cx + 2, cy, cz, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
        setBlock(level, cx - 1, cy, cz - 1, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());

        // === REDSTONE DUST — sparking electronics ===
        setBlock(level, cx + 2, cy, cz + 1, Blocks.REDSTONE_WIRE.defaultBlockState());
        setBlock(level, cx - 2, cy, cz + 1, Blocks.REDSTONE_WIRE.defaultBlockState());

        // === Clear snow above the structure ===
        for (int dx = -5; dx <= 6; dx++) {
            for (int dz = -4; dz <= 5; dz++) {
                for (int dy = -1; dy <= 5; dy++) {
                    BlockPos above = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockState state = level.getBlockState(above);
                    if (state.isAir()) continue;
                    if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                            || state.is(Blocks.POWDER_SNOW)) {
                        level.removeBlock(above, false);
                    }
                }
            }
        }

        // === BLACK BOX CHEST — the main prize ===
        BlockPos chestPos = new BlockPos(cx + 1, cy, cz);
        level.removeBlock(chestPos, false);
        BlockState chestState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.SOUTH);
        level.setBlock(chestPos, chestState, 3);

        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            // Row 1: Transponder Schematic (the key item)
            chest.setItem(4, createSchematic());
            // Row 2: Satellite Log + utility items
            chest.setItem(9, createSatelliteLog());
            chest.setItem(11, new ItemStack(Items.IRON_INGOT, 12));
            chest.setItem(12, new ItemStack(Items.REDSTONE, 8));
            // Row 3: Salvageable materials
            chest.setItem(18, new ItemStack(Items.IRON_NUGGET, 16));
            chest.setItem(19, new ItemStack(Items.COPPER_INGOT, 6));
            chest.setItem(22, new ItemStack(Items.DIAMOND, 2));
            chest.setItem(25, new ItemStack(Items.GOLD_INGOT, 4));
            chest.setItem(26, new ItemStack(Items.GLASS_PANE, 8));
        }
    }

    private static void setBlock(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }

    private static ItemStack createSchematic() {
        ItemStack stack = new ItemStack(ModItems.TRANSPONDER_SCHEMATIC.get());
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("ORSA Transponder Schematic").withStyle(ChatFormatting.LIGHT_PURPLE));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", "transponder_schematic");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack createSatelliteLog() {
        ItemStack stack = new ItemStack(ModItems.SATELLITE_LOG.get());
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("ORSA Satellite System Log").withStyle(ChatFormatting.GOLD));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", "satellite_log");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
}
