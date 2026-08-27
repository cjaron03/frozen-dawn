package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Slow, near-white material shed by living Bloom surfaces. */
public final class BloomDriftParticle extends TextureSheetParticle {
    private final float baseSize;
    private final float driftPhase;

    private BloomDriftParticle(ClientLevel level, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed,
                               SpriteSet sprites) {
        super(level, x, y, z);
        xd = xSpeed;
        yd = ySpeed;
        zd = zSpeed;
        friction = 0.985F;
        gravity = -0.002F;
        hasPhysics = false;
        lifetime = 56 + random.nextInt(45);
        baseSize = 0.025F + random.nextFloat() * 0.035F;
        quadSize = baseSize;
        driftPhase = random.nextFloat() * Mth.TWO_PI;
        setColor(0.86F, 0.94F, 0.91F);
        setAlpha(0.0F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        float life = age / (float) lifetime;
        double wave = driftPhase + age * 0.105D;
        xd += Math.cos(wave) * 0.00045D;
        zd += Math.sin(wave * 0.87D) * 0.00045D;
        yd += 0.00012D;
        quadSize = baseSize * (0.85F + Mth.sin(life * Mth.PI) * 0.5F);
        setAlpha(Mth.clamp(Math.min(life / 0.14F, (1.0F - life) / 0.25F),
                0.0F, 0.72F));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 220;
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new BloomDriftParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
