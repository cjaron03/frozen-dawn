package com.frozendawn.world;

import com.frozendawn.block.FalloutBlock;
import com.frozendawn.block.ReactorCasingBlock;
import com.frozendawn.block.ReactorControllerBlockEntity;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** A pre-collapse ORSA field reactor that melted down with automatic SCRAM disabled (modelled on Chernobyl Unit 4).
 * Concrete hall 15x15 with the roof blown open over a torn, scorched CFU-7 shell. The core melted through the floor
 * into a sealed basement and cooled into an elephant's foot of Corium, visible through the melt hole. A frozen
 * spent-fuel pool holds the prize: a rack of partly burned rods beside two spent ones, shielded by the ice. Heavy
 * fallout inside and in a ring outside, thrown Reactor Debris, dead ground. Coordinates are relative to the centre
 * of the hall at the surface: floor at y-1, basement interior y-8 to y-3, pool y-5 to y-1, roof y+6. */
public final class ReactorSiteBuilder {
    public static final int HALF = 7, ROOF = 6, BASEMENT_HALF = 3, BASEMENT_FLOOR = -9, POOL_BOTTOM = -6;
    public static final BlockPos RACK = new BlockPos(5, -5, -5), LOCKER = new BlockPos(-6, 0, 6), MEDICAL = new BlockPos(6, 0, 6);
    public static final List<BlockPos> CORIUM = List.of(new BlockPos(0, -8, 0), new BlockPos(1, -8, 0));
    private ReactorSiteBuilder() {}

    public static void place(ServerLevel level, BlockPos c) {
        var random = RandomSource.create(level.getSeed() ^ c.asLong());
        grade(level, c);
        basement(level, c);
        hall(level, c, random);
        pool(level, c, random);
        reactor(level, c, random);
        blast(level, c, random);
        loot(level, c, random);
        deadGround(level, c, random);
        fallout(level, c, random);
        ReactorControllerBlockEntity.ejectDebris(level, c.above(), 4 + random.nextInt(3));
    }

