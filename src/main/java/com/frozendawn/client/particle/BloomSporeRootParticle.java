package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A pale seed shard that brightens and rises as a Spore roots into terrain. */
public final class BloomSporeRootParticle extends TextureSheetParticle {
    private final float startingSize;
    private final float spinDirection;

    private BloomSporeRootParticle(ClientLevel level, double x, double y, double z,
                                   double xSpeed, double ySpeed, double zSpeed,
                                   SpriteSet sprites) {
        super(level, x, y, z);
        xd = xSpeed;
        yd = 0.018D + Math.abs(ySpeed) * 0.45D;
        zd = zSpeed;
        friction = 0.92F;
        gravity = -0.012F;
        hasPhysics = false;
        lifetime = 22 + random.nextInt(13);
        startingSize = 0.055F + random.nextFloat() * 0.055F;
        quadSize = startingSize;
        spinDirection = random.nextBoolean() ? 1.0F : -1.0F;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        setColor(0.82F, 0.93F, 0.84F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        float life = age / (float) lifetime;
        oRoll = roll;
        roll += spinDirection * (0.08F + life * 0.16F);
        double curl = 0.0018D * (1.0D - life);
        xd += Math.cos(age * 0.43D) * curl * spinDirection;
        zd += Math.sin(age * 0.43D) * curl * spinDirection;
        yd += life < 0.55F ? 0.0012D : -0.0008D;
        quadSize = startingSize * (0.72F + Mth.sin(life * Mth.PI) * 1.35F);
        rCol = Mth.lerp(life, 0.82F, 0.70F);
        gCol = Mth.lerp(life, 0.93F, 1.0F);
        bCol = Mth.lerp(life, 0.84F, 0.62F);
        if (life > 0.62F) {
            setAlpha(Mth.clamp((1.0F - life) / 0.38F, 0.0F, 1.0F));
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 240;
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
            return new BloomSporeRootParticle(level, x, y, z,
                    xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
