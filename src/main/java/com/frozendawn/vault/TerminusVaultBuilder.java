package com.frozendawn.vault;

import com.frozendawn.data.HelionSiteState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.vault.VaultDressingBlock.Kind;
import com.frozendawn.vault.VaultMarkBlock.Mark;
import com.frozendawn.world.HelionSiteBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The Terminus Vault, under a Helion hall. Coordinates are relative to the hall floor centre, as in HelionSiteBuilder;
 * drawn at {@code docs/art/terminus/vault-rooms-mockup.png}, {@code watch-floor-mockup.png} and {@code vault-last-rooms-mockup.png}.
 * A drain grate under the Confinement Core lifts onto a ladder that drops through the hall floor to the landing. East of it:
 * door 1, the bridge hall over its pit, the Watch Floor, the mess hall, the drill hall, the Morgue, the cot room, the last door,
 * the vault. The shell cannot be mined or blasted; the bore is the way in and the doors are the way on.
 */
public final class TerminusVaultBuilder {
    public static final int FLOOR = -15, CEILING = -10, PIT_FLOOR = -25, SHELL_BOTTOM = -16, PIT_BOTTOM = -26, LOWER_FLOOR = -24;
    /** Blocks the pit needs under the hall; a site without them cannot hold the vault. */
    public static final int DEPTH_BELOW_HALL = -PIT_BOTTOM + 1;
    /** How far east of the hall the shell reaches, for the chunks a site must wait on. */
    public static final int REACH = 107;
    public static final BlockPos GRATE = new BlockPos(0, 1, 0), DOOR = new BlockPos(3, CEILING, 0), BRIDGE = new BlockPos(5, FLOOR - 1, 0),
            LAST_DOOR = new BlockPos(100, CEILING, 0), CLOCK = new BlockPos(100, CEILING - 1, 0), CRADLE = new BlockPos(105, FLOOR + 1, 0),
            DESK = new BlockPos(101, FLOOR + 1, 3), LOCKER = new BlockPos(103, FLOOR + 1, 3);
    /** The Watch Floor: Argus in the ceiling over the Custodian's mark, a gate each end, the ledge a purge throws you back to. */
    public static final BlockPos ARGUS = new BlockPos(28, CEILING, 0), WEST_DOOR = new BlockPos(17, CEILING, 0), EAST_DOOR = new BlockPos(40, CEILING, 0),
            CUSTODIAN = new BlockPos(28, LOWER_FLOOR + 1, 0), LEDGE = new BlockPos(16, FLOOR + 1, 0), SEAL_ORDER = new BlockPos(18, FLOOR + 1, -7);
    /** The foot of the System Plot's wall, at its west end: 24 blocks east of here and 8 up. */
    public static final BlockPos PLOT = new BlockPos(41, FLOOR + 1, -11);
    /** The tops of the last rooms: the mess hall's and, lower, the drill hall's and the Morgue's. */
    public static final int MESS_ROOF = -6, ROOM_ROOF = -9;
    /** The doors east of the Watch Floor: the mess hall's stands open, the other two are the puzzles'. */
    public static final BlockPos MESS_DOOR = new BlockPos(65, CEILING, 0), DRILL_DOOR = new BlockPos(83, CEILING, 0), MORGUE_DOOR = new BlockPos(95, CEILING, 0);
    /** The middle of the recorder's console, on the wall north of the way in, which runs the Two-Person Rule. */
    public static final BlockPos RECORDER = new BlockPos(66, FLOOR + 2, -4);
    /** Over the way in, on the drill hall's west wall. */
    public static final BlockPos PROJECTOR = new BlockPos(66, ROOM_ROOF - 1, 0);
    /** The two keys, too far apart for one person. */
    public static final BlockPos[] KEYS = {new BlockPos(72, FLOOR + 2, -7), new BlockPos(79, FLOOR + 2, 7)};
    /** The lower block of the cold store's dial, beside its A/C on the Morgue's north wall. */
    public static final BlockPos AC_DIAL = new BlockPos(90, FLOOR + 2, -5);
    /** The north foot of the scanner's panel, two blocks square on the wall beside the Morgue's door, which runs the Morgue. */
    public static final BlockPos VITALS = new BlockPos(95, FLOOR + 2, -4);
    /** North wall west to east, then south: each fuse's slot. */
    public static final BlockPos[] FUSES = {fuse(23, -12), fuse(28, -12), fuse(33, -12), fuse(23, 12), fuse(28, 12), fuse(33, 12)};
    /** Three glyphs either side of the west door, at the upper level. */
    public static final BlockPos[] BOARD = {board(-8), board(-6), board(-4), board(4), board(6), board(8)};
    /** Either side of the east gate and over the west at the upper level, and on the end walls of the lower floor. */
    public static final BlockPos[] CLOCKS = {new BlockPos(40, -13, -3), new BlockPos(40, -13, 3), new BlockPos(17, CEILING - 1, 0), new BlockPos(40, -19, 0), new BlockPos(17, -19, 0)};
    /** Over the east gate, north to south, so they go out from the left as you face it. */
    public static final BlockPos[] TALLY = IntStream.range(0, ArgusFight.LAMPS).mapToObj(i -> new BlockPos(40, CEILING - 1, i - ArgusFight.LAMPS / 2)).toArray(BlockPos[]::new);
    /** North-east and south-west: the only way up. */
    public static final BlockPos[] LADDERS = {new BlockPos(38, 0, -10), new BlockPos(18, 0, 10)};
    /** The Watch Floor's red lights, in the order they stay lit: the clock puts them out from the end of this list. */
    public static final List<BlockPos> LIGHTS = lights();
    private TerminusVaultBuilder() { }

