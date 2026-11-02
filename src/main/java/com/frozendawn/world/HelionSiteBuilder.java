package com.frozendawn.world;

import com.frozendawn.data.FabricatorBlueprints;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** ORSA Helion Confinement Test Facility, abandoned the week after ignition when staff were recalled to Exodus.
 * A buried concrete hall 17x17, sealed in a packed-ice jacket, around a cracked dead tokamak laid out like the
 * SFR-1 torus: copper solenoid round the Confinement Core, tungsten First Wall, a plasma channel frozen solid, a
 * pale blanket ring banded with field coils. A crack runs from the outer wall to the core, so the prize shows.
 * The control desk holds the salvage and the sealed log. An ice-plugged ladder shaft rises to a hatch at the
 * surface. Coordinates are relative to the hall floor centre: floor at y-1, ceiling at y+CEILING. */
public final class HelionSiteBuilder {
    public static final int HALF = 8, CEILING = 7, PLUG = 2, TORUS = 4;
    public static final BlockPos CORE = new BlockPos(0, 2, 0), SHAFT = new BlockPos(-6, 0, HALF - 1),
            COIL_STORE = new BlockPos(-5, 0, -HALF + 1), LOG_DESK = new BlockPos(0, 0, -HALF + 1), WALL_SPARES = new BlockPos(5, 0, -HALF + 1);
    /** The crack through the solenoid, First Wall, channel and blanket, on the line from the core towards +x. */
    public static final List<BlockPos> BREACH = List.of(new BlockPos(1, 2, 0), new BlockPos(2, 2, 0), new BlockPos(3, 2, 0),
            new BlockPos(4, 2, 0), new BlockPos(2, 3, 0), new BlockPos(3, 3, 0), new BlockPos(4, 1, 0));
    private HelionSiteBuilder() {}

    /** Builds the hall at {@code hall} with its shaft rising to {@code shaftTopY}, where the hatch sits. */
    public static void place(ServerLevel level, BlockPos hall, int shaftTopY) {
        var random = RandomSource.create(level.getSeed() ^ hall.asLong());
        jacket(level, hall);
        shell(level, hall, random);
        tokamak(level, hall, random);
        frost(level, hall, random);
        desk(level, hall, random);
        shaft(level, hall, shaftTopY - hall.getY());
    }

    private static void set(ServerLevel l, BlockPos c, int dx, int dy, int dz, BlockState s) { l.setBlock(c.offset(dx, dy, dz), s, 2); }
    private static BlockState concrete(RandomSource r) {
        return (r.nextFloat() < .7f ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GRAY_CONCRETE).defaultBlockState();
    }

