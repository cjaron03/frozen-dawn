package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class StormGlassRenderPass {

    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final int MAX_FACES = 192;
    private static final int OUTDOOR_PROBE_DISTANCE = 7;
    private static final int OUTDOOR_PROBE_HEIGHT = 5;
    private static final int MAX_WINDOW_PARTICLES_PER_TICK = 72;

    private static ClientLevel cachedLevel;
    private static long nextScanGameTime = Long.MIN_VALUE;
    private static List<StormGlassFace> cachedFaces = List.of();

    private StormGlassRenderPass() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || !shouldRender(mc) || mc.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }

        refreshCacheIfNeeded(mc);
        if (cachedFaces.isEmpty()) {
            return;
        }

        spawnWindowBlizzardParticles(mc);
    }

    private static boolean shouldRender(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
            return false;
        }

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        if (!hasWindowBlizzardParticles(phase, progress)) {
            return false;
        }

        return !ClientStormVisibility.isStormExposed(mc);
    }

    private static boolean hasWindowBlizzardParticles(int phase, float progress) {
        if (phase == 5) {
            return true;
        }
        if (phase < 6) {
            return false;
        }

        PhaseManager.Phase6Stage stage = PhaseManager.getPhase6Stage(phase, progress);
        return stage == PhaseManager.Phase6Stage.EARLY || stage == PhaseManager.Phase6Stage.MID;
    }

    private static float getStormStrength() {
        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        if (phase == 5) {
            return 0.78F;
        }
        return switch (PhaseManager.getPhase6Stage(phase, progress)) {
            case EARLY -> 0.9F;
            case MID -> Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), 0.9F, 0.0F);
            case VACUUM, INACTIVE -> 0.0F;
        };
    }

    private static void refreshCacheIfNeeded(Minecraft mc) {
        ClientLevel level = mc.level;
        long gameTime = level.getGameTime();
        if (cachedLevel == level && gameTime < nextScanGameTime) {
            return;
        }

        cachedLevel = level;
        nextScanGameTime = gameTime + SCAN_INTERVAL_TICKS;
        cachedFaces = scanFaces(mc, level);
    }

    private static int spawnWindowBlizzardParticles(Minecraft mc) {
        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        if (!hasWindowBlizzardParticles(phase, progress)) {
            return 0;
        }

        ClientLevel level = mc.level;
        RandomSource random = level.random;
        long gameTime = level.getGameTime();
        int particleBudget = Math.min(MAX_WINDOW_PARTICLES_PER_TICK,
                Math.round(getBlizzardParticleCount(phase, progress) * getStormStrength() * 0.92F));
        if (particleBudget <= 0) {
            return 0;
        }

        int spawned = 0;
        for (int i = 0; i < particleBudget; i++) {
            StormGlassFace face = cachedFaces.get(random.nextInt(cachedFaces.size()));
            Vec3 velocity = getGlassImpactParticleVelocity(phase, progress, gameTime, face.direction(), random);
            Vec3 spawnPos = randomParticlePosition(face, velocity, gameTime, random);
            level.addParticle(ParticleTypes.SNOWFLAKE, spawnPos.x, spawnPos.y, spawnPos.z, velocity.x, velocity.y, velocity.z);
            spawned++;
        }
        return spawned;
    }

    private static Vec3 randomParticlePosition(StormGlassFace face, Vec3 velocity, long gameTime, RandomSource random) {
        BlockPos pos = face.pos();
        Direction direction = face.direction();
        Vec3 horizontalAxis = faceHorizontalAxis(direction);
        double horizontalSpeed = velocity.dot(horizontalAxis);
        double travelSign = horizontalSpeed >= 0.0D ? 1.0D : -1.0D;
        double local = 0.08D + random.nextDouble() * 0.84D;
        local = Mth.clamp(local - travelSign * random.nextDouble() * 0.22D + random.nextGaussian() * 0.05D, 0.04D, 0.96D);
        double y = randomFaceHeight(pos, gameTime, random);
        double outward = 1.45D + random.nextDouble() * 3.25D;

        return switch (direction) {
            case NORTH -> new Vec3(pos.getX() + local, y, pos.getZ() - outward);
            case SOUTH -> new Vec3(pos.getX() + 1.0D - local, y, pos.getZ() + 1.0D + outward);
            case WEST -> new Vec3(pos.getX() - outward, y, pos.getZ() + 1.0D - local);
            case EAST -> new Vec3(pos.getX() + 1.0D + outward, y, pos.getZ() + local);
            default -> Vec3.atCenterOf(pos);
        };
    }

    private static double randomFaceHeight(BlockPos pos, long gameTime, RandomSource random) {
        double base = pos.getY() - 0.18D + random.nextDouble() * 1.36D;
        double gustLift = Mth.sin(gameTime * 0.08F + random.nextFloat() * Mth.TWO_PI) * 0.18D;
        return Mth.clamp(base + gustLift, pos.getY() - 0.25D, pos.getY() + 1.25D);
    }

    private static Vec3 faceHorizontalAxis(Direction direction) {
        if (direction.getAxis() == Direction.Axis.Z) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    private static Vec3 getGlassImpactParticleVelocity(int phase, float progress, long gameTime, Direction exteriorDirection, RandomSource random) {
        Vec3 normal = new Vec3(exteriorDirection.getStepX(), 0.0D, exteriorDirection.getStepZ());
        Vec3 wind = new Vec3(
                BlizzardWindHelper.getWindX(phase, progress, gameTime),
                0.0D,
                BlizzardWindHelper.getWindZ(phase, progress, gameTime)
        );
        double normalSpeed = wind.dot(normal);
        Vec3 horizontalAxis = faceHorizontalAxis(exteriorDirection);
        double tangentSpeed = wind.subtract(normal.scale(normalSpeed)).dot(horizontalAxis);
        double travelSign = directionalTravelSign(tangentSpeed, exteriorDirection, gameTime);
        double windStrength = BlizzardWindHelper.getNormalizedSurfaceWindStrength(phase, progress, gameTime);
        double gust = 0.82D + 0.34D * Mth.sin(gameTime * 0.055F + exteriorDirection.ordinal() * 1.73F);
        double lateralSpeed = (0.42D + random.nextDouble() * 0.34D) * (0.82D + windStrength * 0.62D) * gust;
        Vec3 crosswind = horizontalAxis.scale(travelSign * lateralSpeed);

        double pulse = random.nextFloat() < 0.32F ? 0.08D + random.nextDouble() * 0.12D : 0.0D;
        double impactSpeed = 0.035D + random.nextDouble() * 0.06D + pulse;
        if (phase >= 6 && PhaseManager.getPhase6Stage(phase, progress) == PhaseManager.Phase6Stage.MID) {
            impactSpeed = Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), impactSpeed, 0.03D);
        }

        double vertical = random.nextFloat() < 0.34F
                ? 0.03D + random.nextDouble() * 0.12D
                : -0.08D - random.nextDouble() * 0.14D;
        return crosswind.add(normal.scale(-impactSpeed)).add(0.0D, vertical, 0.0D);
    }

    private static double directionalTravelSign(double tangentSpeed, Direction exteriorDirection, long gameTime) {
        if (Math.abs(tangentSpeed) > 0.025D) {
            return tangentSpeed > 0.0D ? 1.0D : -1.0D;
        }

        float windAngle = BlizzardWindHelper.getWindAngleRad(gameTime);
        double fallback = exteriorDirection.getAxis() == Direction.Axis.Z ? Mth.sin(windAngle) : Mth.cos(windAngle);
        return fallback >= 0.0D ? 1.0D : -1.0D;
    }

    private static int getBlizzardParticleCount(int phase, float progress) {
        if (phase == 5) {
            return 76;
        }
        if (phase < 6) {
            return 0;
        }

        return switch (PhaseManager.getPhase6Stage(phase, progress)) {
            case EARLY -> 86;
            case MID -> (int) Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), 86.0F, 0.0F);
            case VACUUM, INACTIVE -> 0;
        };
    }

    private static List<StormGlassFace> scanFaces(Minecraft mc, ClientLevel level) {
        BlockPos center = mc.player.blockPosition();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        List<StormGlassFace> faces = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -6; y <= 8; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(cursor);
                    if (!isSupportedGlass(state)) {
                        continue;
                    }

                    BlockPos pos = cursor.immutable();
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        if (!isStormFacing(level, pos, direction)) {
                            continue;
                        }
                        if (!isCameraOnInteriorSide(cameraPos, pos, direction)) {
                            continue;
                        }

                        faces.add(new StormGlassFace(pos, direction));
                        if (faces.size() >= MAX_FACES) {
                            return reduceFacesToExteriorColumns(faces);
                        }
                    }
                }
            }
        }

        return reduceFacesToExteriorColumns(faces);
    }

    private static List<StormGlassFace> reduceFacesToExteriorColumns(List<StormGlassFace> faces) {
        if (faces.size() <= 1) {
            return faces;
        }

        List<StormGlassFace> reduced = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            faces.stream()
                    .filter(face -> face.direction() == direction)
                    .collect(java.util.stream.Collectors.groupingBy(StormGlassRenderPass::faceColumnKey))
                    .values()
                    .forEach(column -> column.stream()
                            .min(Comparator.comparingDouble(StormGlassRenderPass::faceCameraDistanceSqr))
                            .ifPresent(reduced::add));
        }
        return reduced;
    }

    private static long faceColumnKey(StormGlassFace face) {
        BlockPos pos = face.pos();
        if (face.direction().getAxis() == Direction.Axis.Z) {
            return BlockPos.asLong(pos.getX(), pos.getY(), 0);
        }
        return BlockPos.asLong(0, pos.getY(), pos.getZ());
    }

    private static double faceCameraDistanceSqr(StormGlassFace face) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0.0D;
        }
        return Vec3.atCenterOf(face.pos()).distanceToSqr(mc.player.position());
    }

    private static boolean isSupportedGlass(BlockState state) {
        return state.is(ModBlocks.INSULATED_GLASS.get())
                || state.is(Blocks.GLASS)
                || state.is(Blocks.GLASS_PANE)
                || state.is(Blocks.TINTED_GLASS)
                || state.is(BlockTags.IMPERMEABLE)
                || state.is(Tags.Blocks.GLASS_BLOCKS)
                || state.is(Tags.Blocks.GLASS_BLOCKS_COLORLESS)
                || state.is(Tags.Blocks.GLASS_BLOCKS_TINTED)
                || state.is(Tags.Blocks.GLASS_PANES)
                || state.is(Tags.Blocks.GLASS_PANES_COLORLESS);
    }

    private static boolean isStormFacing(ClientLevel level, BlockPos glassPos, Direction exteriorDirection) {
        BlockPos firstExteriorPos = glassPos.relative(exteriorDirection);
        BlockState firstExteriorState = level.getBlockState(firstExteriorPos);
        if (isSupportedGlass(firstExteriorState)) {
            return false;
        }
        if (isDirectGlassBlocker(level, firstExteriorPos, exteriorDirection.getOpposite())) {
            return false;
        }
        if (isBoxedAirGap(level, firstExteriorPos, exteriorDirection)) {
            return false;
        }

        return hasNearbyOutdoorStormAccess(level, firstExteriorPos, exteriorDirection);
    }

    private static boolean hasNearbyOutdoorStormAccess(ClientLevel level, BlockPos firstExteriorPos, Direction exteriorDirection) {
        Direction clockwise = exteriorDirection.getClockWise();
        Direction counterClockwise = exteriorDirection.getCounterClockWise();
        for (int outward = 0; outward <= OUTDOOR_PROBE_DISTANCE; outward++) {
            BlockPos outwardPos = firstExteriorPos.relative(exteriorDirection, outward);
            if (outward > 0 && isDirectGlassBlocker(level, outwardPos, exteriorDirection.getOpposite())) {
                return false;
            }

            if (hasVerticalSkyAccess(level, outwardPos)) {
                return true;
            }
            if (outward <= 3 && (hasVerticalSkyAccess(level, outwardPos.relative(clockwise))
                    || hasVerticalSkyAccess(level, outwardPos.relative(counterClockwise)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoxedAirGap(ClientLevel level, BlockPos firstExteriorPos, Direction exteriorDirection) {
        BlockState firstExteriorState = level.getBlockState(firstExteriorPos);
        if (!firstExteriorState.isAir() && !firstExteriorState.getCollisionShape(level, firstExteriorPos).isEmpty()) {
            return false;
        }

        Direction clockwise = exteriorDirection.getClockWise();
        Direction counterClockwise = exteriorDirection.getCounterClockWise();
        boolean outwardBlocked = isDirectGlassBlocker(level, firstExteriorPos.relative(exteriorDirection), exteriorDirection.getOpposite());
        boolean aboveBlocked = isDirectGlassBlocker(level, firstExteriorPos.above(), Direction.DOWN);
        boolean sideABlocked = isDirectGlassBlocker(level, firstExteriorPos.relative(clockwise), clockwise.getOpposite());
        boolean sideBBlocked = isDirectGlassBlocker(level, firstExteriorPos.relative(counterClockwise), counterClockwise.getOpposite());
        return outwardBlocked && aboveBlocked && sideABlocked && sideBBlocked;
    }

    private static boolean hasVerticalSkyAccess(ClientLevel level, BlockPos pos) {
        for (int y = 0; y <= OUTDOOR_PROBE_HEIGHT; y++) {
            BlockPos probe = pos.above(y);
            if (level.canSeeSky(probe)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDirectGlassBlocker(ClientLevel level, BlockPos pos, Direction faceToGlass) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || isSupportedGlass(state)) {
            return false;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return false;
        }
        return state.isFaceSturdy(level, pos, faceToGlass) || state.canOcclude();
    }

    private static boolean isCameraOnInteriorSide(Vec3 cameraPos, BlockPos glassPos, Direction exteriorDirection) {
        Vec3 glassCenter = Vec3.atCenterOf(glassPos);
        Vec3 cameraFromGlass = cameraPos.subtract(glassCenter);
        double exteriorDot = cameraFromGlass.x * exteriorDirection.getStepX() + cameraFromGlass.z * exteriorDirection.getStepZ();
        return exteriorDot < 0.35D;
    }

    private static void clear() {
        cachedLevel = null;
        nextScanGameTime = Long.MIN_VALUE;
        cachedFaces = List.of();
    }

    private record StormGlassFace(BlockPos pos, Direction direction) {
    }

}
