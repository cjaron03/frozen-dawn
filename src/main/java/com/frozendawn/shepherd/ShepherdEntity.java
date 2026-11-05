package com.frozendawn.shepherd;

import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.miller.MillerPattern;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Her body, and only that. It can be lost at any time and {@link ShepherdWatch} rebuilds it from
 * {@link ShepherdSavedData}, so nothing she knows lives here. She cannot die: every road to death ends at Offline.
 */
public final class ShepherdEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_MOOD = SynchedEntityData.defineId(ShepherdEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_OFFLINE = SynchedEntityData.defineId(ShepherdEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TALKING = SynchedEntityData.defineId(ShepherdEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LOGO = SynchedEntityData.defineId(ShepherdEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RESTING = SynchedEntityData.defineId(ShepherdEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_BOOT = SynchedEntityData.defineId(ShepherdEntity.class, EntityDataSerializers.BYTE);
    /** What her display shows for a replay's boot: her own face, nothing, or the BIOS cursor. */
    public static final byte BOOT_NONE = 0, BOOT_DARK = 1, BOOT_CURSOR = 2;
    private static final double HOVER_BLOCKS = 2.5;
    private static final double HOVER_HEIGHT = 1;
    private static final double AIR_DRAG = 0.91;
    /** Close enough to her spot to stop thrusting, or she saws back and forth across it. */
    private static final double SETTLED_SQR = 0.25;
    private static final double ASK_BELOW_EYES = 0.3;
    private static final double ASK_SPEED = 0.7;
    private static final int WATCH_EVERY_TICKS = 5;
    private static final int NOTHING_TO_SEE_TICKS = 40;
    private static final double LOOK_FROM_BLOCKS = 1.5;

    private int rebootTicks;
    private int talkTicks;
    private int logoTicks;
    private int bootTicks;
    private int askTicks;
    private int reactTicks;
    private Mob watching;
    /** Where her follower last stopped, and since when. */
    private Vec3 stoodAt;
    private long stoodSince;
    private long nextLook;
    private ShepherdCuriosity.Target lookingAt;
    /** Client only: the mood last drawn, and how much of the blink between two moods is left. */
    private Mood shownMood;
    private int moodBeat;

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
        return spawnAt(level, Vec3.atBottomCenterOf(at), follower);
    }

    static ShepherdEntity spawnAt(ServerLevel level, Vec3 pos, UUID follower) {
        var she = ModEntities.CS1_UNIT.get().create(level);
        var at = BlockPos.containing(pos);
        she.moveTo(pos);
        var data = ShepherdSavedData.get(level.getServer());
        data.entity(she.getUUID());
        data.dimension(level.dimension());
        data.lastPos(at);
        if (follower != null) data.follower(follower);
        level.addFreshEntity(she);
        return she;
    }

    /** Where she can fit near the player: her usual spot, then around them, then on top of them, never in the rock. */
    static Vec3 roomBeside(ServerPlayer player, Vec3 preferred) {
        var size = ModEntities.CS1_UNIT.get().getDimensions();
        var level = player.level();
        if (level.noCollision(size.makeBoundingBox(preferred))) return preferred;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            var spot = player.position().add(Math.cos(angle) * HOVER_BLOCKS, HOVER_HEIGHT, Math.sin(angle) * HOVER_BLOCKS);
            if (level.noCollision(size.makeBoundingBox(spot))) return spot;
        }
        var above = player.position().add(0, HOVER_HEIGHT, 0);
        return level.noCollision(size.makeBoundingBox(above)) ? above : player.position();
    }

    static Vec3 roomBeside(ServerPlayer player) {
        return roomBeside(player, player.position().add(HOVER_BLOCKS, HOVER_HEIGHT, 0));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOOD, Mood.NEUTRAL.ordinal());
        builder.define(DATA_OFFLINE, false);
        builder.define(DATA_TALKING, false);
        builder.define(DATA_LOGO, false);
        builder.define(DATA_RESTING, false);
        builder.define(DATA_BOOT, BOOT_NONE);
    }

    @Override protected PathNavigation createNavigation(Level level) {
        var navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new CuriosityGoal());
        goalSelector.addGoal(1, new HoverGoal());
    }

    public Mood mood() {
        return Mood.values()[entityData.get(DATA_MOOD)];
    }

    /** Offline shows nothing, whatever a conversation last asked for. */
    public void setMood(Mood mood) {
        show(mood, false);
    }

    private void show(Mood mood, boolean resting) {
        if (offline()) return;
        entityData.set(DATA_MOOD, mood.ordinal());
        entityData.set(DATA_RESTING, resting);
    }

    /** A face she has settled on, which keeps its idle life. A reaction or a conversation has none. */
    public boolean resting() {
        return entityData.get(DATA_RESTING);
    }

    /** The face she goes back to: what she feels. Server side only: the score lives there. */
    public void rest() {
        show(ShepherdSavedData.get(getServer()).tier() == Tier.WARY ? Mood.RECORDING : MillerPattern.restingMood(getServer()), true);
    }

    /** Her face with someone at the hub: what the talk screen shows, whatever she feels. */
    void composed() {
        setMood(ShepherdSavedData.get(getServer()).tier() == Tier.WARY ? Mood.RECORDING : Mood.NEUTRAL);
    }

    /** Her face answers what she just saw, then goes back. A conversation keeps her face to itself. */
    public void react(Mood mood) {
        react(mood, ShepherdPolicy.REACT_TICKS);
    }

    void react(Mood mood, int ticks) {
        if (ShepherdTalk.live()) return;
        reactTicks = ticks;
        setMood(mood);
    }

    /** The hostile she has turned to, if any. */
    Mob watching() {
        return watching;
    }

    /** What she has wandered off to look at, if anything. */
    ShepherdCuriosity.Target lookingAt() {
        return lookingAt;
    }

    /** How long her follower has stayed put. */
    long stoodFor() {
        return stoodAt == null ? 0 : level().getGameTime() - stoodSince;
    }

    /** GameTest seam: fifteen real seconds is too long to wait on a gate, ten times over. */
    void stillFor(int ticks) {
        var player = follower();
        stoodAt = player == null ? null : player.position();
        stoodSince = level().getGameTime() - ticks;
    }

    private void trackFollower() {
        var player = follower();
        if (player == null) {
            stoodAt = null;
        } else if (stoodAt == null || player.position().distanceTo(stoodAt) >= ShepherdPolicy.STILL_BLOCKS) {
            stoodAt = player.position();
            stoodSince = level().getGameTime();
        }
    }

    /** What she can see for herself, and only that. */
    private Mob nearestHostile(ServerLevel level) {
        Mob nearest = null;
        for (var mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(ShepherdPolicy.WATCH_BLOCKS), mob -> mob instanceof Enemy && mob.isAlive())) {
            if (distanceTo(mob) <= ShepherdPolicy.WATCH_BLOCKS && (nearest == null || distanceTo(mob) < distanceTo(nearest)) && hasLineOfSight(mob)) nearest = mob;
        }
        return nearest;
    }

    private void watch(ServerLevel level) {
        if (offline()) {
            watching = null;
            return;
        }
        if (tickCount % WATCH_EVERY_TICKS == 0) watching = nearestHostile(level);
        if (watching != null && !watching.isAlive()) watching = null;
        if (watching == null || asking()) return;
        getLookControl().setLookAt(watching, 30, 30);
        if (reactTicks <= 1 && ShepherdSavedData.get(level.getServer()).tier() != Tier.WARY) react(Mood.ALARMED, 2);
    }

    public boolean offline() {
        return entityData.get(DATA_OFFLINE);
    }

    /** Her mouth moves on her body for this long. The server cannot measure the line: its text lives on the client. */
    public void talkFor(int ticks) {
        talkTicks = ticks;
        entityData.set(DATA_TALKING, ticks > 0);
    }

    public boolean talking() {
        return entityData.get(DATA_TALKING);
    }

    /** Her first boot: the ORSA logo and its sound, before her own face comes up. */
    public void showLogo() {
        logoTicks = ShepherdFace.LOGO_TICKS;
        entityData.set(DATA_LOGO, true);
        level().playSound(null, this, ModSounds.TERMINAL_BOOT_ORSA.get(), SoundSource.NEUTRAL, 1, 1);
    }

    public boolean logo() {
        return entityData.get(DATA_LOGO);
    }

    int logoTicks() {
        return logoTicks;
    }

    public byte bootDisplay() {
        return entityData.get(DATA_BOOT);
    }

    /** A replay's choice is up: her display goes black until it is answered. */
    void bootDark() {
        entityData.set(DATA_BOOT, BOOT_DARK);
    }

    /** A replay's BIOS: the cursor, for as long as the lines run. */
    void bootFor(int ticks) {
        bootTicks = ticks;
        entityData.set(DATA_BOOT, BOOT_CURSOR);
    }

    void bootStop() {
        bootTicks = 0;
        entityData.set(DATA_BOOT, BOOT_NONE);
    }

    /** Floats up in front of her follower and holds there thinking, while the strip asks. Zero lets her drop back. */
    public void askFor(int ticks) {
        askTicks = ticks;
    }

    public boolean asking() {
        return askTicks > 0;
    }

    public boolean moodBeat() {
        return moodBeat > 0;
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
        talkFor(0);
        logoTicks = 0;
        bootStop();
        askTicks = 0;
        reactTicks = 0;
        entityData.set(DATA_LOGO, false);
        entityData.set(DATA_MOOD, Mood.OFFLINE.ordinal());
        entityData.set(DATA_OFFLINE, true);
        rebootTicks = ShepherdPolicy.REBOOT_TICKS;
        setNoGravity(false);
        getNavigation().stop();
    }

    private void reboot() {
        entityData.set(DATA_OFFLINE, false);
        rest();
        setHealth(getMaxHealth());
        setNoGravity(true);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            if (moodBeat > 0) moodBeat--;
            if (mood() != shownMood) {
                if (shownMood != null) moodBeat = ShepherdFace.MOOD_BEAT_TICKS;
                shownMood = mood();
            }
            return;
        }
        // A /summon, or a body that loaded after its replacement was built. There is one of her.
        if (!getUUID().equals(ShepherdSavedData.get(level.getServer()).entity())) {
            discard();
            return;
        }
        if (offline() && --rebootTicks <= 0) reboot();
        if (talkTicks > 0 && --talkTicks == 0) entityData.set(DATA_TALKING, false);
        if (logoTicks > 0 && --logoTicks == 0) entityData.set(DATA_LOGO, false);
        if (bootTicks > 0 && --bootTicks == 0) {
            bootStop();
            ShepherdBoot.biosDone(this);
        }
        watch(level);
        trackFollower();
        if (askTicks > 0) askTicks--;
        if (reactTicks > 0) reactTicks--;
        if (ShepherdTalk.live() || reactTicks > 0) return;
        if (asking()) {
            setMood(Mood.THINKING);
        } else {
            rest();
        }
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
        return followerIn(level());
    }

    /** The follower, if they are in {@code level}. */
    static ServerPlayer followerIn(Level level) {
        var id = ShepherdSavedData.get(level.getServer()).follower();
        return id != null && level.getPlayerByUUID(id) instanceof ServerPlayer player ? player : null;
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer speaker) ShepherdTalk.openHub(speaker, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
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

    /**
     * When the follower has stood still and things are calm, she drifts over to something near them, stops a block short
     * and looks. Any reason to be back at their side ends it, and {@link HoverGoal} takes her there.
     */
    private final class CuriosityGoal extends Goal {
        private int ticks, looked;
        private ShepherdAmbient.Trigger saw;

        CuriosityGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        /** Why she stays at their side, whatever there is to see. */
        private boolean needed(ServerPlayer player) {
            var server = getServer();
            var data = ShepherdSavedData.get(server);
            return player == null || !data.flag(ShepherdPrompt.FIRST_MEETING_DONE) || data.waiting() || data.tier() == Tier.WARY
                    || ShepherdTalk.live() || asking() || watching != null || stoodFor() < ShepherdPolicy.STILL_TICKS
                    || !ShepherdPrompt.calm(server, (ServerLevel) level(), List.of(player));
        }

        @Override public boolean canUse() {
            long now = level().getGameTime();
            if (offline() || now < nextLook) return false;
            var player = follower();
            if (needed(player)) return false;
            lookingAt = ShepherdCuriosity.find((ServerLevel) level(), player, ShepherdEntity.this);
            if (lookingAt == null) nextLook = now + NOTHING_TO_SEE_TICKS;
            else saw = ShepherdAmbient.Trigger.looked(lookingAt.kind());
            return lookingAt != null;
        }

        @Override public boolean canContinueToUse() {
            return !offline() && looked < ShepherdPolicy.LOOK_TICKS && ticks < ShepherdPolicy.LOOK_TICKS * 2
                    && ShepherdCuriosity.stillThere((ServerLevel) level(), lookingAt) && !needed(follower());
        }

        @Override public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override public void start() {
            ticks = 0;
            looked = 0;
        }

        @Override public void tick() {
            ticks++;
            var at = lookingAt.at();
            getLookControl().setLookAt(at.x, at.y, at.z);
            var away = position().subtract(at);
            if (away.length() <= LOOK_FROM_BLOCKS + 0.5) {
                looked++;
            } else {
                var spot = at.add(away.normalize().scale(LOOK_FROM_BLOCKS));
                getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.5);
            }
        }

        @Override public void stop() {
            lookingAt = null;
            if (looked >= ShepherdPolicy.LOOK_TICKS) ShepherdReactions.murmur((ServerLevel) level(), saw);
            nextLook = level().getGameTime() + ShepherdPolicy.CURIOUS_GAP_TICKS;
        }
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

        @Override public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override public void tick() {
            var player = target();
            if (player == null) return;
            if (watching == null || asking()) getLookControl().setLookAt(player, 30, 30);
            double distance = distanceTo(player);
            var away = position().subtract(player.position()).multiply(1, 0, 1);
            var spot = asking() ? askSpot(player, away)
                    : player.position().add(away.lengthSqr() < 1e-4 ? new Vec3(HOVER_BLOCKS, 0, 0) : away.normalize().scale(HOVER_BLOCKS)).add(0, HOVER_HEIGHT, 0);
            if (distance > ShepherdPolicy.TELEPORT_BLOCKS) {
                getNavigation().stop();
                var room = roomBeside(player, spot);
                teleportTo(room.x, room.y, room.z);
            } else if (distance > HOVER_BLOCKS + 1) {
                if (--repath <= 0) {
                    repath = 10;
                    getNavigation().moveTo(spot.x, spot.y, spot.z, 1);
                }
            } else {
                getNavigation().stop();
                if (position().distanceToSqr(spot) > SETTLED_SQR) getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, asking() ? ASK_SPEED : 0.5);
            }
        }

        /** Eye level in front of them. From behind she goes round their shoulder, not through their head. */
        private Vec3 askSpot(ServerPlayer player, Vec3 away) {
            var ahead = Vec3.directionFromRotation(0, player.getYRot());
            var side = new Vec3(-ahead.z, 0, ahead.x);
            if (away.dot(side) < 0) side = side.scale(-1);
            var eyes = player.getEyePosition().subtract(0, ASK_BELOW_EYES, 0);
            return roomBeside(player, eyes.add((away.dot(ahead) < 0 ? side : ahead).scale(ShepherdPolicy.ASK_BLOCKS)));
        }
    }
}
