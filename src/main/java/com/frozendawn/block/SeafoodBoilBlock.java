package com.frozendawn.block;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.item.SeafoodBoilHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A placed Seafood Boil: four servings, taken with an empty bowl. While HOT it
 * steams, and each bowl leaves with the pot's remaining heat.
 */
public class SeafoodBoilBlock extends Block implements EntityBlock {

    public static final int MAX_SERVINGS = 4;
    public static final IntegerProperty SERVINGS = IntegerProperty.create("servings", 1, MAX_SERVINGS);
    public static final BooleanProperty HOT = BooleanProperty.create("hot");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 9, 14);

    public SeafoodBoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SERVINGS, MAX_SERVINGS).setValue(HOT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SERVINGS, HOT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeafoodBoilBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SeafoodBoilBlockEntity pot)) return;
        long now = level.getGameTime();
        int left = SeafoodBoilHeat.remaining(stack, now);
        if (left <= 0) return;
        pot.setSteamingUntil(now + left);
        level.setBlock(pos, state.setValue(HOT, true), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, left);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(HOT) || !(level.getBlockEntity(pos) instanceof SeafoodBoilBlockEntity pot)) return;
        long left = pot.getSteamingUntil() - level.getGameTime();
        if (left > 0) level.scheduleTick(pos, this, (int) Math.min(left, Integer.MAX_VALUE));
        else level.setBlock(pos, state.setValue(HOT, false), Block.UPDATE_CLIENTS);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.BOWL)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SeafoodBoilBlockEntity pot) {
            ItemStack bowl = new ItemStack(ModItems.SEAFOOD_BOIL_BOWL.get());
            if (state.getValue(HOT) && level.getGameTime() < pot.getSteamingUntil()) {
                bowl.set(ModDataComponents.STEAMING_UNTIL.get(), pot.getSteamingUntil());
            }
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, bowl));
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            int servings = state.getValue(SERVINGS);
            if (servings > 1) level.setBlock(pos, state.setValue(SERVINGS, servings - 1), Block.UPDATE_ALL);
            else level.removeBlock(pos, false);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.frozendawn.seafood_boil.need_bowl"), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(HOT) || random.nextInt(3) != 0) return;
        double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
        level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 0.6, z, 0.0, 0.04, 0.0);
    }
}
