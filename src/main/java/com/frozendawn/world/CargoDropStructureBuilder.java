package com.frozendawn.world;

import com.frozendawn.world.CargoDropStructureLayout.ContainerPalette;
import com.frozendawn.world.CargoDropStructureLayout.DropPreset;
import com.frozendawn.world.CargoDropStructureLayout.LootProfile;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.frozendawn.world.CargoDropStructureLayout.cargoHash;
import static com.frozendawn.world.CargoDropStructureLayout.craterDepthAt;
import static com.frozendawn.world.CargoDropStructureLayout.craterSurfaceState;
import static com.frozendawn.world.CargoDropStructureLayout.getCurrentPhase;
import static com.frozendawn.world.CargoDropStructureLayout.paletteFor;
import static com.frozendawn.world.CargoDropStructureLayout.toWorld;
import static com.frozendawn.world.CargoDropStructureLayout.trailSurfaceState;

public final class CargoDropStructureBuilder {

    private static final int DROP_RADIUS = 16;

    private CargoDropStructureBuilder() {
    }

    public static void place(ServerLevel level, BlockPos center) {
        CargoDropStructureLayout.Plan plan = CargoDropStructureLayout.createPlan(level, center);
        long seed = plan.seed();
        RandomSource random = RandomSource.create(seed);
        int phase = plan.phase();
        Direction facing = plan.facing();
        DropPreset preset = plan.preset();
        LootProfile lootProfile = plan.lootProfile();

        clearSnowAndBrush(level, center, facing);
        shapeImpactZone(level, center, facing, preset, random);
        scatterDebris(level, center, facing, random);
        placeSkidMarks(level, center, facing, seed);
        placeContainer(level, center, facing, preset, lootProfile);
        applyReentryScarring(level, center, facing, preset, random);
        placeCrates(level, center, facing, preset, lootProfile, random);
        placeParachute(level, center, facing, preset, phase, random);
        placeHotImpactDebris(level, center, facing, phase, seed, random);
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

    private static void placeSkidMarks(ServerLevel level, BlockPos center, Direction facing, long seed) {
        for (int forward = -33; forward <= -3; forward++) {
            int distanceFromPod = Math.abs(forward + 3);
            int width = distanceFromPod <= 3 ? 3
                    : distanceFromPod <= 10 ? 2
                    : distanceFromPod <= 24 ? 1
                    : 0;

            for (int right = -width; right <= width; right++) {
                long hash = cargoHash(seed, center.getX() + right * 13 + forward * 31, center.getZ() + right * 29 - forward * 17);
                if (width > 0 && Math.abs(right) == width && Math.floorMod((int) (hash >>> 9), 100) < 25) {
                    continue;
                }

                BlockPos pos = toWorld(center, facing, right, 0, forward);
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
                if (surfaceY <= level.getMinBuildHeight()) {
                    continue;
                }

                BlockPos surfacePos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
                BlockState state = trailSurfaceState(hash, distanceFromPod, Math.abs(right));
                level.setBlock(surfacePos, state, 3);
            }

            if (distanceFromPod >= 2 && distanceFromPod <= 18) {
                placeTrailGouge(level, center, facing, seed, forward, -(width + 1));
                placeTrailGouge(level, center, facing, seed, forward, width + 1);
            }
        }
    }

    private static void placeTrailGouge(ServerLevel level, BlockPos center, Direction facing, long seed, int forward, int right) {
        long hash = cargoHash(seed, center.getX() + right * 41 + forward * 7, center.getZ() + right * 19 - forward * 23);
        if (Math.floorMod((int) (hash >>> 15), 100) < 45) {
            return;
        }

        BlockPos pos = toWorld(center, facing, right, 0, forward);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
        if (surfaceY <= level.getMinBuildHeight()) {
            return;
        }

        BlockPos surfacePos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
        level.setBlock(surfacePos, Math.floorMod((int) (hash >>> 22), 2) == 0
                ? Blocks.COARSE_DIRT.defaultBlockState()
                : Blocks.GRAVEL.defaultBlockState(), 3);
    }

    private static void placeContainer(ServerLevel level, BlockPos center, Direction facing,
                                       DropPreset preset, LootProfile lootProfile) {
        ContainerPalette palette = paletteFor(lootProfile);
        for (int i = 0; i < preset.floorOffsets().length; i++) {
            int forward = i - 3;
            int floorOffset = preset.floorOffsets()[i];

            for (int right = -2; right <= 2; right++) {
                BlockPos floorPos = toWorld(center, facing, right, floorOffset, forward);
                ensureSupportBelow(level, floorPos);
                level.setBlock(floorPos, Math.abs(right) == 2 ? palette.floorEdge() : palette.floorCenter(), 3);

                for (int y = 1; y <= 2; y++) {
                    BlockPos innerPos = toWorld(center, facing, right, floorOffset + y, forward);
                    if (Math.abs(right) <= 1 && Math.abs(forward) <= 2) {
                        level.setBlock(innerPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                BlockPos roofPos = toWorld(center, facing, right, floorOffset + 3, forward);
                level.setBlock(roofPos, Math.abs(right) == 2 ? palette.frame() : palette.shell(), 3);
            }

            for (int y = 1; y <= 2; y++) {
                level.setBlock(toWorld(center, facing, -2, floorOffset + y, forward),
                        y == 2 ? palette.stripe() : palette.frame(), 3);
                level.setBlock(toWorld(center, facing, 2, floorOffset + y, forward),
                        y == 2 ? palette.stripe() : palette.frame(), 3);
            }

            if (forward <= 2) {
                level.setBlock(toWorld(center, facing, 0, floorOffset + 3, forward), palette.roofRib(), 3);
            }
            if (forward >= -1 && forward <= 2) {
                level.setBlock(toWorld(center, facing, -1, floorOffset + 2, forward), palette.shellShade(), 3);
                level.setBlock(toWorld(center, facing, 1, floorOffset + 2, forward), palette.shellShade(), 3);
            }

            if (forward < 3) {
                placeSideCorrugation(level, center, facing, floorOffset, forward);
            }
        }

        placeOpenEnd(level, center, facing, preset.floorOffsets()[0], palette);
        placeNoseEnd(level, center, facing, preset.floorOffsets()[preset.floorOffsets().length - 1], palette);
        placeOpenEndDebris(level, center, facing, preset.floorOffsets()[0], palette);
    }

    private static void placeOpenEnd(ServerLevel level, BlockPos center, Direction facing, int floorOffset,
                                     ContainerPalette palette) {
        int forward = -3;
        for (int y = 1; y <= 2; y++) {
            level.setBlock(toWorld(center, facing, -2, floorOffset + y, forward), palette.frame(), 3);
            level.setBlock(toWorld(center, facing, 2, floorOffset + y, forward), palette.frame(), 3);
        }
        level.setBlock(toWorld(center, facing, -1, floorOffset + 3, forward), palette.shell(), 3);
        level.setBlock(toWorld(center, facing, 1, floorOffset + 3, forward), palette.shell(), 3);
        level.setBlock(toWorld(center, facing, 0, floorOffset + 3, forward), palette.stripe(), 3);
    }

    private static void placeNoseEnd(ServerLevel level, BlockPos center, Direction facing, int floorOffset,
                                     ContainerPalette palette) {
        int forward = 3;
        for (int right = -2; right <= 2; right++) {
            for (int y = 1; y <= 2; y++) {
                if (right == 2 && y == 2) {
                    continue;
                }
                BlockState state = Math.abs(right) == 2 ? palette.frame() : palette.shell();
                level.setBlock(toWorld(center, facing, right, floorOffset + y, forward), state, 3);
            }
        }
        level.setBlock(toWorld(center, facing, 0, floorOffset + 2, forward), palette.accent(), 3);
    }

    private static void placeOpenEndDebris(ServerLevel level, BlockPos center, Direction facing, int floorOffset,
                                           ContainerPalette palette) {
        level.setBlock(toWorld(center, facing, -1, floorOffset, -4), palette.brokenDoor(), 3);
        level.setBlock(toWorld(center, facing, 1, floorOffset, -4), palette.brokenDoor(), 3);
        level.setBlock(toWorld(center, facing, 0, floorOffset + 1, -4), palette.accent(), 3);
    }

    private static void applyReentryScarring(ServerLevel level, BlockPos center, Direction facing,
                                             DropPreset preset, RandomSource random) {
        int noseFloor = preset.floorOffsets()[preset.floorOffsets().length - 1];
        BlockState charred = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState scorched = Blocks.GRAY_CONCRETE.defaultBlockState();
        LocalPlacement[] scorchedPanels = {
                new LocalPlacement(-1, noseFloor + 2, 3, charred),
                new LocalPlacement(1, noseFloor + 2, 3, charred),
                new LocalPlacement(-1, noseFloor + 3, 2, scorched),
                new LocalPlacement(1, noseFloor + 3, 2, scorched),
                new LocalPlacement(0, noseFloor + 3, 1, scorched)
        };

        for (LocalPlacement local : scorchedPanels) {
            if (random.nextInt(100) < 80) {
                level.setBlock(toWorld(center, facing, local.right(), local.up(), local.forward()), local.state(), 3);
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

    private static void placeHotImpactDebris(ServerLevel level, BlockPos center, Direction facing, int phase,
                                             long seed, RandomSource random) {
        boolean guaranteedHotImpact = phase <= 2;
        boolean lingeringHotImpact = phase <= 4 && Math.floorMod((int) (seed >>> 34), 100) < 35;
        if ((!guaranteedHotImpact && !lingeringHotImpact) || phase >= 5) {
            return;
        }

        int pieces = phase <= 2 ? 4 + random.nextInt(2) : 1 + random.nextInt(2);
        for (int i = 0; i < pieces; i++) {
            int right = -2 + random.nextInt(5);
            int forward = 4 + random.nextInt(4);
            BlockPos pos = toWorld(center, facing, right, 0, forward);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
            if (surfaceY <= level.getMinBuildHeight()) {
                continue;
            }

            BlockPos ground = new BlockPos(pos.getX(), surfaceY, pos.getZ());
            BlockPos above = ground.above();
            if (!level.getBlockState(ground).isSolid() || !level.getBlockState(above).isAir()) {
                continue;
            }

            level.setBlock(ground, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            if (phase <= 2 || (phase <= 4 && random.nextInt(4) == 0)) {
                BlockState campfire = Blocks.CAMPFIRE.defaultBlockState()
                        .setValue(CampfireBlock.LIT, phase <= 2);
                level.setBlock(above, campfire, 3);
            }
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

    private static void ensureSupportBelow(ServerLevel level, BlockPos pos) {
        for (int i = 1; i <= 5; i++) {
            BlockPos below = pos.below(i);
            if (level.getBlockState(below).isSolid()) {
                break;
            }
            level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
        }
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


    private record LocalPlacement(int right, int up, int forward, BlockState state) {
    }
}
