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
    private static final int HOVER_TICKS = 14;

    private final float initialSize;
    private final double launchSpeed;
    private final SpriteSet sprites;

    private AggregatePressureSignalParticle(
            ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        launchSpeed = ySpeed;
        xd = xSpeed * 0.35D;
        yd = 0.006D + random.nextDouble() * 0.008D;
        zd = zSpeed * 0.35D;
        gravity = 0.0F;
        friction = 0.992F;
        hasPhysics = false;
        lifetime = 72 + random.nextInt(10);
        initialSize = 0.15F + random.nextFloat() * 0.09F;
        quadSize = initialSize;
        setColor(0.98F, 0.98F, 1.0F);
        setAlpha(0.96F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        float progress = age / (float)lifetime;
        if (age < HOVER_TICKS) {
            yd *= 0.76D;
            xd += (random.nextDouble() - 0.5D) * 0.0035D;
            zd += (random.nextDouble() - 0.5D) * 0.0035D;
        } else if (age == HOVER_TICKS) {
            yd = launchSpeed * 0.46D;
            xd *= 0.45D;
            zd *= 0.45D;
        } else {
            yd = Math.min(0.42D, yd + 0.0045D);
            xd *= 0.97D;
            zd *= 0.97D;
        }
        quadSize = initialSize * Mth.lerp(progress, 1.0F, 0.3F);
        setAlpha(Mth.clamp((1.0F - progress) * 1.35F, 0.0F, 0.96F));
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
