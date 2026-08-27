package com.frozendawn.world.remnant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded authored lure layouts. Positions are semantic markers, never inferred scans. */
public final class RemnantLureTemplate {
    public enum Role { PERMANENT, OWNED, TRIGGER, SEAM, ANCHOR, MEMBRANE, LOOT, PROP }

    public enum Kind {
        SURVIVOR_CABIN("survivor_cabin", Blocks.SPRUCE_PLANKS.defaultBlockState(),
                Blocks.SPRUCE_LOG.defaultBlockState()),
        ORSA_WEATHER_SHACK("orsa_weather_shack", Blocks.IRON_BLOCK.defaultBlockState(),
                Blocks.SMOOTH_STONE.defaultBlockState()),
        BUNKER_ENTRANCE("bunker_entrance", Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
        HEATER_ROOM("heater_room", Blocks.POLISHED_ANDESITE.defaultBlockState(),
                Blocks.COPPER_BLOCK.defaultBlockState()),
        CHECKPOINT("checkpoint", Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()),
        CRASHED_VEHICLE("crashed_vehicle_shelter", Blocks.IRON_BLOCK.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState());

        private final String id;
        private final BlockState wall;
        private final BlockState frame;

        Kind(String id, BlockState wall, BlockState frame) {
            this.id = id;
            this.wall = wall;
            this.frame = frame;
        }

        public String id() { return id; }
        public BlockState wall() { return wall; }
        public BlockState frame() { return frame; }

        public static Kind byId(String id) {
            for (Kind kind : values()) if (kind.id.equals(id)) return kind;
            return SURVIVOR_CABIN;
        }

        public static Kind parse(String value) {
            String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
            for (Kind kind : values()) {
                if (kind.id.equals(normalized) || kind.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return kind;
                }
            }
            return SURVIVOR_CABIN;
        }
    }

    public record Cell(BlockPos local, BlockState state, Role role) {}

    private final Kind kind;
    private final List<Cell> cells;
    private final BlockPos foldPoint;

    private RemnantLureTemplate(Kind kind, List<Cell> cells, BlockPos foldPoint) {
        this.kind = kind;
        this.cells = List.copyOf(cells);
        this.foldPoint = foldPoint;
    }

    public Kind kind() { return kind; }
    public List<Cell> cells() { return cells; }
    public BlockPos foldPoint() { return foldPoint; }
    public BlockState floorState() { return floorFor(kind); }
    public int radius() { return kind == Kind.CRASHED_VEHICLE ? 5 : 4; }
    public int height() { return kind == Kind.BUNKER_ENTRANCE ? 7 : 6; }

