package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A narrow pressure filament rising from a player-attributed post-Maeve kill. */
public final class AggregatePressureSignalParticle extends TextureSheetParticle {
    private final float initialSize;

    private AggregatePressureSignalParticle(
            ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        xd = xSpeed;
        yd = ySpeed;
        zd = zSpeed;
        gravity = 0.0F;
        friction = 0.992F;
        hasPhysics = false;
        lifetime = 42 + random.nextInt(9);
        initialSize = 0.12F + random.nextFloat() * 0.08F;
        quadSize = initialSize;
        setColor(0.76F, 0.79F, 0.76F);
        setAlpha(0.92F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float progress = age / (float)lifetime;
        xd += (random.nextDouble() - 0.5D) * 0.0012D;
        zd += (random.nextDouble() - 0.5D) * 0.0012D;
        quadSize = initialSize * Mth.lerp(progress, 1.0F, 0.3F);
        setAlpha(Mth.clamp((1.0F - progress) * 1.28F, 0.0F, 0.92F));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 210;
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
            return new AggregatePressureSignalParticle(
                    level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
