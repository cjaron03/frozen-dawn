package com.frozendawn.block;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.world.RoomAtmosphere;
import com.frozendawn.world.TemperatureManager;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;

/** Rejects reactor heat to the outside. Needs one uncovered outdoor face, the same rule as the heat vent.
 * Hot radiators glow, burn what touches them and boil off snow, ice and frozen atmosphere; idle ones get buried. */
public final class ReactorRadiatorBlock extends Block {
    public static final BooleanProperty HOT = BooleanProperty.create("hot");
    public static final int LINK_RANGE = 16;
    private static final Direction[] FACES = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    public ReactorRadiatorBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(HOT, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(HOT); }

    /** Snow, ice and frozen atmosphere bury an idle radiator; a hot one boils all of them off its open faces. */
    private static boolean meltable(BlockState s) {
        return s.getBlock() instanceof SnowLayerBlock || s.getBlock() instanceof FrozenAtmosphereBlock || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.POWDER_SNOW) || s.is(net.minecraft.tags.BlockTags.ICE);
    }
    /** True when a hot radiator would shed a deposit at this position: frost formation skips it. */
    public static boolean warms(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        for (var face : FACES) {
            var radiator = pos.relative(face.getOpposite());
            if (level.hasChunkAt(radiator) && level.getBlockState(radiator).getBlock() instanceof ReactorRadiatorBlock
                    && level.getBlockState(radiator).getValue(HOT)) return true;
        }
        return false;
    }
    /** Outside temperature at the radiator, or empty when every face is covered, roofed or unloaded. */
    public static OptionalDouble sink(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof ReactorRadiatorBlock)) return OptionalDouble.empty();
        for (var face : FACES) {
            var outlet = pos.relative(face);
            if (!level.isLoaded(outlet)) continue;
            var state = level.getBlockState(outlet);
            if (meltable(state) || !RoomAtmosphere.isPassage(level, outlet, state)) continue;
            if (outlet.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, outlet.getX(), outlet.getZ())) continue;
            var a = ApocalypseState.get(level.getServer());
            return OptionalDouble.of(TemperatureManager.getBackgroundTemperature(outlet.getY(), a.getCurrentDay(), a.getTotalDays()));
        }
        return OptionalDouble.empty();
    }
    public static void publish(ServerLevel level, BlockPos pos, boolean hot) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ReactorRadiatorBlock)) return;
        if (state.getValue(HOT) != hot) level.setBlock(pos, state.setValue(HOT, hot), 3);
        if (hot) for (var face : FACES) {
            var outlet = pos.relative(face);
            if (level.isLoaded(outlet) && meltable(level.getBlockState(outlet))) level.removeBlock(outlet, false);
        }
    }
    @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(HOT) && entity instanceof LivingEntity living && !living.fireImmune())
            entity.hurt(level.damageSources().hotFloor(), 2f);
        super.stepOn(level, pos, state, entity);
    }
}
