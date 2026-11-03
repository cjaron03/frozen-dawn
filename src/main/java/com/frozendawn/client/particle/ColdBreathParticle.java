package com.frozendawn.client.particle;

import com.frozendawn.client.ColdBreathPolicy;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Small expanding wisps, naturally lit and collision-aware around the player's mouth. */
public final class ColdBreathParticle extends TextureSheetParticle {
    private static ShaderInstance shader;

    public static void setShader(ShaderInstance loadedShader) { shader = loadedShader; }

    private static final ParticleRenderType SOFT_BREATH = new ParticleRenderType() {
        @Override public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
            RenderSystem.setShader(() -> shader != null ? shader : GameRenderer.getParticleShader());
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // Keep terrain depth testing, without faint wisps occluding other particles.
            RenderSystem.depthMask(false);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
        @Override public String toString() { return "FROZENDAWN_COLD_BREATH"; }
    };

    private ColdBreathParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx; yd = vy; zd = vz;
        friction = 0.94F;
        gravity = -0.002F;
        hasPhysics = true;
        lifetime = 32 + random.nextInt(9);
        quadSize = ColdBreathPolicy.size(0);
        setSize(0.035F, 0.035F);
        setColor(0.88F, 0.93F, 0.97F);
        setAlpha(0);
        pickSprite(sprites);
    }
    @Override public void tick() {
        super.tick();
        if (removed) return;
        float life = age / (float) lifetime;
        quadSize = ColdBreathPolicy.size(life);
        setAlpha(ColdBreathPolicy.opacity(life));
    }
    @Override public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        float life = (age + partialTick) / lifetime;
        quadSize = ColdBreathPolicy.size(life);
        setAlpha(ColdBreathPolicy.opacity(life));
        super.render(buffer, camera, partialTick);
    }
    @Override public ParticleRenderType getRenderType() { return SOFT_BREATH; }
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new ColdBreathParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
