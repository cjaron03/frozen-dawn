package com.frozendawn.entity;

import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.aggregate.AggregatePressureHandler;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.FrostwritheColonyManager;
import com.frozendawn.world.MiteAwayRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A temporary Frostmite superorganism whose body is its surviving colony. */
public final class FrostwritheEntity extends Monster {
    private static final int SURFACE_ATTACK_TICKS = 120;
    private static final int POST_HIT_COMMIT_TICKS = 80;
    private static final double SURFACE_ESCAPE_DISTANCE = 14.0D;
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(FrostwritheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICKS =
            SynchedEntityData.defineId(FrostwritheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_COHESION =
            SynchedEntityData.defineId(FrostwritheEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BIOMASS =
            SynchedEntityData.defineId(FrostwritheEntity.class, EntityDataSerializers.INT);

    private @Nullable UUID colonyId;
    private int shellCooldown;
    private int bridgeCooldown;
    private int overrunCooldown;
    private int burrowCooldown;
    private int surfaceAttackTicks;
    private int burrowRouteRetryTicks;
    private int pressureHits;
    private int pressureWindow;
    private int shellRecoveryThisCycle;
    private boolean attackBurrow;
    private Vec3 patrolHeading = Vec3.ZERO;
    private final ArrayDeque<BlockPos> patrolMemory = new ArrayDeque<>();
    private @Nullable Vec3 actionDestination;
    private @Nullable BlockPos disassemblyRally;
    private @Nullable BlockPos burrowSurface;
    private final FrostwritheBurrowController burrowController =
            new FrostwritheBurrowController();

    public FrostwritheEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 52.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, FrostwritheState.CRAWLER.ordinal());
        builder.define(DATA_STATE_TICKS, 0);
        builder.define(DATA_COHESION, 100.0F);
        builder.define(DATA_BIOMASS, FrostwrithePolicy.MAX_BIOMASS);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.12D, false) {
            @Override
            public boolean canUse() {
                return activityState() == FrostwritheState.CRAWLER && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return activityState() == FrostwritheState.CRAWLER
                        && super.canContinueToUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true));
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target instanceof Player player && !isCombatPlayer(player)) {
            target = null;
        }
        super.setTarget(target);
    }

    private void clearInvalidPlayerTarget() {
        if (getTarget() instanceof Player player && !isCombatPlayer(player)) {
            setTarget(null);
        }
    }

    @Nullable
    private Player nearestCombatPlayer(ServerLevel level, double distance) {
        return level.getNearestPlayer(getX(), getY(), getZ(), distance,
                candidate -> candidate instanceof Player player
                        && isCombatPlayer(player));
    }

    private static boolean isCombatPlayer(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    public FrostwritheState activityState() {
        return FrostwritheState.byOrdinal(entityData.get(DATA_STATE));
    }

    public int stateTicks() {
        return entityData.get(DATA_STATE_TICKS);
    }

    public float cohesion() {
        return entityData.get(DATA_COHESION);
    }

    public void debugSetCohesion(float value) {
        setCohesion(value);
        if (cohesion() <= 0.0F) forceDisassemble();
    }

    public int cohesionBand() {
        return FrostwrithePolicy.cohesionBand(cohesion());
    }

    public int visibleBodies() {
        return FrostwrithePolicy.visibleBodies(cohesion());
    }

    public int biomass() {
        return entityData.get(DATA_BIOMASS);
    }

    @Nullable
    public UUID colonyId() {
        return colonyId;
    }

    public void initializeColony(UUID id, int biomass, FrostwritheState state) {
        // Once Frostmites commit to a colony, it must resolve through breakup
        // rather than silently disappearing through vanilla monster despawn.
        setPersistenceRequired();
        colonyId = id;
        entityData.set(DATA_BIOMASS, Mth.clamp(biomass, 1,
                FrostwrithePolicy.MAX_BIOMASS));
        setCohesion(Mth.clamp(biomass, 1, FrostwrithePolicy.MAX_BIOMASS));
        setHealth(FrostwrithePolicy.reformedHealth(biomass, getMaxHealth()));
        setActivityState(state);
    }

    public void setActivityState(FrostwritheState state) {
        entityData.set(DATA_STATE, state.ordinal());
        entityData.set(DATA_STATE_TICKS, 0);
        actionDestination = null;
        boolean underground = state == FrostwritheState.BURROWING
                || state == FrostwritheState.ERUPTING;
        if (underground) fallDistance = 0.0F;
        setNoGravity(underground);
        noPhysics = underground;
        setInvisible(underground);
        if (state != FrostwritheState.CRAWLER) getNavigation().stop();
        if (state == FrostwritheState.SHELL) shellRecoveryThisCycle = 0;
    }

    public void forceDisassemble() {
        forceDisassembleAt(blockPosition());
    }

    private void forceDisassembleAt(BlockPos rally) {
        if (activityState() != FrostwritheState.DISASSEMBLING
                && activityState() != FrostwritheState.DEAD) {
            disassemblyRally = rally.immutable();
            setHealth(Math.max(1.0F, getHealth()));
            setActivityState(FrostwritheState.DISASSEMBLING);
            playSound(ModSounds.FROSTWRITHE_DISASSEMBLE.get(), 1.45F, 0.88F);
        }
    }

    public void forceShell() {
        if (activityState() == FrostwritheState.CRAWLER && cohesion() > 20.0F) {
            setActivityState(FrostwritheState.SHELL);
            playSound(ModSounds.FROSTWRITHE_SHELL.get(), 1.15F, 0.9F);
        }
    }

    public void forceClimb() {
        LivingEntity target = getTarget();
        if (target == null && level() instanceof ServerLevel server) {
            target = nearestCombatPlayer(server, 16.0D);
            setTarget(target);
        }
        if (target != null) beginClimb(target.position());
    }

    public void forceBridge() {
        LivingEntity target = getTarget();
        if (target == null && level() instanceof ServerLevel server) {
            target = nearestCombatPlayer(server, 16.0D);
            setTarget(target);
        }
        if (target != null) beginBridge(target.position());
    }

    public void forceOverrun() {
        LivingEntity target = getTarget();
        if (target == null && level() instanceof ServerLevel server) {
            target = nearestCombatPlayer(server, 5.0D);
            setTarget(target);
        }
        if (target != null) beginOverrun(target);
    }

    public boolean forceBurrow() {
        if (!(level() instanceof ServerLevel server)) return false;
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            target = nearestCombatPlayer(server, 24.0D);
            setTarget(target);
        }
        return target != null && beginBurrow(server, target);
    }

    public boolean forceMimicNearby() {
        return level() instanceof ServerLevel server && imitateNearbyMob(server, true);
    }

    @Override
    protected void customServerAiStep() {
        tickCooldowns();
        entityData.set(DATA_STATE_TICKS, stateTicks() + 1);
        if (!(level() instanceof ServerLevel server)) {
            super.customServerAiStep();
            return;
        }
        clearInvalidPlayerTarget();
        tickMimicry(server);
        switch (activityState()) {
            case ASSEMBLING -> tickAssembling(server);
            case CRAWLER -> {
                super.customServerAiStep();
                tickCrawler(server);
            }
            case BURROWING -> tickBurrowing(server);
            case ERUPTING -> tickErupting(server);
            case SHELL -> tickShell(server);
            case CLIMBER -> tickClimber(server);
            case BRIDGING -> tickBridge(server);
            case OVERRUN -> tickOverrun(server);
            case DISASSEMBLING -> tickDisassembling(server);
            case LOOSE, REGROUPING, DEAD -> {
            }
        }
    }

    private void tickMimicry(ServerLevel level) {
        int interval = activityState() == FrostwritheState.BURROWING ? 55 : 400;
        if (isSilent() || !canMimicInCurrentState() || random.nextInt(interval) != 0) return;
        imitateNearbyMob(level, false);
    }

    private boolean imitateNearbyMob(ServerLevel level, boolean forced) {
        if ((!forced && random.nextInt(2) != 0) || isSilent()
                || !canMimicInCurrentState()) {
            return false;
        }
        List<Mob> candidates = level.getEntitiesOfClass(Mob.class,
                getBoundingBox().inflate(FrostwrithePolicy.MIMIC_RADIUS),
                mob -> mob != this && mob.isAlive() && !mob.isSilent()
                        && imitatedSound(mob) != null);
        if (candidates.isEmpty()) return false;

        Mob source = candidates.get(random.nextInt(candidates.size()));
        SoundEvent imitation = imitatedSound(source);
        if (imitation == null) return false;
        level.playSound(null, getX(), getY(), getZ(), imitation,
                getSoundSource(), 0.78F,
                FrostwrithePolicy.mimicPitch(
                        random.nextFloat(), random.nextFloat()));
        return true;
    }

    private boolean canMimicInCurrentState() {
        return switch (activityState()) {
            case CRAWLER, BURROWING, ERUPTING, SHELL, CLIMBER,
                    BRIDGING, OVERRUN -> true;
            default -> false;
        };
    }

    @Nullable
    private SoundEvent imitatedSound(Mob source) {
        if (source instanceof ArchitectEntity architect
                && !architect.isMasterArchitectVisual()) {
            return ModSounds.FROSTWRITHE_IMITATE_ARCHITECT.get();
        }
        if (source instanceof UndoneArchitectEntity) {
            return ModSounds.FROSTWRITHE_IMITATE_UNDONE_ARCHITECT.get();
        }
        if (source instanceof UndoneEntity undone) {
            return undone.isBloombound()
                    ? ModSounds.FROSTWRITHE_IMITATE_BLOOMBOUND.get()
                    : ModSounds.FROSTWRITHE_IMITATE_UNDONE.get();
        }
        if (source instanceof RimeboundEntity) {
            return ModSounds.FROSTWRITHE_IMITATE_RIMEBOUND.get();
        }
        if (source instanceof ResonantEntity) {
            return ModSounds.FROSTWRITHE_IMITATE_RESONANT.get();
        }
        if (source instanceof RemnantEntity) {
            return ModSounds.FROSTWRITHE_IMITATE_REMNANT.get();
        }
        return null;
    }

    private void tickCooldowns() {
        shellCooldown = Math.max(0, shellCooldown - 1);
        bridgeCooldown = Math.max(0, bridgeCooldown - 1);
        overrunCooldown = Math.max(0, overrunCooldown - 1);
        burrowCooldown = Math.max(0, burrowCooldown - 1);
        surfaceAttackTicks = Math.max(0, surfaceAttackTicks - 1);
        if (pressureWindow > 0 && --pressureWindow == 0) pressureHits = 0;
    }

    private void tickAssembling(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (stateTicks() % 3 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 0.25D, getZ(), 8,
                    1.25D, 0.3D, 1.25D, 0.06D);
        }
        if (stateTicks() >= FrostwrithePolicy.ASSEMBLY_TICKS) {
            LivingEntity target = nearestCombatPlayer(level, 32.0D);
            if (target != null) setTarget(target);
            setActivityState(FrostwritheState.CRAWLER);
            surfaceAttackTicks = 0;
            playSound(ModSounds.FROSTWRITHE_ASSEMBLE.get(), 1.35F, 0.94F);
        }
    }

    private void tickCrawler(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            target = nearestCombatPlayer(level, 48.0D);
            setTarget(target);
        }
        if (target == null) {
            if (surfaceAttackTicks <= 0) submergeInPlace(level);
            return;
        }
        double distance = distanceTo(target);
        // Once it erupts, it commits to the chase. Burrowing is travel between
        // attack windows, not an animation reset after every body check.
        if (surfaceAttackTicks <= 0
                || (stateTicks() > 30 && distance > SURFACE_ESCAPE_DISTANCE)) {
            beginBurrow(level, target);
            return;
        }
        if (overrunCooldown <= 0 && distance < 1.65D && cohesion() > 20.0F) {
            beginOverrun(target);
        }
    }

    private boolean beginBurrow(ServerLevel level, LivingEntity target) {
        if (activityState() != FrostwritheState.CRAWLER) return false;
        attackBurrow = true;
        boolean routed = planBurrowRoute(level, target);
        enterBurrow(level, routed);
        return true;
    }

    private void submergeInPlace(ServerLevel level) {
        if (activityState() != FrostwritheState.CRAWLER) return;
        attackBurrow = false;
        burrowController.clear();
        enterBurrow(level, false);
    }

    private void enterBurrow(ServerLevel level, boolean routed) {
        burrowSurface = FrostwritheBurrowController.surfaceAt(
                level, blockPosition());
        if (burrowSurface == null) burrowSurface = blockPosition();
        setActivityState(FrostwritheState.BURROWING);
        double buriedY = FrostwritheBurrowController.surfaceHeight(
                level, burrowSurface) - 0.55D;
        setPos(getX(), buriedY, getZ());
        burrowRouteRetryTicks = routed ? 0 : 10;
        imitateNearbyMob(level, true);
        playSound(ModSounds.FROSTWRITHE_MOVEMENT.get(), 1.25F, 0.68F);
    }

    private boolean planBurrowRoute(ServerLevel level, LivingEntity target) {
        Vec3 predicted = target.position().add(
                target.getDeltaMovement().multiply(6.0D, 0.0D, 6.0D));
        Vec3 towardTarget = predicted.subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        if (towardTarget.lengthSqr() < 9.0D && towardTarget.lengthSqr() > 0.01D) {
            // After a body check, continue beneath the player and surface on
            // the far side instead of rejecting a route whose goal is the
            // block the colony already occupies.
            predicted = predicted.add(towardTarget.normalize().scale(2.2D));
        }
        Vec3 away = position().subtract(predicted).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() > 0.01D) {
            predicted = predicted.add(away.normalize().scale(1.4D));
        }
        int[][] offsets = {
                {0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 2}, {-2, 2}, {2, -2}, {-2, -2}
        };
        BlockPos start = burrowSurface == null ? blockPosition() : burrowSurface;
        for (int[] offset : offsets) {
            BlockPos desired = BlockPos.containing(predicted).offset(
                    offset[0], 0, offset[1]);
            if (burrowController.buildRoute(level, start, desired)) return true;
        }
        return false;
    }

    private boolean planPatrolRoute(ServerLevel level) {
        BlockPos start = FrostwritheBurrowController.surfaceAt(level,
                burrowSurface == null ? blockPosition() : burrowSurface);
        if (start == null) return false;

        Vec3 heading = patrolHeading.multiply(1.0D, 0.0D, 1.0D);
        if (heading.lengthSqr() < 0.01D) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            heading = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        } else {
            heading = heading.normalize();
        }

        List<FrostwritheEntity> neighbors = level.getEntitiesOfClass(
                FrostwritheEntity.class, getBoundingBox().inflate(32.0D),
                other -> other != this && other.isAlive());
        List<PatrolCandidate> candidates = new ArrayList<>();
        double baseAngle = Math.atan2(heading.z, heading.x)
                + (random.nextDouble() - 0.5D) * 0.45D;
        double[] turns = {0.0D, 0.55D, -0.55D, 1.05D, -1.05D, 1.75D, -1.75D};
        for (double turn : turns) {
            double angle = baseAngle + turn;
            Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            int distance = random.nextIntBetweenInclusive(
                    FrostwrithePolicy.PATROL_MIN_DISTANCE,
                    FrostwrithePolicy.PATROL_MAX_DISTANCE);
            BlockPos desired = start.offset(
                    Mth.floor(direction.x * distance), 0,
                    Mth.floor(direction.z * distance));
            BlockPos surface = FrostwritheBurrowController.surfaceAt(level, desired);
            if (surface == null) continue;

            double recentDistance = patrolMemory.stream()
                    .mapToDouble(memory -> Math.sqrt(memory.distSqr(surface)))
                    .min().orElse(24.0D);
            if (recentDistance < 3.5D) continue;
            double colonyDistance = neighbors.stream()
                    .mapToDouble(other -> Math.sqrt(
                            other.blockPosition().distSqr(surface)))
                    .min().orElse(32.0D);
            double score = FrostwrithePolicy.patrolScore(
                    heading.dot(direction), start.distManhattan(surface),
                    recentDistance, colonyDistance, random.nextDouble());
            candidates.add(new PatrolCandidate(surface, direction, score));
        }
        candidates.sort(Comparator.comparingDouble(PatrolCandidate::score).reversed());
        for (PatrolCandidate candidate : candidates) {
            if (burrowController.buildRoute(level, start, candidate.position())) {
                patrolHeading = candidate.direction();
                attackBurrow = false;
                return true;
            }
        }
        return false;
    }

    private void tickBurrowing(ServerLevel level) {
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
        fallDistance = 0.0F;
        getNavigation().stop();
        LivingEntity activeTarget = getTarget();
        if (attackBurrow && (activeTarget == null || !activeTarget.isAlive())) {
            attackBurrow = false;
            burrowController.clear();
        }
        if (!attackBurrow) {
            LivingEntity found = nearestCombatPlayer(level, 32.0D);
            setTarget(found);
            if (found != null) {
                attackBurrow = true;
                planBurrowRoute(level, found);
            }
        }
        BlockPos waypoint = burrowController.currentWaypoint();
        if (waypoint == null || !level.isLoaded(waypoint)) {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive()) {
                target = nearestCombatPlayer(level, 32.0D);
                setTarget(target);
            }
            if (--burrowRouteRetryTicks <= 0) {
                burrowRouteRetryTicks = attackBurrow ? 10
                        : FrostwrithePolicy.PATROL_REPLAN_TICKS;
                if (target != null) {
                    attackBurrow = true;
                    planBurrowRoute(level, target);
                } else {
                    attackBurrow = false;
                    planPatrolRoute(level);
                }
            }
            BlockPos surface = burrowSurface == null ? blockPosition() : burrowSurface;
            if (tickCount % 3 == 0) spawnBurrowWake(level, surface);
            return;
        }

        burrowSurface = waypoint.immutable();
        double targetX = waypoint.getX() + 0.5D;
        double targetZ = waypoint.getZ() + 0.5D;
        double dx = targetX - getX();
        double dz = targetZ - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double step = Math.min(0.31D, horizontal);
        double nextX = horizontal > 1.0E-4D
                ? getX() + dx / horizontal * step : getX();
        double nextZ = horizontal > 1.0E-4D
                ? getZ() + dz / horizontal * step : getZ();
        double nextY = FrostwritheBurrowController.surfaceHeight(level, waypoint) - 0.55D;
        setDeltaMovement(Vec3.ZERO);
        setPos(nextX, nextY, nextZ);
        if (horizontal > 1.0E-4D) {
            setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
            yBodyRot = getYRot();
        }
        burrowController.advanceIfReached(getX(), getZ());
        if (tickCount % 2 == 0) spawnBurrowWake(level, waypoint);
        if (burrowController.complete()) {
            if (attackBurrow) {
                lockEruption(level, waypoint);
            } else {
                rememberPatrolPosition(waypoint);
                burrowSurface = waypoint.immutable();
                burrowController.clear();
                if (!planPatrolRoute(level)) {
                    burrowRouteRetryTicks = FrostwrithePolicy.PATROL_REPLAN_TICKS;
                }
            }
        }
    }

    private void rememberPatrolPosition(BlockPos position) {
        patrolMemory.addLast(position.immutable());
        while (patrolMemory.size() > FrostwrithePolicy.PATROL_MEMORY_SIZE) {
            patrolMemory.removeFirst();
        }
    }

    private record PatrolCandidate(BlockPos position, Vec3 direction, double score) {
    }

    private void spawnBurrowWake(ServerLevel level, BlockPos surface) {
        BlockPos particlePos = level.getBlockState(surface)
                .is(ModBlocks.FROZEN_ATMOSPHERE.get()) ? surface : surface.below();
        var ground = level.getBlockState(particlePos);
        double y = FrostwritheBurrowController.surfaceHeight(level, surface) + 0.06D;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                getX(), y, getZ(), 5,
                0.34D, 0.08D, 0.34D, 0.055D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState()),
                getX(), y + 0.04D, getZ(), 3,
                0.28D, 0.07D, 0.28D, 0.045D);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                getX(), y + 0.08D, getZ(), 3,
                0.32D, 0.06D, 0.32D, 0.025D);
    }

    private void lockEruption(ServerLevel level, BlockPos surface) {
        burrowSurface = surface.immutable();
        setActivityState(FrostwritheState.ERUPTING);
        double y = FrostwritheBurrowController.surfaceHeight(level, surface) - 0.55D;
        setPos(surface.getX() + 0.5D, y, surface.getZ() + 0.5D);
        setDeltaMovement(Vec3.ZERO);
    }

    private void tickErupting(ServerLevel level) {
        BlockPos surface = burrowSurface == null ? blockPosition() : burrowSurface;
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
        if (stateTicks() % 2 == 0) spawnBurrowWake(level, surface);
        if (stateTicks() < FrostwrithePolicy.ERUPTION_TICKS) return;

        double y = FrostwritheBurrowController.surfaceHeight(level, surface);
        setPos(surface.getX() + 0.5D, y, surface.getZ() + 0.5D);
        fallDistance = 0.0F;
        setActivityState(FrostwritheState.CRAWLER);
        surfaceAttackTicks = SURFACE_ATTACK_TICKS;
        burrowCooldown = 260;
        burrowController.clear();
        playSound(ModSounds.FROSTWRITHE_ASSEMBLE.get(), 1.55F, 0.76F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        ModBlocks.FROZEN_ATMOSPHERE.get().defaultBlockState()),
                getX(), getY() + 0.2D, getZ(), 28,
                0.85D, 0.35D, 0.85D, 0.16D);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                getX(), getY() + 0.25D, getZ(), 30,
                0.9D, 0.4D, 0.9D, 0.13D);
    }

    private void tickShell(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (stateTicks() % 20 == 0 && shellRecoveryThisCycle < 10
                && !MiteAwayRegistry.isProtected(level, position())) {
            int consumed = FrostwritheColonyManager.consumeLooseBiomass(
                    level, this, Math.min(2, 10 - shellRecoveryThisCycle));
            if (consumed > 0) {
                shellRecoveryThisCycle += consumed;
                entityData.set(DATA_BIOMASS, Math.min(100, biomass() + consumed));
                setCohesion(Math.min(100.0F, cohesion() + consumed));
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        getX(), getY() + 0.25D, getZ(), 8,
                        0.7D, 0.2D, 0.7D, 0.05D);
            }
        }
        if (stateTicks() >= FrostwrithePolicy.SHELL_TICKS) {
            shellCooldown = 240;
            surfaceAttackTicks = 0;
            setActivityState(FrostwritheState.CRAWLER);
        }
    }

    private void beginClimb(Vec3 destination) {
        actionDestination = destination;
        setActivityState(FrostwritheState.CLIMBER);
        actionDestination = destination;
        setNoGravity(true);
        playSound(ModSounds.FROSTWRITHE_CLIMB.get(), 1.05F, 1.0F);
    }

    private void tickClimber(ServerLevel level) {
        if (actionDestination == null || stateTicks() >= FrostwrithePolicy.CLIMB_TICKS) {
            setActivityState(FrostwritheState.CRAWLER);
            return;
        }
        Vec3 delta = actionDestination.subtract(position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        Vec3 motion = horizontal.lengthSqr() > 0.01D
                ? horizontal.normalize().scale(0.075D) : Vec3.ZERO;
        setDeltaMovement(motion.x, Mth.clamp(delta.y * 0.09D, -0.08D, 0.16D), motion.z);
        if (stateTicks() % 3 == 0) {
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    getX(), getY() + 0.2D, getZ(), 3,
                    0.45D, 0.15D, 0.45D, 0.04D);
        }
        if (delta.lengthSqr() < 1.5D) setActivityState(FrostwritheState.CRAWLER);
    }

    private void beginBridge(Vec3 destination) {
        actionDestination = destination;
        setActivityState(FrostwritheState.BRIDGING);
        actionDestination = destination;
        setNoGravity(true);
        setCohesion(cohesion() - 8.0F);
        playSound(ModSounds.FROSTWRITHE_BRIDGE.get(), 1.15F, 0.96F);
    }

    private void tickBridge(ServerLevel level) {
        if (actionDestination == null || stateTicks() >= FrostwrithePolicy.BRIDGE_TICKS) {
            bridgeCooldown = 400;
            setActivityState(FrostwritheState.CRAWLER);
            return;
        }
        Vec3 delta = actionDestination.subtract(position());
        Vec3 direction = delta.lengthSqr() > 0.01D ? delta.normalize() : Vec3.ZERO;
        setDeltaMovement(direction.scale(0.16D));
        if (stateTicks() % 2 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 0.2D, getZ(), 4,
                    0.7D, 0.12D, 0.7D, 0.03D);
        }
        if (delta.lengthSqr() < 1.2D) {
            bridgeCooldown = 400;
            setActivityState(FrostwritheState.CRAWLER);
        }
    }

    private void beginOverrun(LivingEntity target) {
        actionDestination = target.position();
        setActivityState(FrostwritheState.OVERRUN);
        actionDestination = target.position();
        setCohesion(cohesion() - 12.0F);
        playSound(ModSounds.FROSTWRITHE_OVERRUN.get(), 1.25F, 1.02F);
    }

    private void tickOverrun(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()
                || stateTicks() >= FrostwrithePolicy.OVERRUN_TICKS) {
            overrunCooldown = 240;
            surfaceAttackTicks = 0;
            setActivityState(FrostwritheState.CRAWLER);
            return;
        }
        Vec3 delta = target.position().subtract(position());
        if (delta.lengthSqr() > 0.04D) setDeltaMovement(delta.normalize().scale(0.12D));
        if (distanceToSqr(target) < 3.0D) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 1));
            if (stateTicks() == 10) {
                target.hurt(damageSources().mobAttack(this), 3.0F);
                Vec3 push = target.position().subtract(position()).normalize().scale(0.45D);
                target.push(push.x, 0.14D, push.z);
            }
        }
        if (stateTicks() % 2 == 0) {
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    target.getX(), target.getY() + 0.15D, target.getZ(), 5,
                    0.6D, 0.2D, 0.6D, 0.05D);
        }
    }

    private void tickDisassembling(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (stateTicks() % 2 == 0) {
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    getX(), getY() + 0.25D, getZ(), 10,
                    1.15D, 0.35D, 1.15D, 0.12D);
        }
        if (stateTicks() < FrostwrithePolicy.DISASSEMBLY_TICKS) return;
        spawnRepresentatives(level);
        discard();
    }

    private void spawnRepresentatives(ServerLevel level) {
        UUID id = colonyId == null ? UUID.randomUUID() : colonyId;
        int totalBiomass = Mth.clamp(Math.min(biomass(), Math.round(Math.max(
                cohesion(), getHealth() / getMaxHealth() * 100.0F))), 20, 100);
        int count = FrostwrithePolicy.representativeCount(totalBiomass);
        long scatterUntil = level.getGameTime() + 60L + random.nextInt(41);
        long regroupDeadline = scatterUntil + 60L;
        BlockPos rally = disassemblyRally == null
                ? blockPosition() : disassemblyRally;
        for (int index = 0; index < count; index++) {
            FrostmiteEntity mite = ModEntities.FROSTMITE.get().create(
                    level, null, rally, MobSpawnType.EVENT, true, false);
            if (mite == null) continue;
            double angle = Math.PI * 2.0D * index / count + random.nextDouble() * 0.3D;
            mite.moveTo(getX() + Math.cos(angle) * 0.65D, getY() + 0.1D,
                    getZ() + Math.sin(angle) * 0.65D,
                    random.nextFloat() * 360.0F, 0.0F);
            mite.joinColony(id,
                    FrostwrithePolicy.splitBiomass(totalBiomass, count, index),
                    rally, scatterUntil, regroupDeadline);
            if (getPersistentData().getBoolean(AggregatePressureHandler.IGNORE_PRESSURE_TAG)) {
                AggregatePressureHandler.markIgnored(mite);
            }
            mite.setDeltaMovement(Math.cos(angle) * 0.24D,
                    0.13D + random.nextDouble() * 0.08D,
                    Math.sin(angle) * 0.24D);
            level.addFreshEntity(mite);
        }
    }

    private boolean hasShortWallAhead(LivingEntity target) {
        Vec3 toward = target.position().subtract(position());
        if (toward.horizontalDistanceSqr() < 0.01D) return false;
        Vec3 direction = toward.multiply(1.0D, 0.0D, 1.0D).normalize();
        BlockPos wall = BlockPos.containing(position().add(direction.scale(0.9D)));
        return level().isLoaded(wall) && !level().getBlockState(wall).getCollisionShape(
                level(), wall).isEmpty();
    }

    private boolean hasShortGapToward(LivingEntity target) {
        Vec3 toward = target.position().subtract(position());
        if (toward.horizontalDistanceSqr() < 4.0D) return false;
        Vec3 direction = toward.multiply(1.0D, 0.0D, 1.0D).normalize();
        int gap = 0;
        for (int step = 1; step <= 4; step++) {
            BlockPos floor = BlockPos.containing(position().add(direction.scale(step))).below();
            if (!level().isLoaded(floor)) return false;
            if (level().getBlockState(floor).getCollisionShape(level(), floor).isEmpty()) {
                gap++;
            } else if (gap >= 2) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private BlockPos findNarrowPassageExit(LivingEntity target) {
        if (!horizontalCollision || distanceToSqr(target) > 64.0D) return null;
        Vec3 toward = target.position().subtract(position());
        if (toward.horizontalDistanceSqr() < 4.0D) return null;
        Vec3 direction = toward.multiply(1.0D, 0.0D, 1.0D).normalize();
        int stepX = Math.abs(direction.x) >= Math.abs(direction.z)
                ? (direction.x >= 0.0D ? 1 : -1) : 0;
        int stepZ = stepX == 0 ? (direction.z >= 0.0D ? 1 : -1) : 0;
        BlockPos opening = blockPosition().offset(stepX, 0, stepZ);
        BlockPos exit = opening.offset(stepX * 2, 0, stepZ * 2);
        if (!level().isLoaded(opening) || !level().isLoaded(exit)) return null;
        if (!isTwoHighPassage(opening) || !isTwoHighPassage(exit)) return null;

        BlockPos left = opening.offset(-stepZ, 0, stepX);
        BlockPos right = opening.offset(stepZ, 0, -stepX);
        boolean laterallyNarrow = !level().getBlockState(left)
                .getCollisionShape(level(), left).isEmpty()
                || !level().getBlockState(right)
                .getCollisionShape(level(), right).isEmpty();
        return laterallyNarrow ? exit : null;
    }

    private boolean isTwoHighPassage(BlockPos pos) {
        return level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()
                && level().getBlockState(pos.above())
                .getCollisionShape(level(), pos.above()).isEmpty()
                && !level().getBlockState(pos.below())
                .getCollisionShape(level(), pos.below()).isEmpty();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            playSound(ModSounds.FROSTWRITHE_BODY_CHECK.get(), 1.1F,
                    0.92F + random.nextFloat() * 0.12F);
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
            surfaceAttackTicks = Math.max(surfaceAttackTicks, POST_HIT_COMMIT_TICKS);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        FrostwritheState state = activityState();
        if (source.is(DamageTypeTags.IS_FREEZING)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypes.IN_WALL)
                || state == FrostwritheState.BURROWING
                || state == FrostwritheState.ERUPTING
                || state == FrostwritheState.DISASSEMBLING || state == FrostwritheState.DEAD) {
            return false;
        }
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        boolean sweeping = source.getEntity() instanceof Player && !projectile && amount >= 5.0F;
        float cohesionDamage = FrostwrithePolicy.cohesionDamage(
                amount, fire, explosion, sweeping, projectile);
        setCohesion(cohesion() - cohesionDamage);
        if (fire) {
            amount *= 2.0F;
            if (state == FrostwritheState.SHELL) setActivityState(FrostwritheState.CRAWLER);
        } else if (state == FrostwritheState.SHELL) {
            amount *= 0.45F;
        }
        if (projectile) shedRepresentative();
        pressureHits++;
        pressureWindow = 60;
        boolean hurt = super.hurt(source, amount);
        if (activityState() != FrostwritheState.DISASSEMBLING
                && cohesion() <= 0.0F) forceDisassemble();
        if (pressureHits >= 4 && shellCooldown <= 0
                && activityState() == FrostwritheState.CRAWLER && !fire) {
            forceShell();
            pressureHits = 0;
        }
        return hurt;
    }

    private void shedRepresentative() {
        if (!(level() instanceof ServerLevel level) || biomass() <= 45) return;
        int shed = Math.min(6, biomass() - 40);
        entityData.set(DATA_BIOMASS, biomass() - shed);
        FrostmiteEntity mite = ModEntities.FROSTMITE.get().create(
                level, null, blockPosition(), MobSpawnType.EVENT, true, false);
        if (mite == null) return;
        UUID id = colonyId == null ? UUID.randomUUID() : colonyId;
        colonyId = id;
        long now = level.getGameTime();
        mite.joinColony(id, shed, blockPosition(), now + 30L, now + 100L);
        mite.moveTo(getX(), getY() + 0.15D, getZ(), random.nextFloat() * 360.0F, 0.0F);
        mite.setDeltaMovement((random.nextDouble() - 0.5D) * 0.3D, 0.14D,
                (random.nextDouble() - 0.5D) * 0.3D);
        level.addFreshEntity(mite);
        playSound(ModSounds.FROSTWRITHE_SHED.get(), 1.0F, 1.05F);
    }

    @Override
    public void die(DamageSource source) {
        if (activityState() == FrostwritheState.DISASSEMBLING
                || activityState() == FrostwritheState.DEAD) return;
        setHealth(1.0F);
        forceDisassemble();
    }

    private void setCohesion(float value) {
        entityData.set(DATA_COHESION, Mth.clamp(value, 0.0F, 100.0F));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("FrostwritheState", activityState().name());
        tag.putFloat("FrostwritheCohesion", cohesion());
        tag.putInt("FrostwritheBiomass", biomass());
        if (colonyId != null) tag.putUUID("FrostwritheColony", colonyId);
        if (disassemblyRally != null) {
            tag.putLong("FrostwritheRally", disassemblyRally.asLong());
        }
        tag.putInt("FrostwritheShellCooldown", shellCooldown);
        tag.putInt("FrostwritheBridgeCooldown", bridgeCooldown);
        tag.putInt("FrostwritheOverrunCooldown", overrunCooldown);
        tag.putInt("FrostwritheBurrowCooldown", burrowCooldown);
        tag.putInt("FrostwritheSurfaceAttackTicks", surfaceAttackTicks);
        tag.putDouble("FrostwrithePatrolHeadingX", patrolHeading.x);
        tag.putDouble("FrostwrithePatrolHeadingZ", patrolHeading.z);
        tag.putLongArray("FrostwrithePatrolMemory", patrolMemory.stream()
                .mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        FrostwritheState loaded;
        try {
            loaded = FrostwritheState.valueOf(tag.getString("FrostwritheState"));
        } catch (IllegalArgumentException ignored) {
            loaded = FrostwritheState.CRAWLER;
        }
        setActivityState(loaded.isUnsafeAfterReload()
                ? FrostwritheState.CRAWLER : loaded);
        setCohesion(tag.contains("FrostwritheCohesion")
                ? tag.getFloat("FrostwritheCohesion") : 100.0F);
        entityData.set(DATA_BIOMASS, Mth.clamp(tag.contains("FrostwritheBiomass")
                ? tag.getInt("FrostwritheBiomass") : 100, 1, 100));
        colonyId = tag.hasUUID("FrostwritheColony")
                ? tag.getUUID("FrostwritheColony") : UUID.randomUUID();
        disassemblyRally = tag.contains("FrostwritheRally")
                ? BlockPos.of(tag.getLong("FrostwritheRally")) : null;
        shellCooldown = Math.max(0, tag.getInt("FrostwritheShellCooldown"));
        bridgeCooldown = Math.max(0, tag.getInt("FrostwritheBridgeCooldown"));
        overrunCooldown = Math.max(0, tag.getInt("FrostwritheOverrunCooldown"));
        burrowCooldown = Math.max(0, tag.getInt("FrostwritheBurrowCooldown"));
        surfaceAttackTicks = Math.max(0, tag.getInt("FrostwritheSurfaceAttackTicks"));
        patrolHeading = new Vec3(tag.getDouble("FrostwrithePatrolHeadingX"),
                0.0D, tag.getDouble("FrostwrithePatrolHeadingZ"));
        patrolMemory.clear();
        for (long packed : tag.getLongArray("FrostwrithePatrolMemory")) {
            patrolMemory.addLast(BlockPos.of(packed));
            if (patrolMemory.size() >= FrostwrithePolicy.PATROL_MEMORY_SIZE) break;
        }
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public int getTicksFrozen() {
        return 0;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FROSTWRITHE_MOVEMENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 150;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FROSTWRITHE_SHED.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }
}
