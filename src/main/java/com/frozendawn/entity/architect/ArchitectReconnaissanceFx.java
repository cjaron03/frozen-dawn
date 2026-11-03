package com.frozendawn.entity.architect;

import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Client particles only. The existing living entity remains at the visible cloud. */
public final class ArchitectReconnaissanceFx {
    private ArchitectReconnaissanceFx() { }

    public static void tick(ArchitectEntity actor) {
        int form = actor.getReconnaissanceDissolve();
        if (form == 0 || !actor.level().isClientSide()) return;
        // Fixed samples do not consume the executor's random stream. Always-visible
        // particles keep the vulnerable body legible on reduced particle settings.
        for (int i = 0; i < 4; i++) {
            double angle = (actor.tickCount * .47 + i * Math.PI / 2);
            double radius = form < 0 ? 1.15 : .30 + .4 * Math.abs(form) / 20.0;
            double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
            double y = .35 + Math.floorMod(actor.tickCount + i * 7, 20) * .08;
            double speed = form < 0 ? -.17 : form < 20 ? .055 : .006;
            actor.level().addAlwaysVisibleParticle(ParticleTypes.SOUL, true,
                    actor.getX() + x, actor.getY() + y, actor.getZ() + z,
                    x * speed, form < 0 ? (1 - y) * .12 : .01, z * speed);
        }
        exchange(actor);
    }

    /** One exchange, tied to the server's departure clock, never to observed player input. */
    private static void exchange(ArchitectEntity actor) {
        int age = actor.getReconnaissanceCloudAge();
        boolean outgoing = age >= 20 && age < 60;
        boolean incoming = age >= 340 && age < 380;
        if (!outgoing && !incoming) return;
        Vec3 origin = actor.position().add(0, 1.1, 0);
        Vec3 end = origin.add(0, 6, 0);
        // This ray stays in the actor's loaded column. A roof shortens the signal;
        // there is no entity or destination in the sky and no world information is reported to Maeve.
        if (!actor.level().hasChunkAt(actor.blockPosition())) return;
        var hit = actor.level().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
        double height = hit.getType() == HitResult.Type.MISS ? 6 : Math.max(0, hit.getLocation().y - origin.y - .3);
        double progress = outgoing ? (age - 20) / 39.0 : 1 - (age - 340) / 39.0;
        double angle = progress * Math.PI * 4;
        double x = origin.x + Math.cos(angle) * .12, z = origin.z + Math.sin(angle) * .12;
        actor.level().addAlwaysVisibleParticle(ParticleTypes.SOUL, true,
                x, origin.y + height * progress, z, 0, outgoing ? .035 : -.035, 0);
        actor.level().addAlwaysVisibleParticle(ParticleTypes.SOUL_FIRE_FLAME, true,
                x, origin.y + height * progress, z, 0, 0, 0);
        if (age == 20 || age == 379) {
            for (int i = 0; i < 12; i++) {
                double theta = i * Math.PI / 6, dx = Math.cos(theta), dz = Math.sin(theta);
                double radius = outgoing ? .3 : 1.1, speed = outgoing ? .06 : -.06;
                actor.level().addAlwaysVisibleParticle(ParticleTypes.SOUL_FIRE_FLAME, true,
                        origin.x + dx * radius, origin.y, origin.z + dz * radius, dx * speed, 0, dz * speed);
            }
        }
    }
}
