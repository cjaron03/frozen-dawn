package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** The pale mote a stopped mind lets go of: it lifts half a block from the head, hangs, flickers once, and winks out. */
public final class TerminusMoteParticle extends TextureSheetParticle {
    private static final int LIFT = 8, LIFE = 22;

    private TerminusMoteParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        xd = zd = 0;
        yd = .07;
        friction = .82F;
        gravity = 0;
        hasPhysics = false;
        lifetime = LIFE;
        quadSize = .07F;
        setColor(.95F, .98F, 1F);
        pickSprite(sprites);
    }

    @Override public void tick() {
        super.tick();
        if (removed) return;
        if (age > LIFT) yd = 0;
        setAlpha(age == LIFE - 5 || age == LIFE - 3 ? .25F : 1F);
    }

    @Override protected int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double vx, double vy, double vz) {
            return new TerminusMoteParticle(level, x, y, z, sprites);
        }
    }
}
