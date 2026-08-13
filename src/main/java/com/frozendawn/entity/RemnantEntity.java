package com.frozendawn.entity;

import com.frozendawn.data.RemnantLureSavedData;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.init.ModToolTiers;
import com.frozendawn.world.remnant.RemnantLureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Separate Mimic evolution bound to a persistent authored false refuge. */
public final class RemnantEntity extends Monster {
    public static final String GRAB_MARKER = "frozendawnRemnantGrab";
    private static final ResourceLocation LURE_ARMOR_ID = ResourceLocation.fromNamespaceAndPath(
            "frozendawn", "remnant_lure_armor");
    private static final double LURE_ARMOR_BONUS = 6.0D;
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICKS =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GRAB_TARGET =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_INSIDE_LURE =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> DATA_FACE_PLAYER =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_COUNTER_TICKS =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WALL_LATCH_TICKS =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WALL_DIRECTION =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL_MAINHAND =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL_OFFHAND =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL_HEAD =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL_CHEST =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL_LEGS =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL_FEET =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_REFLECTION_FLAGS =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_REFLECTION_HEAD_YAW =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_REFLECTION_HEAD_PITCH =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_REFLECTION_STRAFE =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_REFLECTION_FORWARD =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_REFLECTION_USE_ANIM =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_REFLECTION_HAND =
            SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);

    private static final int REFLECTION_CROUCHING = 1;
    private static final int REFLECTION_SPRINTING = 1 << 1;
    private static final int REFLECTION_USING_ITEM = 1 << 2;
    private static final int REFLECTION_SWING = 1 << 3;

    private UUID lureId;
    private BlockPos lureAnchor;
    private int slipCooldown;
    private int slipWindup;
    private int slipRecovery;
    private BlockPos slipTarget;
    private int grabCooldown;
    private int grabSwings;
    private boolean swingWasActive;
    private String learnedAttack = "";
    private int learnedHits;
    private int learnWindowTicks;
    private int learnedDodgeCooldown;
    private int counterCooldown;
    private int counterTargetId = -1;
    private String counterAttack = "";
    private Direction slipWallDirection = Direction.NORTH;
    private float wallLatchHealedThisUse;
    private float wallLatchHealedTotal;
    private final ArrayDeque<ReflectionFrame> reflectionDelay = new ArrayDeque<>();
    private boolean recordedSwing;
    private int reflectionAttackCooldown;
    private int reflectionGuardTicks;
    private transient boolean visualEquipmentAccess;

    public RemnantEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 84.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true) {
            @Override public boolean canUse() { return isCombatActive() && super.canUse(); }
            @Override public boolean canContinueToUse() { return isCombatActive() && super.canContinueToUse(); }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true, player -> isCombatActive()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, RemnantState.DORMANT.ordinal());
        builder.define(DATA_STATE_TICKS, 0);
        builder.define(DATA_GRAB_TARGET, -1);
        builder.define(DATA_INSIDE_LURE, true);
        builder.define(DATA_FACE_PLAYER, Optional.empty());
        builder.define(DATA_COUNTER_TICKS, 0);
        builder.define(DATA_WALL_LATCH_TICKS, 0);
        builder.define(DATA_WALL_DIRECTION, Direction.NORTH.get3DDataValue());
        builder.define(DATA_VISUAL_MAINHAND, ItemStack.EMPTY);
        builder.define(DATA_VISUAL_OFFHAND, ItemStack.EMPTY);
        builder.define(DATA_VISUAL_HEAD, ItemStack.EMPTY);
        builder.define(DATA_VISUAL_CHEST, ItemStack.EMPTY);
        builder.define(DATA_VISUAL_LEGS, ItemStack.EMPTY);
        builder.define(DATA_VISUAL_FEET, ItemStack.EMPTY);
        builder.define(DATA_REFLECTION_FLAGS, 0);
        builder.define(DATA_REFLECTION_HEAD_YAW, 0.0F);
        builder.define(DATA_REFLECTION_HEAD_PITCH, 0.0F);
        builder.define(DATA_REFLECTION_STRAFE, 0.0F);
        builder.define(DATA_REFLECTION_FORWARD, 0.0F);
        builder.define(DATA_REFLECTION_USE_ANIM, UseAnim.NONE.ordinal());
        builder.define(DATA_REFLECTION_HAND, InteractionHand.MAIN_HAND.ordinal());
    }

    public void bind(UUID lureId, BlockPos anchor, RemnantState state) {
        this.lureId = lureId;
        this.lureAnchor = anchor.immutable();
        setState(state);
        setPersistenceRequired();
    }

    public void exposeWithoutLure(ServerPlayer player) {
        lureId = null;
        lureAnchor = null;
        entityData.set(DATA_FACE_PLAYER, Optional.of(player.getUUID()));
        capturePlayerAppearance(player);
        entityData.set(DATA_INSIDE_LURE, false);
        setState(RemnantState.HUNTING);
        setPersistenceRequired();
    }

    public UUID lureId() { return lureId; }
    public BlockPos lureAnchor() { return lureAnchor == null ? blockPosition() : lureAnchor; }
    public RemnantState state() { return RemnantState.byOrdinal(entityData.get(DATA_STATE)); }
    public int stateTicks() { return entityData.get(DATA_STATE_TICKS); }
    public int grabTargetId() { return entityData.get(DATA_GRAB_TARGET); }
    public boolean insideLure() { return entityData.get(DATA_INSIDE_LURE); }
    public Optional<UUID> facePlayer() { return entityData.get(DATA_FACE_PLAYER); }
    public int counterTicks() { return entityData.get(DATA_COUNTER_TICKS); }
    public int wallLatchTicks() { return entityData.get(DATA_WALL_LATCH_TICKS); }
    public boolean isWallLatched() { return wallLatchTicks() > 0; }
    public Direction wallLatchDirection() {
        return Direction.from3DDataValue(entityData.get(DATA_WALL_DIRECTION));
    }

    public void setState(RemnantState state) {
        if (this.state() == RemnantState.EXPOSED && state != RemnantState.EXPOSED) {
            clearGrabTarget();
        }
        entityData.set(DATA_STATE, state.ordinal());
        entityData.set(DATA_STATE_TICKS, 0);
        setInvisible(state == RemnantState.DORMANT || state == RemnantState.OBSERVING
                || state == RemnantState.LURE_READY || state == RemnantState.COMMITTED);
        setNoAi(state == RemnantState.DORMANT || state == RemnantState.OBSERVING
                || state == RemnantState.LURE_READY || state == RemnantState.COMMITTED
                || state == RemnantState.SEALING || state == RemnantState.COLLAPSING);
    }

    public void awaken(ServerPlayer player) {
        entityData.set(DATA_FACE_PLAYER, Optional.of(player.getUUID()));
        capturePlayerAppearance(player);
        setTarget(player);
        setState(RemnantState.COMMITTED);
    }

    public boolean forceSlip() {
        return insideLure() && state() == RemnantState.HUNTING && startWallSlip();
    }

    public void forceGrab(ServerPlayer player) {
        if (state() == RemnantState.HUNTING) beginGrab(player);
    }

    public void forceMarkedTarget(ServerPlayer player) {
        if (player.isAlive() && state().isCommitted()) setTarget(player);
    }

    private boolean isCombatActive() {
        return state() == RemnantState.HUNTING || state() == RemnantState.EXPOSED;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            tickClientSmoke();
            return;
        }
        entityData.set(DATA_STATE_TICKS, stateTicks() + 1);
        if (state() == RemnantState.COLLAPSING) {
            if (level() instanceof ServerLevel server && lureId != null) {
                RemnantLureManager.beginCollapse(server, lureId);
            }
            discard();
            return;
        }
        if (slipCooldown > 0) slipCooldown--;
        if (grabCooldown > 0) grabCooldown--;
        if (learnedDodgeCooldown > 0) learnedDodgeCooldown--;
        if (counterCooldown > 0) counterCooldown--;
        if (reflectionAttackCooldown > 0) reflectionAttackCooldown--;
        if (reflectionGuardTicks > 0) reflectionGuardTicks--;
        if (learnWindowTicks > 0 && --learnWindowTicks == 0) clearLearnedAttack();
        insideLureCheck();

        if (state() == RemnantState.SEALING
                && stateTicks() >= RemnantPolicy.SEALING_TICKS) setState(RemnantState.HUNTING);
        if (state() == RemnantState.HUNTING) {
            tickReflection();
            tickCounterfeitSwing();
            tickHunting();
        } else if (state() != RemnantState.SEALING) clearReflectionPose();
        if (state() == RemnantState.EXPOSED) tickGrab();
        if (lureAnchor != null && distanceToSqr(Vec3.atCenterOf(lureAnchor))
                > RemnantPolicy.LEASH_RADIUS * RemnantPolicy.LEASH_RADIUS) {
            setTarget(null);
            getNavigation().moveTo(lureAnchor.getX() + 0.5D,
                    lureAnchor.getY() + 1.0D, lureAnchor.getZ() + 0.5D, 1.1D);
        }
    }

    private void capturePlayerAppearance(ServerPlayer player) {
        entityData.set(DATA_VISUAL_MAINHAND, visualCopy(player.getMainHandItem()));
        entityData.set(DATA_VISUAL_OFFHAND, visualCopy(player.getOffhandItem()));
        entityData.set(DATA_VISUAL_HEAD, visualCopy(player.getItemBySlot(EquipmentSlot.HEAD)));
        entityData.set(DATA_VISUAL_CHEST, visualCopy(player.getItemBySlot(EquipmentSlot.CHEST)));
        entityData.set(DATA_VISUAL_LEGS, visualCopy(player.getItemBySlot(EquipmentSlot.LEGS)));
        entityData.set(DATA_VISUAL_FEET, visualCopy(player.getItemBySlot(EquipmentSlot.FEET)));
    }

    private static ItemStack visualCopy(ItemStack source) {
        return source.isEmpty() ? ItemStack.EMPTY : source.copyWithCount(1);
    }

    public ItemStack visualItem(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> entityData.get(DATA_VISUAL_MAINHAND);
            case OFFHAND -> entityData.get(DATA_VISUAL_OFFHAND);
            case HEAD -> entityData.get(DATA_VISUAL_HEAD);
            case CHEST -> entityData.get(DATA_VISUAL_CHEST);
            case LEGS -> entityData.get(DATA_VISUAL_LEGS);
            case FEET -> entityData.get(DATA_VISUAL_FEET);
            default -> ItemStack.EMPTY;
        };
    }

    /** Rendering uses copied equipment without equipping it or granting any item mechanics. */
    public void beginVisualEquipmentRender() {
        visualEquipmentAccess = level().isClientSide();
    }

    public void endVisualEquipmentRender() {
        visualEquipmentAccess = false;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return visualEquipmentAccess ? visualItem(slot) : super.getItemBySlot(slot);
    }

    @Override
    public ItemStack getMainHandItem() {
        return visualEquipmentAccess ? visualItem(EquipmentSlot.MAINHAND) : super.getMainHandItem();
    }

    @Override
    public ItemStack getOffhandItem() {
        return visualEquipmentAccess ? visualItem(EquipmentSlot.OFFHAND) : super.getOffhandItem();
    }

    public boolean reflectionCrouching() {
        return (entityData.get(DATA_REFLECTION_FLAGS) & REFLECTION_CROUCHING) != 0;
    }

    public boolean reflectionSprinting() {
        return (entityData.get(DATA_REFLECTION_FLAGS) & REFLECTION_SPRINTING) != 0;
    }

    public boolean reflectionUsingItem() {
        return (entityData.get(DATA_REFLECTION_FLAGS) & REFLECTION_USING_ITEM) != 0;
    }

    public float reflectionHeadYaw() { return entityData.get(DATA_REFLECTION_HEAD_YAW); }
    public float reflectionHeadPitch() { return entityData.get(DATA_REFLECTION_HEAD_PITCH); }
    public float reflectionStrafe() { return entityData.get(DATA_REFLECTION_STRAFE); }
    public float reflectionForward() { return entityData.get(DATA_REFLECTION_FORWARD); }
    public InteractionHand reflectionHand() {
        return entityData.get(DATA_REFLECTION_HAND) == InteractionHand.OFF_HAND.ordinal()
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
    public UseAnim reflectionUseAnimation() {
        int index = entityData.get(DATA_REFLECTION_USE_ANIM);
        UseAnim[] values = UseAnim.values();
        return index >= 0 && index < values.length ? values[index] : UseAnim.NONE;
    }

    private void tickReflection() {
        if (!(getTarget() instanceof ServerPlayer target) || !target.isAlive()) {
            clearReflectionPose();
            reflectionDelay.clear();
            return;
        }
        if (facePlayer().isEmpty()) {
            entityData.set(DATA_FACE_PLAYER, Optional.of(target.getUUID()));
            capturePlayerAppearance(target);
        }
        ReflectionFrame frame = captureReflectionFrame(target);
        reflectionDelay.addLast(frame);
        if (reflectionDelay.size() <= RemnantPolicy.REFLECTION_DELAY_TICKS) return;
        applyReflectionFrame(target, reflectionDelay.removeFirst());
    }

    private ReflectionFrame captureReflectionFrame(ServerPlayer player) {
        boolean swingEdge = player.swinging && !recordedSwing;
        recordedSwing = player.swinging;
        int flags = 0;
        if (player.isCrouching()) flags |= REFLECTION_CROUCHING;
        if (player.isSprinting()) flags |= REFLECTION_SPRINTING;
        if (player.isUsingItem()) flags |= REFLECTION_USING_ITEM;
        if (swingEdge) flags |= REFLECTION_SWING;
        Vec3 velocity = player.getDeltaMovement();
        double yaw = Math.toRadians(player.getYRot());
        float forward = (float) ((-Math.sin(yaw) * velocity.x
                + Math.cos(yaw) * velocity.z) / 0.22D);
        float strafe = (float) ((Math.cos(yaw) * velocity.x
                + Math.sin(yaw) * velocity.z) / 0.22D);
        UseAnim useAnim = player.isUsingItem()
                ? player.getUseItem().getUseAnimation() : UseAnim.NONE;
        return new ReflectionFrame(flags,
                Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot),
                Mth.clamp(player.getXRot(), -90.0F, 90.0F),
                Mth.clamp(strafe, -1.0F, 1.0F),
                Mth.clamp(forward, -1.0F, 1.0F),
                useAnim.ordinal(),
                player.isUsingItem() ? player.getUsedItemHand().ordinal()
                        : InteractionHand.MAIN_HAND.ordinal());
    }

    private void applyReflectionFrame(ServerPlayer target, ReflectionFrame frame) {
        entityData.set(DATA_REFLECTION_FLAGS, frame.flags());
        entityData.set(DATA_REFLECTION_HEAD_YAW, frame.headYaw());
        entityData.set(DATA_REFLECTION_HEAD_PITCH, frame.headPitch());
        entityData.set(DATA_REFLECTION_STRAFE, frame.strafe());
        entityData.set(DATA_REFLECTION_FORWARD, frame.forward());
        entityData.set(DATA_REFLECTION_USE_ANIM, frame.useAnim());
        entityData.set(DATA_REFLECTION_HAND, frame.hand());
        if ((frame.flags() & REFLECTION_USING_ITEM) != 0
                && frame.useAnim() == UseAnim.BLOCK.ordinal()) {
            reflectionGuardTicks = RemnantPolicy.REFLECTION_GUARD_TICKS;
        }
        if ((frame.flags() & REFLECTION_SWING) != 0 && reflectionAttackCooldown <= 0
                && counterTicks() <= 0 && slipWindup <= 0 && !isWallLatched()) {
            reflectionAttackCooldown = RemnantPolicy.REFLECTION_ATTACK_COOLDOWN;
            swing(InteractionHand.MAIN_HAND, true);
            if (distanceToSqr(target) <= RemnantPolicy.REFLECTION_ATTACK_REACH_SQR
                    && hasLineOfSight(target)) {
                target.hurt(damageSources().mobAttack(this),
                        RemnantPolicy.REFLECTION_ATTACK_DAMAGE);
            }
        }
        if (counterTicks() <= 0 && slipWindup <= 0 && !isWallLatched()
                && Math.abs(frame.strafe()) > 0.12F && distanceToSqr(target) <= 64.0D) {
            getMoveControl().strafe(Math.max(0.15F, frame.forward() * 0.55F),
                    frame.strafe() * 0.8F);
        }
    }

    private void clearReflectionPose() {
        if (entityData.get(DATA_REFLECTION_FLAGS) == 0
                && entityData.get(DATA_REFLECTION_STRAFE) == 0.0F
                && entityData.get(DATA_REFLECTION_FORWARD) == 0.0F
                && reflectionDelay.isEmpty()) {
            recordedSwing = false;
            return;
        }
        entityData.set(DATA_REFLECTION_FLAGS, 0);
        entityData.set(DATA_REFLECTION_STRAFE, 0.0F);
        entityData.set(DATA_REFLECTION_FORWARD, 0.0F);
        entityData.set(DATA_REFLECTION_USE_ANIM, UseAnim.NONE.ordinal());
        reflectionDelay.clear();
        recordedSwing = false;
    }

    private record ReflectionFrame(int flags, float headYaw, float headPitch,
                                   float strafe, float forward, int useAnim, int hand) {
    }

    private void tickClientSmoke() {
        if (isInvisible() || state() == RemnantState.DORMANT
                || state() == RemnantState.OBSERVING
                || state() == RemnantState.LURE_READY) return;
        int count = state() == RemnantState.DYING ? 3 : random.nextFloat() < 0.55F ? 1 : 0;
        for (int i = 0; i < count; i++) {
            double px = getX() + (random.nextDouble() - 0.5D) * 0.9D;
            double py = getY() + 0.1D + random.nextDouble() * 2.25D;
            double pz = getZ() + (random.nextDouble() - 0.5D) * 0.9D;
            level().addParticle(random.nextFloat() < 0.18F
                            ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                    px, py, pz,
                    (random.nextDouble() - 0.5D) * 0.012D,
                    0.018D + random.nextDouble() * 0.022D,
                    (random.nextDouble() - 0.5D) * 0.012D);
        }
    }

    private void insideLureCheck() {
        if (lureAnchor == null) {
            entityData.set(DATA_INSIDE_LURE, false);
            return;
        }
        boolean inside =
                Math.abs(getX() - lureAnchor.getX()) <= 8.5D
                        && Math.abs(getZ() - lureAnchor.getZ()) <= 8.5D
                        && getY() >= lureAnchor.getY() - 2 && getY() <= lureAnchor.getY() + 10;
        entityData.set(DATA_INSIDE_LURE, inside);
        var armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            if (inside) armor.addOrUpdateTransientModifier(new AttributeModifier(
                    LURE_ARMOR_ID, LURE_ARMOR_BONUS,
                    AttributeModifier.Operation.ADD_VALUE));
            else armor.removeModifier(LURE_ARMOR_ID);
        }
    }

    private void tickHunting() {
        if (slipWindup > 0) {
            slipWindup--;
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), getY() + 1.05D, getZ(),
                        7, 0.38D, 0.72D, 0.38D, 0.035D);
                server.sendParticles(ParticleTypes.WHITE_ASH,
                        getX(), getY() + 1.1D, getZ(),
                        5, 0.32D, 0.65D, 0.32D, 0.025D);
            }
            if (slipWindup == 0 && slipTarget != null) {
                teleportTo(slipTarget.getX() + 0.5D, slipTarget.getY() + 0.05D,
                        slipTarget.getZ() + 0.5D);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.POOF,
                            getX(), getY() + 1.05D, getZ(),
                            30, 0.45D, 0.85D, 0.45D, 0.08D);
                    server.sendParticles(ParticleTypes.SMOKE,
                            getX(), getY() + 1.05D, getZ(),
                            18, 0.35D, 0.7D, 0.35D, 0.045D);
                }
                setInvisible(false);
                noPhysics = false;
                setNoGravity(false);
                setWallFacing(slipWallDirection.getOpposite());
                entityData.set(DATA_WALL_DIRECTION, slipWallDirection.get3DDataValue());
                wallLatchHealedThisUse = 0.0F;
                if (RemnantPolicy.canStartWallRecovery(
                        getHealth(), getMaxHealth(), wallLatchHealedTotal)) {
                    entityData.set(DATA_WALL_LATCH_TICKS, RemnantPolicy.WALL_LATCH_TICKS);
                } else {
                    entityData.set(DATA_WALL_LATCH_TICKS, 0);
                    slipRecovery = RemnantPolicy.WALL_SLIP_RECOVERY;
                }
                slipCooldown = RemnantPolicy.WALL_SLIP_COOLDOWN;
                playSound(ModSounds.REMNANT_WALL_SHIFT.get(), 1.15F, 0.85F);
            }
            return;
        }
        if (isWallLatched()) {
            tickWallLatch();
            return;
        }
        if (slipRecovery > 0) {
            slipRecovery--;
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (counterTicks() > 0) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && insideLure()) {
            if (distanceToSqr(target) < 5.0D && grabCooldown <= 0) beginGrab(target);
            else if (RemnantPolicy.canStartWallSlip(
                    distanceToSqr(target), slipCooldown)) startWallSlip();
        }
    }

    private boolean startWallSlip() {
        if (!(level() instanceof ServerLevel server) || lureId == null) return false;
        RemnantLureSavedData.LureRecord record = RemnantLureSavedData.get(server.getServer())
                .lure(lureId).orElse(null);
        if (record == null) return false;
        WallLatchDestination destination = wallLatchDestinations(server, record).stream()
                .max(Comparator.comparingDouble(candidate -> getTarget() == null ? 0.0D
                        : candidate.feet().distSqr(getTarget().blockPosition()))).orElse(null);
        if (destination == null) return false;
        slipTarget = destination.feet();
        slipWallDirection = destination.wallDirection();
        beginFold(RemnantPolicy.WALL_SLIP_TELEGRAPH, 1.0F, 0.8F);
        return true;
    }

    private boolean startLearnedDodge(Player attacker) {
        if (!(level() instanceof ServerLevel server) || lureId == null || !insideLure()) return false;
        RemnantLureSavedData.LureRecord record = RemnantLureSavedData.get(server.getServer())
                .lure(lureId).orElse(null);
        if (record == null) return false;
        WallLatchDestination destination = wallLatchDestinations(server, record).stream()
                .max(Comparator.comparingDouble(candidate ->
                        candidate.feet().distSqr(attacker.blockPosition())))
                .orElse(null);
        if (destination == null) return false;
        slipTarget = destination.feet();
        slipWallDirection = destination.wallDirection();
        learnedDodgeCooldown = RemnantPolicy.LEARNED_DODGE_COOLDOWN;
        slipCooldown = Math.max(slipCooldown, RemnantPolicy.WALL_SLIP_COOLDOWN);
        beginFold(RemnantPolicy.LEARNED_DODGE_FOLD_TICKS, 1.15F, 1.18F);
        clearLearnedAttack();
        return true;
    }

    private void beginFold(int windupTicks, float volume, float pitch) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF,
                    getX(), getY() + 1.05D, getZ(),
                    38, 0.5D, 0.9D, 0.5D, 0.11D);
            server.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 1.05D, getZ(),
                    28, 0.48D, 0.86D, 0.48D, 0.075D);
        }
        slipWindup = windupTicks;
        setInvisible(true);
        noPhysics = true;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        playSound(ModSounds.REMNANT_WALL_PRESSURE.get(), volume, pitch);
    }

    private List<WallLatchDestination> wallLatchDestinations(
            ServerLevel server, RemnantLureSavedData.LureRecord record) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos anchor : record.wallAnchors()) {
            candidates.addAll(RemnantPolicy.inwardSlipCandidates(anchor, record.origin()));
        }
        // Authored props can occupy an anchor's preferred lane. The bounded interior scan
        // guarantees the ability still works without ever phasing outside its own shelter.
        int interiorRadius = record.wallAnchors().stream()
                .mapToInt(anchor -> Math.max(
                        Math.abs(anchor.getX() - record.origin().getX()),
                        Math.abs(anchor.getZ() - record.origin().getZ())))
                .max().orElse(3);
        for (int radius = 1; radius <= interiorRadius; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    candidates.add(record.origin().offset(x, 1, z));
                }
            }
        }
        List<WallLatchDestination> destinations = new ArrayList<>();
        for (BlockPos candidate : candidates.stream().distinct().toList()) {
            if (!isSafeFoldDestination(server, record, candidate)) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (isLatchableWall(server, candidate, direction)) {
                    destinations.add(new WallLatchDestination(candidate, direction));
                    break;
                }
            }
        }
        return List.copyOf(destinations);
    }

    private boolean isSafeFoldDestination(ServerLevel server,
                                          RemnantLureSavedData.LureRecord record,
                                          BlockPos pos) {
        if (!server.isLoaded(pos) || !record.contains(pos)
                || !server.getBlockState(pos.below()).isFaceSturdy(
                        server, pos.below(), Direction.UP)) return false;
        for (int y = 0; y < 3; y++) {
            BlockPos clearance = pos.above(y);
            if (!server.getBlockState(clearance).getCollisionShape(
                    server, clearance).isEmpty()) return false;
        }
        return server.noCollision(getBoundingBox().move(
                pos.getX() + 0.5D - getX(), pos.getY() - getY(),
                pos.getZ() + 0.5D - getZ()));
    }

    private static boolean isLatchableWall(ServerLevel server, BlockPos feet,
                                           Direction direction) {
        for (int y = 0; y < 3; y++) {
            BlockPos wall = feet.above(y).relative(direction);
            if (!server.isLoaded(wall)
                    || server.getBlockState(wall).getCollisionShape(server, wall).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void tickWallLatch() {
        int ticks = wallLatchTicks();
        if (ticks <= 0) return;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(false);
        setWallFacing(wallLatchDirection().getOpposite());
        float heal = RemnantPolicy.wallLatchHealStep(
                getHealth(), getMaxHealth(), wallLatchHealedThisUse, wallLatchHealedTotal);
        if (heal <= 0.0F) {
            finishWallLatch();
            return;
        }
        heal(heal);
        wallLatchHealedThisUse += heal;
        wallLatchHealedTotal += heal;
        if (level() instanceof ServerLevel server && ticks % 2 == 0) {
            spawnHealingParticles(server);
        }
        entityData.set(DATA_WALL_LATCH_TICKS, ticks - 1);
        if (ticks == 1) finishWallLatch();
    }

    private void spawnHealingParticles(ServerLevel server) {
        Vec3 center = position().add(0.0D, 1.15D, 0.0D);
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.65D + random.nextDouble() * 0.45D;
            Vec3 source = new Vec3(
                    getX() + Math.cos(angle) * radius,
                    getY() + 0.15D + random.nextDouble() * 2.1D,
                    getZ() + Math.sin(angle) * radius);
            Vec3 toward = center.subtract(source).normalize().scale(0.09D);
            server.sendParticles(ParticleTypes.WHITE_ASH,
                    source.x, source.y, source.z,
                    0, toward.x, toward.y, toward.z, 1.0D);
            if (random.nextFloat() < 0.45F) {
                server.sendParticles(ParticleTypes.END_ROD,
                        source.x, source.y, source.z,
                        0, toward.x * 0.7D, toward.y * 0.7D, toward.z * 0.7D, 1.0D);
            }
        }
    }

    private void setWallFacing(Direction direction) {
        float yaw = direction.toYRot();
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    private void finishWallLatch() {
        entityData.set(DATA_WALL_LATCH_TICKS, 0);
        setNoGravity(false);
        wallLatchHealedThisUse = 0.0F;
        slipRecovery = RemnantPolicy.WALL_SLIP_RECOVERY;
    }

    public boolean interruptWallLatch() {
        if (!isWallLatched()) return false;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF,
                    getX(), getY() + 1.0D, getZ(),
                    26, 0.55D, 0.75D, 0.55D, 0.09D);
        }
        finishWallLatch();
        slipRecovery = 8;
        return true;
    }

    private record WallLatchDestination(BlockPos feet, Direction wallDirection) {
    }

    private int rememberAcceptedAttack(String signature) {
        if (signature.equals(learnedAttack) && learnWindowTicks > 0) learnedHits++;
        else {
            learnedAttack = signature;
            learnedHits = 1;
        }
        learnWindowTicks = RemnantPolicy.LEARN_WINDOW_TICKS;
        if (learnedHits == 2 && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMOKE,
                    getX(), getY() + 1.35D, getZ(),
                    12, 0.28D, 0.55D, 0.28D, 0.045D);
            playSound(ModSounds.REMNANT_LATCH.get(), 0.7F, 1.45F);
        }
        return learnedHits;
    }

    private void clearLearnedAttack() {
        learnedAttack = "";
        learnedHits = 0;
        learnWindowTicks = 0;
    }

    private void scheduleCounterfeitSwing(Player attacker, String signature) {
        if (counterCooldown > 0 || counterTicks() > 0 || state() != RemnantState.HUNTING) return;
        entityData.set(DATA_COUNTER_TICKS, RemnantPolicy.COUNTER_TOTAL_TICKS);
        counterTargetId = attacker.getId();
        counterAttack = signature;
        counterCooldown = RemnantPolicy.COUNTER_COOLDOWN;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    getX(), getY() + 1.25D, getZ(),
                    18, 0.42D, 0.65D, 0.42D, 0.05D);
        }
        playSound(ModSounds.REMNANT_WALL_PRESSURE.get(), 1.15F, 1.32F);
    }

    private void tickCounterfeitSwing() {
        int ticks = counterTicks();
        if (ticks <= 0) return;
        if (slipWindup > 0 || isWallLatched()) return;
        if (!(level() instanceof ServerLevel server)
                || !(server.getEntity(counterTargetId) instanceof Player target)
                || !target.isAlive()
                || !counterAttack.equals(meleeAttackSignature(target))) {
            clearCounterfeitSwing();
            return;
        }
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        getLookControl().setLookAt(target, 60.0F, 60.0F);
        ticks--;
        entityData.set(DATA_COUNTER_TICKS, ticks);
        if (ticks == RemnantPolicy.COUNTER_STRIKE_TICK) {
            swing(InteractionHand.MAIN_HAND, true);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    getX(), getY() + 1.0D, getZ(),
                    4, 0.35D, 0.35D, 0.35D, 0.0D);
            if (distanceToSqr(target) <= RemnantPolicy.COUNTER_REACH_SQR
                    && hasLineOfSight(target)) {
                target.hurt(damageSources().mobAttack(this), RemnantPolicy.COUNTER_DAMAGE);
                Vec3 shove = target.position().subtract(position()).normalize().scale(0.55D);
                target.push(shove.x, 0.18D, shove.z);
            }
        }
        if (ticks == 0) clearCounterfeitSwing();
    }

    private void clearCounterfeitSwing() {
        entityData.set(DATA_COUNTER_TICKS, 0);
        counterTargetId = -1;
        counterAttack = "";
    }

    private void beginGrab(LivingEntity target) {
        if (!(target instanceof ServerPlayer player)) return;
        entityData.set(DATA_GRAB_TARGET, player.getId());
        grabSwings = 0;
        swingWasActive = false;
        grabCooldown = RemnantPolicy.GRAB_COOLDOWN;
        setState(RemnantState.EXPOSED);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                RemnantPolicy.GRAB_MAX_TICKS + 10, 3, false, false));
        player.getPersistentData().putBoolean(GRAB_MARKER, true);
        playSound(ModSounds.REMNANT_GRAB.get(), 1.2F, 0.78F);
    }

    private void tickGrab() {
        if (!(level() instanceof ServerLevel server)
                || !(server.getEntity(grabTargetId()) instanceof ServerPlayer player)
                || !player.isAlive() || stateTicks() >= RemnantPolicy.GRAB_MAX_TICKS) {
            releaseGrab();
            return;
        }
        Vec3 toward = position().add(0.0D, 0.9D, 0.0D).subtract(player.position());
        if (toward.lengthSqr() > 0.36D) player.setDeltaMovement(toward.normalize().scale(0.18D));
        if (stateTicks() % 20 == 0) player.hurt(damageSources().mobAttack(this), 1.0F);
        boolean swinging = player.swinging;
        if (swinging && !swingWasActive) {
            grabSwings += isAcheronite(player) ? 2 : 1;
            if (grabSwings >= 4) releaseGrab();
        }
        swingWasActive = swinging;
    }

    private void releaseGrab() {
        clearGrabTarget();
        if (state() == RemnantState.EXPOSED) setState(RemnantState.HUNTING);
    }

    private void clearGrabTarget() {
        if (level() instanceof ServerLevel server
                && server.getEntity(grabTargetId()) instanceof ServerPlayer player) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.getPersistentData().remove(GRAB_MARKER);
        }
        entityData.set(DATA_GRAB_TARGET, -1);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FREEZING)) return false;
        interruptWallLatch();
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        Player attacker = source.getEntity() instanceof Player player ? player : null;
        boolean acheronite = attacker != null && isAcheronite(attacker);
        if (fire) {
            amount *= 1.5F;
            releaseGrab();
            clearCounterfeitSwing();
        }
        if (acheronite) amount *= 2.0F;
        else if (attacker != null && reflectionGuardTicks > 0 && !fire) {
            amount *= RemnantPolicy.REFLECTION_GUARD_MULTIPLIER;
        }
        String signature = attacker == null ? "" : attackSignature(source, attacker);
        boolean samePattern = !signature.isEmpty() && signature.equals(learnedAttack);
        if (attacker != null && state() == RemnantState.HUNTING
                && RemnantPolicy.canEvadeRepeatedAttack(fire,
                        samePattern, learnedHits, learnWindowTicks, learnedDodgeCooldown)
                && startLearnedDodge(attacker)) {
            return false;
        }
        if (insideLure() && source.is(DamageTypeTags.IS_PROJECTILE)) amount *= 0.75F;
        boolean hurt = super.hurt(source, amount);
        if (hurt && attacker != null && isAlive() && state() == RemnantState.HUNTING
                && !fire) {
            int observedHits = rememberAcceptedAttack(signature);
            if (!source.is(DamageTypeTags.IS_PROJECTILE) && observedHits >= 2) {
                scheduleCounterfeitSwing(attacker, signature);
                if (learnedDodgeCooldown <= 0) startLearnedDodge(attacker);
            }
        }
        return hurt;
    }

    private static String attackSignature(DamageSource source, Player player) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            Entity direct = source.getDirectEntity();
            ResourceLocation type = direct == null
                    ? ResourceLocation.fromNamespaceAndPath("minecraft", "unknown")
                    : BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType());
            return "projectile:" + type;
        }
        return meleeAttackSignature(player);
    }

    private static String meleeAttackSignature(Player player) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.is(ItemTags.SWORDS) || weapon.getItem() instanceof SwordItem) {
            return "melee:sword";
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(weapon.getItem());
        return "melee:" + item;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (counterTicks() > 0 || slipWindup > 0 || isWallLatched()) return false;
        return super.doHurtTarget(target);
    }

    private static boolean isAcheronite(Player player) {
        return player.getMainHandItem().is(ModItems.SOUL_HARVEST_BLADE.get())
                || player.getMainHandItem().getItem() instanceof TieredItem tiered
                && tiered.getTier() == ModToolTiers.ACHERONITE;
    }

    @Override
    public void die(DamageSource source) {
        releaseGrab();
        interruptWallLatch();
        clearCounterfeitSwing();
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setState(RemnantState.DYING);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(),
                    ModSounds.REMNANT_DEATH.get(), SoundSource.HOSTILE, 2.4F, 0.78F);
        }
        if (level() instanceof ServerLevel server && lureId != null) {
            RemnantLureManager.beginDeathPresentation(server, lureId);
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            if (deathTime == 1) {
                server.sendParticles(ParticleTypes.POOF,
                        getX(), getY() + 1.15D, getZ(),
                        34, 0.65D, 1.0D, 0.65D, 0.12D);
                server.sendParticles(ParticleTypes.WHITE_ASH,
                        getX(), getY() + 1.2D, getZ(),
                        28, 0.48D, 0.9D, 0.48D, 0.08D);
            }
            if (deathTime <= 28) {
                server.sendParticles(ParticleTypes.SMOKE,
                        getX(), getY() + 1.15D, getZ(),
                        5, 0.52D, 0.95D, 0.52D, 0.045D);
            }
            if (deathTime >= 8 && deathTime <= 34 && deathTime % 2 == 0) {
                server.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), getY() + 0.95D, getZ(),
                        4, 0.65D, 0.9D, 0.65D, 0.07D);
                server.sendParticles(ParticleTypes.WHITE_ASH,
                        getX(), getY() + 1.05D, getZ(),
                        9, 0.58D, 1.0D, 0.58D, 0.075D);
            }
            if (deathTime >= RemnantPolicy.DEATH_PRESENTATION_TICKS) {
                server.sendParticles(ParticleTypes.POOF,
                        getX(), getY() + 0.9D, getZ(),
                        46, 1.0D, 1.25D, 1.0D, 0.15D);
                server.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), getY() + 0.75D, getZ(),
                        34, 0.9D, 1.15D, 0.9D, 0.12D);
                if (lureId != null) RemnantLureManager.beginCollapse(server, lureId);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override public boolean canFreeze() { return false; }
    @Override public int getTicksFrozen() { return 0; }
    @Override
    protected SoundEvent getAmbientSound() {
        return isCombatActive() ? ModSounds.REMNANT_AMBIENT.get() : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 360;
    }

    @Override protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.REMNANT_HURT.get();
    }
    @Override protected SoundEvent getDeathSound() { return null; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (lureId != null) tag.putUUID("RemnantLure", lureId);
        if (lureAnchor != null) tag.putLong("RemnantAnchor", lureAnchor.asLong());
        tag.putInt("RemnantState", state().ordinal());
        tag.putInt("RemnantStateTicks", stateTicks());
        tag.putInt("RemnantSlipCooldown", slipCooldown);
        tag.putInt("RemnantGrabCooldown", grabCooldown);
        tag.putString("RemnantLearnedAttack", learnedAttack);
        tag.putInt("RemnantLearnedHits", learnedHits);
        tag.putInt("RemnantLearnWindow", learnWindowTicks);
        tag.putInt("RemnantDodgeCooldown", learnedDodgeCooldown);
        tag.putInt("RemnantCounterCooldown", counterCooldown);
        tag.putFloat("RemnantWallHealSpent", wallLatchHealedTotal);
        facePlayer().ifPresent(uuid -> tag.putUUID("RemnantFacePlayer", uuid));
        saveVisualItem(tag, "RemnantVisualMainhand", EquipmentSlot.MAINHAND);
        saveVisualItem(tag, "RemnantVisualOffhand", EquipmentSlot.OFFHAND);
        saveVisualItem(tag, "RemnantVisualHead", EquipmentSlot.HEAD);
        saveVisualItem(tag, "RemnantVisualChest", EquipmentSlot.CHEST);
        saveVisualItem(tag, "RemnantVisualLegs", EquipmentSlot.LEGS);
        saveVisualItem(tag, "RemnantVisualFeet", EquipmentSlot.FEET);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lureId = tag.hasUUID("RemnantLure") ? tag.getUUID("RemnantLure") : null;
        lureAnchor = tag.contains("RemnantAnchor") ? BlockPos.of(tag.getLong("RemnantAnchor")) : blockPosition();
        RemnantState saved = RemnantState.byOrdinal(tag.getInt("RemnantState"));
        if (saved == RemnantState.DYING) saved = RemnantState.COLLAPSING;
        else if (saved.isUnsafeAfterReload()) saved = RemnantState.HUNTING;
        setState(saved);
        entityData.set(DATA_STATE_TICKS, Math.max(0, tag.getInt("RemnantStateTicks")));
        slipCooldown = Math.max(0, tag.getInt("RemnantSlipCooldown"));
        grabCooldown = Math.max(0, tag.getInt("RemnantGrabCooldown"));
        learnedAttack = tag.getString("RemnantLearnedAttack");
        learnedHits = Math.max(0, tag.getInt("RemnantLearnedHits"));
        learnWindowTicks = Math.max(0, tag.getInt("RemnantLearnWindow"));
        learnedDodgeCooldown = Math.max(0, tag.getInt("RemnantDodgeCooldown"));
        counterCooldown = Math.max(0, tag.getInt("RemnantCounterCooldown"));
        wallLatchHealedTotal = Math.max(0.0F, Math.min(
                RemnantPolicy.WALL_LATCH_HEAL_BUDGET, tag.getFloat("RemnantWallHealSpent")));
        entityData.set(DATA_WALL_LATCH_TICKS, 0);
        setNoGravity(false);
        clearCounterfeitSwing();
        entityData.set(DATA_FACE_PLAYER, tag.hasUUID("RemnantFacePlayer")
                ? Optional.of(tag.getUUID("RemnantFacePlayer")) : Optional.empty());
        readVisualItem(tag, "RemnantVisualMainhand", DATA_VISUAL_MAINHAND);
        readVisualItem(tag, "RemnantVisualOffhand", DATA_VISUAL_OFFHAND);
        readVisualItem(tag, "RemnantVisualHead", DATA_VISUAL_HEAD);
        readVisualItem(tag, "RemnantVisualChest", DATA_VISUAL_CHEST);
        readVisualItem(tag, "RemnantVisualLegs", DATA_VISUAL_LEGS);
        readVisualItem(tag, "RemnantVisualFeet", DATA_VISUAL_FEET);
        clearReflectionPose();
    }

    private void saveVisualItem(CompoundTag tag, String key, EquipmentSlot slot) {
        ItemStack stack = visualItem(slot);
        if (!stack.isEmpty()) tag.put(key, stack.save(registryAccess()));
    }

    private void readVisualItem(CompoundTag tag, String key,
                                EntityDataAccessor<ItemStack> accessor) {
        entityData.set(accessor, tag.contains(key)
                ? ItemStack.parseOptional(registryAccess(), tag.getCompound(key))
                : ItemStack.EMPTY);
    }
}
