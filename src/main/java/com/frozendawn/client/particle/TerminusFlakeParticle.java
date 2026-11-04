package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A grey flake of a stopped body, one texel across: it drifts off on the speed it is given, pales, and is gone. */
public final class TerminusFlakeParticle extends TextureSheetParticle {
    private final float r0, g0, b0;

    private TerminusFlakeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx; yd = vy; zd = vz;
        friction = .96F;
        gravity = 0;
        hasPhysics = false;
        lifetime = 11 + random.nextInt(7);
        quadSize = 1 / 32F;
        // Between the two greys of the mockup's body.
        float k = random.nextFloat();
        r0 = Mth.lerp(k, .54F, .66F); g0 = Mth.lerp(k, .57F, .69F); b0 = Mth.lerp(k, .59F, .71F);
        setColor(r0, g0, b0);
        pickSprite(sprites);
    }

    @Override public void tick() {
        super.tick();
        if (removed) return;
        float t = age / (float) lifetime;
        setColor(Mth.lerp(t, r0, .9F), Mth.lerp(t, g0, .95F), Mth.lerp(t, b0, .97F));
        setAlpha(1 - t * t);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double vx, double vy, double vz) {
            return new TerminusFlakeParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
