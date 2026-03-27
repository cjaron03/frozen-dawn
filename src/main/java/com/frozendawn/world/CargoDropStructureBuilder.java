package com.frozendawn.world;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class CargoDropStructureBuilder {

    private static final int DROP_RADIUS = 16;
    private static final BlockState CONTAINER_SHELL = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState CONTAINER_FRAME = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState CONTAINER_STRIPE = Blocks.ORANGE_CONCRETE.defaultBlockState();
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final DropPreset[] PRESETS = {
            new DropPreset(new int[]{0, 0, 0, -1, -1, -2, -2}, 2, 4, -1),
            new DropPreset(new int[]{1, 0, 0, -1, -1, -2, -3}, 3, 5, 2),
            new DropPreset(new int[]{1, 1, 0, 0, -1, -2, -2}, 2, 4, 1)
    };

    private CargoDropStructureBuilder() {
    }

    public static void place(ServerLevel level, BlockPos center) {
        long seed = cargoHash(level.getSeed(), center);
        RandomSource random = RandomSource.create(seed);
        int phase = getCurrentPhase(level);
        Direction facing = HORIZONTAL_DIRECTIONS[Math.floorMod((int) (seed >> 8), HORIZONTAL_DIRECTIONS.length)];
        DropPreset preset = PRESETS[Math.floorMod((int) (seed >> 2), PRESETS.length)];
        LootProfile lootProfile = LootProfile.fromRoll(Math.floorMod((int) (seed >>> 28), 100));

        clearSnowAndBrush(level, center, facing);
        shapeImpactZone(level, center, facing, preset, random);
        scatterDebris(level, center, facing, random);
        placeSkidMarks(level, center, facing);
        placeContainer(level, center, facing, preset);
        placeCrates(level, center, facing, preset, lootProfile, random);
        placeParachute(level, center, facing, preset, phase, random);
        applyPhaseBurial(level, center, facing, phase, random);
    }

    private static void clearSnowAndBrush(ServerLevel level, BlockPos center, Direction facing) {
        for (int right = -8; right <= 8; right++) {
            for (int forward = -8; forward <= 10; forward++) {
                BlockPos top = toWorld(center, facing, right, 0, forward);
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.getX(), top.getZ());
                for (int y = topY; y <= topY + 6; y++) {
                    BlockPos pos = new BlockPos(top.getX(), y, top.getZ());
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }

    private static void shapeImpactZone(ServerLevel level, BlockPos center, Direction facing,
                                        DropPreset preset, RandomSource random) {
        for (int right = -6; right <= 6; right++) {
            for (int forward = -3; forward <= 9; forward++) {
                int depression = craterDepthAt(preset, right, forward);
                if (depression < 0) {
                    continue;
                }

                BlockPos column = toWorld(center, facing, right, 0, forward);
                int targetY = center.getY() - depression;
                BlockState surface = craterSurfaceState(forward, Math.abs(right), random);
                gradeColumn(level, column, targetY, surface);
            }
        }
    }

    private static int craterDepthAt(DropPreset preset, int right, int forward) {
        if (Math.abs(right) > 6 || forward < -3 || forward > 9) {
            return -1;
        }

        double ellipse = (right * right) / 24.0 + ((forward - 2.0) * (forward - 2.0)) / 38.0;
        if (ellipse > 2.2) {
            return -1;
        }

        int depth;
        if (forward <= -1) {
            depth = 0;
        } else if (forward <= 2) {
            depth = 1;
        } else if (forward <= 5) {
            depth = Math.min(2, preset.craterDepth());
        } else {
            depth = preset.craterDepth();
        }

        if (Math.abs(right) >= 4) {
            depth = Math.max(0, depth - 1);
        }
        if (Math.abs(right) >= 5 && forward >= 4) {
            depth = Math.max(0, depth - 1);
        }
        return depth;
    }

    private static void scatterDebris(ServerLevel level, BlockPos center, Direction facing, RandomSource random) {
        for (int right = -DROP_RADIUS; right <= DROP_RADIUS; right++) {
            for (int forward = -DROP_RADIUS; forward <= DROP_RADIUS; forward++) {
                if ((right * right) + (forward * forward) > DROP_RADIUS * DROP_RADIUS) {
                    continue;
                }

                if (Math.abs(right) <= 6 && forward >= -3 && forward <= 9) {
                    continue;
                }

                long hash = BlockPos.asLong(center.getX() + right, center.getY(), center.getZ() + forward) ^ random.nextLong();
                int roll = Math.floorMod((int) (hash >>> 12), 100);
                if (roll >= 18) {
                    continue;
                }

                BlockPos worldPos = toWorld(center, facing, right, 0, forward);
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldPos.getX(), worldPos.getZ()) - 1;
                if (surfaceY <= level.getMinBuildHeight()) {
                    continue;
                }

                BlockPos surfacePos = new BlockPos(worldPos.getX(), surfaceY, worldPos.getZ());
                BlockState state = switch (roll % 3) {
                    case 0 -> Blocks.COARSE_DIRT.defaultBlockState();
                    case 1 -> Blocks.DIRT_PATH.defaultBlockState();
                    default -> Blocks.PACKED_MUD.defaultBlockState();
                };
                level.setBlock(surfacePos, state, 3);
            }
        }
    }

    private static void placeSkidMarks(ServerLevel level, BlockPos center, Direction facing) {
        for (int forward = -8; forward <= -4; forward++) {
            for (int right = -1; right <= 1; right++) {
                BlockPos pos = toWorld(center, facing, right, 0, forward);
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
                if (surfaceY <= level.getMinBuildHeight()) {
                    continue;
                }
                level.setBlock(new BlockPos(pos.getX(), surfaceY, pos.getZ()), Blocks.DIRT_PATH.defaultBlockState(), 3);
            }
        }
    }

    private static void placeContainer(ServerLevel level, BlockPos center, Direction facing, DropPreset preset) {
        for (int i = 0; i < preset.floorOffsets().length; i++) {
            int forward = i - 3;
            int floorOffset = preset.floorOffsets()[i];

            for (int right = -2; right <= 2; right++) {
                BlockPos floorPos = toWorld(center, facing, right, floorOffset, forward);
                ensureSupportBelow(level, floorPos);
                level.setBlock(floorPos, Math.abs(right) == 2 ? CONTAINER_FRAME : CONTAINER_SHELL, 3);

                for (int y = 1; y <= 2; y++) {
                    BlockPos innerPos = toWorld(center, facing, right, floorOffset + y, forward);
                    if (Math.abs(right) <= 1 && Math.abs(forward) <= 2) {
                        level.setBlock(innerPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                BlockPos roofPos = toWorld(center, facing, right, floorOffset + 3, forward);
                level.setBlock(roofPos, Math.abs(right) == 2 ? CONTAINER_FRAME : CONTAINER_SHELL, 3);
            }

            for (int y = 1; y <= 2; y++) {
                level.setBlock(toWorld(center, facing, -2, floorOffset + y, forward),
                        y == 2 ? CONTAINER_STRIPE : CONTAINER_FRAME, 3);
                level.setBlock(toWorld(center, facing, 2, floorOffset + y, forward),
                        y == 2 ? CONTAINER_STRIPE : CONTAINER_FRAME, 3);
            }

            if (forward < 3) {
                placeSideCorrugation(level, center, facing, floorOffset, forward);
            }
        }

        placeOpenEnd(level, center, facing, preset.floorOffsets()[0]);
        placeNoseEnd(level, center, facing, preset.floorOffsets()[preset.floorOffsets().length - 1]);
    }

    private static void placeOpenEnd(ServerLevel level, BlockPos center, Direction facing, int floorOffset) {
        int forward = -3;
        for (int y = 1; y <= 2; y++) {
            level.setBlock(toWorld(center, facing, -2, floorOffset + y, forward), CONTAINER_FRAME, 3);
            level.setBlock(toWorld(center, facing, 2, floorOffset + y, forward), CONTAINER_FRAME, 3);
        }
        level.setBlock(toWorld(center, facing, -1, floorOffset + 3, forward), CONTAINER_SHELL, 3);
        level.setBlock(toWorld(center, facing, 1, floorOffset + 3, forward), CONTAINER_SHELL, 3);
    }

    private static void placeNoseEnd(ServerLevel level, BlockPos center, Direction facing, int floorOffset) {
        int forward = 3;
        for (int right = -2; right <= 2; right++) {
            for (int y = 1; y <= 2; y++) {
                if (right == 2 && y == 2) {
                    continue;
                }
                BlockState state = Math.abs(right) == 2 ? CONTAINER_FRAME : CONTAINER_SHELL;
                level.setBlock(toWorld(center, facing, right, floorOffset + y, forward), state, 3);
            }
        }
    }

    private static void placeSideCorrugation(ServerLevel level, BlockPos center, Direction facing, int floorOffset, int forward) {
        BlockState leftTrapdoor = Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.FACING, facing.getCounterClockWise());
        BlockState rightTrapdoor = Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.FACING, facing.getClockWise());

        for (int y = 1; y <= 2; y++) {
            level.setBlock(toWorld(center, facing, -3, floorOffset + y, forward), leftTrapdoor, 3);
            level.setBlock(toWorld(center, facing, 3, floorOffset + y, forward), rightTrapdoor, 3);
        }
    }

    private static void placeCrates(ServerLevel level, BlockPos center, Direction facing, DropPreset preset,
                                    LootProfile lootProfile, RandomSource random) {
        BlockPos firstCratePos = toWorld(center, facing, -1, preset.floorOffsets()[2] + 1, -1);
        BlockPos secondCratePos = toWorld(center, facing, 1, preset.floorOffsets()[4] + 1, 1);

        BlockState crateState = ModBlocks.ORSA_SUPPLY_CRATE.get().defaultBlockState()
                .setValue(BarrelBlock.FACING, Direction.UP)
                .setValue(BarrelBlock.OPEN, false);

        level.setBlock(firstCratePos, crateState, 3);
        level.setBlock(secondCratePos, crateState, 3);

        List<ItemStack> firstCrateLoot = new ArrayList<>();
        List<ItemStack> secondCrateLoot = new ArrayList<>();
        buildLoot(lootProfile, random, firstCrateLoot, secondCrateLoot);

        ItemStack manifest = createManifest(lootProfile.manifestTitle());

        if (level.getBlockEntity(firstCratePos) instanceof BarrelBlockEntity firstCrate) {
            firstCrate.setItem(0, manifest);
            fillSequentially(firstCrate, firstCrateLoot, 1);
        }
        if (level.getBlockEntity(secondCratePos) instanceof BarrelBlockEntity secondCrate) {
            fillSequentially(secondCrate, secondCrateLoot, 0);
        }
    }

    private static void buildLoot(LootProfile lootProfile, RandomSource random,
                                  List<ItemStack> firstCrateLoot, List<ItemStack> secondCrateLoot) {
        switch (lootProfile) {
            case JACKPOT -> {
                firstCrateLoot.add(new ItemStack(ModItems.THERMAL_CORE.get(), 1 + random.nextInt(2)));
                firstCrateLoot.add(createRandomInsulatedArmor(random));
                firstCrateLoot.add(new ItemStack(ModItems.FROZEN_MEAT.get(), 4 + random.nextInt(3)));
                secondCrateLoot.add(new ItemStack(Items.COAL_BLOCK, 2 + random.nextInt(3)));
                secondCrateLoot.add(new ItemStack(Items.IRON_INGOT, 8 + random.nextInt(9)));
            }
            case USEFUL -> {
                firstCrateLoot.add(new ItemStack(Items.BREAD, 2 + random.nextInt(4)));
                firstCrateLoot.add(new ItemStack(Items.COOKED_BEEF, 2 + random.nextInt(4)));
                firstCrateLoot.add(new ItemStack(Items.OAK_PLANKS, 16 + random.nextInt(17)));
                secondCrateLoot.add(new ItemStack(Items.COBBLESTONE, 16 + random.nextInt(17)));
                secondCrateLoot.add(new ItemStack(Items.TORCH, 8 + random.nextInt(9)));
                secondCrateLoot.add(new ItemStack(Items.LEATHER, 4 + random.nextInt(5)));
                secondCrateLoot.add(new ItemStack(Items.STRING, 4 + random.nextInt(5)));
                if (random.nextBoolean()) {
                    firstCrateLoot.add(new ItemStack(Items.IRON_PICKAXE));
                }
                if (random.nextBoolean()) {
                    secondCrateLoot.add(new ItemStack(Items.IRON_AXE));
                }
            }
            case BUREAUCRATIC -> {
                firstCrateLoot.add(new ItemStack(Items.PAPER, 32 + random.nextInt(17)));
                firstCrateLoot.add(new ItemStack(Items.BOOK, 8 + random.nextInt(9)));
                firstCrateLoot.add(new ItemStack(Items.INK_SAC, 4 + random.nextInt(5)));
                for (int i = 0; i < 16; i++) {
                    if (i < 10) {
                        firstCrateLoot.add(createSafetyManual());
                    } else {
                        secondCrateLoot.add(createSafetyManual());
                    }
                }
                secondCrateLoot.add(createMemo(
                        "Updated Overtime Policy",
                        "Effective immediately, all emergency overtime must be pre-approved by the same office that has not answered its radio in nine days."
                ));
                secondCrateLoot.add(createMemo(
                        "Overtime Policy Clarification",
                        "The prior memorandum was distributed in error. Overtime remains mandatory wherever staffing levels are described as 'temporarily impossible.'"
                ));
                secondCrateLoot.add(createMemo(
                        "Clarification Supersession Notice",
                        "For avoidance of duplicate confusion, the clarification has been superseded by the original memorandum, which remains unworkable but authoritative."
                ));
            }
        }
    }

    private static void placeParachute(ServerLevel level, BlockPos center, Direction facing,
                                       DropPreset preset, int phase, RandomSource random) {
        int canopyHeight = phase >= 6 ? preset.canopyHeight() - 1 : preset.canopyHeight();
        BlockPos snagOrigin = toWorld(center, facing, preset.canopySideBias(), canopyHeight, -6);
        BlockPos snagAnchor = findSnagAnchor(level, snagOrigin);

        List<LocalPlacement> canopy = new ArrayList<>();
        canopy.add(new LocalPlacement(-2, canopyHeight, -4, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(-1, canopyHeight, -4, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(0, canopyHeight, -4, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(1, canopyHeight, -4, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(2, canopyHeight, -4, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(-1, canopyHeight - 1, -3, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(0, canopyHeight - 1, -3, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(1, canopyHeight - 1, -3, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(-2, canopyHeight - 2, -2, Blocks.WHITE_WOOL.defaultBlockState()));
        canopy.add(new LocalPlacement(2, canopyHeight - 2, -2, Blocks.WHITE_WOOL.defaultBlockState()));

        if (phase >= 5) {
            canopy.removeIf(local -> Math.abs(local.right()) == 2 && local.forward() == -4);
            canopy.removeIf(local -> local.right() == 1 && local.forward() == -4);
        }
        if (phase >= 6) {
            canopy.removeIf(local -> local.right() == -1 && local.forward() == -4);
        }

        for (LocalPlacement local : canopy) {
            BlockState state = phase >= 6 && Math.abs(local.right()) == 2
                    ? Blocks.WHITE_CONCRETE.defaultBlockState()
                    : local.state();
            level.setBlock(toWorld(center, facing, local.right(), local.up(), local.forward()), state, 3);
        }

        if (snagAnchor != null && phase <= 4) {
            level.setBlock(snagAnchor, Blocks.WHITE_WOOL.defaultBlockState(), 3);
            level.setBlock(snagAnchor.below(), Blocks.COBWEB.defaultBlockState(), 3);
            placeCobwebLine(level, snagAnchor, toWorld(center, facing, 0, canopyHeight - 1, -3));
        } else {
            level.setBlock(toWorld(center, facing, 2, 1, -2), Blocks.WHITE_WOOL.defaultBlockState(), 3);
            level.setBlock(toWorld(center, facing, 2, 0, -1), Blocks.WHITE_CARPET.defaultBlockState(), 3);
            if (phase <= 4) {
                level.setBlock(toWorld(center, facing, -3, 1, -1), Blocks.WHITE_WOOL.defaultBlockState(), 3);
                level.setBlock(toWorld(center, facing, -3, 0, 0), Blocks.WHITE_CARPET.defaultBlockState(), 3);
            }
        }

        placeLineAccent(level, center, facing, canopyHeight, phase);
    }

    private static void placeLineAccent(ServerLevel level, BlockPos center, Direction facing, int canopyHeight, int phase) {
        if (phase >= 6) {
            level.setBlock(toWorld(center, facing, 0, canopyHeight - 1, -2), Blocks.PACKED_ICE.defaultBlockState(), 3);
            return;
        }

        for (int right : new int[]{-1, 1}) {
            level.setBlock(toWorld(center, facing, right, canopyHeight - 1, -2), Blocks.COBWEB.defaultBlockState(), 3);
            level.setBlock(toWorld(center, facing, right, canopyHeight - 2, -1), Blocks.COBWEB.defaultBlockState(), 3);
        }
    }

    private static void applyPhaseBurial(ServerLevel level, BlockPos center, Direction facing, int phase, RandomSource random) {
        if (phase < 3) {
            return;
        }

        for (int right = -3; right <= 3; right++) {
            for (int forward = -4; forward <= 3; forward++) {
                BlockPos pos = toWorld(center, facing, right, 0, forward);
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
                if (topY <= level.getMinBuildHeight()) {
                    continue;
                }
                BlockPos top = new BlockPos(pos.getX(), topY, pos.getZ());
                if (!level.getBlockState(top).isSolid()) {
                    continue;
                }
                BlockPos above = top.above();
                if (!level.getBlockState(above).isAir()) {
                    continue;
                }

                int roll = Math.floorMod((int) ((cargoHash(level.getSeed(), top) >>> 16) + right * 17L + forward * 31L), 100);
                int chance = switch (phase) {
                    case 3 -> 25;
                    case 4 -> 45;
                    case 5 -> 75;
                    default -> 90;
                };
                if (roll >= chance) {
                    continue;
                }

                int layers = switch (phase) {
                    case 3 -> 1 + (roll % 2);
                    case 4 -> 2 + (roll % 3);
                    case 5 -> 3 + (roll % 4);
                    default -> 4 + (roll % 4);
                };
                layers = Math.min(layers, 8);
                level.setBlock(above, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 3);
            }
        }

        if (phase >= 5) {
            int buryTop = center.getY() + (phase >= 6 ? 2 : 1);
            for (int right = -5; right <= 5; right++) {
                for (int forward = -1; forward <= 4; forward++) {
                    BlockPos pos = toWorld(center, facing, right, 0, forward);
                    int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
                    for (int y = topY + 1; y <= buryTop; y++) {
                        BlockPos buryPos = new BlockPos(pos.getX(), y, pos.getZ());
                        if (level.getBlockState(buryPos).isAir()) {
                            level.setBlock(buryPos, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        if (phase >= 6) {
            level.setBlock(toWorld(center, facing, 0, 2, -4), Blocks.PACKED_ICE.defaultBlockState(), 3);
        }
    }

    private static void fillSequentially(BarrelBlockEntity crate, List<ItemStack> items, int startSlot) {
        int slot = startSlot;
        for (ItemStack stack : items) {
            if (slot >= crate.getContainerSize()) {
                break;
            }
            crate.setItem(slot++, stack);
        }
    }

    private static ItemStack createManifest(String title) {
        ItemStack doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", "cargo_drop_manifest");
        doc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return doc;
    }

    private static ItemStack createRandomInsulatedArmor(RandomSource random) {
        return switch (random.nextInt(4)) {
            case 0 -> new ItemStack(ModItems.INSULATED_HELMET.get());
            case 1 -> new ItemStack(ModItems.INSULATED_CHESTPLATE.get());
            case 2 -> new ItemStack(ModItems.INSULATED_LEGGINGS.get());
            default -> new ItemStack(ModItems.INSULATED_BOOTS.get());
        };
    }

    private static ItemStack createSafetyManual() {
        String title = "ORSA Safety Manual";
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        List<Filterable<Component>> pages = List.of(
                Filterable.passThrough(Component.literal(
                        "SECTION 4 - FIELD OPERATIONS\n\n"
                                + "If the worksite is no longer climatologically stable, personnel should remain calm and continue documenting standard hazards in the usual format."
                )),
                Filterable.passThrough(Component.literal(
                        "SECTION 7 - OVERTIME\n\n"
                                + "Overtime meals are not guaranteed during atmospheric emergencies.\n\n"
                                + "Personnel may submit reimbursement requests once mail service resumes."
                ))
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title),
                "ORSA Administrative Services",
                0,
                pages,
                true
        ));
        return book;
    }

    private static ItemStack createMemo(String title, String line) {
        ItemStack note = new ItemStack(Items.PAPER);
        note.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        note.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(line))));
        return note;
    }

    private static int getCurrentPhase(ServerLevel level) {
        ApocalypseState state = ApocalypseState.get(level.getServer());
        return state.getPhase();
    }

    private static void gradeColumn(ServerLevel level, BlockPos base, int targetY, BlockState surfaceState) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ()) - 1;
        if (surfaceY < targetY) {
            for (int y = surfaceY + 1; y <= targetY; y++) {
                BlockPos fillPos = new BlockPos(base.getX(), y, base.getZ());
                level.setBlock(fillPos, y == targetY ? surfaceState : Blocks.DIRT.defaultBlockState(), 3);
            }
            return;
        }

        for (int y = surfaceY; y > targetY; y--) {
            level.removeBlock(new BlockPos(base.getX(), y, base.getZ()), false);
        }
        level.setBlock(new BlockPos(base.getX(), targetY, base.getZ()), surfaceState, 3);
    }

    private static BlockState craterSurfaceState(int forward, int absRight, RandomSource random) {
        if (forward >= 5 && absRight <= 2) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (forward >= 2) {
            return random.nextBoolean() ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        }
        return Blocks.DIRT_PATH.defaultBlockState();
    }

    private static void ensureSupportBelow(ServerLevel level, BlockPos pos) {
        for (int i = 1; i <= 5; i++) {
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

    private static BlockPos findSnagAnchor(ServerLevel level, BlockPos around) {
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 2; dy >= -2; dy--) {
                        BlockPos candidate = around.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(candidate);
                        if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
                            continue;
                        }
                        BlockPos anchor = candidate.above();
                        if (level.getBlockState(anchor).isAir()) {
                            return anchor;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void placeCobwebLine(ServerLevel level, BlockPos start, BlockPos end) {
        int steps = Math.max(Math.max(Math.abs(start.getX() - end.getX()), Math.abs(start.getY() - end.getY())),
                Math.abs(start.getZ() - end.getZ()));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / (double) steps;
            int x = (int) Math.round(start.getX() + (end.getX() - start.getX()) * t);
            int y = (int) Math.round(start.getY() + (end.getY() - start.getY()) * t);
            int z = (int) Math.round(start.getZ() + (end.getZ() - start.getZ()) * t);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
            }
        }
    }

    private static long cargoHash(long seed, BlockPos pos) {
        return cargoHash(seed, pos.getX(), pos.getZ());
    }

    private static long cargoHash(long seed, int x, int z) {
        long h = seed ^ 0x434152474F44524FL; // "CARGODRO"
        h = h * 6364136223846793005L + x * 1442695040888963407L;
        h = h * 6364136223846793005L + z * 7664345821815920749L;
        return h ^ (h >>> 23);
    }

    private record DropPreset(int[] floorOffsets, int craterDepth, int canopyHeight, int canopySideBias) {
    }

    private enum LootProfile {
        JACKPOT("ORSA Logistics -- Emergency Resupply Package 7-C. Contents verified."),
        USEFUL("ORSA Logistics -- Standard Field Kit. Priority: LOW."),
        BUREAUCRATIC("ORSA Administrative Services -- Document Distribution, Batch 14 of 22.");

        private final String manifestTitle;

        LootProfile(String manifestTitle) {
            this.manifestTitle = manifestTitle;
        }

        public String manifestTitle() {
            return manifestTitle;
        }

        public static LootProfile fromRoll(int roll) {
            if (roll < 20) {
                return JACKPOT;
            }
            if (roll < 60) {
                return USEFUL;
            }
            return BUREAUCRATIC;
        }
    }

    private record LocalPlacement(int right, int up, int forward, BlockState state) {
    }
}
