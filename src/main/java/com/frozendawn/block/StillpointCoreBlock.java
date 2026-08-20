package com.frozendawn.block;

import com.frozendawn.aggregate.AggregateSavedData;
import com.frozendawn.aggregate.StillpointFieldManager;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModParticles;
import com.frozendawn.init.ModSounds;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;

/** The single relocatable quiet point left by the Aggregate. */
public final class StillpointCoreBlock extends Block {
    public static final BooleanProperty DEPLOYED = BooleanProperty.create("deployed");
    public static final IntegerProperty USES = IntegerProperty.create("uses", 0, 2);
    public static final IntegerProperty FINAL_STAGE = IntegerProperty.create(
            "final_stage", 0, 9);
    private static final int FINAL_STAGE_TICKS = 3;

    public StillpointCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(DEPLOYED, false)
                .setValue(USES, 0)
                .setValue(FINAL_STAGE, 0));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) return;
        int uses = Math.clamp(stack.getDamageValue(), 0, 2);
        BlockState deployed = state.setValue(DEPLOYED, true)
                .setValue(USES, uses)
                .setValue(FINAL_STAGE, 0);
        serverLevel.setBlock(pos, deployed, Block.UPDATE_ALL);
        AggregateSavedData.get(serverLevel.getServer()).armStillpoint(
                serverLevel, pos, placer == null ? null : placer.getUUID());
        StillpointFieldManager.announceCharge(serverLevel, pos);
        if (placer instanceof ServerPlayer player) {
            StillpointFieldManager.handleFirstPlacement(player);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            AggregateSavedData.get(serverLevel.getServer()).clearStillpoint(serverLevel, pos);
            StillpointFieldManager.syncAll(serverLevel.getServer());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected float getDestroyProgress(BlockState state,
                                       net.minecraft.world.entity.player.Player player,
                                       BlockGetter level, BlockPos pos) {
        if (state.getValue(FINAL_STAGE) > 0
                || !player.getMainHandItem().is(ModItems.ACHERONITE_PICKAXE.get())) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos) * 1.6F;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(FINAL_STAGE) > 0) return Collections.emptyList();
        ItemStack core = new ItemStack(ModItems.INERT_CONVERGENCE_CORE.get());
        int uses = state.getValue(USES) + (state.getValue(DEPLOYED) ? 1 : 0);
        if (uses >= 3) return Collections.emptyList();
        core.setDamageValue(uses);
        return List.of(core);
    }

    public static void beginFinalCollapse(ServerLevel level, BlockPos pos,
                                          BlockState state) {
        if (state.getValue(FINAL_STAGE) > 0) return;
        level.setBlock(pos, state.setValue(FINAL_STAGE, 1), Block.UPDATE_ALL);
        emitFinalizingParticles(level, pos, 1);
        level.scheduleTick(pos, state.getBlock(), FINAL_STAGE_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos,
                        RandomSource random) {
        int stage = state.getValue(FINAL_STAGE);
        if (stage <= 0) return;
        if (stage < 9) {
            int next = stage + 1;
            level.setBlock(pos, state.setValue(FINAL_STAGE, next), Block.UPDATE_ALL);
            emitFinalizingParticles(level, pos, (next - 1) % 3 + 1);
            level.scheduleTick(pos, this, FINAL_STAGE_TICKS);
            return;
        }

        level.removeBlock(pos, false);
        level.playSound(null, pos, ModSounds.STILLPOINT_EXHAUST.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 4.0F, 0.72F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                2, 0.2D, 0.2D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                3, 0.12D, 0.12D, 0.12D, 0.0D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                96, 0.65D, 0.65D, 0.65D, 0.28D);
    }

    private static void emitFinalizingParticles(ServerLevel level, BlockPos pos, int stage) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z,
                12 + stage * 8, 0.38D, 0.38D, 0.38D, 0.055D);
        level.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(), x, y, z,
                7 + stage * 4, 0.32D, 0.32D, 0.32D, 0.045D);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DEPLOYED, USES, FINAL_STAGE);
    }
}
