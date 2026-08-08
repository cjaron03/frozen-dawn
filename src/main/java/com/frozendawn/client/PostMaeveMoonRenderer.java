package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.homo.PostMaeveMoonPolicy;
import com.frozendawn.homo.PostMaeveMoonStage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Renders the nearby, damaged post-Maeve Moon and its orbital debris. */
public final class PostMaeveMoonRenderer {
    private static final ResourceLocation CLEAN_MOON = moonTexture(
            "post_maeve_moon_clean.png");
    private static final ResourceLocation STRESSED_MOON = moonTexture(
            "post_maeve_moon_stressed.png");
    private static final ResourceLocation CALVING_MOON = moonTexture(
            "post_maeve_moon_calving.png");
    private static final ResourceLocation RAGGED_MOON = moonTexture(
            "post_maeve_moon_ragged.png");
    private static final ResourceLocation RINGING_MOON = moonTexture(
            "post_maeve_moon_ringing.png");
    private static final ResourceLocation[] MOON_FRAGMENTS = {
            moonTexture("post_maeve_moon_fragment_1.png"),
            moonTexture("post_maeve_moon_fragment_2.png"),
            moonTexture("post_maeve_moon_fragment_3.png")
    };
    private static final float VANILLA_MOON_SIZE = 20.0F;
    private static final float SKY_DISTANCE = 96.0F;
    private static final double RING_ORBIT_PERIOD_TICKS = 24_000.0D;
    private static final double RING_PRECESSION_PERIOD_TICKS = 16.0D * 24_000.0D;
    private static final int MOON_LIGHT_GRID = 24;
    private static final float MOON_BODY_SCALE = 0.25F;
    private static final MoonUv MAIN_MOON_UV = new MoonUv(
            96.0F / 1024.0F, 96.0F / 512.0F,
            160.0F / 1024.0F, 160.0F / 512.0F);
    private static final MoonUv FRAGMENT_UV = new MoonUv(
            0.375F, 0.375F, 0.625F, 0.625F);

    private PostMaeveMoonRenderer() {
    }

    public static void render(Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                              float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.level.dimension() != Level.OVERWORLD
                || !PostMaeveClientState.isMaeveErased()
                || !PostMaeveClientState.isMoonriseStarted()
                || !FrozenDawnConfig.ENABLE_POST_MAEVE_MOON.get()) {
            return;
        }

        long elapsed = PostMaeveClientState.moonElapsedDayTicks(
                minecraft.level.getDayTime());
        PostMaeveMoonPolicy.Snapshot snapshot = PostMaeveMoonPolicy.snapshot(
                elapsed, PostMaeveClientState.moonVisualSeed());
        if (snapshot.stage() == PostMaeveMoonStage.HIDDEN) {
            return;
        }

        PoseStack poses = new PoseStack();
        poses.mulPose(frustumMatrix);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        FogRenderer.setupNoFog();

        long seed = PostMaeveClientState.moonVisualSeed();
        double dayTime = minecraft.level.getDayTime() + partialTick;
        double gameTime = minecraft.level.getGameTime() + partialTick;
        if (snapshot.ringAlpha() > 0.001F) {
            renderRing(poses, seed, snapshot.ringAlpha(), dayTime);
        }
        renderDebris(poses, snapshot, seed, gameTime);
        renderDamagedMoon(poses, snapshot, minecraft, seed, dayTime, gameTime);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private static void renderDamagedMoon(
            PoseStack poses, PostMaeveMoonPolicy.Snapshot snapshot,
            Minecraft minecraft, long seed, double dayTime, double gameTime) {
        poses.pushPose();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float parallaxYaw = (float) Math.toDegrees(Math.atan(camera.x / 6_000.0D))
                * 0.06F;
        float parallaxPitch = (float) Math.toDegrees(Math.atan(camera.z / 6_000.0D))
                * 0.045F;
        poses.mulPose(Axis.YP.rotationDegrees(-90.0F + parallaxYaw));
        poses.mulPose(Axis.ZP.rotationDegrees(parallaxPitch));
        poses.mulPose(Axis.XP.rotationDegrees(
                PostMaeveMoonPolicy.brokenOrbitDegrees(dayTime, seed)));

        float sizePulse = 1.0F
                + 0.010F * (float) Math.sin(gameTime / 173.0D)
                + 0.004F * (float) Math.sin(gameTime / 61.0D + 1.7D);
        float size = VANILLA_MOON_SIZE
                * PostMaeveMoonPolicy.apparentSizeScale(snapshot.damageAgeTicks())
                * sizePulse;
        MoonLight light = moonLight(gameTime, seed);
        renderMoonMesh(poses.last().pose(), size * MOON_BODY_SCALE, -100.0F,
                textureFor(snapshot.stage()), MAIN_MOON_UV, light);
        renderMoonFragments(poses, snapshot.stage(), size, gameTime, seed, light);
        poses.popPose();
    }