    /** One block of packed ice all round the hall, below the floor and over the ceiling. */
    private static void jacket(ServerLevel l, BlockPos c) {
        int j = HALF + 1;
        for (int dx = -j; dx <= j; dx++) for (int dz = -j; dz <= j; dz++) for (int dy = -2; dy <= CEILING + 1; dy++)
            if (Math.abs(dx) == j || Math.abs(dz) == j || dy == -2 || dy == CEILING + 1) set(l, c, dx, dy, dz, Blocks.PACKED_ICE.defaultBlockState());
    }
    private static void shell(ServerLevel l, BlockPos c, RandomSource r) {
        for (int dx = -HALF; dx <= HALF; dx++) for (int dz = -HALF; dz <= HALF; dz++) for (int dy = -1; dy <= CEILING; dy++) {
            boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF || dy == -1 || dy == CEILING;
            // Dead ceiling lamps on a 4-block grid.
            boolean lamp = dy == CEILING && dx % 4 == 0 && dz % 4 == 0 && Math.abs(dx) < HALF && Math.abs(dz) < HALF;
            set(l, c, dx, dy, dz, lamp ? Blocks.REDSTONE_LAMP.defaultBlockState() : edge ? concrete(r) : Blocks.AIR.defaultBlockState());
        }
    }
    /** The 9x9x5 torus, cracked open from the core to the outer ring. Copper and tile stand in for the real coils and
     * wall: they were stripped as scrap, which is why the stores hold it. Only the core is the real block. */
    private static void tokamak(ServerLevel l, BlockPos c, RandomSource r) {
        for (int dx = -TORUS; dx <= TORUS; dx++) for (int dz = -TORUS; dz <= TORUS; dz++) for (int dy = 0; dy <= 4; dy++) {
            int d = Math.max(Math.abs(dx), Math.abs(dz));
            BlockState s;
            if (dy == 0 || dy == 4) {
                if (dy == 4 && r.nextFloat() < .25f) continue; // fallen cap segments
                s = Blocks.WEATHERED_CUT_COPPER.defaultBlockState();
            } else if (d <= 1) s = Blocks.WEATHERED_CUT_COPPER.defaultBlockState();
            else if (d == 2) s = (r.nextFloat() < .6f ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES).defaultBlockState();
            else if (d == 3) s = Blocks.BLUE_ICE.defaultBlockState();
            else s = (dx + dz) % 2 == 0 ? Blocks.OXIDIZED_CUT_COPPER.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
            set(l, c, dx, dy, dz, s);
        }
        set(l, c, CORE.getX(), CORE.getY(), CORE.getZ(), ModBlocks.CONFINEMENT_CORE.get().defaultBlockState());
        for (var p : BREACH) set(l, c, p.getX(), p.getY(), p.getZ(), Blocks.AIR.defaultBlockState());
        // Debris thrown from the crack across the floor.
        for (var p : List.of(new BlockPos(5, 0, 0), new BlockPos(6, 0, 1), new BlockPos(5, 0, -1), new BlockPos(7, 0, -2)))
            set(l, c, p.getX(), p.getY(), p.getZ(), (r.nextBoolean() ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.WEATHERED_CUT_COPPER).defaultBlockState());
    }
    /** Twenty years of cold: snow drifted on the floor, ice where the cooling lines burst. */
    private static void frost(ServerLevel l, BlockPos c, RandomSource r) {
        for (int dx = -HALF + 1; dx < HALF; dx++) for (int dz = -HALF + 1; dz < HALF; dz++) {
            var p = c.offset(dx, 0, dz);
            if (!l.getBlockState(p).isAir()) continue;
            float roll = r.nextFloat();
            if (roll < .06f) l.setBlock(p, Blocks.PACKED_ICE.defaultBlockState(), 2);
            else if (roll < .45f) l.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + r.nextInt(2)), 2);
        }
        for (int dy = 1; dy < CEILING; dy++) if (r.nextFloat() < .5f) set(l, c, HALF - 1, dy, -HALF + 1, Blocks.ICE.defaultBlockState());
    }
    public static final float CAPSULE_CHANCE = 0.1f;
    private static void desk(ServerLevel l, BlockPos c, RandomSource r) {
        for (int dx = -3; dx <= 3; dx++) set(l, c, dx, 0, -HALF + 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (var p : List.of(COIL_STORE, LOG_DESK, WALL_SPARES)) set(l, c, p.getX(), p.getY(), p.getZ(), Blocks.BARREL.defaultBlockState());
        fill(l, c.offset(COIL_STORE), new ItemStack(ModItems.SUPERCONDUCTING_COIL_SCRAP.get(), 16));
        fill(l, c.offset(WALL_SPARES), new ItemStack(ModItems.FIRST_WALL_TILE.get(), 12));
        // Two or three of the facility's fabrication slates survived in the log desk.
        var ids = new java.util.ArrayList<>(FabricatorBlueprints.SLATES); net.minecraft.Util.shuffle(ids, r);
        var desk = new java.util.ArrayList<ItemStack>(List.of(sealedLog()));
        for (var id : ids.subList(0, 2 + r.nextInt(2))) desk.add(FabricatorBlueprints.slate(id));
        // Now and then a sealed pre-collapse antimatter capsule was left in the desk. It is a lucky find, never a requirement.
        if (r.nextFloat() < CAPSULE_CHANCE) desk.add(com.frozendawn.item.AntimatterCapsuleItem.sealed(com.frozendawn.power.IcarusPhysics.CAPSULE));
        fill(l, c.offset(LOG_DESK), desk.toArray(ItemStack[]::new));
    }
    /** Ladder from the hall floor up through the ceiling and ice, plugged below a hatch at the surface. */
    private static void shaft(ServerLevel l, BlockPos c, int top) {
        int x = SHAFT.getX(), z = SHAFT.getZ();
        var ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        for (int dy = 0; dy < top; dy++) {
            if (dy > CEILING) for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++)
                if (ox != 0 || oz != 0) set(l, c, x + ox, dy, z + oz, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            set(l, c, x, dy, z, dy < top - PLUG ? ladder : Blocks.PACKED_ICE.defaultBlockState());
        }
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            if (ox != 0 || oz != 0) set(l, c, x + ox, top, z + oz, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
            for (int dy = 1; dy <= 3; dy++) set(l, c, x + ox, top + dy, z + oz, Blocks.AIR.defaultBlockState());
        }
        set(l, c, x, top, z, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH));
        // A bent antenna mast beside the hatch, so the site can be spotted across the snow.
        for (int dy = 1; dy <= 3; dy++) set(l, c, x + 2, top + dy - 1, z, Blocks.IRON_BARS.defaultBlockState());
        set(l, c, x + 2, top + 3, z, Blocks.LIGHTNING_ROD.defaultBlockState());
    }
    private static void fill(ServerLevel l, BlockPos pos, ItemStack... items) {
        if (!(l.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) return;
        for (int i = 0; i < items.length; i++) barrel.setItem(i * 2 + 4, items[i]);
        barrel.setChanged();
    }
    private static ItemStack sealedLog() {
        var log = new ItemStack(net.minecraft.world.item.Items.PAPER);
        log.set(DataComponents.CUSTOM_NAME, Component.literal("Helion Sealed Log"));
        log.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("ORSA HELION CONFINEMENT TEST FACILITY"),
                Component.literal("SEALED BY ORDER OF THE BOARD"),
                Component.literal("Ignition achieved, June 13, 2077. Q = 1.8."),
                Component.literal("The board's response: classify it."),
                Component.literal("June 18. All staff recalled to Exodus."),
                Component.literal("  Magnets left cold. Core left in the solenoid."))));
        return log;
    }
}
