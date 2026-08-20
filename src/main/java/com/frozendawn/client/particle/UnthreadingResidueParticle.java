package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A pale, broken-body mote shared with the Undone's visual vocabulary. */
public final class UnthreadingResidueParticle extends TextureSheetParticle {
    private final float initialSize;
    private final float driftPhase;

    private UnthreadingResidueParticle(
            ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        xd = xSpeed * 0.48D;
        yd = ySpeed * 0.2D + 0.006D;
        zd = zSpeed * 0.48D;
        friction = 0.965F;
        gravity = 0.002F;
        hasPhysics = false;
        lifetime = 34 + random.nextInt(25);
        initialSize = 0.045F + random.nextFloat() * 0.075F;
        quadSize = initialSize;
        driftPhase = random.nextFloat() * Mth.TWO_PI;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        setColor(0.74F, 0.81F, 0.82F);
        setAlpha(0.76F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float life = age / (float) lifetime;
        xd += Math.sin(driftPhase + age * 0.19D) * 0.0009D;
        zd += Math.cos(driftPhase + age * 0.17D) * 0.0009D;
        oRoll = roll;
        roll += 0.018F;
        quadSize = initialSize * Mth.lerp(life, 1.0F, 0.55F);
        setAlpha(Mth.clamp((1.0F - life) * 1.15F, 0.0F, 0.76F));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 170;
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
            return new UnthreadingResidueParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