    private static void renderMoonFragments(PoseStack poses,
                                            PostMaeveMoonStage stage,
                                            float moonSize,
                                            double gameTime,
                                            long seed,
                                            MoonLight light) {
        int count = switch (stage) {
            case CALVING -> 1;
            case RAGGED -> 2;
            case RINGING -> 3;
            default -> 0;
        };
        float[] baseX = {0.85F, 0.90F, -0.75F};
        float[] baseZ = {-0.35F, 0.60F, 0.70F};
        float pixelScale = moonSize / 16.0F;
        for (int index = 0; index < count; index++) {
            long fragmentSeed = mix64(seed + index * 0x517CC1B727220A95L);
            double phase = unitHash(fragmentSeed) * Math.PI * 2.0D;
            float driftX = baseX[index]
                    + 0.20F * (float) Math.sin(gameTime / (360.0D + index * 91.0D)
                    + phase);
            float driftZ = baseZ[index]
                    + 0.16F * (float) Math.sin(gameTime / (470.0D + index * 73.0D)
                    + phase * 1.37D);
            float fragmentPulse = 1.0F + 0.018F * (float) Math.sin(
                    gameTime / (230.0D + index * 47.0D) + phase);
            poses.pushPose();
            poses.translate(driftX * pixelScale, 0.10D, driftZ * pixelScale);
            renderMoonMesh(poses.last().pose(),
                    moonSize * MOON_BODY_SCALE * fragmentPulse,
                    -100.0F, MOON_FRAGMENTS[index], FRAGMENT_UV, light);
            poses.popPose();
        }
    }

