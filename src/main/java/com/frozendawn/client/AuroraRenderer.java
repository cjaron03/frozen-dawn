package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aurora.AuroraPolicy;
import com.frozendawn.config.FrozenDawnClientConfig;
import com.frozendawn.network.AuroraStatePayload;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Distant curtains drawn before terrain/weather, masked by vanilla's actual cloud texture. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class AuroraRenderer {
    private static final ResourceLocation CLOUDS = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
    private static final int SEGMENTS = 112;
    private static final int ROWS = 8;
    private static ShaderInstance shader;
    private static AuroraStatePayload display = AuroraStatePayload.NONE;
    private AuroraRenderer() { }

    public static void setShader(ShaderInstance value) { shader = value; }
    public static void receive(AuroraStatePayload payload) { display = payload; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { display = AuroraStatePayload.NONE; }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || shader == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD
                || !FrozenDawnClientConfig.ENABLE_AURORAS.get() || ApocalypseClientData.getPhase() > 3
                || PostMaeveClientState.isMaeveErased() || display.durationTicks() <= 0
                || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        if (event.getCamera().getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float daylightPartial = mc.level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) ? partial : 0;
        double clock = display.preview() ? mc.level.getGameTime() + partial : mc.level.getDayTime() + daylightPartial;
        double age = clock - display.startTick();
        float opacity = display.strength() * AuroraPolicy.envelope(age, display.durationTicks())
                * AuroraPolicy.darkness(mc.level.getDayTime(), daylightPartial)
                * AuroraPolicy.weatherVisibility(mc.level.getRainLevel(partial), mc.level.getThunderLevel(partial))
                * FrozenDawnClientConfig.AURORA_BRIGHTNESS.get().floatValue();
        if (opacity < 0.002F) return;

        var camera = event.getCamera().getPosition();
        float cloudHeight = mc.level.effects().getCloudHeight();
        boolean clouds = mc.options.getCloudsType() != CloudStatus.OFF && Float.isFinite(cloudHeight);
        // Same 12-block texels, 0.03-block/tick drift, +0.33 cloud-space Z/Y offsets as LevelRenderer.
        shader.safeGetUniform("CloudOrigin").set(
                (float) ((camera.x + (event.getRenderTick() + partial) * 0.03D) % 3072.0D),
                (float) camera.y, (float) ((camera.z + 3.96D) % 3072.0D));
        shader.safeGetUniform("CloudHeight").set(clouds ? cloudHeight + 0.33F : 0.0F);
        shader.safeGetUniform("CloudMask").set(clouds ? 1.0F : 0.0F);
        shader.safeGetUniform("Intensity").set(opacity * 0.60F);
        double animation = (mc.level.getGameTime() % 2_400_000L + partial) / 20.0D;
        shader.safeGetUniform("Time").set((float) animation);
        shader.setSampler("CloudSampler", mc.getTextureManager().getTexture(CLOUDS).getId());

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            double seedPhase = AuroraPolicy.unit(AuroraPolicy.mix(display.seed())) * Math.PI * 2;
            Matrix4f view = event.getModelViewMatrix();
            shader.safeGetUniform("SkyView").set(view);
            shader.safeGetUniform("EventPhase").set((float) seedPhase);
            for (int ribbon = 0; ribbon < 3; ribbon++) {
                double ribbonSeed = AuroraPolicy.unit(AuroraPolicy.mix(
                        display.seed() + ribbon * 0x517cc1b727220a95L)) * Math.PI * 2;
                CurtainColumn leftColumn = column(0, ribbon, animation, ribbonSeed);
                for (int segment = 0; segment < SEGMENTS; segment++) {
                    float left = segment / (float) SEGMENTS, right = (segment + 1F) / SEGMENTS;
                    CurtainColumn rightColumn = column(right, ribbon, animation, ribbonSeed);
                    for (int row = 0; row < ROWS; row++) {
                        float bottom = row / (float) ROWS, top = (row + 1F) / ROWS;
                        vertex(buffer, left, bottom, ribbon, leftColumn);
                        vertex(buffer, right, bottom, ribbon, rightColumn);
                        vertex(buffer, right, top, ribbon, rightColumn);
                        vertex(buffer, left, top, ribbon, leftColumn);
                    }
                    leftColumn = rightColumn;
                }
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    /** Compute each fold once per column, shared by all vertical cells. */
    private static CurtainColumn column(float along, int ribbon, double time, double seed) {
        double broadFold = Math.sin(along * (7.8 + ribbon * 0.65) + seed + time * 0.045);
        double pleat = Math.sin(along * (18.0 + ribbon * 1.7) - time * 0.030 + seed * 1.41);
        double fineFold = Math.sin(along * 33.0 + time * 0.020 + seed * 0.73);
        double yaw = (along - 0.5) * (1.85 + ribbon * 0.07)
                + (seed - Math.PI) * 0.07 + (ribbon - 1) * 0.26
                + broadFold * 0.13 + pleat * 0.060;
        double base = 0.20 + ribbon * 0.14 + broadFold * 0.050
                + pleat * 0.025 + fineFold * 0.010;
        double height = 0.45 + 0.09 * Math.sin(along * 5.0 + seed)
                + broadFold * 0.035;
        double lean = 0.045 * Math.sin(along * 11.0 + seed + time * 0.022);
        return new CurtainColumn(yaw, base, height, lean, pleat * 0.055);
    }

    private static void vertex(BufferBuilder buffer, float along, float up, int ribbon, CurtainColumn column) {
        double yaw = column.yaw() + up * up * column.lean()
                + Math.sin(up * Math.PI) * column.curl();
        double elevation = column.base() + up * column.height();
        float horizontal = (float) Math.cos(elevation) * 100F;
        buffer.addVertex((float) Math.sin(yaw) * horizontal, (float) Math.sin(elevation) * 100F,
                        (float) -Math.cos(yaw) * horizontal)
                .setUv(along + ribbon * 2.0F, up)
                .setColor(1.0F, 1.0F, 1.0F, 1.0F - ribbon * 0.20F);
    }

    private record CurtainColumn(double yaw, double base, double height, double lean, double curl) { }
}
