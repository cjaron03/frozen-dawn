package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Sparse optional particles that give the otherwise shader-only shell a material edge. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class StillpointClientParticles {
    private StillpointClientParticles() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || !StillpointClientState.isPresentHere()) {
            return;
        }
        double density = FrozenDawnClientConfig.STILLPOINT_PARTICLE_DENSITY.get();
        if (density <= 0.0D) return;
        Vec3 center = StillpointClientState.center().getCenter();
        RandomSource random = minecraft.level.random;
        if (StillpointClientState.isChargingHere()
                && minecraft.player.distanceToSqr(center) < 80.0D * 80.0D) {
            int count = random.nextDouble() < density ? 2 : 0;
            for (int i = 0; i < count; i++) {
                Vec3 offset = randomUnit(random).scale(2.5D + random.nextDouble() * 2.5D);
                Vec3 inward = offset.normalize().scale(-0.09D);
                minecraft.level.addParticle(ParticleTypes.REVERSE_PORTAL,
                        center.x + offset.x, center.y + offset.y, center.z + offset.z,
                        inward.x, inward.y, inward.z);
            }
            return;
        }
        if (!StillpointClientState.isActiveHere()) return;
        float formationAge = StillpointClientState.formationAgeTicks(0.0F);
        if (formationAge < 24.0F) {
            double progress = 1.0D - Math.pow(1.0D - formationAge / 24.0D, 3.0D);
            int count = Math.max(2, (int) Math.round(10.0D * density));
            for (int i = 0; i < count; i++) {
                Vec3 direction = randomUnit(random);
                Vec3 shell = center.add(direction.scale(
                        StillpointClientState.radius() * progress));
                Vec3 velocity = direction.scale(0.18D + random.nextDouble() * 0.16D);
                minecraft.level.addParticle(ParticleTypes.END_ROD,
                        shell.x, shell.y, shell.z,
                        velocity.x, velocity.y, velocity.z);
            }
        }
        int attempts = density >= 0.75D ? 2 : 1;
        for (int i = 0; i < attempts; i++) {
            if (random.nextDouble() > density * 0.36D) continue;
            Vec3 shell = center.add(randomUnit(random).scale(StillpointClientState.radius()));
            if (minecraft.player.distanceToSqr(shell) > 72.0D * 72.0D) continue;
            minecraft.level.addParticle(ParticleTypes.WHITE_ASH,
                    shell.x, shell.y, shell.z, 0.0D, 0.004D, 0.0D);
        }
    }

    private static Vec3 randomUnit(RandomSource random) {
        double y = random.nextDouble() * 2.0D - 1.0D;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
        return new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal);
    }
}
