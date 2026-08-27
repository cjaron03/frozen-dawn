package com.frozendawn.entity;

import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.FrozenDawn;
import com.frozendawn.block.FuelProcessingSiloMultiblock;
import com.frozendawn.block.BloomMassBlock;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModParticles;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.BlastPitWarmZoneRegistry;
import com.frozendawn.world.ThermalVentRegistry;
import com.frozendawn.world.TransponderRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** An Architect-derived remnant whose construction loop has lost its purpose. */
public final class UndoneArchitectEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_BUILD_TICKS =
            SynchedEntityData.defineId(
                    UndoneArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEATH_TICKS =
            SynchedEntityData.defineId(
                    UndoneArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACCRETION =
            SynchedEntityData.defineId(
                    UndoneArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACCRETION_TICKS =
            SynchedEntityData.defineId(
                    UndoneArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BLOOM_EMERGENCE_TICKS =
            SynchedEntityData.defineId(
                    UndoneArchitectEntity.class, EntityDataSerializers.INT);
    private static final ResourceLocation ACCRETION_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "undone_architect_accretion_health");
    private static final ResourceLocation ACCRETION_ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "undone_architect_accretion_armor");
    private static final int MAX_LOCAL_ICE = 192;
    private static final double TERRITORY_SHIFT_DISTANCE_SQR = 48.0D * 48.0D;
    private static final int TARGET_SCAN_INTERVAL = 10;
    private static final double PURSUIT_SPEED = 1.28D;
    private static final int MIN_ATTENTION_TICKS = 200;
    private static final int ATTENTION_VARIANCE_TICKS = 101;
    private static final int MIN_BUILD_DISTRACTION_TICKS = 100;
    private static final int BUILD_DISTRACTION_VARIANCE_TICKS = 61;
    public static final int MAX_ACCRETION_STACKS = 8;
    private static final double HEALTH_PER_ACCRETION = 6.0D;
    private static final double ARMOR_PER_ACCRETION = 0.75D;
    private static final float ACCRETION_HEAL = 10.0F;
    private static final int ACCRETION_DURATION_TICKS = 24;
    public static final int BLOOM_EMERGENCE_DURATION = 44;

    private final List<BlockPos> builtIce = new ArrayList<>();
    @Nullable
    private BlockPos buildOrigin;
    private int buildCooldown;
    private int attentionTicks;
    private int buildDistractionTicks;
    @Nullable
    private Vec3 accretionOrigin;

    public UndoneArchitectEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
        setCustomName(Component.literal("The Undone Architect"));
        setCustomNameVisible(true);
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        setDropChance(EquipmentSlot.HEAD, 0.0F);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        getNavigation().getNodeEvaluator().setCanOpenDoors(true);
        getNavigation().getNodeEvaluator().setCanPassDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 64.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BUILD_TICKS, 0);
        builder.define(DATA_DEATH_TICKS, 0);
        builder.define(DATA_ACCRETION, 0);
        builder.define(DATA_ACCRETION_TICKS, 0);
        builder.define(DATA_BLOOM_EMERGENCE_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, PURSUIT_SPEED, false) {
            @Override
            public boolean canUse() {
                return !isBloomEmerging() && getBuildTicks() == 0 && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isBloomEmerging() && getBuildTicks() == 0
                        && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.92D) {
            @Override
            public boolean canUse() {
                return !isBloomEmerging() && getTarget() == null && super.canUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (isBloomEmerging()) {
            tickBloomEmergence();
            return;
        }
        if (getAccretionTicks() > 0 && level() instanceof ServerLevel serverLevel) {
            tickAccretion(serverLevel);
            updateHeldItem();
            return;
        }
        int buildTicks = getBuildTicks();
        if (buildTicks > 0) {
            entityData.set(DATA_BUILD_TICKS, buildTicks - 1);
        }
        if (buildDistractionTicks > 0) {
            buildDistractionTicks--;
        }
        tickAttentionSpan();
        maintainAggression();
        tickConstruction();
        updateHeldItem();
        LivingEntity target = getTarget();
        setSprinting(getBuildTicks() == 0 && isValidTarget(target));
        if (tickCount % 20 == 0) {
            breachOwnForgottenIce();
        }
    }

    public int getBuildTicks() {
        return entityData.get(DATA_BUILD_TICKS);
    }

    public int getDeathTicks() {
        return entityData.get(DATA_DEATH_TICKS);
    }

    public int getAccretionStacks() {
        return entityData.get(DATA_ACCRETION);
    }

    public int getAccretionTicks() {
        return entityData.get(DATA_ACCRETION_TICKS);
    }

    public boolean isBloomEmerging() {
        return entityData.get(DATA_BLOOM_EMERGENCE_TICKS) > 0;
    }

    public int getBloomEmergenceTicks() {
        return entityData.get(DATA_BLOOM_EMERGENCE_TICKS);
    }

    public void beginBloomEmergence() {
        entityData.set(DATA_BLOOM_EMERGENCE_TICKS, BLOOM_EMERGENCE_DURATION);
        setTarget(null);
        getNavigation().stop();
        if (level() instanceof ServerLevel serverLevel) {
            playSound(ModSounds.BLOOM_CRACK.get(), 1.35F, 0.62F);
            serverLevel.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                    getX(), getY() + 0.25D, getZ(),
                    42, 0.58D, 0.22D, 0.58D, 0.045D);
        }
    }

    private void tickBloomEmergence() {
        int ticks = getBloomEmergenceTicks();
        entityData.set(DATA_BLOOM_EMERGENCE_TICKS, Math.max(0, ticks - 1));
        setTarget(null);
        getNavigation().stop();
        setSprinting(false);
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        updateHeldItem();
        if (level() instanceof ServerLevel serverLevel && ticks % 3 == 0) {
            double height = 0.18D + (1.0D - ticks / (double) BLOOM_EMERGENCE_DURATION)
                    * 1.65D;
            serverLevel.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                    getX(), getY() + height, getZ(),
                    5, 0.38D, 0.14D, 0.38D, 0.02D);
        }
    }

    public int getAccretionVisualStage() {
        int stacks = getAccretionStacks();
        return stacks >= 6 ? 2 : stacks >= 3 ? 1 : 0;
    }

    private void maintainAggression() {
        if (buildDistractionTicks > 0) {
            setTarget(null);
            getNavigation().stop();
            return;
        }
        LivingEntity target = getTarget();
        if (tickCount % TARGET_SCAN_INTERVAL != 0) {
            return;
        }
        if (isValidTarget(target)) {
            Path currentPath = getNavigation().createPath(target, 0);
            if (currentPath != null
                    && (currentPath.canReach() || distanceToSqr(target) <= 9.0D)) {
                getNavigation().moveTo(currentPath, PURSUIT_SPEED);
                return;
            }
        }
        setTarget(null);
        List<LivingEntity> candidates = level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(48.0D, 24.0D, 48.0D),
                        this::isValidTarget)
                .stream()
                .sorted(Comparator.comparingDouble(this::distanceToSqr))
                .limit(16)
                .toList();
        LivingEntity fallback = candidates.isEmpty() ? null : candidates.getFirst();
        for (LivingEntity candidate : candidates) {
            Path path = getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                focusTarget(candidate);
                getNavigation().moveTo(path, PURSUIT_SPEED);
                return;
            }
        }
        if (fallback != null) {
            focusTarget(fallback);
            getNavigation().moveTo(fallback, PURSUIT_SPEED);
        }
    }

    private void focusTarget(LivingEntity target) {
        setTarget(target);
        attentionTicks = MIN_ATTENTION_TICKS
                + random.nextInt(ATTENTION_VARIANCE_TICKS);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(isSporeTarget(target) || target instanceof ArchivistEntity
                ? null : target);
    }

    private void tickAttentionSpan() {
        if (!isValidTarget(getTarget())) {
            attentionTicks = 0;
            return;
        }
        if (attentionTicks > 0 && --attentionTicks > 0) {
            return;
        }
        setTarget(null);
        getNavigation().stop();
        setSprinting(false);
        buildDistractionTicks = MIN_BUILD_DISTRACTION_TICKS
                + random.nextInt(BUILD_DISTRACTION_VARIANCE_TICKS);
        buildCooldown = 0;
    }

    private boolean isValidTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || candidate == this || !candidate.isAlive()
                || candidate == getVehicle() || candidate.hasPassenger(this)
                || candidate instanceof ArchivistEntity
                || candidate instanceof BloomSporeEntity
                || candidate instanceof BloomSporeCorpseEntity) {
            return false;
        }
        if (candidate instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return candidate.isAttackable();
    }

    private void updateHeldItem() {
        ItemStack next = getAccretionTicks() > 0
                ? ItemStack.EMPTY
                : getBuildTicks() > 0
                ? new ItemStack(Blocks.PACKED_ICE)
                : isValidTarget(getTarget())
                        ? new ItemStack(Items.WOODEN_SWORD)
                        : ItemStack.EMPTY;
        if (!ItemStack.isSameItemSameComponents(getMainHandItem(), next)) {
            setItemSlot(EquipmentSlot.MAINHAND, next);
        }
    }

    private void tickConstruction() {
        if (--buildCooldown > 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (buildOrigin == null) {
            buildOrigin = blockPosition();
        } else if (buildOrigin.distSqr(blockPosition()) > TERRITORY_SHIFT_DISTANCE_SQR) {
            buildOrigin = blockPosition();
            builtIce.clear();
        }
        builtIce.removeIf(pos -> level().hasChunkAt(pos)
                && (!level().getBlockState(pos).is(Blocks.PACKED_ICE)
                && !level().getBlockState(pos).is(Blocks.ICE)));
        if (builtIce.size() >= MAX_LOCAL_ICE) {
            return;
        }

        BlockPos center = selectBuildCenter(serverLevel);
        if (center == null) {
            buildCooldown = buildDistractionTicks > 0 ? 20 : 100;
            return;
        }
        int placed = switch (random.nextInt(4)) {
            case 0 -> buildBrokenWall(serverLevel, center);
            case 1 -> buildDoorToNowhere(serverLevel, center);
            case 2 -> buildCrookedPillar(serverLevel, center);
            default -> buildAroundSelf(serverLevel);
        };
        if (placed > 0) {
            buildCooldown = 400 + random.nextInt(301);
            entityData.set(DATA_BUILD_TICKS, 18);
            setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Blocks.PACKED_ICE));
            swing(InteractionHand.MAIN_HAND);
            playSound(ModSounds.ARCHITECT_ICE_PLACE.get(), 0.82F,
                    0.72F + random.nextFloat() * 0.12F);
        } else {
            buildCooldown = buildDistractionTicks > 0 ? 20 : 100;
        }
    }

    @Nullable
    private BlockPos selectBuildCenter(ServerLevel level) {
        Player nearby = buildDistractionTicks > 0
                ? null : level.getNearestPlayer(this, 24.0D);
        double angle;
        if (nearby != null) {
            angle = Math.atan2(nearby.getZ() - getZ(), nearby.getX() - getX())
                    + (random.nextDouble() - 0.5D) * 1.7D;
        } else {
            angle = random.nextDouble() * Math.PI * 2.0D;
        }
        int distance = 4 + random.nextInt(7);
        int x = (int) Math.floor(getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(getZ() + Math.sin(angle) * distance);
        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        return level.hasChunkAt(surface) ? surface : null;
    }

    private int buildBrokenWall(ServerLevel level, BlockPos center) {
        Direction axis = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
        int placed = 0;
        for (int width = -1; width <= 1; width++) {
            for (int height = 0; height < 2; height++) {
                if (random.nextInt(7) == 0) {
                    continue;
                }
                placed += placeForgottenIce(level,
                        center.relative(axis, width).above(height)) ? 1 : 0;
            }
        }
        return placed;
    }

    private int buildDoorToNowhere(ServerLevel level, BlockPos center) {
        Direction axis = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
        int placed = 0;
        for (int height = 0; height < 3; height++) {
            placed += placeForgottenIce(level,
                    center.relative(axis).above(height)) ? 1 : 0;
            placed += placeForgottenIce(level,
                    center.relative(axis.getOpposite()).above(height)) ? 1 : 0;
        }
        placed += placeForgottenIce(level, center.above(2)) ? 1 : 0;
        return placed;
    }

    private int buildCrookedPillar(ServerLevel level, BlockPos center) {
        int height = 2 + random.nextInt(3);
        int placed = 0;
        BlockPos cursor = center;
        for (int i = 0; i < height; i++) {
            if (i > 1 && random.nextBoolean()) {
                cursor = cursor.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
            }
            placed += placeForgottenIce(level, cursor.above(i)) ? 1 : 0;
        }
        return placed;
    }

    private int buildAroundSelf(ServerLevel level) {
        BlockPos center = blockPosition();
        int placed = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (random.nextInt(4) == 0) {
                continue;
            }
            placed += placeForgottenIce(level, center.relative(direction)) ? 1 : 0;
            if (random.nextBoolean()) {
                placed += placeForgottenIce(
                        level, center.relative(direction).above()) ? 1 : 0;
            }
        }
        return placed;
    }

    private boolean placeForgottenIce(ServerLevel level, BlockPos pos) {
        if (builtIce.size() >= MAX_LOCAL_ICE || !level.hasChunkAt(pos)
                || isProtectedInfrastructure(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if ((!state.isAir() && !state.canBeReplaced())
                || !state.getFluidState().isEmpty()
                || !level.getEntities(null, new AABB(pos).inflate(0.04D)).isEmpty()) {
            return false;
        }
        BlockPos below = pos.below();
        boolean supported = level.getBlockState(below).isSolidRender(level, below)
                || builtIce.contains(below)
                || isBloom(level.getBlockState(below));
        boolean attached = supported;
        boolean bloomAttached = isBloom(level.getBlockState(below));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            BlockState sideState = level.getBlockState(side);
            attached |= sideState.isSolidRender(level, side);
            bloomAttached |= isBloom(sideState);
        }
        if (!attached) {
            return false;
        }
        BlockState ice = bloomAttached && random.nextInt(3) == 0
                ? ModBlocks.BLOOM_MASS.get().defaultBlockState()
                        .setValue(BloomMassBlock.BAND, BloomBand.MID)
                : random.nextInt(5) == 0
                        ? Blocks.ICE.defaultBlockState()
                        : Blocks.PACKED_ICE.defaultBlockState();
        level.setBlock(pos, ice, 3);
        builtIce.add(pos.immutable());
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                8, 0.28D, 0.35D, 0.28D, 0.025D);
        return true;
    }

    private static boolean isBloom(BlockState state) {
        return state.is(ModBlocks.BLOOM_MASS.get())
                || state.is(ModBlocks.BLOOM_CRUST.get())
                || state.is(ModBlocks.BLOOM_TIP.get());
    }

    private boolean isProtectedInfrastructure(ServerLevel level, BlockPos pos) {
        if (StillpointPolicy.isSuppressed(level, pos)
                || BlastPitWarmZoneRegistry.isInsideWarmZone(level, pos)
                || ThermalVentRegistry.isVolcanicField(level, pos)
                || FuelProcessingSiloMultiblock.isProtectedFromEnvironmentalDeposit(
                        level, pos)) {
            return true;
        }
        return TransponderRegistry.getTransponders(level).stream()
                .anyMatch(transponder -> transponder.distSqr(pos) <= 144.0D);
    }

    private void breachOwnForgottenIce() {
        if (!horizontalCollision || builtIce.isEmpty()
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = blockPosition().relative(direction);
            if (!builtIce.remove(pos)) {
                continue;
            }
            if (level().getBlockState(pos).is(Blocks.PACKED_ICE)
                    || level().getBlockState(pos).is(Blocks.ICE)) {
                level().destroyBlock(pos, false, this);
                serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        12, 0.32D, 0.32D, 0.32D, 0.08D);
            }
            return;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isBloomEmerging() || isSporeTarget(target)) {
            setTarget(null);
            getNavigation().stop();
            return false;
        }
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playSound(ModSounds.UNDONE_ARCHITECT_ATTACK.get(),
                    1.0F, 0.94F + random.nextFloat() * 0.10F);
        }
        return hit;
    }

    private static boolean isSporeTarget(@Nullable Entity target) {
        return target instanceof BloomSporeEntity
                || target instanceof BloomSporeCorpseEntity;
    }

    public void gainAccretion(LivingEntity victim) {
        if (!(level() instanceof ServerLevel serverLevel)
                || victim instanceof Player
                || getAccretionStacks() >= MAX_ACCRETION_STACKS) {
            return;
        }
        int next = Math.min(MAX_ACCRETION_STACKS, getAccretionStacks() + 1);
        entityData.set(DATA_ACCRETION, next);
        entityData.set(DATA_ACCRETION_TICKS, ACCRETION_DURATION_TICKS);
        accretionOrigin = victim.position().add(
                0.0D, Math.max(0.35D, victim.getBbHeight() * 0.52D), 0.0D);
        applyAccretionModifiers(next);
        heal(ACCRETION_HEAL);
        getNavigation().stop();
        setSprinting(false);
        setDeltaMovement(getDeltaMovement().scale(0.15D));
        playSound(ModSounds.UNDONE_ARCHITECT_ACCRETE.get(),
                1.45F, 0.92F + random.nextFloat() * 0.06F);
        emitAccretionStartFx(serverLevel, accretionOrigin);
    }

    private void applyAccretionModifiers(int stacks) {
        replacePermanentModifier(
                Attributes.MAX_HEALTH,
                ACCRETION_HEALTH_ID,
                stacks * HEALTH_PER_ACCRETION);
        replacePermanentModifier(
                Attributes.ARMOR,
                ACCRETION_ARMOR_ID,
                stacks * ARMOR_PER_ACCRETION);
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    private void replacePermanentModifier(
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id,
            double amount) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount > 0.0D) {
            instance.addPermanentModifier(new AttributeModifier(
                    id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void emitAccretionStartFx(ServerLevel level, Vec3 origin) {
        level.sendParticles(
                new BlockParticleOption(
                        ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                origin.x, origin.y, origin.z,
                22, 0.45D, 0.55D, 0.45D, 0.08D);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                origin.x, origin.y, origin.z,
                16, 0.38D, 0.48D, 0.38D, 0.07D);
    }

    private void tickAccretion(ServerLevel level) {
        int ticks = getAccretionTicks();
        if (ticks <= 0) {
            return;
        }
        getNavigation().stop();
        setSprinting(false);
        setDeltaMovement(getDeltaMovement().scale(0.35D));

        Vec3 destination = position().add(0.0D, 1.08D, 0.0D);
        Vec3 source = accretionOrigin != null
                ? accretionOrigin : destination.add(0.0D, -0.8D, 0.0D);
        double progress = 1.0D - ticks / (double) ACCRETION_DURATION_TICKS;
        for (int i = 0; i < 6; i++) {
            Vec3 start = source.add(
                    (random.nextDouble() - 0.5D) * 0.9D,
                    (random.nextDouble() - 0.5D) * 0.7D,
                    (random.nextDouble() - 0.5D) * 0.9D);
            double streamProgress = Math.min(0.96D,
                    progress + i * 0.035D + random.nextDouble() * 0.025D);
            Vec3 point = start.lerp(destination, streamProgress).add(
                    0.0D, Math.sin(streamProgress * Math.PI) * 0.85D, 0.0D);
            Vec3 motion = destination.subtract(point);
            if (motion.lengthSqr() > 1.0E-5D) {
                motion = motion.normalize().scale(0.13D + progress * 0.12D);
            }
            level.sendParticles(
                    new BlockParticleOption(
                            ParticleTypes.BLOCK,
                            random.nextBoolean()
                                    ? Blocks.PACKED_ICE.defaultBlockState()
                                    : Blocks.BLUE_ICE.defaultBlockState()),
                    point.x, point.y, point.z,
                    0, motion.x, motion.y, motion.z, 1.0D);
            if ((i & 1) == 0) {
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        point.x, point.y, point.z,
                        0, motion.x, motion.y, motion.z, 0.9D);
            }
        }

        int nextTicks = ticks - 1;
        entityData.set(DATA_ACCRETION_TICKS, nextTicks);
        if (nextTicks == 0) {
            accretionOrigin = null;
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    destination.x, destination.y, destination.z,
                    34, 0.42D, 0.58D, 0.42D, 0.10D);
            level.sendParticles(
                    new BlockParticleOption(
                            ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState()),
                    destination.x, destination.y, destination.z,
                    24, 0.34D, 0.48D, 0.34D, 0.08D);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isBloomEmerging() || source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker
                && attacker.isAlive()) {
            buildDistractionTicks = 0;
            focusTarget(attacker);
        }
        return hurt;
    }

    @Override
    protected void tickDeath() {
        int ticks = getDeathTicks() + 1;
        entityData.set(DATA_DEATH_TICKS, ticks);
        setSprinting(false);
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel serverLevel) {
            double sinkY = getY() + 1.45D - ticks / 30.0D * 1.05D;
            serverLevel.sendParticles(ParticleTypes.WHITE_ASH,
                    getX(), sinkY, getZ(), 3,
                    0.22D, 0.22D, 0.22D, 0.005D);
            if (ticks % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                        getX(), sinkY, getZ(), 2,
                        0.18D, 0.18D, 0.18D, 0.008D);
            }
            if (ticks >= 22) {
                serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                        getX(), getY() + 0.35D, getZ(), 1,
                        0.12D, 0.12D, 0.12D, 0.01D);
            }
        }
        if (ticks < 30) {
            return;
        }
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        remove(RemovalReason.KILLED);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 0.65D, getZ(), 20,
                    0.32D, 0.42D, 0.32D, 0.045D);
            serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    getX(), getY() + 0.55D, getZ(), 38,
                    0.48D, 0.55D, 0.48D, 0.10D);
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 0.4D, getZ(), 10,
                    0.28D, 0.18D, 0.28D, 0.045D);
        }
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.UNDONE_ARCHITECT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.UNDONE_ARCHITECT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.UNDONE_ARCHITECT_DEATH.get();
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BuildCooldown", buildCooldown);
        if (buildOrigin != null) {
            tag.putLong("BuildOrigin", buildOrigin.asLong());
        }
        tag.putLongArray("BuiltIce", builtIce.stream()
                .mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Accretion", getAccretionStacks());
        tag.putInt("AttentionTicks", attentionTicks);
        tag.putInt("BuildDistractionTicks", buildDistractionTicks);
        tag.putInt("BloomEmergenceTicks", getBloomEmergenceTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        buildCooldown = Math.max(80, tag.getInt("BuildCooldown"));
        buildOrigin = tag.contains("BuildOrigin")
                ? BlockPos.of(tag.getLong("BuildOrigin")) : null;
        builtIce.clear();
        for (long packed : tag.getLongArray("BuiltIce")) {
            if (builtIce.size() >= MAX_LOCAL_ICE) {
                break;
            }
            builtIce.add(BlockPos.of(packed));
        }
        int accretion = Math.max(0, Math.min(
                MAX_ACCRETION_STACKS, tag.getInt("Accretion")));
        entityData.set(DATA_ACCRETION, accretion);
        applyAccretionModifiers(accretion);
        attentionTicks = Math.max(0, tag.getInt("AttentionTicks"));
        buildDistractionTicks = Math.max(
                0, tag.getInt("BuildDistractionTicks"));
        entityData.set(DATA_BLOOM_EMERGENCE_TICKS,
                Math.max(0, tag.getInt("BloomEmergenceTicks")));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        setDropChance(EquipmentSlot.HEAD, 0.0F);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }
}
