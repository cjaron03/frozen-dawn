package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AlarmBeaconBlockEntity;
import com.frozendawn.world.AlarmLightSweepSolver;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class AlarmDynamicLightManager {

    private static final int SEARCH_RADIUS_CHUNKS = 5;
    private static final int MAX_BEACONS = 2;
    private static final double MAX_DISTANCE_SQR = 88.0 * 88.0;

    private static volatile ClientLevel currentLevel;
    private static volatile Long2IntOpenHashMap currentDynamicLights = emptyMap();
    private static volatile List<AlarmLightSweepSolver.SurfacePaint> currentSurfacePaints = List.of();

    private AlarmDynamicLightManager() {
    }

    public static void prepareFrame(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
            clear();
            return;
        }

        ClientLevel level = mc.level;
        Vec3 playerPos = mc.player.position();
        List<AlarmBeaconBlockEntity> beacons = findNearestActiveBeacons(level, playerPos, partialTick);
        if (beacons.isEmpty()) {
            clear();
            return;
        }

        Long2IntOpenHashMap nextLights = emptyMap();
        Map<SurfaceKey, Float> nextPaints = new HashMap<>();
        for (AlarmBeaconBlockEntity beacon : beacons) {
            AlarmLightSweepSolver.SweepResult sweep = AlarmLightSweepSolver.solve(level, beacon, partialTick);
            for (Long2IntMap.Entry entry : sweep.dynamicLights().long2IntEntrySet()) {
                long posLong = entry.getLongKey();
                int light = entry.getIntValue();
                if (light > nextLights.get(posLong)) {
                    nextLights.put(posLong, light);
                }
            }
            for (AlarmLightSweepSolver.SurfacePaint paint : sweep.surfacePaints()) {
                SurfaceKey key = new SurfaceKey(paint.pos(), paint.face());
                nextPaints.merge(key, paint.strength(), Math::max);
            }
        }

        currentLevel = level;
        currentDynamicLights = nextLights;
        currentSurfacePaints = nextPaints.entrySet().stream()
                .map(entry -> new AlarmLightSweepSolver.SurfacePaint(entry.getKey().pos(), entry.getKey().face(), entry.getValue()))
                .toList();
    }

    public static int getDynamicLight(BlockAndTintGetter level, BlockPos pos) {
        ClientLevel activeLevel = currentLevel;
        if (!(level instanceof ClientLevel clientLevel) || activeLevel != clientLevel) {
            return 0;
        }
        return currentDynamicLights.get(pos.asLong());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || currentLevel != mc.level || currentSurfacePaints.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean rendered = false;
        for (AlarmLightSweepSolver.SurfacePaint paint : currentSurfacePaints) {
            rendered |= addSurfacePaint(buffer, poseStack, event, cameraPos, paint);
        }
        if (rendered) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static List<AlarmBeaconBlockEntity> findNearestActiveBeacons(ClientLevel level, Vec3 playerPos, float partialTick) {
        int originChunkX = BlockPos.containing(playerPos).getX() >> 4;
        int originChunkZ = BlockPos.containing(playerPos).getZ() >> 4;
        List<AlarmBeaconBlockEntity> candidates = new ArrayList<>();

        for (int chunkX = originChunkX - SEARCH_RADIUS_CHUNKS; chunkX <= originChunkX + SEARCH_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = originChunkZ - SEARCH_RADIUS_CHUNKS; chunkZ <= originChunkZ + SEARCH_RADIUS_CHUNKS; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof AlarmBeaconBlockEntity beacon) || !beacon.isEffectivelyRunning(partialTick)) {
                        continue;
                    }
                    if (playerPos.distanceToSqr(beacon.getHeadWorldPos()) <= MAX_DISTANCE_SQR) {
                        candidates.add(beacon);
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(beacon -> playerPos.distanceToSqr(beacon.getHeadWorldPos())));
        if (candidates.size() > MAX_BEACONS) {
            return new ArrayList<>(candidates.subList(0, MAX_BEACONS));
        }
        return candidates;
    }

    private static void clear() {
        currentLevel = null;
        currentDynamicLights = emptyMap();
        currentSurfacePaints = List.of();
    }

    private static Long2IntOpenHashMap emptyMap() {
        Long2IntOpenHashMap map = new Long2IntOpenHashMap();
        map.defaultReturnValue(0);
        return map;
    }

    private static boolean addSurfacePaint(BufferBuilder buffer, PoseStack poseStack, RenderLevelStageEvent event,
                                           Vec3 cameraPos, AlarmLightSweepSolver.SurfacePaint paint) {
        if (paint.face() != Direction.UP) {
            return false;
        }

        float strength = paint.strength();
        if (strength <= 0.05f) {
            return false;
        }

        Vec3 worldPos = Vec3.atLowerCornerOf(paint.pos());
        AABB bounds = new AABB(worldPos, worldPos.add(1.0, 1.0, 1.0));
        if (!event.getFrustum().isVisible(bounds)) {
            return false;
        }

        int outerA = alphaInt(strength * 0.64f);
        int coreA = alphaInt(strength * 0.86f);
        if (outerA <= 0 && coreA <= 0) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(worldPos.x - cameraPos.x, worldPos.y - cameraPos.y, worldPos.z - cameraPos.z);
        Matrix4f pose = poseStack.last().pose();
        float surfaceY = getSurfaceTopHeight(currentLevel, paint.pos());
        if (surfaceY <= 0.01f) {
            poseStack.popPose();
            return false;
        }

        boolean rendered = false;
        if (outerA > 0) {
            rendered |= addFaceQuad(buffer, pose, paint.face(), 0.015f, surfaceY, 214, 32, 24, outerA);
        }
        if (coreA > 0) {
            rendered |= addFaceQuad(buffer, pose, paint.face(), 0.12f, surfaceY, 255, 120, 96, coreA);
        }

        poseStack.popPose();
        return rendered;
    }

    private static boolean addFaceQuad(BufferBuilder buffer, Matrix4f pose, Direction face, float inset, float surfaceY,
                                       int r, int g, int b, int a) {
        if (a <= 0) {
            return false;
        }

        float min = inset;
        float max = 1.0f - inset;
        float o = 0.02f;

        switch (face) {
            case UP -> {
                buffer.addVertex(pose, min, surfaceY + o, min).setColor(r, g, b, a);
                buffer.addVertex(pose, max, surfaceY + o, min).setColor(r, g, b, a);
                buffer.addVertex(pose, max, surfaceY + o, max).setColor(r, g, b, a);
                buffer.addVertex(pose, min, surfaceY + o, max).setColor(r, g, b, a);
                return true;
            }
            case NORTH -> {
                buffer.addVertex(pose, min, min, -o).setColor(r, g, b, a);
                buffer.addVertex(pose, max, min, -o).setColor(r, g, b, a);
                buffer.addVertex(pose, max, max, -o).setColor(r, g, b, a);
                buffer.addVertex(pose, min, max, -o).setColor(r, g, b, a);
                return true;
            }
            case SOUTH -> {
                buffer.addVertex(pose, max, min, 1.0f + o).setColor(r, g, b, a);
                buffer.addVertex(pose, min, min, 1.0f + o).setColor(r, g, b, a);
                buffer.addVertex(pose, min, max, 1.0f + o).setColor(r, g, b, a);
                buffer.addVertex(pose, max, max, 1.0f + o).setColor(r, g, b, a);
                return true;
            }
            case WEST -> {
                buffer.addVertex(pose, -o, min, max).setColor(r, g, b, a);
                buffer.addVertex(pose, -o, min, min).setColor(r, g, b, a);
                buffer.addVertex(pose, -o, max, min).setColor(r, g, b, a);
                buffer.addVertex(pose, -o, max, max).setColor(r, g, b, a);
                return true;
            }
            case EAST -> {
                buffer.addVertex(pose, 1.0f + o, min, min).setColor(r, g, b, a);
                buffer.addVertex(pose, 1.0f + o, min, max).setColor(r, g, b, a);
                buffer.addVertex(pose, 1.0f + o, max, max).setColor(r, g, b, a);
                buffer.addVertex(pose, 1.0f + o, max, min).setColor(r, g, b, a);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static int alphaInt(float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
    }

    private static float getSurfaceTopHeight(ClientLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            shape = state.getCollisionShape(level, pos);
        }
        if (shape.isEmpty()) {
            return 1.0f;
        }
        return (float) shape.max(Direction.Axis.Y);
    }

    private record SurfaceKey(BlockPos pos, Direction face) {
    }
}