    private static void set(ServerLevel l, BlockPos c, int dx, int dy, int dz, BlockState s) { l.setBlock(c.offset(dx, dy, dz), s, 2); }
    private static BlockState concrete(RandomSource r) {
        return (r.nextFloat() < .75f ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
    }

    private static void grade(ServerLevel l, BlockPos c) {
        for (int dx = -13; dx <= 13; dx++) for (int dz = -13; dz <= 13; dz++) {
            for (int dy = -1; dy >= -10; dy--) if (!l.getBlockState(c.offset(dx, dy, dz)).isSolid())
                set(l, c, dx, dy, dz, (dy == -1 ? Blocks.DIRT : Blocks.STONE).defaultBlockState());
            for (int dy = 0; dy <= 16; dy++) if (!l.getBlockState(c.offset(dx, dy, dz)).isAir()) set(l, c, dx, dy, dz, Blocks.AIR.defaultBlockState());
        }
    }
    /** Sealed concrete room under the reactor: two concrete layers above it, no way in but the melt hole. */
    private static void basement(ServerLevel l, BlockPos c) {
        var r = RandomSource.create(c.asLong());
        for (int dx = -BASEMENT_HALF; dx <= BASEMENT_HALF; dx++) for (int dz = -BASEMENT_HALF; dz <= BASEMENT_HALF; dz++)
            for (int dy = BASEMENT_FLOOR; dy <= -2; dy++) {
                boolean shell = Math.abs(dx) == BASEMENT_HALF || Math.abs(dz) == BASEMENT_HALF || dy == BASEMENT_FLOOR || dy == -2;
                set(l, c, dx, dy, dz, shell ? concrete(r) : Blocks.AIR.defaultBlockState());
            }
        for (var p : List.of(new BlockPos(-1, -8, 0), new BlockPos(0, -8, -1), new BlockPos(1, -8, 1), new BlockPos(2, -8, 0)))
            set(l, c, p.getX(), p.getY(), p.getZ(), Blocks.SMOOTH_BASALT.defaultBlockState());
        for (var p : CORIUM) set(l, c, p.getX(), p.getY(), p.getZ(), ModBlocks.CORIUM.get().defaultBlockState());
    }
    private static void hall(ServerLevel l, BlockPos c, RandomSource r) {
        for (int dx = -HALF; dx <= HALF; dx++) for (int dz = -HALF; dz <= HALF; dz++) {
            set(l, c, dx, -1, dz, concrete(r));
            if (Math.abs(dx) > BASEMENT_HALF || Math.abs(dz) > BASEMENT_HALF) set(l, c, dx, -2, dz, concrete(r));
            boolean wall = Math.abs(dx) == HALF || Math.abs(dz) == HALF, partition = dz == 3 && !wall;
            for (int dy = 0; dy < ROOF; dy++) if (wall || partition) set(l, c, dx, dy, dz, concrete(r));
            set(l, c, dx, ROOF, dz, concrete(r));
        }
        for (int dy = 0; dy <= 1; dy++) { set(l, c, -1, dy, HALF, Blocks.AIR.defaultBlockState()); set(l, c, 0, dy, HALF, Blocks.AIR.defaultBlockState());
            set(l, c, -4, dy, 3, Blocks.AIR.defaultBlockState()); set(l, c, -3, dy, 3, Blocks.AIR.defaultBlockState()); }
        // Control-room window onto the hall.
        for (int dx = -1; dx <= 2; dx++) for (int dy = 2; dy <= 3; dy++) set(l, c, dx, dy, 3, Blocks.GLASS_PANE.defaultBlockState());
        set(l, c, -2, 1, 4, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState()); set(l, c, -1, 1, 4, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
        set(l, c, 0, 1, 4, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
        for (int dx = -2; dx <= 0; dx++) set(l, c, dx, 0, 4, Blocks.IRON_BLOCK.defaultBlockState());
    }
    /** Frozen spent-fuel pool: four ice layers over the rack. */
    private static void pool(ServerLevel l, BlockPos c, RandomSource r) {
        for (int dx = 4; dx <= 6; dx++) for (int dz = -6; dz <= -3; dz++) {
            set(l, c, dx, POOL_BOTTOM, dz, concrete(r));
            for (int dy = POOL_BOTTOM + 1; dy <= -1; dy++) set(l, c, dx, dy, dz, Blocks.PACKED_ICE.defaultBlockState());
        }
        for (int dy = POOL_BOTTOM; dy <= -1; dy++) {
            for (int dz = -7; dz <= -2; dz++) set(l, c, 7, dy, dz, concrete(r));
            for (int dx = 3; dx <= 7; dx++) { set(l, c, dx, dy, -7, concrete(r)); set(l, c, dx, dy, -2, concrete(r)); }
            for (int dz = -7; dz <= -2; dz++) set(l, c, 3, dy, dz, concrete(r));
        }
        set(l, c, RACK.getX(), RACK.getY(), RACK.getZ(), Blocks.BARREL.defaultBlockState());
    }
    /** The torn CFU-7 shell: core gone, melt channel straight down through the floor, casing scorched. */
    private static void reactor(ServerLevel l, BlockPos c, RandomSource r) {
        var casing = ModBlocks.REACTOR_CASING.get().defaultBlockState().setValue(ReactorCasingBlock.SCORCHED, true);
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0 && dy <= 1) continue;
            if (r.nextFloat() < (dy == 0 ? .15f : .45f)) continue;
            set(l, c, dx, dy, dz, casing);
        }
        for (int dy = -2; dy <= 1; dy++) set(l, c, 0, dy, 0, Blocks.AIR.defaultBlockState());
        set(l, c, 0, -1, 1, Blocks.SMOOTH_BASALT.defaultBlockState()); set(l, c, 1, -1, 0, Blocks.SMOOTH_BASALT.defaultBlockState());
    }
    /** The hydrogen blast: roof open over the reactor, upper walls knocked down towards the north, rubble. */
    private static void blast(ServerLevel l, BlockPos c, RandomSource r) {
        for (int dx = -HALF; dx <= HALF; dx++) for (int dz = -HALF; dz <= HALF; dz++) {
            double d = Math.sqrt(dx * dx + (dz + 1) * (dz + 1));
            if (d < 4 + r.nextFloat() * 2) set(l, c, dx, ROOF, dz, Blocks.AIR.defaultBlockState());
            if ((Math.abs(dx) == HALF || dz == -HALF) && dz < 3)
                for (int dy = 3; dy < ROOF; dy++) if (r.nextFloat() < .55f * (1 - d / 11)) set(l, c, dx, dy, dz, Blocks.AIR.defaultBlockState());
        }
        var rubble = List.of(Blocks.GRAY_CONCRETE, Blocks.COBBLESTONE, Blocks.GRAVEL, Blocks.LIGHT_GRAY_CONCRETE);
        for (int i = 0; i < 30; i++) {
            int dx = r.nextInt(25) - 12, dz = r.nextInt(25) - 12;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
            var top = topOpen(l, c.offset(dx, ROOF + 1, dz));
            if (top != null) l.setBlock(top, rubble.get(r.nextInt(rubble.size())).defaultBlockState(), 2);
        }
    }
    private static BlockPos topOpen(ServerLevel l, BlockPos from) {
        for (int i = 0; i < 16; i++) {
            var p = from.below(i);
            if (l.getBlockState(p).isAir() && l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) return p;
        }
        return null;
    }
    private static void loot(ServerLevel l, BlockPos c, RandomSource r) {
        set(l, c, LOCKER.getX(), LOCKER.getY(), LOCKER.getZ(), Blocks.BARREL.defaultBlockState());
        set(l, c, MEDICAL.getX(), MEDICAL.getY(), MEDICAL.getZ(), Blocks.BARREL.defaultBlockState());
        fill(l, c.offset(LOCKER), new ItemStack(ModItems.GEIGER_COUNTER.get()), new ItemStack(ModItems.RAD_AID.get(), 1 + r.nextInt(2)),
                com.frozendawn.item.PowerCellItem.charged(100_000 + r.nextInt(200_001)), shiftLog());
        // ORSA Uplink salvage: the site's comms feed survived in the medical store.
        fill(l, c.offset(MEDICAL), new ItemStack(ModItems.SHIELDED_CASK.get()), new ItemStack(ModItems.RAD_AID.get()), new ItemStack(ModItems.FEED_HORN.get()));
        int rods = 2 + r.nextInt(3); var rack = new ItemStack[rods + 1];
        for (int i = 0; i < rods; i++) {
            var rod = new ItemStack(ModItems.FUEL_ROD.get());
            rod.setDamageValue((int) (rod.getMaxDamage() * (.4 + r.nextFloat() * .4)));
            rack[i] = rod;
        }
        rack[rods] = new ItemStack(ModItems.SPENT_FUEL_ROD.get(), 2);
        fill(l, c.offset(RACK), rack);
    }
    private static void fill(ServerLevel l, BlockPos pos, ItemStack... items) {
        if (!(l.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) return;
        for (int i = 0; i < items.length; i++) barrel.setItem(i * 2, items[i]);
        barrel.setChanged();
    }
    private static ItemStack shiftLog() {
        var log = new ItemStack(net.minecraft.world.item.Items.PAPER);
        log.set(DataComponents.CUSTOM_NAME, Component.literal("CFU-7 Shift Log"));
        log.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Day 61. -71C outside. Radiators at full flow."),
                Component.literal("Day 63. Auto-SCRAM tripped twice on load swings."),
                Component.literal("  Lost heat to the dorms both times."),
                Component.literal("Day 64. Chief disabled automatic SCRAM."),
                Component.literal("  Two keys, two signatures."),
                Component.literal("Day 66. Core 640C, CORE DAMAGE alarm. Holding"),
                Component.literal("  until the night shift. Water pool is fro"))));
        return log;
    }
    private static void deadGround(ServerLevel l, BlockPos c, RandomSource r) {
        for (int dx = -18; dx <= 18; dx++) for (int dz = -18; dz <= 18; dz++) {
            if (dx * dx + dz * dz > 18 * 18) continue;
            int x = c.getX() + dx, z = c.getZ() + dz, y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            for (int dy = 0; dy >= -3; dy--) {
                var p = new BlockPos(x, y + dy, z); var s = l.getBlockState(p);
                if (s.is(BlockTags.LEAVES)) { if (r.nextFloat() < .6f) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2); }
                else if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)) l.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                else if (s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SMALL_FLOWERS))
                    l.setBlock(p, (r.nextFloat() < .3f ? Blocks.DEAD_BUSH : Blocks.AIR).defaultBlockState(), 2);
            }
        }
    }
    /** Heavy fallout inside the hall and close outside, thin fallout out to 16 blocks; the control room got less. */
    private static void fallout(ServerLevel l, BlockPos c, RandomSource r) {
        var thin = ModBlocks.FALLOUT.get().defaultBlockState(); var heavy = thin.setValue(FalloutBlock.SEVERE, true);
        for (int dx = -16; dx <= 16; dx++) for (int dz = -16; dz <= 16; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 16) continue;
            boolean inside = Math.abs(dx) < HALF && Math.abs(dz) < HALF;
            if (inside) {
                var floor = c.offset(dx, 0, dz);
                if (!l.getBlockState(floor).isAir() || !l.getBlockState(floor.below()).isFaceSturdy(l, floor.below(), Direction.UP)) continue;
                if (dz > 3 ? r.nextFloat() < .35f : r.nextFloat() < .85f) l.setBlock(floor, dz > 3 ? thin : heavy, 2);
                continue;
            }
            var top = topOpen(l, c.offset(dx, ROOF + 2, dz));
            if (top == null) continue;
            if (d < 10 ? r.nextFloat() < .75f : r.nextFloat() < .6f * (1 - (d - 10) / 6)) l.setBlock(top, d < 10 ? heavy : thin, 2);
        }
    }
}
