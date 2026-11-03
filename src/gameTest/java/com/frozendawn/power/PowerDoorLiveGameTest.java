package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.PowerStorageBlockEntity;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.item.PowerCellItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression for the airlock O2-shortage flash: a door changing a room's geometry used to take every link touching
 * the room offline and re-check 8 per tick, so a network whose re-check spilled past the once-a-second allocation
 * (12 reactor coolant links queued ahead of the power links were enough) left its loads unpowered for a full second.
 * Runs on real server ticks, opens and closes a door at and one tick before an allocation, and requires the lamp to
 * stay powered on every tick. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerDoorLiveGameTest {
    private static final int[] OFFSETS = {0, 1};

    @BeforeBatch(batch = "power_door_live")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "power_door_live", timeoutTicks = 900)
    public static void powerLinksStayLiveWhileADoorChangesTheRoom(GameTestHelper h) {
        var level = h.getLevel();
        var p = new Probe();
        h.runAfterDelay(1, () -> {
            for (int x = 4; x <= 12; x++) for (int y = 1; y <= 6; y++) for (int z = 4; z <= 12; z++) {
                boolean shell = x == 4 || x == 12 || y == 1 || y == 6 || z == 4 || z == 12;
                h.setBlock(new BlockPos(x, y, z), shell ? Blocks.GLASS : Blocks.AIR);
            }
            var door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
            p.door = h.absolutePos(new BlockPos(8, 2, 12));
            level.setBlock(p.door, door, 2); level.setBlock(p.door.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            var bank = h.absolutePos(new BlockPos(6, 2, 6));
            level.setBlock(bank, ModBlocks.BATTERY_BANK.get().defaultBlockState(), 3);
            var store = (PowerStorageBlockEntity) level.getBlockEntity(bank);
            for (int i = 0; i < 4; i++) store.setItem(i, PowerCellItem.charged(PowerCellItem.CAPACITY_JOULES));
            p.relay = h.absolutePos(new BlockPos(8, 4, 8));
            level.setBlock(p.relay, ModBlocks.POWER_RELAY.get().defaultBlockState(), 3);
            p.lamp = h.absolutePos(new BlockPos(10, 5, 10));
            level.setBlock(p.lamp, ModBlocks.PANEL_LIGHT.get().defaultBlockState(), 3);
            h.assertTrue(PowerNetworks.link(level, bank, p.relay) == PowerLinkRules.Result.OK, "bank -> relay");
            h.assertTrue(PowerNetworks.link(level, p.relay, p.lamp) == PowerLinkRules.Result.OK, "relay -> lamp");
            // Twelve reactor coolant links in the same store, through the same room, as a Hearthstone base has.
            var port = h.absolutePos(new BlockPos(6, 2, 10));
            level.setBlock(port, ModBlocks.COOLANT_PORT.get().defaultBlockState(), 3);
            for (int i = 0; i < 12; i++) {
                var radiator = h.absolutePos(new BlockPos(1 + i % 6, 1 + i / 6, 15));
                level.setBlock(radiator, ModBlocks.REACTOR_RADIATOR.get().defaultBlockState(), 3);
                h.assertTrue(PowerNetworks.link(level, port, radiator) == PowerLinkRules.Result.OK, "port -> radiator " + i);
            }
            p.started = true;
        });
        h.onEachTick(() -> {
            if (!p.started || p.finished) return;
            long t = level.getGameTime();
            boolean lit = PowerNetworks.powered(level, p.lamp);
            int inactive = PowerNetworks.reading(level, p.relay).inactiveLinks();
            switch (p.stage) {
                case STABLE -> {
                    p.stableTicks = lit && inactive == 0 ? p.stableTicks + 1 : 0;
                    if (p.stableTicks >= 25) p.stage = Stage.ARM;
                    else if (p.waited++ > 200) h.fail("Network never settled: lamp " + lit + ", inactive links " + inactive);
                }
                case ARM -> {
                    if (Math.floorMod(t, 20) == Math.floorMod(20 - OFFSETS[p.round], 20)) {
                        setDoor(level, p.door, !p.open); p.open = !p.open; p.changedAt = t; p.trace = new ArrayList<>();
                        p.stage = Stage.OBSERVE;
                    }
                }
                case OBSERVE -> {
                    p.trace.add((t % 20 == 0 ? "|" : "") + (lit ? "1" : "0") + (inactive > 0 ? "x" + inactive : ""));
                    if (!lit) h.fail("Door " + (p.open ? "opening" : "closing") + " " + OFFSETS[p.round]
                            + " tick(s) before an allocation unpowered the lamp: " + String.join(" ", p.trace));
                    if (t - p.changedAt >= 40) {
                        if (!p.open) p.round++;
                        if (p.round >= OFFSETS.length) { p.finished = true; h.succeed(); return; }
                        p.stage = Stage.STABLE; p.stableTicks = 0; p.waited = 0;
                    }
                }
            }
        });
    }

    /** Owner report: opening a filled airlock into the base blinked every light. Joining a small chamber to a
     * large base makes a room too big to scan in one tick; while that scan runs the seal reads UNKNOWN, which is
     * "not decided yet", not a failed re-check, so the links through the base must stay live. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "power_door_large", timeoutTicks = 900)
    public static void powerLinksStayLiveWhileAJoinedRoomIsStillScanning(GameTestHelper h) {
        var level = h.getLevel();
        var p = new Probe();
        // A 40x6x48 base (11,520 cells) beside a 12x6x12 chamber: each scans in one tick, together they do not.
        var origin = h.absolutePos(new BlockPos(0, 20, 0));
        var chunks = new ArrayList<net.minecraft.world.level.ChunkPos>();
        h.runAfterDelay(1, () -> {
            for (int cx = origin.getX() >> 4; cx <= (origin.getX() + 54) >> 4; cx++)
                for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + 49) >> 4; cz++) {
                    level.setChunkForced(cx, cz, true); chunks.add(new net.minecraft.world.level.ChunkPos(cx, cz));
                }
            box(level, origin, 41, 7, 49); box(level, origin.offset(41, 0, 10), 13, 7, 13);
            var door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            p.door = origin.offset(41, 1, 15);
            level.setBlock(p.door, door, 2); level.setBlock(p.door.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            var bank = origin.offset(30, 1, 15);
            level.setBlock(bank, ModBlocks.BATTERY_BANK.get().defaultBlockState(), 3);
            ((PowerStorageBlockEntity) level.getBlockEntity(bank)).setItem(0, PowerCellItem.charged(PowerCellItem.CAPACITY_JOULES));
            p.relay = origin.offset(34, 4, 15);
            level.setBlock(p.relay, ModBlocks.POWER_RELAY.get().defaultBlockState(), 3);
            p.lamp = origin.offset(38, 5, 18);
            level.setBlock(p.lamp, ModBlocks.PANEL_LIGHT.get().defaultBlockState(), 3);
            p.started = true;
            p.links = List.of(new PowerLink(bank, p.relay), new PowerLink(p.relay, p.lamp));
        });
        h.onEachTick(() -> {
            if (!p.started || p.finished) return;
            long t = level.getGameTime();
            if (!PowerNetworkSavedData.get(level).links().containsAll(p.links)) for (var link : p.links) PowerNetworks.link(level, link.a(), link.b());
            boolean lit = PowerNetworks.powered(level, p.lamp);
            int inactive = PowerNetworks.reading(level, p.relay).inactiveLinks();
            switch (p.stage) {
                case STABLE, ARM -> {
                    p.stableTicks = lit && inactive == 0 && PowerNetworkSavedData.get(level).links().containsAll(p.links) ? p.stableTicks + 1 : 0;
                    if (p.stableTicks >= 25) { setDoor(level, p.door, true); p.changedAt = t; p.stage = Stage.OBSERVE; }
                    else if (p.waited++ > 400) h.fail("Network never settled: lamp " + lit + ", inactive links " + inactive);
                }
                case OBSERVE -> {
                    var why = new ArrayList<String>();
                    for (var link : p.links) why.add(String.valueOf(PowerLinkRules.validate(level, link.a(), link.b())));
                    p.trace.add((t % 20 == 0 ? "|" : "") + (lit ? "1" : "0") + (inactive > 0 ? "x" + inactive : "") + why);
                    if (!lit || inactive > 0) h.fail("Opening into the large room unpowered the lamp: " + String.join(" ", p.trace));
                    if (t - p.changedAt >= 60) {
                        p.finished = true;
                        for (int x = 0; x <= 54; x++) for (int y = 0; y <= 7; y++) for (int z = 0; z <= 49; z++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                        for (var c : chunks) level.setChunkForced(c.x, c.z, false);
                        h.succeed();
                    }
                }
            }
        });
    }

    /** Glass shell, air inside, from the corner to corner + size. */
    private static void box(ServerLevel level, BlockPos corner, int sx, int sy, int sz) {
        for (int x = 0; x <= sx; x++) for (int y = 0; y <= sy; y++) for (int z = 0; z <= sz; z++) {
            boolean shell = x == 0 || x == sx || y == 0 || y == sy || z == 0 || z == sz;
            level.setBlock(corner.offset(x, y, z), shell ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void setDoor(ServerLevel level, BlockPos lower, boolean open) {
        level.setBlock(lower, level.getBlockState(lower).setValue(DoorBlock.OPEN, open), 2);
        level.setBlock(lower.above(), level.getBlockState(lower.above()).setValue(DoorBlock.OPEN, open), 2);
    }

    private enum Stage { STABLE, ARM, OBSERVE }
    private static final class Probe {
        BlockPos door, relay, lamp; boolean started, finished, open; int round, stableTicks, waited; long changedAt; List<PowerLink> links = List.of();
        Stage stage = Stage.STABLE; List<String> trace = new ArrayList<>();
    }
}
