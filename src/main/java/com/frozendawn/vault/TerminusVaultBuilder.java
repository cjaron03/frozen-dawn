package com.frozendawn.vault;

import com.frozendawn.data.HelionSiteState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.vault.VaultMechanismBlock.Kind;
import com.frozendawn.world.HelionSiteBuilder;
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
 * drawn at {@code docs/art/terminus/vault-rooms-mockup.png}. A drain grate under the Confinement Core lifts onto a ladder
 * that drops through the hall floor to the landing. East of it: door 1, the bridge hall over its pit, the cot room, the
 * last door, the vault. The shell cannot be mined or blasted; the bore is the way in and the doors are the way on.
 */
public final class TerminusVaultBuilder {
    public static final int FLOOR = -15, CEILING = -10, PIT_FLOOR = -25, SHELL_BOTTOM = -16, PIT_BOTTOM = -26;
    /** Blocks the pit needs under the hall; a site without them cannot hold the vault. */
    public static final int DEPTH_BELOW_HALL = -PIT_BOTTOM + 1;
    /** How far east of the hall the shell reaches, for the chunks a site must wait on. */
    public static final int REACH = 28;
    public static final BlockPos GRATE = new BlockPos(0, 1, 0), DOOR = new BlockPos(3, CEILING, 0), BRIDGE = new BlockPos(5, FLOOR - 1, 0),
            LAST_DOOR = new BlockPos(22, CEILING, 0), CLOCK = new BlockPos(22, CEILING - 1, 0), CRADLE = new BlockPos(27, FLOOR + 1, 0),
            DESK = new BlockPos(23, FLOOR + 1, 3), LOCKER = new BlockPos(25, FLOOR + 1, 3);
    private TerminusVaultBuilder() { }

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
        lastDoor(level, hall.offset(LAST_DOOR));
        vault(level, hall);
        clue(level, hall);
    }

    private static void box(ServerLevel l, BlockPos c, int x0, int x1, int y0, int y1, int z0, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) l.setBlock(c.offset(x, y, z), s, 2);
    }

    private static void shell(ServerLevel l, BlockPos c) {
        var shell = ModBlocks.VAULT_SHELL.get().defaultBlockState();
        var floor = ModBlocks.VAULT_FLOOR.get().defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        box(l, c, -3, 28, SHELL_BOTTOM, CEILING, -5, 5, shell);
        box(l, c, 4, 16, PIT_BOTTOM, SHELL_BOTTOM, -3, 3, shell);
        box(l, c, -2, 2, FLOOR, FLOOR, -2, 2, floor);
        box(l, c, 4, 21, FLOOR, FLOOR, -3, 3, floor);
        box(l, c, 23, 27, FLOOR, FLOOR, -3, 3, floor);
        box(l, c, -2, 2, FLOOR + 1, CEILING - 1, -2, 2, air);
        box(l, c, 4, 16, FLOOR + 1, CEILING - 1, -2, 2, air);
        box(l, c, 17, 21, FLOOR + 1, CEILING - 1, -3, 3, air);
        box(l, c, 23, 27, FLOOR + 1, CEILING - 1, -3, 3, air);
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
    public static void door(ServerLevel l, BlockPos control) { mechanism(l, control, Kind.DOOR); cells(l, control, false); }

    /** The bridge: its mechanism under the ledge's lip; the cells come out east of it. */
    public static void bridge(ServerLevel l, BlockPos control) { mechanism(l, control, Kind.BRIDGE); }

    /** The last door and what it answers to: the cot, and the technician who turns. */
    public static void lastDoor(ServerLevel l, BlockPos control) {
        mechanism(l, control, Kind.LAST_DOOR);
        cells(l, control, true);
        var head = control.offset(VaultMechanismBlockEntity.COT);
        var cot = ModBlocks.VAULT_COT.get().defaultBlockState().setValue(VaultCotBlock.FACING, Direction.WEST);
        l.setBlock(head, cot.setValue(VaultCotBlock.PART, BedPart.HEAD), 2);
        l.setBlock(head.east(), cot.setValue(VaultCotBlock.PART, BedPart.FOOT), 2);
        l.setBlock(control.offset(VaultMechanismBlockEntity.TECHNICIAN), ModBlocks.VAULT_TECHNICIAN.get().defaultBlockState(), 2);
    }
    private static void mechanism(ServerLevel l, BlockPos control, Kind kind) {
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
        for (int x : new int[] {24, 26}) l.setBlock(c.offset(x, CEILING - 1, 0), Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 3), 2);
        l.setBlock(c.offset(CLOCK), ModBlocks.VAULT_CLOCK.get().defaultBlockState(), 2);
        if (l.getBlockEntity(c.offset(CLOCK)) instanceof VaultClockBlockEntity clock) clock.setStart(VaultClock.start(l.getSeed()));
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
    }
}
