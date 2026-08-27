package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A dark memory filament that coils upward around the Unthreading vessel. */
public final class UnthreadingMemoryParticle extends TextureSheetParticle {
    private final float initialSize;
    private final float curlDirection;

    private UnthreadingMemoryParticle(
            ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        xd = xSpeed;
        yd = 0.014D + Math.abs(ySpeed) * 0.38D;
        zd = zSpeed;
        friction = 0.94F;
        gravity = -0.008F;
        hasPhysics = false;
        lifetime = 30 + random.nextInt(19);
        initialSize = 0.09F + random.nextFloat() * 0.11F;
        quadSize = initialSize;
        curlDirection = random.nextBoolean() ? 1.0F : -1.0F;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        setColor(0.12F, 0.18F, 0.21F);
        setAlpha(0.9F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float life = age / (float) lifetime;
        oRoll = roll;
        roll += curlDirection * (0.035F + life * 0.05F);
        double curl = (1.0D - life) * 0.0035D;
        xd += Math.cos(age * 0.31D) * curl * curlDirection;
        zd += Math.sin(age * 0.31D) * curl * curlDirection;
        quadSize = initialSize * (0.8F + Mth.sin(life * Mth.PI) * 0.75F);
        setAlpha(Mth.clamp((1.0F - life) * 1.4F, 0.0F, 0.9F));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 210;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new UnthreadingMemoryParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
