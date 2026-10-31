package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ThaevenLoreSavedData;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.item.OrsaDocumentItem;
import com.frozendawn.mixin.HeightmapAccessor;
import com.frozendawn.power.IcarusBlast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Carves queued Icarus craters a column at a time, a part-carved chunk first and then the chunk nearest the centre, under a
 * per-tick time budget. A chunk that is not loaded waits in its job until it is; the job ends when every chunk in reach is
 * done. Plain vanilla blocks are written straight into the chunk sections, and the chunk's heightmaps, light and clients
 * are brought up to date once per tick, not once per block. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class IcarusCrater {
    /** Half a 50 ms tick: the owner wants the crater close to instant, and a blast is worth a short dip in tick rate. */
    public static final long BUDGET_NANOS = 25_000_000L;
    /** Columns in a chunk. The budget is checked after each one: a deep chunk near the centre took 180 ms whole when every
     * block went through Level.setBlock. */
    public static final int COLUMNS = 256;
    private static final int MELT_DEPTH = 8;
    /** The crater floor: this share turns to magma, then glass fused from the rock, obsidian and violet-glinting crying
     * obsidian, cumulatively; the rest stays as it was. Near the rim, where there is air, fires burn on the floor. */
    private static final float MAGMA_CHANCE = 0.15f, GLASS_CHANCE = 0.40f, OBSIDIAN_CHANCE = 0.58f, CRYING_CHANCE = 0.63f, FIRE_CHANCE = 0.3f;
    private static final double FIRE_FROM_EDGE = 0.8;
    /** The ash heap round a story block: it rises to the block's own level beside it and falls this many blocks for each
     * block further out, as far as this reach. */
    private static final int MOUND_SLOPE = 2, MOUND_REACH = 6;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final Heightmap.Types[] HEIGHTMAPS = {Heightmap.Types.WORLD_SURFACE, Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR};

    private IcarusCrater() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel l)) return;
        var data = IcarusCraterData.get(l);
        if (!data.jobs().isEmpty()) run(l, data, BUDGET_NANOS);
    }

    /** Carves columns of loaded pending chunks until the budget is spent, always at least one when any chunk is loaded;
     * returns how many columns it visited. */
    public static int run(ServerLevel l, IcarusCraterData data, long budgetNanos) {
        long start = System.nanoTime();
        int visited = 0;
        // Ordered once a tick: picking the nearest chunk again after each one scanned every pending chunk each time.
        var order = new ArrayList<Candidate>();
        for (var job : data.jobs()) {
            int cx = job.center().getX() >> 4, cz = job.center().getZ() >> 4;
            for (long chunk : job.pending()) {
                int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
                double distance = job.columns().containsKey(chunk) ? -1 : (double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz);
                if (carvable(l, x, z)) order.add(new Candidate(job, chunk, distance));
            }
        }
        order.sort(Comparator.comparingDouble(Candidate::distance));
        for (var next : order) {
            var best = next.job(); long bestChunk = next.chunk();
            var c = new ChunkPos(bestChunk);
            var chunk = l.getChunk(c.x, c.z);
            var changes = new ArrayList<BlockChangeHooks.Change>();
            boolean[] wasEmpty = new boolean[chunk.getSectionsCount()];
            for (int i = 0; i < wasEmpty.length; i++) wasEmpty[i] = chunk.getSection(i).hasOnlyAir();
            var story = storyBlocks(l, c);
            int column = best.columns().get(bestChunk);
            boolean spent = false;
            while (column < COLUMNS && !spent) {
                processColumn(l, chunk, changes, best, story, c.getMinBlockX() + (column & 15), c.getMinBlockZ() + (column >> 4));
                column++; visited++;
                spent = System.nanoTime() - start >= budgetNanos;
            }
            BlockChangeHooks.changed(l, changes);
            settle(l, chunk, wasEmpty);
            if (column == COLUMNS) data.carved(best, bestChunk); else data.progress(best, bestChunk, column);
            if (spent) return visited;
        }
        return visited;
    }

    private record Candidate(IcarusCraterData.Job job, long chunk, double distance) {}

    /** True for a chunk inside the block-ticking range. A chunk can still be cached while the server unloads it, and
     * touching that one blocks the tick until it is loaded again; a 16 s tick in the full-load perf run came from it. */
    public static boolean carvable(ServerLevel l, int x, int z) {
        return l.shouldTickBlocksAt(ChunkPos.asLong(x, z)) && l.hasChunk(x, z);
    }

    /** Carves one crater column, or thaws one melt-ring column; returns how many blocks changed. */
    private static int processColumn(ServerLevel l, LevelChunk chunk, List<BlockChangeHooks.Change> changes, IcarusCraterData.Job job,
                                     List<BlockPos> story, int x, int z) {
        var center = job.center(); double radius = job.radius(), melt = IcarusBlast.meltRadius(radius);
        int changed = 0, bottom = l.getMinBuildHeight();
        var pos = new BlockPos.MutableBlockPos();
        int dx = x - center.getX(), dz = z - center.getZ();
        double d = Math.hypot(dx, dz);
        if (d > melt) return 0;
        int surface = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        double edge = IcarusBlast.edge(radius, job.seed(), Math.atan2(dz, dx));
        if (d <= edge) {
            int floor = Math.max(bottom, Math.min(center.getY() - IcarusBlast.depth(d, radius, job.seed(), dx, dz),
                    surface - IcarusBlast.rimDepth(job.seed(), dx, dz)));
            int mound = mound(story, x, z), top = mound > floor ? Math.max(surface - 1, mound + 1) : floor;
            var ash = ModBlocks.VOLCANIC_ASH.get().defaultBlockState();
            for (int y = Math.max(surface - 1, mound + 1); y > floor; y--) {
                var state = chunk.getBlockState(pos.set(x, y, z));
                if (state.is(Blocks.BEDROCK) && top == floor) top = y;
                if (story.contains(pos) || state.is(Blocks.BEDROCK)) continue;
                var next = y <= mound ? ash.setValue(SnowLayerBlock.LAYERS, SnowLayerBlock.MAX_HEIGHT)
                        : y == mound + 1 && !story.contains(pos.below()) ? ash.setValue(SnowLayerBlock.LAYERS, 1 + Math.floorMod(x * 31 + z * 17, 3))
                        : Blocks.AIR.defaultBlockState();
                if (state == next || state.isAir() && next.isAir()) continue;
                set(l, chunk, changes, pos, state, next);
                changed++;
            }
            var ground = chunk.getBlockState(pos.set(x, floor, z));
            var random = RandomSource.create(job.seed() ^ BlockPos.asLong(x, floor, z));
            float roll = random.nextFloat();
            var fused = roll < MAGMA_CHANCE ? Blocks.MAGMA_BLOCK : roll < GLASS_CHANCE ? Blocks.TINTED_GLASS
                    : roll < OBSIDIAN_CHANCE ? Blocks.OBSIDIAN : roll < CRYING_CHANCE ? Blocks.CRYING_OBSIDIAN : null;
            if (fused != null && mound <= floor && !ground.isAir() && !ground.is(Blocks.BEDROCK)) {
                set(l, chunk, changes, pos, ground, fused.defaultBlockState());
                changed++;
            }
            // Before the fire check, which asks the heightmap whether the floor sees the sky.
            if (changed > 0) reheight(chunk, x, z, top, bottom);
            var above = pos.set(x, floor + 1, z).immutable();
            if (mound <= floor && d > FIRE_FROM_EDGE * edge && random.nextFloat() < FIRE_CHANCE && l.getBlockState(above).isAir()
                    && !l.getBlockState(above.below()).isAir() && RoomAtmosphere.hasAir(l, above)) {
                l.setBlock(above, Blocks.FIRE.defaultBlockState(), FLAGS);
                changed++;
            }
        } else {
            for (int y = surface - 1; y >= Math.max(bottom, surface - MELT_DEPTH); y--) {
                var thawed = thaw(l.getBlockState(pos.set(x, y, z)));
                if (thawed == null) continue;
                l.setBlock(pos, thawed, FLAGS);
                changed++;
            }
        }
        return changed;
    }

    /** Story blocks near this chunk, which the crater leaves standing: containers still holding an ORSA document, such as the
     * satellite's black box with the only Transponder Schematic and the Blast Pit's crate, and the recorded Thaeven carriers,
     * such as the First Crossing memory wall. Each is generated once per world. Neighbouring chunks count too, for the
     * ash heaped round a block near the edge; one not loaded is missed, and its heap lacks this side. */
    static List<BlockPos> storyBlocks(ServerLevel l, ChunkPos at) {
        var out = new ArrayList<BlockPos>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var chunk = l.getChunkSource().getChunkNow(at.x + dx, at.z + dz);
            if (chunk == null) continue;
            for (var be : chunk.getBlockEntities().values())
                if (be instanceof Container container && holdsDocument(container)) out.add(be.getBlockPos());
        }
        if (l.dimension() == Level.OVERWORLD) {
            var lore = ThaevenLoreSavedData.get(l.getServer());
            var carriers = new ArrayList<>(lore.velAnRelicPositions());
            lore.firstCrossingVesselPos().ifPresent(p -> { carriers.add(p); carriers.add(p.above()); });
            lore.unthreadingVesselPos().ifPresent(carriers::add);
            for (var p : carriers)
                if (Math.abs((p.getX() >> 4) - at.x) <= 1 && Math.abs((p.getZ() >> 4) - at.z) <= 1 && l.isLoaded(p) && !l.getBlockState(p).isAir())
                    out.add(p.immutable());
        }
        return out;
    }

    private static boolean holdsDocument(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).getItem() instanceof OrsaDocumentItem) return true;
        return false;
    }

    /** The top of the ash heap in this column, or the lowest build height when no story block is near: right under a story
     * block it carries it, beside one it reaches the block's own level, and further out it falls away. */
    private static int mound(List<BlockPos> story, int x, int z) {
        int top = Integer.MIN_VALUE;
        for (var p : story) {
            int r = (int) Math.round(Math.hypot(p.getX() - x, p.getZ() - z));
            if (r <= MOUND_REACH) top = Math.max(top, r == 0 ? p.getY() - 1 : p.getY() - MOUND_SLOPE * (r - 1));
        }
        return top;
    }

    /** Writes a plain vanilla block straight into its section with no light check, heightmap update, client update,
     * removal hook or Frozen Dawn notice per block: the change is collected for {@link BlockChangeHooks}, and
     * {@link #reheight} and {@link #settle} catch up once per column and per chunk. A block entity, a point of interest or a mod block, such as a second
     * reactor's part that must go up in turn, takes the slow path through Level.setBlock. */
    private static void set(ServerLevel l, LevelChunk chunk, List<BlockChangeHooks.Change> changes, BlockPos pos, BlockState old,
                            BlockState next) {
        if (old.hasBlockEntity() || PoiTypes.hasPoi(old)
                || !old.getBlock().builtInRegistryHolder().key().location().getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            Clearable.tryClear(l.getBlockEntity(pos));
            l.setBlock(pos, next, FLAGS);
            return;
        }
        // Unlocked: only the server thread writes to a loaded chunk.
        chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, next, false);
        changes.add(new BlockChangeHooks.Change(pos.immutable(), old, next));
    }

    /** Sets a carved column's heightmaps in one pass down from its highest remaining block. LevelChunk updates each one
     * per block, which was most of the carve's time. */
    private static void reheight(LevelChunk chunk, int x, int z, int from, int bottom) {
        var pos = new BlockPos.MutableBlockPos();
        int lx = x & 15, lz = z & 15, unset = (1 << HEIGHTMAPS.length) - 1;
        for (int y = from; y >= bottom && unset != 0; y--) {
            var state = chunk.getBlockState(pos.set(x, y, z));
            for (int i = 0; i < HEIGHTMAPS.length; i++) {
                if ((unset & 1 << i) == 0 || !HEIGHTMAPS[i].isOpaque().test(state)) continue;
                ((HeightmapAccessor) chunk.getOrCreateHeightmapUnprimed(HEIGHTMAPS[i])).frozendawn$setHeight(lx, lz, y + 1);
                unset &= ~(1 << i);
            }
        }
        for (int i = 0; i < HEIGHTMAPS.length; i++)
            if ((unset & 1 << i) != 0) ((HeightmapAccessor) chunk.getOrCreateHeightmapUnprimed(HEIGHTMAPS[i])).frozendawn$setHeight(lx, lz, bottom);
    }

    /** Catches a chunk up after its sections were written directly: sky light sources, emptied sections and light, then
     * the whole chunk is resent to the players who can see it. Light reaches them as it is computed. */
    private static void settle(ServerLevel l, LevelChunk chunk, boolean[] wasEmpty) {
        chunk.initializeLightSources();
        var light = l.getChunkSource().getLightEngine();
        var at = chunk.getPos();
        for (int i = 0; i < wasEmpty.length; i++) {
            boolean empty = chunk.getSection(i).hasOnlyAir();
            if (empty != wasEmpty[i]) light.updateSectionStatus(SectionPos.of(at, chunk.getSectionYFromSectionIndex(i)), empty);
        }
        light.propagateLightSources(at);
        chunk.setUnsaved(true);
        var players = l.getChunkSource().chunkMap.getPlayers(at, false);
        if (players.isEmpty()) return;
        var packet = new ClientboundLevelChunkWithLightPacket(chunk, light, null, null);
        for (var p : players) p.connection.send(packet);
    }

    private static BlockState thaw(BlockState state) {
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return Blocks.AIR.defaultBlockState();
        if (state.is(BlockTags.ICE)) return Blocks.WATER.defaultBlockState();
        return null;
    }
}