    public static RemnantLureTemplate create(Kind kind) {
        List<Cell> cells = new ArrayList<>();
        int radius = kind == Kind.CRASHED_VEHICLE ? 5 : 4;
        int wallTop = kind == Kind.BUNKER_ENTRANCE ? 5 : 4;
        BlockState wall = kind.wall();
        BlockState frame = kind.frame();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                cells.add(new Cell(new BlockPos(x, 0, z), floorFor(kind), Role.PERMANENT));
            }
        }
        for (int y = 1; y <= wallTop; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z : new int[]{-radius, radius}) {
                    if (isEntrance(x, y, z, radius)) continue;
                    Role role = roleForWall(x, y, z, radius);
                    cells.add(new Cell(new BlockPos(x, y, z), role == Role.OWNED ? frame : wall, role));
                }
            }
            for (int z = -radius + 1; z < radius; z++) {
                for (int x : new int[]{-radius, radius}) {
                    Role role = roleForWall(x, y, z, radius);
                    cells.add(new Cell(new BlockPos(x, y, z), role == Role.OWNED ? frame : wall, role));
                }
            }
        }
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean rafter = z == 0 && Math.abs(x) <= 3;
                cells.add(new Cell(new BlockPos(x, wallTop + 1, z),
                        rafter ? frame : roofFor(kind), rafter ? Role.OWNED : Role.PERMANENT));
            }
        }

        int front = -radius;
        cells.add(new Cell(new BlockPos(0, 1, front),
                Blocks.SPRUCE_DOOR.defaultBlockState()
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Role.MEMBRANE));
        cells.add(new Cell(new BlockPos(0, 2, front),
                Blocks.SPRUCE_DOOR.defaultBlockState()
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Role.MEMBRANE));
        cells.add(new Cell(new BlockPos(0, 1, radius - 2), Blocks.BARREL.defaultBlockState(), Role.LOOT));
        if (kind == Kind.SURVIVOR_CABIN) {
            cells.add(new Cell(new BlockPos(-2, 1, radius - 1),
                    Blocks.RED_BED.defaultBlockState()
                            .setValue(BedBlock.PART, BedPart.FOOT)
                            .setValue(BedBlock.FACING, Direction.NORTH), Role.LOOT));
            cells.add(new Cell(new BlockPos(-2, 1, radius - 2),
                    Blocks.RED_BED.defaultBlockState()
                            .setValue(BedBlock.PART, BedPart.HEAD)
                            .setValue(BedBlock.FACING, Direction.NORTH), Role.LOOT));
        }
        cells.add(new Cell(new BlockPos(radius - 2, 1, radius - 1), propFor(kind), Role.PROP));
        cells.add(new Cell(new BlockPos(0, 1, radius - 1), frame, Role.TRIGGER));
        cells.add(new Cell(new BlockPos(-radius + 1, 1, 0), frame, Role.ANCHOR));
        cells.add(new Cell(new BlockPos(radius - 1, 1, 0), frame, Role.ANCHOR));
        cells.add(new Cell(new BlockPos(0, 1, radius - 1), frame, Role.ANCHOR));
        cells.add(new Cell(new BlockPos(0, 1, -radius + 1), frame, Role.ANCHOR));

        return new RemnantLureTemplate(kind, cells, new BlockPos(0, 1, radius - 1));
    }

    private static boolean isEntrance(int x, int y, int z, int radius) {
        return z == -radius && x == 0 && y <= 2;
    }

    private static boolean isAnatomy(int x, int y, int z, int radius) {
        return y >= 2 && ((z == radius && Math.abs(x) <= 1)
                || (Math.abs(x) == radius && Math.abs(z) <= 1));
    }

    private static Role roleForWall(int x, int y, int z, int radius) {
        if (y == 2 && Math.abs(x) == radius && z == 0) return Role.SEAM;
        return isAnatomy(x, y, z, radius) ? Role.OWNED : Role.PERMANENT;
    }

    private static BlockState floorFor(Kind kind) {
        return switch (kind) {
            case BUNKER_ENTRANCE -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case ORSA_WEATHER_SHACK, HEATER_ROOM, CRASHED_VEHICLE -> Blocks.SMOOTH_STONE.defaultBlockState();
            case CHECKPOINT -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
            default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
        };
    }

    private static BlockState roofFor(Kind kind) {
        return switch (kind) {
            case ORSA_WEATHER_SHACK, HEATER_ROOM, CRASHED_VEHICLE -> Blocks.IRON_BLOCK.defaultBlockState();
            case BUNKER_ENTRANCE -> Blocks.STONE_BRICKS.defaultBlockState();
            case CHECKPOINT -> Blocks.DARK_OAK_SLAB.defaultBlockState();
            default -> Blocks.SPRUCE_SLAB.defaultBlockState();
        };
    }

    private static BlockState propFor(Kind kind) {
        return switch (kind) {
            case HEATER_ROOM -> Blocks.BLAST_FURNACE.defaultBlockState();
            case ORSA_WEATHER_SHACK -> Blocks.SMITHING_TABLE.defaultBlockState();
            case BUNKER_ENTRANCE -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            case CHECKPOINT -> Blocks.CARTOGRAPHY_TABLE.defaultBlockState();
            case CRASHED_VEHICLE -> Blocks.LODESTONE.defaultBlockState();
            default -> Blocks.FURNACE.defaultBlockState();
        };
    }

    public static BlockPos rotate(BlockPos local, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new BlockPos(-local.getZ(), local.getY(), local.getX());
            case 2 -> new BlockPos(-local.getX(), local.getY(), -local.getZ());
            case 3 -> new BlockPos(local.getZ(), local.getY(), -local.getX());
            default -> local;
        };
    }
}