    private static void renderMoonMesh(Matrix4f matrix, float size, float depth,
                                       ResourceLocation texture, MoonUv uv,
                                       MoonLight light) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder moon = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int row = 0; row < MOON_LIGHT_GRID; row++) {
            float top = row / (float) MOON_LIGHT_GRID;
            float bottom = (row + 1.0F) / MOON_LIGHT_GRID;
            float zTop = Mth.lerp(top, size, -size);
            float zBottom = Mth.lerp(bottom, size, -size);
            float vTop = Mth.lerp(top, uv.v1(), uv.v0());
            float vBottom = Mth.lerp(bottom, uv.v1(), uv.v0());
            for (int column = 0; column < MOON_LIGHT_GRID; column++) {
                float left = column / (float) MOON_LIGHT_GRID;
                float right = (column + 1.0F) / MOON_LIGHT_GRID;
                float xLeft = Mth.lerp(left, -size, size);
                float xRight = Mth.lerp(right, -size, size);
                float uLeft = Mth.lerp(left, uv.u1(), uv.u0());
                float uRight = Mth.lerp(right, uv.u1(), uv.u0());
                addLitMoonVertex(moon, matrix, xLeft, depth, zTop,
                        uLeft, vTop, -1.0F + left * 2.0F,
                        1.0F - top * 2.0F, light);
                addLitMoonVertex(moon, matrix, xRight, depth, zTop,
                        uRight, vTop, -1.0F + right * 2.0F,
                        1.0F - top * 2.0F, light);
                addLitMoonVertex(moon, matrix, xRight, depth, zBottom,
                        uRight, vBottom, -1.0F + right * 2.0F,
                        1.0F - bottom * 2.0F, light);
                addLitMoonVertex(moon, matrix, xLeft, depth, zBottom,
                        uLeft, vBottom, -1.0F + left * 2.0F,
                        1.0F - bottom * 2.0F, light);
            }
        }
        BufferUploader.drawWithShader(moon.buildOrThrow());
    }

    private static void addLitMoonVertex(BufferBuilder buffer, Matrix4f matrix,
                                         float x, float y, float z,
                                         float u, float v,
                                         float normalX, float normalY,
                                         MoonLight light) {
        float radial = normalX * normalX + normalY * normalY;
        float normalZ = (float) Math.sqrt(Math.max(0.0F, 1.0F - radial));
        float diffuse = Math.max(0.0F,
                normalX * light.x() + normalY * light.y() + normalZ * light.z());
        float limb = 0.78F + 0.22F * normalZ;
        int shade = Math.round(255.0F * (0.12F + diffuse * 0.88F) * limb);
        buffer.addVertex(matrix, x, y, z)
                .setUv(u, v)
                .setColor(shade, shade, shade, 255);
    }

    private static ResourceLocation textureFor(PostMaeveMoonStage stage) {
        return switch (stage) {
            case STRESSED -> STRESSED_MOON;
            case CALVING -> CALVING_MOON;
            case RAGGED -> RAGGED_MOON;
            case RINGING -> RINGING_MOON;
            default -> CLEAN_MOON;
        };
    }

    private static ResourceLocation moonTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                FrozenDawn.MOD_ID, "textures/environment/" + name);
    }

    private static void renderDebris(PoseStack poses,
                                     PostMaeveMoonPolicy.Snapshot snapshot,
                                     long seed, double gameTime) {
        int count = Math.round(snapshot.debrisCount()
                * FrozenDawnConfig.POST_MAEVE_DEBRIS_DENSITY.get().floatValue());
        if (count <= 0) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poses.last().pose();
        for (int i = 0; i < count; i++) {
            long debrisSeed = mix64(seed + i * 0x632BE59BD9B4E019L);
            double periodTicks = Mth.lerp(unitHash(debrisSeed), 900.0D, 2400.0D);
            double phase = unitHash(debrisSeed ^ 0x4D595DF4D0F33173L)
                    * Math.PI * 2.0D;
            double angle = phase + gameTime / periodTicks * Math.PI * 2.0D;
            double inclination = Math.toRadians(Mth.lerp(
                    unitHash(debrisSeed ^ 0x94D049BB133111EBL), -64.0D, 64.0D));
            double node = unitHash(debrisSeed ^ 0xBF58476D1CE4E5B9L)
                    * Math.PI * 2.0D;
            Vec3 direction = orbitalDirection(angle, inclination, node);
            Vec3 center = direction.scale(SKY_DISTANCE);
            Vec3 right = new Vec3(-direction.z, 0.0D, direction.x);
            if (right.lengthSqr() < 0.001D) {
                right = new Vec3(1.0D, 0.0D, 0.0D);
            } else {
                right = right.normalize();
            }
            Vec3 up = direction.cross(right).normalize();
            float size = Mth.lerp((float) unitHash(
                    debrisSeed ^ 0xA24BAED4963EE407L), 0.18F, 0.55F);
            int warmth = Math.round(Mth.lerp((float) unitHash(
                    debrisSeed ^ 0x9FB21C651E98DF25L), 0.0F, 14.0F));
            addColorQuad(buffer, matrix, center, right, up, size,
                    154 + warmth, 150 + warmth, 142 + warmth, 196);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void renderRing(PoseStack poses, long seed, float alpha,
                                   double dayTime) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poses.last().pose();
        double inclination = Math.toRadians(PostMaeveMoonPolicy.ORBIT_INCLINATION_DEGREES);
        double node = Math.toRadians(seededNode(seed))
                + dayTime / RING_PRECESSION_PERIOD_TICKS * Math.PI * 2.0D;
        double orbitalRotation = dayTime / RING_ORBIT_PERIOD_TICKS
                * Math.PI * 2.0D;
        int segments = 144;
        double width = Math.toRadians(1.15D);
        for (int i = 0; i < segments; i++) {
            long segmentSeed = mix64(seed ^ i * 0xD6E8FEB86659FD93L);
            if (unitHash(segmentSeed) < 0.22D) {
                continue;
            }
            double a0 = i / (double) segments * Math.PI * 2.0D
                    + orbitalRotation;
            double a1 = (i + 1.0D) / segments * Math.PI * 2.0D
                    + orbitalRotation;
            Vec3 p00 = orbitalDirection(a0, inclination - width, node).scale(SKY_DISTANCE);
            Vec3 p01 = orbitalDirection(a0, inclination + width, node).scale(SKY_DISTANCE);
            Vec3 p11 = orbitalDirection(a1, inclination + width, node).scale(SKY_DISTANCE);
            Vec3 p10 = orbitalDirection(a1, inclination - width, node).scale(SKY_DISTANCE);
            int segmentAlpha = Math.round(alpha * Mth.lerp(
                    (float) unitHash(segmentSeed ^ 0xDB4F0B9175AE2165L),
                    24.0F, 58.0F));
            addColorVertex(buffer, matrix, p00, 139, 137, 131, segmentAlpha);
            addColorVertex(buffer, matrix, p01, 139, 137, 131, segmentAlpha);
            addColorVertex(buffer, matrix, p11, 139, 137, 131, segmentAlpha);
            addColorVertex(buffer, matrix, p10, 139, 137, 131, segmentAlpha);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void addColorQuad(BufferBuilder buffer, Matrix4f matrix,
                                     Vec3 center, Vec3 right, Vec3 up, float halfSize,
                                     int red, int green, int blue, int alpha) {
        addColorVertex(buffer, matrix,
                center.add(right.scale(-halfSize)).add(up.scale(-halfSize)),
                red, green, blue, alpha);
        addColorVertex(buffer, matrix,
                center.add(right.scale(halfSize)).add(up.scale(-halfSize)),
                red, green, blue, alpha);
        addColorVertex(buffer, matrix,
                center.add(right.scale(halfSize)).add(up.scale(halfSize)),
                red, green, blue, alpha);
        addColorVertex(buffer, matrix,
                center.add(right.scale(-halfSize)).add(up.scale(halfSize)),
                red, green, blue, alpha);
    }

    private static void addColorVertex(BufferBuilder buffer, Matrix4f matrix,
                                       Vec3 position, int red, int green,
                                       int blue, int alpha) {
        buffer.addVertex(matrix, (float) position.x, (float) position.y,
                        (float) position.z)
                .setColor(red, green, blue, alpha);
    }

    private static MoonLight moonLight(double gameTime, long seed) {
        double angle = unitHash(seed ^ 0x243F6A8885A308D3L) * Math.PI * 2.0D
                + smoothNoise(seed ^ 0x13198A2E03707344L, gameTime, 5_200.0D) * 1.15D
                + smoothNoise(seed ^ 0xA4093822299F31D0L, gameTime, 8_300.0D) * 0.42D;
        double front = Mth.clamp(0.62D
                + smoothNoise(seed ^ 0x082EFA98EC4E6C89L,
                gameTime, 7_100.0D) * 0.14D, 0.46D, 0.78D);
        double lateral = Math.sqrt(Math.max(0.0D, 1.0D - front * front));
        return new MoonLight(
                (float) (Math.cos(angle) * lateral),
                (float) (Math.sin(angle) * lateral),
                (float) front);
    }

    private static double smoothNoise(long seed, double time, double interval) {
        double cellPosition = Math.max(0.0D, time) / interval;
        long cell = (long) Math.floor(cellPosition);
        double progress = cellPosition - cell;
        double smooth = progress * progress * (3.0D - 2.0D * progress);
        double start = unitHash(mix64(seed + cell * 0x9E3779B97F4A7C15L))
                * 2.0D - 1.0D;
        double end = unitHash(mix64(seed + (cell + 1L) * 0x9E3779B97F4A7C15L))
                * 2.0D - 1.0D;
        return Mth.lerp(smooth, start, end);
    }

    private static Vec3 orbitalDirection(double angle, double inclination, double node) {
        double x = Math.cos(angle);
        double y = Math.sin(angle) * Math.sin(inclination);
        double z = Math.sin(angle) * Math.cos(inclination);
        return new Vec3(
                x * Math.cos(node) - z * Math.sin(node),
                y,
                x * Math.sin(node) + z * Math.cos(node)).normalize();
    }

    private static float seededNode(long seed) {
        return (float) Math.floorMod(mix64(seed), 360L);
    }

    private static double unitHash(long seed) {
        return (mix64(seed) >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record MoonUv(float u0, float v0, float u1, float v1) {
    }

    private record MoonLight(float x, float y, float z) {
    }
}
