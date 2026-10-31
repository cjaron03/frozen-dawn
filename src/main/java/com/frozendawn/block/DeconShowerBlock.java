package com.frozendawn.block;

import com.frozendawn.power.PowerNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** ORSA decontamination shower head, mounted under a ceiling. When a player stands beneath it (up to REACH blocks)
 * it asks the power network for DEMAND_WATTS to pump and heat water from an adjacent tank block (water, ice or
 * snow), and while it runs it washes off contamination and visor grime like a swim does. */
public final class DeconShowerBlock extends Block {
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final int DEMAND_WATTS = 3000, REACH = 3, CHECK_TICKS = 10;
    private static final VoxelShape SHAPE = Block.box(4, 12, 4, 12, 16, 12);

    public DeconShowerBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(OCCUPIED, false).setValue(RUNNING, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(OCCUPIED, RUNNING); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }
    @Override protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) { return level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN); }
    @Override protected BlockState updateShape(BlockState s, Direction d, BlockState n, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos np) {
        return d == Direction.UP && !s.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : s;
    }
    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide() && !old.is(this)) level.scheduleTick(pos, this, CHECK_TICKS);
    }
    @Override protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        update(level, pos, !level.getEntitiesOfClass(Player.class, spray(pos), p -> !p.isSpectator()).isEmpty());
        level.scheduleTick(pos, this, CHECK_TICKS);
    }
    /** One check. Occupancy raises the power demand; water runs once the network grants it. Public for GameTests. */
    public static void update(ServerLevel level, BlockPos pos, boolean occupied) {
        var s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof DeconShowerBlock)) return;
        boolean running = occupied && supplied(level, pos) && PowerNetworks.powered(level, pos);
        if (s.getValue(OCCUPIED) != occupied || s.getValue(RUNNING) != running)
            level.setBlock(pos, s.setValue(OCCUPIED, occupied).setValue(RUNNING, running), 3);
    }
    /** Watts asked of the network: only while someone stands under it. */
    public static int demandWatts(BlockState s) { return s.getBlock() instanceof DeconShowerBlock && s.getValue(OCCUPIED) ? DEMAND_WATTS : 0; }
    /** A water, ice or snow block beside or above the head is its tank; the heater melts what it draws. */
    public static boolean supplied(Level level, BlockPos pos) {
        for (var d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            var s = level.getBlockState(pos.relative(d));
            if (s.getFluidState().isSource() && s.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) return true;
            if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.SNOW_BLOCK) || s.getBlock() instanceof SnowLayerBlock) return true;
        }
        return false;
    }
    /** The column a running head sprays: its own cell down to REACH blocks below. */
    public static AABB spray(BlockPos pos) { return new AABB(pos.getX(), pos.getY() - REACH, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1); }
    /** True while a running shower within REACH blocks above, with open space between, sprays this player. */
    public static boolean washing(Player p) {
        var head = net.minecraft.core.BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        for (int dy = 0; dy <= REACH; dy++) {
            var at = head.above(dy); var s = p.level().getBlockState(at);
            if (s.getBlock() instanceof DeconShowerBlock) return s.getValue(RUNNING) && spray(at).intersects(p.getBoundingBox());
            if (dy > 0 && s.isCollisionShapeFullBlock(p.level(), at)) return false;
        }
        return false;
    }

    @Override public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource random) {
        if (!s.getValue(RUNNING)) return;
        for (int i = 0; i < 4; i++)
            level.addParticle(ParticleTypes.FALLING_WATER, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.7, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0, 0);
        var floor = pos.below();
        for (int i = 0; i < REACH && level.getBlockState(floor).getCollisionShape(level, floor).isEmpty(); i++) floor = floor.below();
        level.addParticle(ParticleTypes.SPLASH, floor.getX() + 0.5 + (random.nextDouble() - .5) * .6, floor.getY() + 1.05, floor.getZ() + 0.5 + (random.nextDouble() - .5) * .6, 0, 0, 0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.CLOUD, floor.getX() + 0.5, floor.getY() + 1.4, floor.getZ() + 0.5, 0, 0.03, 0); // hot water in a cold room
        if (random.nextInt(8) == 0) level.playLocalSound(pos, SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.35f, 1.3f, false);
    }
}
