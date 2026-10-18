package com.frozendawn.flora;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Frostcap (Gelimyces nivalis): a cluster on snow or ice, or a shelf on the side of a Rimewood log. Needs no light and
 * gives off a faint blue-white glow (light 3) but no heat. At -20C or colder it ripens and spreads, faster the colder
 * it is; between -20C and 0C it stalls; above 0C it dies.
 */
public final class FrostcapBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", d -> d != Direction.DOWN);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_2;
    private static final VoxelShape CLUSTER = Block.box(3, 0, 3, 13, 7, 13);
    private static final Map<Direction, VoxelShape> SHELVES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(2, 5, 9, 14, 10, 16), Direction.SOUTH, Block.box(2, 5, 0, 14, 10, 7),
            Direction.WEST, Block.box(9, 5, 2, 16, 10, 14), Direction.EAST, Block.box(0, 5, 2, 7, 10, 14)));

    public FrostcapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(AGE, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, AGE); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING) == Direction.UP ? CLUSTER : SHELVES.get(state.getValue(FACING));
    }

    /** Snow, ice and Rimewood from the tag; a snow layer only when full. */
    public static boolean ground(BlockState state) {
        return state.is(ColdFlora.FROSTCAP_GROUND) || state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) == 8;
    }

    /** A cluster stands on ground below it; a shelf hangs on the Rimewood log behind it. */
    public static boolean supported(LevelReader level, BlockPos pos, Direction facing) {
        var support = level.getBlockState(pos.relative(facing.getOpposite()));
        return facing == Direction.UP ? ground(support) : support.is(ColdFlora.RIMEWOOD_LOGS);
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return supported(level, pos, state.getValue(FACING)); }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace();
        if (face != Direction.DOWN && supported(context.getLevel(), context.getClickedPos(), face)) return defaultBlockState().setValue(FACING, face);
        return supported(context.getLevel(), context.getClickedPos(), Direction.UP) ? defaultBlockState() : null;
    }

    @Override protected boolean isRandomlyTicking(BlockState state) { return true; }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        grow(level, pos, state, random, ColdFlora.temperature(level, pos));
    }

    /** One random tick at {@code celsius}: die if warm, stall if cool, otherwise ripen, then spread. */
    public static void grow(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, float celsius) {
        if (celsius > ColdFloraPolicy.FROSTCAP_DIES_ABOVE) { level.removeBlock(pos, false); return; }
        double growth = ColdFloraPolicy.frostcapGrowth(celsius);
        if (growth == 0) return;
        if (state.getValue(AGE) < 2) {
            if (random.nextDouble() < ColdFloraPolicy.FROSTCAP_RIPEN_CHANCE * growth) level.setBlock(pos, state.cycle(AGE), 2);
        } else if (random.nextDouble() < ColdFloraPolicy.FROSTCAP_SPREAD_CHANCE * growth) spread(level, pos, random);
    }

    /** Like vanilla mushrooms: one try at a nearby open spot, unless the 9x3x9 box around already holds five. */
    public static void spread(ServerLevel level, BlockPos pos, RandomSource random) {
        int r = ColdFloraPolicy.FROSTCAP_AREA_RADIUS, found = 0;
        for (var p : BlockPos.betweenClosed(pos.offset(-r, -1, -r), pos.offset(r, 1, r)))
            if (level.getBlockState(p).getBlock() instanceof FrostcapBlock && ++found >= ColdFloraPolicy.FROSTCAP_AREA_CAP) return;
        var target = pos.offset(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
        if (!level.isEmptyBlock(target)) return;
        var cap = level.getBlockState(pos).getBlock().defaultBlockState();
        if (supported(level, target, Direction.UP)) { level.setBlock(target, cap, 2); return; }
        for (var facing : Direction.Plane.HORIZONTAL.shuffledCopy(random))
            if (supported(level, target, facing)) { level.setBlock(target, cap.setValue(FACING, facing), 2); return; }
    }
}
