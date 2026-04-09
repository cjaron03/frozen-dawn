package com.frozendawn.world;

import com.frozendawn.data.ApocalypseState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;

final class CargoDropStructureLayout {

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final DropPreset[] PRESETS = {
            new DropPreset(new int[]{0, 0, 0, -1, -1, -2, -2}, 2, 4, -1),
            new DropPreset(new int[]{1, 0, 0, -1, -1, -2, -3}, 3, 5, 2),
            new DropPreset(new int[]{1, 1, 0, 0, -1, -2, -2}, 2, 4, 1)
    };

    private CargoDropStructureLayout() {
    }

    static Plan createPlan(ServerLevel level, BlockPos center) {
        long seed = cargoHash(level.getSeed(), center);
        int phase = getCurrentPhase(level);
        Direction facing = HORIZONTAL_DIRECTIONS[Math.floorMod((int) (seed >> 8), HORIZONTAL_DIRECTIONS.length)];
        DropPreset preset = PRESETS[Math.floorMod((int) (seed >> 2), PRESETS.length)];
        LootProfile lootProfile = LootProfile.fromRoll(Math.floorMod((int) (seed >>> 28), 100));
        return new Plan(seed, phase, facing, preset, lootProfile);
    }

    static int getCurrentPhase(ServerLevel level) {
        ApocalypseState state = ApocalypseState.get(level.getServer());
        return state.getPhase();
    }

    static int craterDepthAt(DropPreset preset, int right, int forward) {
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

    static BlockState trailSurfaceState(long hash, int distanceFromPod, int absRight) {
        if (distanceFromPod <= 2 && absRight <= 1) {
            return Math.floorMod((int) (hash >>> 18), 3) == 0
                    ? Blocks.PACKED_MUD.defaultBlockState()
                    : Blocks.DIRT_PATH.defaultBlockState();
        }
        if (distanceFromPod <= 8) {
            return switch (Math.floorMod((int) (hash >>> 11), 4)) {
                case 0 -> Blocks.COARSE_DIRT.defaultBlockState();
                case 1 -> Blocks.PACKED_MUD.defaultBlockState();
                case 2 -> Blocks.GRAVEL.defaultBlockState();
                default -> Blocks.DIRT_PATH.defaultBlockState();
            };
        }
        if (distanceFromPod <= 18) {
            return switch (Math.floorMod((int) (hash >>> 8), 5)) {
                case 0 -> Blocks.COARSE_DIRT.defaultBlockState();
                case 1 -> Blocks.GRAVEL.defaultBlockState();
                case 2 -> Blocks.DIRT_PATH.defaultBlockState();
                case 3 -> Blocks.PACKED_MUD.defaultBlockState();
                default -> Blocks.ROOTED_DIRT.defaultBlockState();
            };
        }
        return Math.floorMod((int) (hash >>> 7), 3) == 0
                ? Blocks.GRAVEL.defaultBlockState()
                : Blocks.DIRT_PATH.defaultBlockState();
    }

    static BlockState craterSurfaceState(int forward, int absRight, RandomSource random) {
        if (forward >= 5 && absRight <= 2) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (forward >= 2) {
            return random.nextBoolean() ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        }
        return Blocks.DIRT_PATH.defaultBlockState();
    }

    static BlockPos toWorld(BlockPos origin, Direction facing, int right, int up, int forward) {
        Direction rightDir = facing.getClockWise();
        int dx = rightDir.getStepX() * right + facing.getStepX() * forward;
        int dz = rightDir.getStepZ() * right + facing.getStepZ() * forward;
        return origin.offset(dx, up, dz);
    }

    static long cargoHash(long seed, BlockPos pos) {
        return cargoHash(seed, pos.getX(), pos.getZ());
    }

    static long cargoHash(long seed, int x, int z) {
        long h = seed ^ 0x434152474F44524FL; // "CARGODRO"
        h = h * 6364136223846793005L + x * 1442695040888963407L;
        h = h * 6364136223846793005L + z * 7664345821815920749L;
        return h ^ (h >>> 23);
    }

    static ContainerPalette paletteFor(LootProfile lootProfile) {
        return switch (lootProfile) {
            case JACKPOT -> new ContainerPalette(
                    Blocks.WHITE_CONCRETE.defaultBlockState(),
                    Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                    Blocks.YELLOW_CONCRETE.defaultBlockState(),
                    Blocks.GRAY_CONCRETE.defaultBlockState(),
                    Blocks.RED_CONCRETE.defaultBlockState(),
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
            );
            case USEFUL -> new ContainerPalette(
                    Blocks.WHITE_CONCRETE.defaultBlockState(),
                    Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                    Blocks.ORANGE_CONCRETE.defaultBlockState(),
                    Blocks.GRAY_CONCRETE.defaultBlockState(),
                    Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
                    Blocks.SMOOTH_STONE.defaultBlockState(),
                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
            );
            case BUREAUCRATIC -> new ContainerPalette(
                    Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                    Blocks.GRAY_CONCRETE.defaultBlockState(),
                    Blocks.BLUE_CONCRETE.defaultBlockState(),
                    Blocks.WHITE_CONCRETE.defaultBlockState(),
                    Blocks.BLUE_TERRACOTTA.defaultBlockState(),
                    Blocks.POLISHED_ANDESITE.defaultBlockState(),
                    Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState()
            );
        };
    }

    record Plan(long seed, int phase, Direction facing, DropPreset preset, LootProfile lootProfile) {
    }

    record DropPreset(int[] floorOffsets, int craterDepth, int canopyHeight, int canopySideBias) {
    }

    record ContainerPalette(BlockState shell, BlockState frame, BlockState stripe, BlockState shellShade,
                            BlockState accent, BlockState floorCenter, BlockState floorEdge) {
        BlockState roofRib() {
            return stripe;
        }

        BlockState brokenDoor() {
            return floorEdge;
        }
    }

    enum LootProfile {
        JACKPOT("ORSA Logistics -- Emergency Resupply Package 7-C. Contents verified."),
        USEFUL("ORSA Logistics -- Standard Field Kit. Priority: LOW."),
        BUREAUCRATIC("ORSA Administrative Services -- Document Distribution, Batch 14 of 22.");

        private final String manifestTitle;

        LootProfile(String manifestTitle) {
            this.manifestTitle = manifestTitle;
        }

        String manifestTitle() {
            return manifestTitle;
        }

        static LootProfile fromRoll(int roll) {
            if (roll < 20) {
                return JACKPOT;
            }
            if (roll < 60) {
                return USEFUL;
            }
            return BUREAUCRATIC;
        }
    }
}
