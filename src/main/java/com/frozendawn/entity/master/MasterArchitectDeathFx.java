package com.frozendawn.entity.master;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Master-only charge, detonation, and non-destructive blast effects. */
public final class MasterArchitectDeathFx {

    private MasterArchitectDeathFx() {
    }

    public static void tickCharge(
            ServerLevel level, ArchitectEntity architect, int deathTicks) {
        if (deathTicks < MasterArchitectCombatPolicy.DEATH_CHARGE_START_TICK) {
            return;
        }

        float charge = MasterArchitectCombatPolicy.deathChargeProgress(deathTicks);
        Vec3 center = architect.position().add(0.0D, 1.0D, 0.0D);
        int cadence = charge < 0.45F ? 3 : 2;
        if (deathTicks % cadence == 0) {
            int particles = charge < 0.65F ? 2 : 4;
            for (int i = 0; i < particles; i++) {
                double angle = architect.getRandom().nextDouble() * Math.PI * 2.0D;
                double radius = 0.8D + architect.getRandom().nextDouble() * 1.1D;
                double yOffset = architect.getRandom().nextDouble() * 1.9D - 0.7D;
                Vec3 origin = center.add(
                        Math.cos(angle) * radius,
                        yOffset,
                        Math.sin(angle) * radius);
                Vec3 velocity = center.subtract(origin).normalize()
                        .scale(0.07D + charge * 0.10D);
                level.sendParticles(
                        ParticleTypes.END_ROD,
                        origin.x,
                        origin.y,
                        origin.z,
                        0,
                        velocity.x,
                        velocity.y,
                        velocity.z,
                        1.0D);
            }
        }
        if (deathTicks % 4 == 0) {
            level.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    center.x,
                    center.y,
                    center.z,
                    2 + Mth.floor(charge * 4.0F),
                    0.25D + charge * 0.35D,
                    0.55D + charge * 0.35D,
                    0.25D + charge * 0.35D,
                    0.03D + charge * 0.05D);
        }
    }

    public static void detonate(ServerLevel level, ArchitectEntity architect) {
        Vec3 center = architect.position().add(0.0D, 1.0D, 0.0D);
        level.playSound(
                null,
                BlockPos.containing(center),
                ModSounds.MASTER_ARCHITECT_DETONATE.get(),
                SoundSource.HOSTILE,
                3.0F,
                0.82F);
        level.sendParticles(
                ParticleTypes.FLASH,
                center.x,
                center.y,
                center.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                center.x,
                center.y,
                center.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
        level.sendParticles(
                ParticleTypes.SNOWFLAKE,
                center.x,
                center.y,
                center.z,
                110,
                1.0D,
                1.25D,
                1.0D,
                0.42D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                center.x,
                center.y,
                center.z,
                42,
                0.65D,
                0.85D,
                0.65D,
                0.28D);
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                center.x,
                center.y,
                center.z,
                55,
                0.8D,
                1.0D,
                0.8D,
                0.32D);
        level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                center.x,
                center.y,
                center.z,
                24,
                0.55D,
                0.75D,
                0.55D,
                0.18D);

        applyBlast(level, architect, center);
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.setVisualOnly(true);
            lightning.moveTo(architect.getX(), architect.getY(), architect.getZ());
            level.addFreshEntity(lightning);
        }
    }

    private static void applyBlast(
            ServerLevel level, ArchitectEntity architect, Vec3 center) {
        double radius = MasterArchitectCombatPolicy.DEATH_BLAST_RADIUS;
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != architect && candidate.isAlive())) {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            double distance = targetCenter.distanceTo(center);
            if (distance > radius) {
                continue;
            }
            float damage = MasterArchitectCombatPolicy.deathBlastDamage(distance);
            target.hurt(level.damageSources().explosion(null, architect), damage);

            Vec3 direction = targetCenter.subtract(center);
            if (direction.lengthSqr() < 1.0E-5D) {
                direction = new Vec3(1.0D, 0.0D, 0.0D);
            }
            float falloff = 1.0F - Mth.clamp((float) (distance / radius), 0.0F, 1.0F);
            Vec3 push = direction.normalize().scale(0.35D + falloff * 0.75D);
            target.push(push.x, 0.25D + falloff * 0.30D, push.z);
            target.hurtMarked = true;
        }
    }
}
