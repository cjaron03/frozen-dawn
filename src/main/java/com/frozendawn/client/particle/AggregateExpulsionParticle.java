package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A hot ossuary splinter violently expelled with an Aggregate reinforcement. */
public final class AggregateExpulsionParticle extends TextureSheetParticle {
    private final float initialSize;
    private final float spin;

    private AggregateExpulsionParticle(
            ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        xd = xSpeed * 1.35D;
        yd = ySpeed * 1.15D + 0.08D;
        zd = zSpeed * 1.35D;
        friction = 0.84F;
        gravity = 0.48F;
        hasPhysics = true;
        lifetime = 11 + random.nextInt(9);
        initialSize = 0.34F + random.nextFloat() * 0.38F;
        quadSize = initialSize * 0.35F;
        spin = (random.nextFloat() - 0.5F) * 0.9F;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        float palette = random.nextFloat();
        if (palette < 0.48F) {
            setColor(1.0F, 0.84F, 0.58F);
        } else if (palette < 0.78F) {
            setColor(0.72F, 0.24F, 0.10F);
        } else {
            setColor(0.27F, 0.24F, 0.22F);
        }
        setAlpha(1.0F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        oRoll = roll;
        roll += spin * (1.0F - age / (float) lifetime * 0.45F);
        float progress = age / (float) lifetime;
        float punch = progress < 0.18F
                ? Mth.lerp(progress / 0.18F, 0.35F, 1.25F)
                : Mth.lerp((progress - 0.18F) / 0.82F, 1.25F, 0.12F);
        quadSize = initialSize * punch;
        setAlpha(Mth.clamp((1.0F - progress) * 1.55F, 0.0F, 1.0F));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0x00F000F0;
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
            return new AggregateExpulsionParticle(
                    level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
