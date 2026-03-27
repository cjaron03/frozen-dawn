package com.frozendawn.world;

import com.frozendawn.block.CampRadioBlock;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Procedural builder for ORSA field camp structures.
 * <p>
 * Layout (~12x12 blocks):
 * - Central radio on a polished deepslate table
 * - Two A-frame tents (wool + slab roofs, open fronts)
 * - Supply crates with early-game loot + frozen food
 * - Flag pole (5 fence posts + ORSA flag block)
 * - Campfire / lanterns for atmosphere
 * <p>
 * Phase-dependent appearance:
 * - Phase 1-2: recently abandoned, intact
 * - Phase 3-4: partially collapsed, some snow coverage
 * - Phase 5-6: mostly buried, flag tip barely visible
 */
public final class CampStructureBuilder {

    private static final int CAMP_RADIUS = 6;
    private static final int FLAG_HEIGHT = 6;

    private CampStructureBuilder() {
    }

    public static void place(ServerLevel level, BlockPos center, boolean hasLinkedVehicle) {
        int phase = getCurrentPhase(level);
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        // Grade the terrain flat
        gradeTerrain(level, cx, cy, cz);

        // Clear air above
        clearAbove(level, cx, cy, cz);

        // Place ground cover
        placeGroundCover(level, cx, cy, cz);

        // Central radio setup
        placeRadioSetup(level, cx, cy, cz);

        // Tent 1 (north side)
        placeTent(level, cx - 3, cy, cz - 4, Direction.SOUTH, phase);

        // Tent 2 (south side)
        placeTent(level, cx + 1, cy, cz + 2, Direction.NORTH, phase);

        // Supply crates
        placeSupplyCrates(level, cx, cy, cz, phase, hasLinkedVehicle);

        // Flag pole
        placeFlagPole(level, cx - 5, cy, cz, phase);

        // Campfire
        placeCampfire(level, cx + 2, cy, cz - 1, phase);

        // Misc props
        placeProps(level, cx, cy, cz, phase);

        // Phase-dependent snow burial
        if (phase >= 3) {
            applySnowCoverage(level, cx, cy, cz, phase);
        }
    }