    private static BlockPos fuse(int x, int z) { return new BlockPos(x, LOWER_FLOOR + 2, z); }
    private static BlockPos board(int z) { return new BlockPos(17, FLOOR + 2, z); }
    private static List<BlockPos> lights() {
        var all = new ArrayList<BlockPos>();
        for (int y : new int[] {CEILING - 1, -18}) {
            int offset = y == -18 ? 1 : 0;
            for (int x : new int[] {20, 25, 30, 35}) { all.add(new BlockPos(x + offset, y, -11)); all.add(new BlockPos(x + offset, y, 11)); }
            for (int z : new int[] {-6, 6}) { all.add(new BlockPos(18, y, z)); all.add(new BlockPos(39, y, z)); }
        }
        Collections.shuffle(all, new Random(7));
        return List.copyOf(all);
    }

    /** The vault goes under the first site that can hold it, once a world, and the world's flag remembers. */
    public static boolean claim(ServerLevel level, BlockPos hall) {
        var state = HelionSiteState.get(level.getServer());
        if (state.vault() != null || hall.getY() - DEPTH_BELOW_HALL <= level.getMinBuildHeight()) return false;
        place(level, hall);
        state.markVault(hall);
        return true;
    }

    public static void place(ServerLevel level, BlockPos hall) {
        shell(level, hall);
        bore(level, hall);
        door(level, hall.offset(DOOR));
        bridge(level, hall.offset(BRIDGE));
        // West to east each room's end wall goes over the last one's: the Watch Floor's last of all, for its gate.
        messHall(level, hall);
        morgue(level, hall);
        drillHall(level, hall);
        watchFloor(level, hall);
        lastDoor(level, hall.offset(LAST_DOOR));
        vault(level, hall);
        dress(level, hall);
        clue(level, hall);
    }

