package com.frozendawn.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A Seared target's embers: white-hot as they leave the burn, cooling through orange to a dull red. */
public final class SearEmberParticle extends TextureSheetParticle {
    private static final float[][] COOLING = {{1F, .93F, .72F}, {1F, .52F, .16F}, {.62F, .1F, .05F}};
    private final float baseSize;

    private SearEmberParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        // Effect particles arrive with a placeholder speed; embers pick their own drift.
        xd = (random.nextDouble() - .5) * .02;
        yd = .015 + random.nextDouble() * .025;
        zd = (random.nextDouble() - .5) * .02;
        friction = .94F;
        gravity = -.01F;
        hasPhysics = false;
        lifetime = 14 + random.nextInt(12);
        baseSize = .04F + random.nextFloat() * .035F;
        quadSize = baseSize;
        pickSprite(sprites);
        cool(0);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float life = age / (float) lifetime;
        cool(life);
        quadSize = baseSize * (1F - life * .6F);
        setAlpha(Mth.clamp((1F - life) / .3F, 0F, 1F));
    }

    private void cool(float life) {
        float at = life * (COOLING.length - 1);
        int i = Math.min(COOLING.length - 2, (int) at);
        float t = at - i;
        setColor(Mth.lerp(t, COOLING[i][0], COOLING[i + 1][0]), Mth.lerp(t, COOLING[i][1], COOLING[i + 1][1]),
                Mth.lerp(t, COOLING[i][2], COOLING[i + 1][2]));
    }

    /** Embers glow at full brightness in the dark. */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new SearEmberParticle(level, x, y, z, sprites);
        }
    }
}