    private static void gradeTerrain(ServerLevel level, int cx, int cy, int cz) {
        for (int dx = -CAMP_RADIUS; dx <= CAMP_RADIUS; dx++) {
            for (int dz = -CAMP_RADIUS; dz <= CAMP_RADIUS; dz++) {
                int x = cx + dx;
                int z = cz + dz;

                // Fill gaps below camp level
                for (int y = cy - 1; y >= cy - 3; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isSolid()) {
                        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                    }
                }

                // Remove blocks above camp level in footprint
                for (int y = cy; y <= cy + 8; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }

    private static void clearAbove(ServerLevel level, int cx, int cy, int cz) {
        for (int dx = -CAMP_RADIUS; dx <= CAMP_RADIUS; dx++) {
            for (int dz = -CAMP_RADIUS; dz <= CAMP_RADIUS; dz++) {
                for (int dy = 0; dy <= 10; dy++) {
                    BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
                            || state.is(Blocks.POWDER_SNOW)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }

    private static void placeGroundCover(ServerLevel level, int cx, int cy, int cz) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos pos = new BlockPos(cx + dx, cy - 1, cz + dz);
                if (Math.abs(dx) + Math.abs(dz) <= 6) {
                    level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void placeRadioSetup(ServerLevel level, int cx, int cy, int cz) {
        // Table (polished deepslate)
        level.setBlock(new BlockPos(cx, cy, cz),
                Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);

        // Radio on top of table
        level.setBlock(new BlockPos(cx, cy + 1, cz),
                ModBlocks.CAMP_RADIO.get().defaultBlockState()
                        .setValue(CampRadioBlock.FACING, Direction.SOUTH), 3);

        // Side table items
        level.setBlock(new BlockPos(cx + 1, cy, cz),
                Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(cx + 1, cy + 1, cz),
                Blocks.LANTERN.defaultBlockState(), 3);
    }

    /**
     * A-frame tent: 4 wide, 3 deep, 3 tall.
     * Wool walls on two sides, slab roof, open front.
     */
    private static void placeTent(ServerLevel level, int startX, int baseY,
                                  int startZ, Direction opening, int phase) {
        BlockState wool = Blocks.LIGHT_GRAY_WOOL.defaultBlockState();
        BlockState slab = Blocks.SPRUCE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();

        int depth = 3;
        int width = 4;

        // Floor
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                level.setBlock(new BlockPos(startX + dx, baseY, startZ + dz), floor, 3);
            }
        }

        // Phase 5-6: tent is collapsed, skip walls/roof
        if (phase >= 5) {
            // Just scatter some wool blocks on the ground
            level.setBlock(new BlockPos(startX + 1, baseY + 1, startZ + 1), wool, 3);
            return;
        }

        // Walls (sides)
        for (int dz = 0; dz < depth; dz++) {
            // Left wall
            level.setBlock(new BlockPos(startX, baseY + 1, startZ + dz), wool, 3);
            // Right wall
            level.setBlock(new BlockPos(startX + width - 1, baseY + 1, startZ + dz), wool, 3);

            // Phase 3-4: some wall blocks missing
            if (phase >= 3 && dz == 1) {
                level.removeBlock(new BlockPos(startX + width - 1, baseY + 1, startZ + dz), false);
            }
        }

        // Back wall
        int backZ = opening == Direction.SOUTH ? startZ : startZ + depth - 1;
        for (int dx = 0; dx < width; dx++) {
            level.setBlock(new BlockPos(startX + dx, baseY + 1, backZ), wool, 3);
        }

        // Roof (slabs on top)
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                BlockPos roofPos = new BlockPos(startX + dx, baseY + 2, startZ + dz);
                // Phase 3-4: some roof blocks missing
                if (phase >= 3 && (dx + dz) % 3 == 0) {
                    continue;
                }
                level.setBlock(roofPos, slab, 3);
            }
        }

        // Interior: crafting table or barrel
        level.setBlock(new BlockPos(startX + 1, baseY + 1, startZ + 1),
                Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
    }

    private static void placeSupplyCrates(ServerLevel level, int cx, int cy, int cz, int phase, boolean hasLinkedVehicle) {
        // Crate 1: food + supplies (west of radio)
        BlockPos crate1Pos = new BlockPos(cx - 2, cy, cz + 1);
        placeCrateWithLoot(level, crate1Pos, Direction.EAST, phase, true, hasLinkedVehicle);

        // Crate 2: tools + materials (east of radio)
        BlockPos crate2Pos = new BlockPos(cx + 3, cy, cz - 2);
        placeCrateWithLoot(level, crate2Pos, Direction.WEST, phase, false, hasLinkedVehicle);
    }

    private static void placeCrateWithLoot(ServerLevel level, BlockPos pos,
                                           Direction facing, int phase,
                                           boolean foodCrate, boolean hasLinkedVehicle) {
        // Phase 5+: crate may be buried under snow, skip placement
        if (phase >= 5) {
            return;
        }

        BlockState crateState = ModBlocks.ORSA_SUPPLY_CRATE.get().defaultBlockState()
                .setValue(BarrelBlock.FACING, Direction.UP)
                .setValue(BarrelBlock.OPEN, false);
        level.setBlock(pos, crateState, 3);

        if (level.getBlockEntity(pos) instanceof BarrelBlockEntity crate) {
            if (foodCrate) {
                crate.setItem(0, new ItemStack(Items.COOKED_BEEF, 6));
                crate.setItem(1, new ItemStack(Items.BREAD, 4));
                crate.setItem(2, new ItemStack(ModItems.FROZEN_MEAT.get(), 3));
                crate.setItem(4, new ItemStack(Items.COAL, 8));
                crate.setItem(9, createFieldReportDocument());
                crate.setItem(13, new ItemStack(Items.LEATHER, 4));
                crate.setItem(18, new ItemStack(Items.TORCH, 6));
            } else {
                crate.setItem(0, new ItemStack(Items.STONE_PICKAXE));
                crate.setItem(1, new ItemStack(Items.STONE_AXE));
                crate.setItem(4, new ItemStack(Items.IRON_INGOT, 3));
                crate.setItem(9, hasLinkedVehicle ? createTransferLogDocument() : createPersonalNoteDocument());
                crate.setItem(13, new ItemStack(Items.STRING, 4));
                crate.setItem(18, new ItemStack(Items.FLINT_AND_STEEL));
            }
        }
    }

    private static void placeFlagPole(ServerLevel level, int fx, int baseY, int fz, int phase) {
        // Fence post pole
        int poleHeight = FLAG_HEIGHT;
        if (phase >= 5) {
            poleHeight = 2; // barely visible above snow
        } else if (phase >= 3) {
            poleHeight = 4; // partially buried
        }

        for (int y = 0; y < poleHeight; y++) {
            level.setBlock(new BlockPos(fx, baseY + y, fz),
                    Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
        }

        // Flag on top
        level.setBlock(new BlockPos(fx, baseY + poleHeight, fz),
                ModBlocks.ORSA_FLAG.get().defaultBlockState(), 3);
    }

    private static void placeCampfire(ServerLevel level, int x, int baseY, int z, int phase) {
        if (phase >= 4) {
            // Dead campfire in later phases
            level.setBlock(new BlockPos(x, baseY, z),
                    Blocks.CAMPFIRE.defaultBlockState()
                            .setValue(CampfireBlock.LIT, false), 3);
        } else {
            level.setBlock(new BlockPos(x, baseY, z),
                    Blocks.CAMPFIRE.defaultBlockState()
                            .setValue(CampfireBlock.LIT, false), 3);
        }

        // Seats around campfire
        level.setBlock(new BlockPos(x - 1, baseY, z),
                Blocks.SPRUCE_LOG.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x + 1, baseY, z),
                Blocks.SPRUCE_LOG.defaultBlockState(), 3);
    }

    private static void placeProps(ServerLevel level, int cx, int baseY, int cz, int phase) {
        // Lantern near entrance
        if (phase < 5) {
            level.setBlock(new BlockPos(cx - 4, baseY, cz - 2),
                    Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }

        // Scattered iron bars (antenna wreckage feel)
        level.setBlock(new BlockPos(cx + 4, baseY, cz + 3),
                Blocks.IRON_BARS.defaultBlockState(), 3);
    }

    /**
     * Covers parts of the camp with snow layers proportional to the phase.
     */
    private static void applySnowCoverage(ServerLevel level, int cx, int cy, int cz, int phase) {
        int coverRadius = CAMP_RADIUS;
        // Higher phase = more snow coverage
        float coverChance = switch (phase) {
            case 3 -> 0.15f;
            case 4 -> 0.35f;
            case 5 -> 0.60f;
            default -> 0.85f; // phase 6
        };

        for (int dx = -coverRadius; dx <= coverRadius; dx++) {
            for (int dz = -coverRadius; dz <= coverRadius; dz++) {
                // Deterministic pseudo-random based on position
                long hash = BlockPos.asLong(cx + dx, cy, cz + dz) * 2654435761L;
                float roll = Math.floorMod(hash >> 12, 100) / 100.0f;
                if (roll >= coverChance) {
                    continue;
                }

                // Find the top solid block in the camp column
                for (int dy = 6; dy >= 0; dy--) {
                    BlockPos below = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockPos above = below.above();
                    if (level.getBlockState(below).isSolid() && level.getBlockState(above).isAir()) {
                        int layers = phase >= 5 ? 3 + (int) (roll * 5) : 1 + (int) (roll * 3);
                        layers = Math.min(layers, 8);
                        level.setBlock(above, Blocks.SNOW.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS,
                                        layers), 3);
                        break;
                    }
                }
            }
        }

        // Phase 5-6: bury lower crate positions with snow blocks
        if (phase >= 5) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = new BlockPos(cx + dx, cy, cz + dz);
                    if (level.getBlockState(pos).isAir()) {
                        long h = BlockPos.asLong(cx + dx, cy, cz + dz) * 48271L;
                        if (Math.floorMod(h >> 8, 100) < 40) {
                            level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    private static int getCurrentPhase(ServerLevel level) {
        ApocalypseState state = ApocalypseState.get(level.getServer());
        return state.getPhase();
    }

    private static ItemStack createFieldReportDocument() {
        ItemStack doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME, Component.literal("ORSA Field Report"));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", "camp_field_report");
        doc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return doc;
    }

    private static ItemStack createPersonalNoteDocument() {
        ItemStack doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME,
                Component.literal("Crumpled Note - \"Day 14: still no extraction\""));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", "camp_personal_note");
        doc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return doc;
    }

    private static ItemStack createTransferLogDocument() {
        ItemStack doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME, Component.literal("Camp Transfer Exception Log"));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", "camp_transfer_log");
        doc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return doc;
    }

    public static void syncTransferDocument(ServerLevel level, BlockPos center, boolean hasLinkedVehicle) {
        BlockPos cratePos = new BlockPos(center.getX() + 3, center.getY(), center.getZ() - 2);
        if (!(level.getBlockEntity(cratePos) instanceof BarrelBlockEntity crate)) {
            return;
        }
        crate.setItem(9, hasLinkedVehicle ? createTransferLogDocument() : createPersonalNoteDocument());
        crate.setChanged();
    }
}