    private static void box(ServerLevel l, BlockPos c, int x0, int x1, int y0, int y1, int z0, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) l.setBlock(c.offset(x, y, z), s, 2);
    }

    private static void shell(ServerLevel l, BlockPos c) {
        var shell = ModBlocks.VAULT_SHELL.get().defaultBlockState();
        var floor = ModBlocks.VAULT_FLOOR.get().defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        box(l, c, -3, 16, SHELL_BOTTOM, CEILING, -5, 5, shell);
        box(l, c, 96, 106, SHELL_BOTTOM, CEILING, -5, 5, shell);
        box(l, c, 4, 16, PIT_BOTTOM, SHELL_BOTTOM, -3, 3, shell);
        box(l, c, -2, 2, FLOOR, FLOOR, -2, 2, floor);
        box(l, c, 4, 16, FLOOR, FLOOR, -3, 3, floor);
        box(l, c, 96, 99, FLOOR, FLOOR, -3, 3, floor);
        box(l, c, 101, 105, FLOOR, FLOOR, -3, 3, floor);
        box(l, c, -2, 2, FLOOR + 1, CEILING - 1, -2, 2, air);
        box(l, c, 4, 16, FLOOR + 1, CEILING - 1, -2, 2, air);
        box(l, c, 96, 99, FLOOR + 1, CEILING - 1, -3, 3, air);
        box(l, c, 101, 105, FLOOR + 1, CEILING - 1, -3, 3, air);
        box(l, c, 4, 16, FLOOR, FLOOR, 3, 3, shell);
        box(l, c, 4, 16, FLOOR, FLOOR, -3, -3, shell);
        // The pit, floored with powder snow: a fall costs nothing but the climb.
        box(l, c, 6, 14, PIT_FLOOR + 1, FLOOR, -2, 2, air);
        box(l, c, 6, 14, PIT_FLOOR, PIT_FLOOR, -2, 2, Blocks.POWDER_SNOW.defaultBlockState());
        var rung = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        box(l, c, 6, 6, PIT_FLOOR + 1, FLOOR, 1, 1, rung);
    }

    /** The grate, then the ladder down through the tokamak's base, the hall floor and the rock, to the landing. */
    private static void bore(ServerLevel l, BlockPos c) {
        var shell = ModBlocks.VAULT_SHELL.get().defaultBlockState();
        var rung = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        l.setBlock(c.offset(GRATE), ModBlocks.VAULT_GRATE.get().defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP), 2);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0) box(l, c, x, x, -9, -3, z, z, shell);
        box(l, c, 0, 0, FLOOR + 1, 0, 0, 0, rung);
        // In the landing the ladder needs something to stand on.
        box(l, c, 0, 0, FLOOR + 1, CEILING - 1, 1, 1, shell);
    }

    /** Door 1: its mechanism in the lintel, three by three cells below. */
    public static void door(ServerLevel l, BlockPos control) { mechanism(l, control, VaultMechanismBlock.Kind.DOOR); cells(l, control, false); }

    /** The bridge: its mechanism under the ledge's lip; the cells come out east of it. */
    public static void bridge(ServerLevel l, BlockPos control) { mechanism(l, control, VaultMechanismBlock.Kind.BRIDGE); }

    /** East of the gate: six long tables and the System Plot along the north wall. A quiet room with nothing in it to fear. */
    public static void messHall(ServerLevel l, BlockPos c) {
        box(l, c, 40, 65, SHELL_BOTTOM, MESS_ROOF, -11, 11, ModBlocks.VAULT_SHELL.get().defaultBlockState());
        box(l, c, 41, 64, FLOOR, FLOOR, -10, 10, ModBlocks.VAULT_FLOOR.get().defaultBlockState());
        box(l, c, 41, 64, FLOOR + 1, MESS_ROOF - 1, -10, 10, Blocks.AIR.defaultBlockState());
        l.setBlock(c.offset(PLOT), ModBlocks.VAULT_PLOT.get().defaultBlockState(), 2);
        for (int x = 44; x <= 56; x += 6) for (int z : new int[] {-4, 3}) for (int i = 0; i < 8; i++)
            drop(l, c, Kind.TABLE, x + i % 4, FLOOR + 1, z + i / 4, Direction.NORTH);
        // Just enough to walk by: the plot lights nothing, it only glows.
        for (int x : new int[] {46, 59}) for (int z : new int[] {-6, 6})
            l.setBlock(c.offset(x, -8, z), Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 2), 2);
    }

    /** The Two-Person Rule: the recorder on the wall by the way in, a key on each long wall, and the door that wants both. */
    public static void drillHall(ServerLevel l, BlockPos c) {
        box(l, c, 65, 83, SHELL_BOTTOM, ROOM_ROOF, -8, 8, ModBlocks.VAULT_SHELL.get().defaultBlockState());
        box(l, c, 66, 82, FLOOR, FLOOR, -7, 7, ModBlocks.VAULT_FLOOR.get().defaultBlockState());
        box(l, c, 66, 82, FLOOR + 1, ROOM_ROOF - 1, -7, 7, Blocks.AIR.defaultBlockState());
        for (int part = 0; part < 3; part++)
            l.setBlock(c.offset(RECORDER).offset(0, 0, part - 1), ModBlocks.VAULT_RECORDER.get().defaultBlockState().setValue(VaultRecorderBlock.PART, part), 2);
        for (int key = 0; key < KEYS.length; key++)
            l.setBlock(c.offset(KEYS[key]), ModBlocks.VAULT_KEY.get().defaultBlockState().setValue(VaultKeyBlock.KEY, key), 2);
        for (int x : new int[] {70, 78}) {
            l.setBlock(c.offset(x, ROOM_ROOF - 1, -7), ModBlocks.VAULT_LIGHT.get().defaultBlockState().setValue(VaultLightBlock.FACING, Direction.SOUTH), 2);
            l.setBlock(c.offset(x, ROOM_ROOF - 1, 7), ModBlocks.VAULT_LIGHT.get().defaultBlockState().setValue(VaultLightBlock.FACING, Direction.NORTH), 2);
        }
        l.setBlock(c.offset(PROJECTOR), ModBlocks.VAULT_PROJECTOR.get().defaultBlockState(), 2);
        // The band runs over the doors, and ORSA's plate says what the room was for.
        for (int x = 66; x <= 82; x++) for (int z : new int[] {-8, 8}) mark(l, c, x, CEILING - 1, z, Mark.HAZARD);
        for (int z = -7; z <= 7; z++) {
            mark(l, c, 65, CEILING - 1, z, Mark.HAZARD);
            if (Math.abs(z) > 1) mark(l, c, 83, CEILING - 1, z, Mark.HAZARD);
        }
        for (int i = 0; i < 8; i++) mark(l, c, 65, CEILING - 2 - i / 4, 5 - i % 4, Mark.valueOf("DRILL_PLATE_" + i));
        gate(l, c.offset(MESS_DOOR), true);
        puzzleGate(l, c, DRILL_DOOR);
    }

    /** The Morgue Gate: the cold store's A/C and its dial, the scanner's arch and plate, its panel, and the door it opens for the dead. */
    public static void morgue(ServerLevel l, BlockPos c) {
        box(l, c, 83, 95, SHELL_BOTTOM, ROOM_ROOF, -5, 5, ModBlocks.VAULT_SHELL.get().defaultBlockState());
        box(l, c, 84, 94, FLOOR, FLOOR, -4, 4, ModBlocks.VAULT_FLOOR.get().defaultBlockState());
        box(l, c, 84, 94, FLOOR + 1, ROOM_ROOF - 1, -4, 4, Blocks.AIR.defaultBlockState());
        for (int i = 0; i < 6; i++) mark(l, c, 87 + i % 3, CEILING - 2 - i / 3, -5, Mark.valueOf("AC_" + i));
        var dial = ModBlocks.VAULT_AC_DIAL.get().defaultBlockState();
        l.setBlock(c.offset(AC_DIAL), dial, 2);
        l.setBlock(c.offset(AC_DIAL).above(), dial.setValue(VaultAcDialBlock.UPPER, true), 2);
        box(l, c, 94, 94, FLOOR + 1, CEILING - 2, -2, -2, ModBlocks.VAULT_ARCH.get().defaultBlockState());
        box(l, c, 94, 94, FLOOR + 1, CEILING - 2, 2, 2, ModBlocks.VAULT_ARCH.get().defaultBlockState());
        box(l, c, 94, 94, CEILING - 1, CEILING - 1, -2, 2, ModBlocks.VAULT_ARCH_HEAD.get().defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        for (int x = 84; x <= 94; x++) for (int z : new int[] {-5, 5}) mark(l, c, x, CEILING - 1, z, Mark.HAZARD);
        for (int z = -4; z <= 4; z++) if (Math.abs(z) > 1) mark(l, c, 95, CEILING - 1, z, Mark.HAZARD);
        for (int i = 0; i < 8; i++) mark(l, c, 87 - i % 4, CEILING - 2 - i / 4, 5, Mark.valueOf("MORGUE_PLATE_" + i));
        box(l, c, 94, 94, FLOOR, FLOOR, -1, 1, ModBlocks.VAULT_PLATE.get().defaultBlockState());
        l.setBlock(c.offset(89, ROOM_ROOF - 1, -4), ModBlocks.VAULT_LIGHT.get().defaultBlockState().setValue(VaultLightBlock.FACING, Direction.SOUTH), 2);
        l.setBlock(c.offset(89, ROOM_ROOF - 1, 4), ModBlocks.VAULT_LIGHT.get().defaultBlockState().setValue(VaultLightBlock.FACING, Direction.NORTH), 2);
        puzzleGate(l, c, MORGUE_DOOR);
        l.setBlock(c.offset(VITALS), ModBlocks.VAULT_VITALS.get().defaultBlockState(), 2);
    }
    /** A gate, shut, with the lamp over it that goes green when it opens. */
    private static void puzzleGate(ServerLevel l, BlockPos c, BlockPos door) {
        gate(l, c.offset(door), false);
        box(l, c, door.getX(), door.getX(), CEILING - 1, CEILING - 1, -1, 1, ModBlocks.VAULT_LINTEL.get().defaultBlockState());
    }

    /** The Watch Floor: a catwalk ring at the upper level, open down to the lower floor, its board, fuses, clocks and lamps. */
    public static void watchFloor(ServerLevel l, BlockPos c) {
        var shell = ModBlocks.VAULT_SHELL.get().defaultBlockState();
        var walkway = ModBlocks.VAULT_WALKWAY.get().defaultBlockState();
        box(l, c, 17, 40, LOWER_FLOOR - 1, CEILING, -12, 12, shell);
        box(l, c, 18, 39, LOWER_FLOOR, LOWER_FLOOR, -11, 11, ModBlocks.VAULT_FLOOR.get().defaultBlockState());
        box(l, c, 18, 39, LOWER_FLOOR + 1, CEILING - 1, -11, 11, Blocks.AIR.defaultBlockState());
        box(l, c, 18, 19, FLOOR, FLOOR, -11, 11, walkway);
        box(l, c, 38, 39, FLOOR, FLOOR, -11, 11, walkway);
        box(l, c, 20, 37, FLOOR, FLOOR, -11, -10, walkway);
        box(l, c, 20, 37, FLOOR, FLOOR, 10, 11, walkway);
        // Each ladder stands on a pillar under the ring and comes up through it.
        ladder(l, c, LADDERS[0], Direction.SOUTH);
        ladder(l, c, LADDERS[1], Direction.NORTH);
        for (int slot = 0; slot < FUSES.length; slot++)
            l.setBlock(c.offset(FUSES[slot]), ModBlocks.VAULT_FUSE.get().defaultBlockState().setValue(VaultFuseBlock.SLOT, slot).setValue(VaultBoardBlock.GLYPH, slot), 2);
        for (int glyph = 0; glyph < BOARD.length; glyph++)
            l.setBlock(c.offset(BOARD[glyph]), ModBlocks.VAULT_BOARD.get().defaultBlockState().setValue(VaultBoardBlock.GLYPH, glyph), 2);
        for (var at : LIGHTS) l.setBlock(c.offset(at), ModBlocks.VAULT_LIGHT.get().defaultBlockState().setValue(VaultLightBlock.FACING, wallFace(at)), 2);
        for (var at : CLOCKS)
            l.setBlock(c.offset(at), ModBlocks.VAULT_FIGHT_CLOCK.get().defaultBlockState().setValue(VaultFightClockBlock.FACING, at.getX() == 40 ? Direction.WEST : Direction.EAST), 2);
        for (var at : TALLY) l.setBlock(c.offset(at), ModBlocks.VAULT_TALLY.get().defaultBlockState(), 2);
        gate(l, c.offset(WEST_DOOR), true);
        gate(l, c.offset(EAST_DOOR), false);
        l.setBlock(c.offset(ARGUS), ModBlocks.VAULT_ARGUS.get().defaultBlockState(), 2);
    }
    private static void ladder(ServerLevel l, BlockPos c, BlockPos at, Direction facing) {
        var pillar = at.relative(facing.getOpposite());
        box(l, c, pillar.getX(), pillar.getX(), LOWER_FLOOR + 1, FLOOR, pillar.getZ(), pillar.getZ(), ModBlocks.VAULT_SHELL.get().defaultBlockState());
        box(l, c, at.getX(), at.getX(), LOWER_FLOOR + 1, FLOOR, at.getZ(), at.getZ(), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
    }
    /** The way a lamp on the hall's walls faces: out of the wall it hangs on. */
    private static Direction wallFace(BlockPos at) {
        return at.getZ() == -11 ? Direction.SOUTH : at.getZ() == 11 ? Direction.NORTH : at.getX() == 18 ? Direction.EAST : Direction.WEST;
    }
    /** A gate: a door that answers to Argus. */
    public static void gate(ServerLevel l, BlockPos control, boolean open) {
        mechanism(l, control, VaultMechanismBlock.Kind.GATE);
        cells(l, control, false);
        if (open && l.getBlockEntity(control) instanceof VaultMechanismBlockEntity gate) gate.gate(l, true);
    }

    /** The last door and what it answers to: the cot, and the technician who turns. */
    public static void lastDoor(ServerLevel l, BlockPos control) {
        mechanism(l, control, VaultMechanismBlock.Kind.LAST_DOOR);
        cells(l, control, true);
        var head = control.offset(VaultMechanismBlockEntity.COT);
        var cot = ModBlocks.VAULT_COT.get().defaultBlockState().setValue(VaultCotBlock.FACING, Direction.WEST);
        l.setBlock(head, cot.setValue(VaultCotBlock.PART, BedPart.HEAD), 2);
        l.setBlock(head.east(), cot.setValue(VaultCotBlock.PART, BedPart.FOOT), 2);
        l.setBlock(control.offset(VaultMechanismBlockEntity.TECHNICIAN), ModBlocks.VAULT_TECHNICIAN.get().defaultBlockState(), 2);
    }
    private static void mechanism(ServerLevel l, BlockPos control, VaultMechanismBlock.Kind kind) {
        l.setBlock(control, ModBlocks.VAULT_MECHANISM.get().defaultBlockState().setValue(VaultMechanismBlock.KIND, kind), 2);
    }
    private static void cells(ServerLevel l, BlockPos control, boolean last) {
        var cell = ModBlocks.VAULT_DOOR_CELL.get().defaultBlockState().setValue(VaultDoorCellBlock.LAST, last);
        for (int dz = -1; dz <= 1; dz++) for (int dy = VaultMechanismBlockEntity.CELL_BOTTOM; dy <= VaultMechanismBlockEntity.CELL_TOP; dy++)
            l.setBlock(control.offset(0, dy, dz), cell, 2);
    }

    private static void vault(ServerLevel l, BlockPos c) {
        l.setBlock(c.offset(CRADLE), ModBlocks.VAULT_CRADLE.get().defaultBlockState(), 2);
        l.setBlock(c.offset(DESK), ModBlocks.VAULT_DESK.get().defaultBlockState(), 2);
        l.setBlock(c.offset(LOCKER), Blocks.BARREL.defaultBlockState(), 2);
        if (l.getBlockEntity(c.offset(LOCKER)) instanceof BarrelBlockEntity barrel) {
            var logs = VaultDocuments.inTheLocker();
            for (int i = 0; i < logs.size(); i++) barrel.setItem(i * 2 + 4, logs.get(i));
            barrel.setChanged();
        }
        // Just enough light that the viewport has something to show.
        for (int x : new int[] {102, 104}) l.setBlock(c.offset(x, CEILING - 1, 0), Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 3), 2);
        l.setBlock(c.offset(CLOCK), ModBlocks.VAULT_CLOCK.get().defaultBlockState(), 2);
        if (l.getBlockEntity(c.offset(CLOCK)) instanceof VaultClockBlockEntity clock) clock.setStart(VaultClock.start(l.getSeed()));
    }

    /** Sealed as everyone fled: bands and stencils on the walls, what they dropped on the floors. No loot in any of it. */
    private static void dress(ServerLevel l, BlockPos c) {
        // The bands, then the words over them.
        for (int y : new int[] {CEILING - 2, -20}) {
            for (int x = 18; x <= 39; x++) for (int z : new int[] {-12, 12}) mark(l, c, x, y, z, Mark.HAZARD);
            for (int z = -11; z <= 11; z++) if (Math.abs(z) > 2) for (int x : new int[] {17, 40}) mark(l, c, x, y, z, Mark.HAZARD);
        }
        for (int x = 4; x <= 16; x++) for (int z : new int[] {-3, 3}) mark(l, c, x, CEILING - 2, z, Mark.HAZARD);
        for (int x : new int[] {21, 33}) for (int y : new int[] {CEILING - 2, -20}) exo(l, c, x, y);
        exo(l, c, 8, CEILING - 2);
        for (int i = 0; i < 2; i++) {
            mark(l, c, 28 + i, CEILING - 1, -12, i == 0 ? Mark.FLAG_0 : Mark.FLAG_1);
            mark(l, c, 28 + i, CEILING - 1, 12, i == 0 ? Mark.FLAG_1 : Mark.FLAG_0);
        }
        mark(l, c, 17, CEILING - 1, -2, Mark.ARGUS_0);
        mark(l, c, 17, CEILING - 1, -3, Mark.ARGUS_1);
        mark(l, c, 17, CEILING - 1, -4, Mark.ARGUS_2);
        // Somebody's warning, by the far ledge: the only instructions there are. Over the band, five by three.
        for (int i = 0; i < 15; i++) mark(l, c, 12 + i % 5, CEILING - 2 - i / 5, -3, Mark.valueOf("WARNING_" + i));
        // The rest of the sublevel's lamps: nothing puts these out.
        for (int x : new int[] {7, 13}) l.setBlock(c.offset(x, CEILING - 1, -2), ModBlocks.VAULT_LIGHT.get().defaultBlockState().setValue(VaultLightBlock.FACING, Direction.SOUTH), 2);
        l.setBlock(c.offset(-2, CEILING - 1, 0), ModBlocks.VAULT_LIGHT.get().defaultBlockState().setValue(VaultLightBlock.FACING, Direction.EAST), 2);
        l.setBlock(c.offset(98, CEILING - 1, -3), ModBlocks.VAULT_LIGHT.get().defaultBlockState().setValue(VaultLightBlock.FACING, Direction.SOUTH), 2);
        drop(l, c, Kind.CRATE, 30, FLOOR + 1, -11, Direction.NORTH);
        drop(l, c, Kind.CRATE, 31, FLOOR + 1, -11, Direction.NORTH);
        drop(l, c, Kind.CASE, 34, FLOOR + 1, 10, Direction.EAST);
        drop(l, c, Kind.HARD_HAT, 25, FLOOR + 1, 11, Direction.SOUTH);
        drop(l, c, Kind.PAPER, 22, FLOOR + 1, -10, Direction.WEST);
        drop(l, c, Kind.PAPER, 37, FLOOR + 1, 11, Direction.NORTH);
        drop(l, c, Kind.CASE, 25, LOWER_FLOOR + 1, 4, Direction.NORTH);
        drop(l, c, Kind.HARD_HAT, 33, LOWER_FLOOR + 1, -6, Direction.WEST);
        drop(l, c, Kind.PAPER, 21, LOWER_FLOOR + 1, -3, Direction.EAST);
        drop(l, c, Kind.PAPER, 35, LOWER_FLOOR + 1, 7, Direction.SOUTH);
        drop(l, c, Kind.HARD_HAT, 16, FLOOR + 1, 2, Direction.EAST);
        drop(l, c, Kind.CRATE, 1, FLOOR + 1, -2, Direction.EAST);
        drop(l, c, Kind.PAPER, 97, FLOOR + 1, -2, Direction.NORTH);
        drop(l, c, Kind.CASE, 47, FLOOR + 1, -9, Direction.WEST);
        drop(l, c, Kind.PAPER, 52, FLOOR + 1, -8, Direction.EAST);
        drop(l, c, Kind.SEAL_ORDER, SEAL_ORDER.getX(), SEAL_ORDER.getY(), SEAL_ORDER.getZ(), Direction.EAST);
    }
    private static void mark(ServerLevel l, BlockPos c, int x, int y, int z, Mark mark) {
        l.setBlock(c.offset(x, y, z), ModBlocks.VAULT_MARK.get().defaultBlockState().setValue(VaultMarkBlock.MARK, mark), 2);
    }
    /** "EXO" and its arrows on both long walls from {@code x}: the way out is west, which is each wall's own hand. */
    private static void exo(ServerLevel l, BlockPos c, int x, int y) {
        int wall = x < 17 ? 3 : 12;
        Mark[] left = {Mark.EXO_LEFT_0, Mark.EXO_LEFT_1, Mark.EXO_LEFT_2}, right = {Mark.EXO_RIGHT_0, Mark.EXO_RIGHT_1, Mark.EXO_RIGHT_2};
        for (int i = 0; i < 3; i++) {
            mark(l, c, x + i, y, -wall, left[i]);
            mark(l, c, x + 2 - i, y, wall, right[i]);
        }
    }
    private static void drop(ServerLevel l, BlockPos c, Kind kind, int x, int y, int z, Direction facing) {
        l.setBlock(c.offset(x, y, z), ModBlocks.VAULT_DRESSING.get().defaultBlockState().setValue(VaultDressingBlock.KIND, kind).setValue(VaultDressingBlock.FACING, facing), 2);
    }

    /** The only pointer in the game: this site's log desk also holds the drain schedule. */
    private static void clue(ServerLevel l, BlockPos c) {
        if (l.getBlockEntity(c.offset(HelionSiteBuilder.LOG_DESK)) instanceof BarrelBlockEntity desk) {
            desk.setItem(0, VaultDocuments.drainSchedule());
            desk.setChanged();
        }
    }

    /** Playtest only: every mechanism back to where the vault starts. */
    public static void reset(ServerLevel l, BlockPos hall) {
        for (var at : new BlockPos[] {DOOR, BRIDGE, LAST_DOOR})
            if (l.getBlockEntity(hall.offset(at)) instanceof VaultMechanismBlockEntity mechanism) mechanism.reset(l);
        if (l.getBlockEntity(hall.offset(LAST_DOOR).offset(VaultMechanismBlockEntity.TECHNICIAN)) instanceof VaultTechnicianBlockEntity technician) technician.reset();
        if (l.getBlockEntity(hall.offset(CRADLE)) instanceof VaultCradleBlockEntity cradle) cradle.reset();
        l.setBlock(hall.offset(DESK), ModBlocks.VAULT_DESK.get().defaultBlockState(), 2);
        if (l.getBlockEntity(hall.offset(ARGUS)) instanceof VaultArgusBlockEntity argus) argus.reset(l);
        if (l.getBlockEntity(hall.offset(RECORDER)) instanceof VaultRecorderBlockEntity recorder) recorder.reset();
        if (l.getBlockEntity(hall.offset(VITALS)) instanceof VaultVitalsBlockEntity vitals) vitals.reset();
        drop(l, hall, Kind.SEAL_ORDER, SEAL_ORDER.getX(), SEAL_ORDER.getY(), SEAL_ORDER.getZ(), Direction.EAST);
    }
}
