package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.MiteAwayBlock;
import com.frozendawn.init.ModBlocks;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Indexed, bounded cleanup of existing lights; immediate normalization of new flames. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class VacuumFlames {
    private static final Map<ServerLevel, Index> INDEX = new WeakHashMap<>();
    private static final class Scan {
        final int x, z; int section, cell;
        Scan(int x, int z) { this.x = x; this.z = z; }
    }
    private static final class Index {
        final Set<BlockPos> active = new HashSet<>();
        final Set<BlockPos> queued = new HashSet<>();
        final ArrayDeque<BlockPos> lights = new ArrayDeque<>();
        final ArrayDeque<Scan> scans = new ArrayDeque<>();
    }
    private VacuumFlames() {}

    public static boolean isOrdinaryFlame(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.LANTERN)
                || state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)
                && (state.is(Blocks.CAMPFIRE) || state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER)
                || state.is(Blocks.BLAST_FURNACE) || state.is(ModBlocks.MITEAWAY.get())
                || state.getBlock() instanceof CandleBlock || state.getBlock() instanceof CandleCakeBlock);
    }

    public static BlockState normalize(ServerLevel level, BlockPos pos, BlockState requested) {
        if (!isOrdinaryFlame(requested) || CombustionAtmosphere.canBurnAt(level, pos)) return requested;
        if (requested.is(Blocks.FIRE)) return Blocks.AIR.defaultBlockState();
        if (requested.is(Blocks.TORCH)) return ModBlocks.SPENT_TORCH.get().defaultBlockState();
        if (requested.is(Blocks.WALL_TORCH)) return ModBlocks.SPENT_WALL_TORCH.get().defaultBlockState()
                .setValue(WallTorchBlock.FACING, requested.getValue(WallTorchBlock.FACING));
        if (requested.is(Blocks.LANTERN)) return ModBlocks.EXTINGUISHED_LANTERN.get().defaultBlockState()
                .setValue(BlockStateProperties.HANGING, requested.getValue(BlockStateProperties.HANGING))
                .setValue(BlockStateProperties.WATERLOGGED, requested.getValue(BlockStateProperties.WATERLOGGED));
        return requested.setValue(BlockStateProperties.LIT, false);
    }

    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState current) {
        var index = INDEX.get(level);
        if (index == null) {
            if (!isOrdinaryFlame(current)) return;
            index = new Index(); INDEX.put(level, index);
        }
        BlockPos key = pos.immutable();
        if (isOrdinaryFlame(current)) {
            index.active.add(key);
            if (index.queued.add(key)) index.lights.add(key);
        } else index.active.remove(key);
    }

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            var pos = event.getChunk().getPos();
            INDEX.computeIfAbsent(level, ignored -> new Index()).scans.add(new Scan(pos.x, pos.z));
        }
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var index = INDEX.get(level); if (index == null) return;
        var chunk = event.getChunk().getPos();
        index.active.removeIf(pos -> (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z);
        index.queued.removeIf(pos -> (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z);
        index.lights.removeIf(pos -> (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z);
        index.scans.removeIf(scan -> scan.x == chunk.x && scan.z == chunk.z);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var level = event.getServer().overworld();
        var index = INDEX.get(level); if (index == null) return;
        scanLoaded(level, index);
        if (!CombustionAtmosphere.isVacuum(level)) return;
        int count = Math.min(32, index.lights.size());
        for (int i = 0; i < count; i++) {
            BlockPos pos = index.lights.removeFirst(); index.queued.remove(pos);
            if (!index.active.contains(pos)) continue;
            if (!level.isLoaded(pos)) { index.active.remove(pos); continue; }
            BlockState state = level.getBlockState(pos);
            if (!isOrdinaryFlame(state)) { index.active.remove(pos); continue; }
            BlockState out = normalize(level, pos, state);
            if (out != state) level.setBlock(pos, out, 3);
            else if (index.queued.add(pos)) index.lights.add(pos);
        }
    }

    private static void scanLoaded(ServerLevel level, Index index) {
        int cells = 4096, sections = 64;
        while (cells > 0 && sections > 0 && !index.scans.isEmpty()) {
            Scan scan = index.scans.peekFirst();
            var chunk = level.getChunkSource().getChunkNow(scan.x, scan.z);
            if (chunk == null || scan.section >= chunk.getSectionsCount()) { index.scans.removeFirst(); continue; }
            var section = chunk.getSections()[scan.section];
            if (scan.cell == 0) {
                sections--;
                if (!section.maybeHas(VacuumFlames::isOrdinaryFlame)) { scan.section++; continue; }
            }
            int end = Math.min(4096, scan.cell + cells);
            for (; scan.cell < end; scan.cell++) {
                int x = scan.cell & 15, z = scan.cell >> 4 & 15, y = scan.cell >> 8;
                var state = section.getBlockState(x, y, z);
                if (isOrdinaryFlame(state)) blockChanged(level, new BlockPos(scan.x * 16 + x,
                        level.getMinBuildHeight() + scan.section * 16 + y, scan.z * 16 + z), state);
                cells--;
            }
            if (scan.cell == 4096) { scan.cell = 0; scan.section++; }
        }
    }

    @SubscribeEvent
    public static void relight(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = level.getBlockState(event.getPos());
        boolean relight = state.is(ModBlocks.SPENT_TORCH.get()) || state.is(ModBlocks.SPENT_WALL_TORCH.get())
                || state.is(ModBlocks.EXTINGUISHED_LANTERN.get());
        if (!relight || !event.getItemStack().is(Items.FLINT_AND_STEEL)) return;
        event.setCanceled(true);
        if (!CombustionAtmosphere.canBurnAt(level, event.getPos())) { event.setCancellationResult(InteractionResult.FAIL); return; }
        BlockState lit = state.is(ModBlocks.EXTINGUISHED_LANTERN.get())
                ? Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, state.getValue(BlockStateProperties.HANGING))
                    .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED))
                : state.is(ModBlocks.SPENT_WALL_TORCH.get())
                    ? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING))
                    : Blocks.TORCH.defaultBlockState();
        if (lit.hasProperty(BlockStateProperties.WATERLOGGED) && lit.getValue(BlockStateProperties.WATERLOGGED)) {
            event.setCancellationResult(InteractionResult.FAIL); return;
        }
        level.setBlock(event.getPos(), lit, 3);
        level.playSound(null, event.getPos(), net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
        event.getItemStack().hurtAndBreak(1, event.getEntity(),
                net.minecraft.world.entity.LivingEntity.getSlotForHand(event.getHand()));
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { INDEX.clear(); CombustionAtmosphere.reset(); }
}
