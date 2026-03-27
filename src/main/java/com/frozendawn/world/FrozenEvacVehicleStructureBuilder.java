package com.frozendawn.world;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FrozenEvacVehicleStructureBuilder {

    private static final int FOOTPRINT_RADIUS = 6;

    private FrozenEvacVehicleStructureBuilder() {
    }

    public static void place(ServerLevel level, BlockPos campCenter, BlockPos vehicleCenter,
                             FrozenEvacVehiclePlacement.VehicleVariant variant) {
        int phase = getCurrentPhase(level);
        Direction facing = directionToward(vehicleCenter, campCenter);

        gradeSite(level, vehicleCenter, facing);
        clearSnowAndBrush(level, vehicleCenter, facing);
        prepareShoulder(level, vehicleCenter, facing);
        placeApproachScuffs(level, vehicleCenter, facing);
        placeVehicleShell(level, vehicleCenter, facing, variant);
        placeLootCrate(level, vehicleCenter, facing, campCenter, variant);
        placeProps(level, vehicleCenter, facing, phase, variant);
        applyPhaseBurial(level, vehicleCenter, facing, phase);
    }

    private static void gradeSite(ServerLevel level, BlockPos center, Direction facing) {
        int groundY = center.getY() - 1;
        for (int right = -4; right <= 4; right++) {
            for (int forward = -6; forward <= 6; forward++) {
                BlockPos column = toWorld(center, facing, right, 0, forward);
                BlockPos surface = new BlockPos(column.getX(), groundY, column.getZ());

                for (int y = groundY; y >= groundY - 3; y--) {
                    BlockPos fillPos = new BlockPos(column.getX(), y, column.getZ());
                    if (!level.getBlockState(fillPos).isSolid()) {
                        level.setBlock(fillPos, Blocks.DIRT.defaultBlockState(), 3);
                    }
                }

                for (int y = groundY + 1; y <= groundY + 5; y++) {
                    BlockPos clearPos = new BlockPos(column.getX(), y, column.getZ());
                    if (!level.getBlockState(clearPos).isAir()) {
                        level.removeBlock(clearPos, false);
                    }
                }

                if (Math.abs(right) <= 3 && Math.abs(forward) <= 4) {
                    level.setBlock(surface, Blocks.DIRT.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void clearSnowAndBrush(ServerLevel level, BlockPos center, Direction facing) {
        for (int right = -FOOTPRINT_RADIUS; right <= FOOTPRINT_RADIUS; right++) {
            for (int forward = -FOOTPRINT_RADIUS; forward <= FOOTPRINT_RADIUS; forward++) {
                BlockPos ground = toWorld(center, facing, right, 0, forward);
                int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        ground.getX(), ground.getZ());
                for (int y = topY; y <= topY + 4; y++) {
                    BlockPos pos = new BlockPos(ground.getX(), y, ground.getZ());
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)
                            || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                            || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                            || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.OAK_LEAVES)
                            || state.is(Blocks.SPRUCE_LEAVES) || state.is(Blocks.BIRCH_LEAVES)
                            || state.is(Blocks.JUNGLE_LEAVES) || state.is(Blocks.ACACIA_LEAVES)
                            || state.is(Blocks.DARK_OAK_LEAVES) || state.is(Blocks.MANGROVE_LEAVES)
                            || state.is(Blocks.OAK_LOG) || state.is(Blocks.SPRUCE_LOG)
                            || state.is(Blocks.BIRCH_LOG) || state.is(Blocks.JUNGLE_LOG)
                            || state.is(Blocks.ACACIA_LOG) || state.is(Blocks.DARK_OAK_LOG)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }

    private static void prepareShoulder(ServerLevel level, BlockPos center, Direction facing) {
        for (int right = -3; right <= 3; right++) {
            for (int forward = -4; forward <= 4; forward++) {
                BlockPos pos = toWorld(center, facing, right, 0, forward);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        pos.getX(), pos.getZ()) - 1;
                if (y <= level.getMinBuildHeight()) {
                    continue;
                }
                BlockPos surface = new BlockPos(pos.getX(), y, pos.getZ());
                BlockState state = switch (Math.floorMod(right * 17 + forward * 29, 4)) {
                    case 0 -> Blocks.GRAVEL.defaultBlockState();
                    case 1 -> Blocks.COARSE_DIRT.defaultBlockState();
                    case 2 -> Blocks.PACKED_MUD.defaultBlockState();
                    default -> Blocks.DIRT_PATH.defaultBlockState();
                };
                level.setBlock(surface, state, 3);
            }
        }
    }

    private static void placeApproachScuffs(ServerLevel level, BlockPos center, Direction facing) {
        for (int forward = 4; forward <= 10; forward++) {
            for (int right = -1; right <= 1; right++) {
                BlockPos pos = toWorld(center, facing, right, 0, forward);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        pos.getX(), pos.getZ()) - 1;
                if (y <= level.getMinBuildHeight()) {
                    continue;
                }
                BlockPos surface = new BlockPos(pos.getX(), y, pos.getZ());
                BlockState state = Math.abs(right) == 1
                        ? Blocks.COARSE_DIRT.defaultBlockState()
                        : Blocks.DIRT_PATH.defaultBlockState();
                level.setBlock(surface, state, 3);
            }
        }
    }

    private static void placeVehicleShell(ServerLevel level, BlockPos center, Direction facing,
                                          FrozenEvacVehiclePlacement.VehicleVariant variant) {
        BlockState body = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState trim = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState windows = Blocks.TINTED_GLASS.defaultBlockState();
        BlockState wheels = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState roof = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState hatch = Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, facing.getOpposite())
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.OPEN, true);

        for (int right = -1; right <= 1; right++) {
            for (int forward = -2; forward <= 2; forward++) {
                BlockPos floor = toWorld(center, facing, right, 0, forward);
                ensureSupportBelow(level, floor);
                if (Math.abs(right) == 1 && (forward == -1 || forward == 1)) {
                    level.setBlock(floor, wheels, 3);
                } else {
                    level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                }
            }
        }

        for (int forward = -2; forward <= 2; forward++) {
            level.setBlock(toWorld(center, facing, -1, 1, forward), body, 3);
            level.setBlock(toWorld(center, facing, 1, 1, forward), body, 3);
        }
        level.setBlock(toWorld(center, facing, 0, 1, 2), trim, 3);
        level.setBlock(toWorld(center, facing, 0, 1, 1), windows, 3);
        level.setBlock(toWorld(center, facing, 0, 1, 0), windows, 3);
        level.setBlock(toWorld(center, facing, 0, 1, -1), body, 3);
        level.setBlock(toWorld(center, facing, 0, 1, -2), Blocks.AIR.defaultBlockState(), 3);

        level.setBlock(toWorld(center, facing, -1, 2, 0), windows, 3);
        level.setBlock(toWorld(center, facing, 1, 2, 0), windows, 3);
        level.setBlock(toWorld(center, facing, 0, 2, 1), windows, 3);
        level.setBlock(toWorld(center, facing, 0, 2, 0), roof, 3);
        level.setBlock(toWorld(center, facing, -1, 2, -1), roof, 3);
        level.setBlock(toWorld(center, facing, 0, 2, -1), roof, 3);
        level.setBlock(toWorld(center, facing, 1, 2, -1), roof, 3);

        level.setBlock(toWorld(center, facing, 0, 2, -2), hatch, 3);
        level.setBlock(toWorld(center, facing, 0, 1, -3), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        level.setBlock(toWorld(center, facing, 0, 1, 3), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);

        if (variant == FrozenEvacVehiclePlacement.VehicleVariant.ROADSIDE_BREAKDOWN) {
            level.setBlock(toWorld(center, facing, 0, 2, 2), Blocks.IRON_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.FACING, facing)
                    .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                    .setValue(TrapDoorBlock.OPEN, true), 3);
        }
    }

    private static void placeLootCrate(ServerLevel level, BlockPos center, Direction facing, BlockPos campCenter,
                                       FrozenEvacVehiclePlacement.VehicleVariant variant) {
        BlockPos cratePos = toWorld(center, facing, 0, 1, -1);
        BlockState crateState = ModBlocks.ORSA_SUPPLY_CRATE.get().defaultBlockState()
                .setValue(BarrelBlock.FACING, Direction.UP)
                .setValue(BarrelBlock.OPEN, false);
        level.setBlock(cratePos, crateState, 3);

        if (!(level.getBlockEntity(cratePos) instanceof BarrelBlockEntity crate)) {
            return;
        }

        List<ItemStack> loot = buildLoot(campCenter, variant);
        for (int i = 0; i < crate.getContainerSize(); i++) {
            crate.setItem(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < loot.size() && i < crate.getContainerSize(); i++) {
            crate.setItem(i, loot.get(i));
        }
        crate.setChanged();
    }

    private static List<ItemStack> buildLoot(BlockPos campCenter, FrozenEvacVehiclePlacement.VehicleVariant variant) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(createTranscriptDocument(variant));
        loot.add(createTransferOrder(campCenter));
        loot.add(createContractorPass());
        loot.add(new ItemStack(Items.BREAD, 2));
        loot.add(new ItemStack(Items.COAL, 4));
        loot.add(new ItemStack(Items.LEATHER, 2));

        switch (variant) {
            case ABANDONED_EMPTY -> {
                loot.add(createStationClosureMemo(campCenter));
                loot.add(createPassengerManifest());
                loot.add(new ItemStack(Items.COOKED_BEEF, 2));
            }
            case ROADSIDE_BREAKDOWN -> {
                loot.add(createPickupDelayNotice(campCenter));
                loot.add(createPassengerManifest());
                loot.add(new ItemStack(ModItems.CRYO_FUEL.get()));
            }
            case FAILED_TRANSFER -> {
                loot.add(createPickupDelayNotice(campCenter));
                loot.add(createStationClosureMemo(campCenter));
                loot.add(createHandwrittenNote());
                loot.add(new ItemStack(ModItems.TATTERED_CLOTHING_SCRAP.get(), 2));
            }
        }

        return loot;
    }

    private static void placeProps(ServerLevel level, BlockPos center, Direction facing, int phase,
                                   FrozenEvacVehiclePlacement.VehicleVariant variant) {
        level.setBlock(toWorld(center, facing, -2, 0, -3), Blocks.WHITE_CARPET.defaultBlockState(), 3);
        level.setBlock(toWorld(center, facing, -1, 0, -4), Blocks.WHITE_CARPET.defaultBlockState(), 3);
        level.setBlock(toWorld(center, facing, 2, 0, -2), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.FACING, facing.getClockWise()), 3);

        if (variant == FrozenEvacVehiclePlacement.VehicleVariant.ROADSIDE_BREAKDOWN) {
            level.setBlock(toWorld(center, facing, 2, 0, 2), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
            level.setBlock(toWorld(center, facing, -2, 0, 1), Blocks.GRAY_CARPET.defaultBlockState(), 3);
        }

        if (variant == FrozenEvacVehiclePlacement.VehicleVariant.FAILED_TRANSFER) {
            if (phase >= 3) {
                level.setBlock(toWorld(center, facing, 0, 1, 0), Blocks.SKELETON_SKULL.defaultBlockState(), 3);
            } else {
                level.setBlock(toWorld(center, facing, 1, 0, -3), Blocks.BROWN_CARPET.defaultBlockState(), 3);
                level.setBlock(toWorld(center, facing, 1, 0, -4), Blocks.GRAY_CARPET.defaultBlockState(), 3);
            }
        }
    }

    private static void applyPhaseBurial(ServerLevel level, BlockPos center, Direction facing, int phase) {
        if (phase < 3) {
            return;
        }

        for (int right = -2; right <= 2; right++) {
            for (int forward = -3; forward <= 3; forward++) {
                BlockPos pos = toWorld(center, facing, right, 0, forward);
                int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        pos.getX(), pos.getZ()) - 1;
                if (topY <= level.getMinBuildHeight()) {
                    continue;
                }
                BlockPos top = new BlockPos(pos.getX(), topY, pos.getZ());
                BlockPos above = top.above();
                if (!level.getBlockState(top).isSolid() || !level.getBlockState(above).isAir()) {
                    continue;
                }
                int layers = switch (phase) {
                    case 3 -> 1;
                    case 4 -> 2 + Math.floorMod(right + forward, 2);
                    case 5 -> 4;
                    default -> 6;
                };
                level.setBlock(above, Blocks.SNOW.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, Math.min(layers, 8)), 3);
            }
        }

        if (phase >= 5) {
            for (int right = -2; right <= 2; right++) {
                for (int forward = -2; forward <= 2; forward++) {
                    for (int up = 1; up <= (phase >= 6 ? 2 : 1); up++) {
                        BlockPos pos = toWorld(center, facing, right, up, forward);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    private static ItemStack createTranscriptDocument(FrozenEvacVehiclePlacement.VehicleVariant variant) {
        String docType = switch (variant) {
            case ABANDONED_EMPTY -> "vehicle_transcript_abandoned";
            case ROADSIDE_BREAKDOWN -> "vehicle_transcript_breakdown";
            case FAILED_TRANSFER -> "vehicle_transcript_failed";
        };
        ItemStack doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME, Component.literal("Recorded Audio Transcript // No Permission Needed"));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", docType);
        doc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return doc;
    }

    private static ItemStack createTransferOrder(BlockPos campCenter) {
        String designation = formatCampDesignation(campCenter);
        return createPaperNote(
                "Transfer Order",
                "DIRECTIVE: REPORT TO FIELD CAMP " + designation,
                String.format(Locale.US, "COORDS: X %d / Z %d", campCenter.getX(), campCenter.getZ()),
                "Source: ORSA terminal directive relay"
        );
    }

    private static ItemStack createPickupDelayNotice(BlockPos campCenter) {
        String designation = formatCampDesignation(campCenter);
        return createPaperNote(
                "Pickup Delay Notice",
                "Transfer pickup delayed due to whiteout conditions.",
                "Field Camp " + designation + " did not confirm dispatch.",
                "Remain mobile if road conditions permit."
        );
    }

    private static ItemStack createStationClosureMemo(BlockPos campCenter) {
        String designation = formatCampDesignation(campCenter);
        return createPaperNote(
                "Closure Memo",
                "All civilian observers to report to Field Camp " + designation + ".",
                "Lead meteorologist remained behind to maintain station logs.",
                "Further relief pending ORSA response."
        );
    }

    private static ItemStack createPassengerManifest() {
        return createPaperNote(
                "Passenger Manifest",
                "3 CIVILIAN CONTRACTORS / 1 DRIVER",
                "Gear: field charts, heater fuel, station records",
                "Status: transfer in progress"
        );
    }

    private static ItemStack createHandwrittenNote() {
        return createPaperNote(
                "Handwritten Note",
                "Camp never sent anyone.",
                "We waited on the ridge until the engine froze.",
                "Leon was right to stay with the station."
        );
    }

    private static ItemStack createContractorPass() {
        ItemStack badge = new ItemStack(ModItems.ORSA_ID_BADGE.get());
        badge.set(DataComponents.CUSTOM_NAME, Component.literal("Contractor Transfer Pass"));
        CompoundTag tag = new CompoundTag();
        tag.putString("BadgeName", "NWS Contract Staff");
        tag.putString("BadgeDept", "Transfer");
        badge.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return badge;
    }

    private static ItemStack createPaperNote(String title, String... lines) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.literal(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static String formatCampDesignation(BlockPos campCenter) {
        int regionX = Math.floorDiv(campCenter.getX() >> 4, 24);
        int regionZ = Math.floorDiv(campCenter.getZ() >> 4, 24);
        char letter = (char) ('A' + Math.floorMod(regionX, 26));
        int number = Math.floorMod(regionZ, 99) + 1;
        return letter + "-" + String.format(Locale.US, "%02d", number);
    }

    private static Direction directionToward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static int getCurrentPhase(ServerLevel level) {
        ApocalypseState state = ApocalypseState.get(level.getServer());
        return state.getPhase();
    }

    private static void ensureSupportBelow(ServerLevel level, BlockPos pos) {
        for (int i = 1; i <= 4; i++) {
            BlockPos below = pos.below(i);
            if (level.getBlockState(below).isSolid()) {
                break;
            }
            level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
        }
    }

    private static BlockPos toWorld(BlockPos origin, Direction facing, int right, int up, int forward) {
        Direction rightDir = facing.getClockWise();
        int dx = rightDir.getStepX() * right + facing.getStepX() * forward;
        int dz = rightDir.getStepZ() * right + facing.getStepZ() * forward;
        return origin.offset(dx, up, dz);
    }
}
