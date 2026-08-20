package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A torn ossuary shard used by the Aggregate's formation and expulsion beats. */
public final class AggregateConvergenceParticle extends TextureSheetParticle {
    private final float initialSize;
    private final float spin;

    private AggregateConvergenceParticle(
            ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        xd = xSpeed;
        yd = ySpeed;
        zd = zSpeed;
        friction = 0.92F;
        gravity = 0.18F;
        hasPhysics = true;
        lifetime = 18 + random.nextInt(13);
        initialSize = 0.18F + random.nextFloat() * 0.18F;
        quadSize = initialSize;
        spin = (random.nextFloat() - 0.5F) * 0.42F;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        if (random.nextBoolean()) {
            setColor(0.67F, 0.63F, 0.57F);
        } else {
            setColor(0.38F, 0.35F, 0.32F);
        }
        setAlpha(0.96F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        oRoll = roll;
        roll += spin;
        float progress = age / (float) lifetime;
        quadSize = initialSize * Mth.lerp(progress, 1.0F, 0.45F);
        setAlpha(Mth.clamp((1.0F - progress) * 1.35F, 0.0F, 0.96F));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 190;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new AggregateConvergenceParticle(
                    level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
