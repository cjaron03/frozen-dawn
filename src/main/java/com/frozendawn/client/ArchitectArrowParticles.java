package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Local presentation only: no projectile mutation, entity scans, or particle packets. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class ArchitectArrowParticles {
    private ArchitectArrowParticles() {}

    @SubscribeEvent
    public static void onArrowTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Arrow arrow) || !arrow.level().isClientSide
                || arrow.isRemoved() || arrow.isInvisible()
                || !(arrow.getOwner() instanceof ArchitectEntity architect)
                || architect.isHearthMasterArchitect()) return;

        // ClientLevel records these coordinates before ticking. Embedded arrows do not move;
        // using actual travel also handles shield deflections without trusting retained velocity.
        Vec3 start = new Vec3(arrow.xOld, arrow.yOld, arrow.zOld);
        Vec3 travel = arrow.position().subtract(start);
        if (travel.lengthSqr() < 1.0E-4 || travel.lengthSqr() > 16) return;
        Vec3 trail = start.add(travel.scale(.5));
        arrow.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                trail.x, trail.y, trail.z, 0, .005, 0);
        if (arrow.tickCount % 4 == 0) {
            arrow.level().addParticle(ParticleTypes.SOUL,
                    trail.x, trail.y, trail.z, 0, .01, 0);
        }
    }
}
