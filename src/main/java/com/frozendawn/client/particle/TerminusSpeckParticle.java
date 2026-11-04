package com.frozendawn.client.particle;

import com.frozendawn.client.TerminusClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A pale speck drawn straight in toward a winding Terminus, brightening as it arrives and gone at the emitter. */
public final class TerminusSpeckParticle extends TextureSheetParticle {
    private TerminusSpeckParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx; yd = vy; zd = vz;
        friction = 1F;
        gravity = 0;
        hasPhysics = false;
        lifetime = TerminusClient.SPECK_TICKS;
        quadSize = .025F + random.nextFloat() * .015F;
        setColor(.86F, .94F, 1F);
        setAlpha(0);
        pickSprite(sprites);
    }

    @Override public void tick() {
        super.tick();
        if (!removed) setAlpha(Mth.clamp(age / (float) lifetime * 1.4F, 0F, 1F));
    }

    @Override protected int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double vx, double vy, double vz) {
            return new TerminusSpeckParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
