package com.frozendawn.entity;

import com.frozendawn.init.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class HeavySnowballEntity extends ThrowableItemProjectile {

    public HeavySnowballEntity(EntityType<? extends HeavySnowballEntity> type, Level level) {
        super(type, level);
    }

    public HeavySnowballEntity(Level level, LivingEntity shooter) {
        super(ModEntities.HEAVY_SNOWBALL.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (result.getEntity() instanceof LivingEntity living) {
            living.hurt(damageSources().thrown(this, getOwner()), 3.0f);
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1)); // Slowness II for 3s
            living.setTicksFrozen(living.getTicksFrozen() + 100);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide()) {
            for (int i = 0; i < 8; i++) {
                level().addParticle(ParticleTypes.ITEM_SNOWBALL,
                        getX(), getY(), getZ(),
                        random.nextGaussian() * 0.15,
                        random.nextDouble() * 0.2,
                        random.nextGaussian() * 0.15);
            }
        }
        discard();
    }
}
