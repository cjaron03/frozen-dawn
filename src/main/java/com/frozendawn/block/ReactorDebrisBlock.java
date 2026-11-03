package com.frozendawn.block;

import com.frozendawn.init.ModItems;
import com.frozendawn.item.ShieldedCaskItem;
import com.frozendawn.radiation.RadiationSourceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Fragments of core and fuel thrown out by a hydrogen or steam explosion (Chernobyl's roof debris). Hot and fiercely
 * radioactive. Right-click with a Shielded Cask that has room to pick one up safely. */
public final class ReactorDebrisBlock extends Block {
    public ReactorDebrisBlock(Properties p) { super(p); }
    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (level instanceof ServerLevel l && !old.is(this)) RadiationSourceData.get(l).put(pos, RadiationSourceData.Kind.DEBRIS);
    }
    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (level instanceof ServerLevel l && !next.is(this)) RadiationSourceData.get(l).remove(pos);
        super.onRemove(s, level, pos, next, moving);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.SHIELDED_CASK.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (ShieldedCaskItem.rods(stack) >= ShieldedCaskItem.CAPACITY) return ItemInteractionResult.FAIL;
        if (!level.isClientSide()) {
            ShieldedCaskItem.add(stack);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.7f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof LivingEntity living && !living.fireImmune()) entity.hurt(level.damageSources().hotFloor(), 3f);
        super.stepOn(level, pos, state, entity);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.8, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
    }
}
