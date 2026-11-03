package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Cold vapour spilling off a frosted Hyperion cell: it drifts out, sinks slowly, swells and fades. */
public final class ColdVapourParticle extends TextureSheetParticle {
    private final float startSize;

    private ColdVapourParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx; yd = vy; zd = vz;
        friction = 0.96F;
        gravity = 0.004F;
        hasPhysics = true;
        lifetime = 60 + random.nextInt(61);
        startSize = 0.12F + random.nextFloat() * 0.08F;
        quadSize = startSize;
        setColor(0.82F, 0.88F, 0.95F);
        setAlpha(0);
        pickSprite(sprites);
    }
    @Override public void tick() {
        super.tick();
        if (removed) return;
        float life = age / (float) lifetime;
        quadSize = startSize * (1 + 2.5F * life);
        // A quick fade in, a long fade out.
        setAlpha(0.32F * Math.min(1, life * 6) * (1 - life));
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ColdVapourParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
