package com.frozendawn.shepherd;

import com.frozendawn.init.ModEntities;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Her body, and only that. It can be lost at any time and {@link ShepherdWatch} rebuilds it from
 * {@link ShepherdSavedData}, so nothing she knows lives here. She cannot die: every road to death ends at Offline.
 */
public final class ShepherdEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_MOOD = SynchedEntityData.defineId(ShepherdEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_OFFLINE = SynchedEntityData.defineId(ShepherdEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double HOVER_BLOCKS = 2.5;
    private static final double HOVER_HEIGHT = 1;
    private static final double AIR_DRAG = 0.91;

    private int rebootTicks;

    public ShepherdEntity(EntityType<? extends ShepherdEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, ShepherdPolicy.MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    /** Builds a body and makes it the saved one. {@code follower} may be null, which keeps the one on record. */
    public static ShepherdEntity spawnFrom(ServerLevel level, BlockPos at, UUID follower) {
        var she = ModEntities.CS1_UNIT.get().create(level);
        she.moveTo(Vec3.atBottomCenterOf(at));
        var data = ShepherdSavedData.get(level.getServer());
        data.entity(she.getUUID());
        data.dimension(level.dimension());
        data.lastPos(at);
        if (follower != null) data.follower(follower);
        level.addFreshEntity(she);
        return she;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOOD, Mood.NEUTRAL.ordinal());
        builder.define(DATA_OFFLINE, false);
    }

    @Override protected PathNavigation createNavigation(Level level) {
        var navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new HoverGoal());
    }

    public Mood mood() {
        return Mood.values()[entityData.get(DATA_MOOD)];
    }

    /** Offline shows nothing, whatever a conversation last asked for. */
    public void setMood(Mood mood) {
        if (!offline()) entityData.set(DATA_MOOD, mood.ordinal());
    }

    public boolean offline() {
        return entityData.get(DATA_OFFLINE);
    }

    public int rebootTicks() {
        return rebootTicks;
    }

    /** GameTest seam: two real minutes is too long to wait on a gate. */
    void rebootIn(int ticks) {
        rebootTicks = ticks;
    }

    public void goOffline() {
        setHealth(1);
        deathTime = 0;
        if (offline()) return;
        entityData.set(DATA_MOOD, Mood.OFFLINE.ordinal());
        entityData.set(DATA_OFFLINE, true);
        rebootTicks = ShepherdPolicy.REBOOT_TICKS;
        setNoGravity(false);
        getNavigation().stop();
    }

    private void reboot() {
        entityData.set(DATA_OFFLINE, false);
        entityData.set(DATA_MOOD, Mood.NEUTRAL.ordinal());
        setHealth(getMaxHealth());
        setNoGravity(true);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        // A /summon, or a body that loaded after its replacement was built. There is one of her.
        if (!getUUID().equals(ShepherdSavedData.get(level.getServer()).entity())) {
            discard();
            return;
        }
        if (offline() && --rebootTicks <= 0) reboot();
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        return !offline() && super.hurt(source, amount);
    }

    @Override public void die(DamageSource source) {
        goOffline();
    }

    @Override protected void tickDeath() {
        goOffline();
    }

    @Override public void kill() {
        goOffline();
    }

    @Override protected void onBelowWorld() {
        if (!(level() instanceof ServerLevel level)) return;
        var player = follower();
        Vec3 to = player != null ? player.position().add(0, HOVER_HEIGHT, 0) : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
        goOffline();
    }

    /** Offline, nothing runs but the fall: no goals, and no move control to switch her gravity back off. */
    @Override protected boolean isImmobile() {
        return offline() || super.isImmobile();
    }

    @Override public void travel(Vec3 input) {
        if (offline() || !isControlledByLocalInstance()) {
            super.travel(input);
            return;
        }
        moveRelative(getSpeed(), input);
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(AIR_DRAG));
        calculateEntityAnimation(false);
    }

    private ServerPlayer follower() {
        var id = ShepherdSavedData.get(getServer()).follower();
        return id != null && level().getPlayerByUUID(id) instanceof ServerPlayer player ? player : null;
    }

    @Override public boolean canBeSeenAsEnemy() {
        return !offline() && super.canBeSeenAsEnemy();
    }

    @Override public boolean canChangeDimensions(Level from, Level to) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canFreeze() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Offline", offline());
        tag.putInt("RebootTicks", rebootTicks);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boolean offline = tag.getBoolean("Offline");
        entityData.set(DATA_OFFLINE, offline);
        if (offline) entityData.set(DATA_MOOD, Mood.OFFLINE.ordinal());
        rebootTicks = tag.getInt("RebootTicks");
    }

    /** Stays beside the follower, a block off their ground, and catches up by teleport past the line. */
    private final class HoverGoal extends Goal {
        private int repath;

        HoverGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private ServerPlayer target() {
            return ShepherdSavedData.get(getServer()).waiting() ? null : follower();
        }

        @Override public boolean canUse() {
            return target() != null;
        }

        @Override public void tick() {
            var player = target();
            if (player == null) return;
            getLookControl().setLookAt(player, 30, 30);
            double distance = distanceTo(player);
            var away = position().subtract(player.position()).multiply(1, 0, 1);
            var spot = player.position().add(away.lengthSqr() < 1e-4 ? new Vec3(HOVER_BLOCKS, 0, 0) : away.normalize().scale(HOVER_BLOCKS)).add(0, HOVER_HEIGHT, 0);
            if (distance > ShepherdPolicy.TELEPORT_BLOCKS) {
                getNavigation().stop();
                teleportTo(spot.x, spot.y, spot.z);
            } else if (distance > HOVER_BLOCKS + 1) {
                if (--repath <= 0) {
                    repath = 10;
                    getNavigation().moveTo(spot.x, spot.y, spot.z, 1);
                }
            } else {
                getNavigation().stop();
                getMoveControl().setWantedPosition(getX(), spot.y, getZ(), 0.5);
            }
        }
    }
}
