package com.frozendawn.vault;

import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * ORSA CSU-3 "Custodian": a security frame left on duty. It holds to the vault's own rule, turned on you: it moves only while
 * no mind faces it, by {@link VaultWatch}, and at a sprint. Nothing hurts it with the shell shut. Argus drives it each tick
 * and says how far an opening may take it. It is no mind itself, so it freezes no door and the wand has nothing to stop.
 * Tuned for the owner's skill: {@link #SPEED} matches a sprint (5.6 blocks a second), so two seconds at a fuse costs eleven
 * blocks, and {@link #HEALTH} needs about twelve diamond-sword blows an opening.
 */
public final class CustodianEntity extends PathfinderMob {
    public static final float HEALTH = 240, TOUCH = 12;
    public static final double SPEED = .36;
    /** Its death runs on its own count, not {@code deathTime}, which would tip it over and flush it red. */
    public static final int DEATH_TICKS = 60;
    private static final int OPEN_TICKS = 14;
    private static final int TOUCH_COOLDOWN = 20;
    private static final EntityDataAccessor<Boolean> SHELL_OPEN = SynchedEntityData.defineId(CustodianEntity.class, EntityDataSerializers.BOOLEAN);
    private @Nullable BlockPos argus;
    private boolean awake;
    private float floor = 1;
    private int touched, dying;
    private float openness, lastOpenness;

    public CustodianEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 48);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHELL_OPEN, false);
    }
    public boolean shellOpen() { return entityData.get(SHELL_OPEN); }

    /** Client side: how far the shell has opened, nought to one. */
    public float openness(float partial) { return Mth.lerp(partial, lastOpenness, openness); }
    /** How far through its death it is, nought to one. */
    public float dying(float partial) { return dying == 0 ? 0 : Math.min(1, (dying + partial) / DEATH_TICKS); }
    /** The core's light: rises with the shell and throbs; in death a flare, a stutter, a fade, then nothing. */
    public float coreLight(float partial) {
        float death = dying(partial);
        if (death == 0) return openness(partial) * (.75F + .25F * Mth.sin((tickCount + partial) * .3F));
        if (death < .25F) return 1;
        if (death < .5F) return dying / 2 % 3 == 0 ? .15F : .6F;
        return Math.max(0, .5F * (1 - (death - .5F) / .25F));
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide()) return;
        lastOpenness = openness;
        openness = Mth.clamp(openness + (shellOpen() || isDeadOrDying() ? 1F : -1F) / OPEN_TICKS, 0, 1);
        if (openness != lastOpenness && openness < .6F) breathe(ParticleTypes.CLOUD, 3, .05);
        if (dying > 0 && dying % 4 == 0) breathe(ParticleTypes.SNOWFLAKE, 2, .03);
    }
    private void breathe(ParticleOptions type, int count, double speed) {
        for (int i = 0; i < count; i++) level().addParticle(type, getRandomX(.6), getY() + 1.2 + random.nextDouble() * 1.2, getRandomZ(.6),
                random.nextGaussian() * speed, random.nextGaussian() * speed, random.nextGaussian() * speed);
    }

    /** From Argus, every tick. {@code floor} is the share of its health an opening cannot take it below. */
    public void drive(BlockPos argus, boolean awake, boolean open, float floor) {
        this.argus = argus;
        this.awake = awake;
        this.floor = floor;
        entityData.set(SHELL_OPEN, open);
    }
    /** Back on its mark, whole, for the next attempt. */
    public void standDown(Vec3 at) {
        awake = false;
        setHealth(getMaxHealth());
        entityData.set(SHELL_OPEN, false);
        moveTo(at.x, at.y, at.z, 0, 0);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
    }

    @Override protected void customServerAiStep() {
        if (touched > 0) touched--;
        if (!(level() instanceof ServerLevel level)) return;
        if (!awake || faced(level)) { hold(); return; }
        Player prey = level.getNearestPlayer(getX(), getY(), getZ(), 48, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        if (prey == null) { hold(); return; }
        getMoveControl().setWantedPosition(prey.getX(), getY(), prey.getZ(), 1);
        if (touched == 0 && getBoundingBox().inflate(.4).intersects(prey.getBoundingBox()) && prey.hurt(damageSources().mobAttack(this), TOUCH)) {
            touched = TOUCH_COOLDOWN;
            playSound(SoundEvents.ANVIL_LAND, 1, .5f);
        }
    }
    private boolean faced(ServerLevel level) {
        var box = getBoundingBox();
        return VaultWatch.watched(level, pos -> false, getEyePosition(), box.getCenter(), new Vec3(getX(), box.minY + .3, getZ()));
    }
    private void hold() {
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    /** A shut shell turns everything. An open one gives up only what this opening is worth. */
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!shellOpen()) {
            if (source.getEntity() instanceof Player) playSound(ModSounds.VAULT_FUSE.get(), .6f, .5f);
            return false;
        }
        float room = getHealth() - floor * getMaxHealth();
        return room > 0 && super.hurt(source, floor > 0 ? Math.min(amount, room) : amount);
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide()) return;
        // Volume is reach, as for the shell.
        level().playSound(null, this, ModSounds.VAULT_CUSTODIAN_DEATH.get(), SoundSource.HOSTILE, 4, 1);
        if (argus != null && level().getBlockEntity(argus) instanceof VaultArgusBlockEntity be) be.custodianFell();
    }
    @Override protected @Nullable SoundEvent getDeathSound() { return null; }
    @Override protected void tickDeath() {
        if (++dying < DEATH_TICKS || level().isClientSide() || isRemoved()) return;
        level().broadcastEntityEvent(this, EntityEvent.POOF);
        remove(RemovalReason.KILLED);
    }
    @Override public void handleEntityEvent(byte id) {
        if (id != EntityEvent.POOF) { super.handleEntityEvent(id); return; }
        breathe(ParticleTypes.CLOUD, 50, .12);
        breathe(ParticleTypes.SNOWFLAKE, 40, .2);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (argus != null) tag.putLong("Argus", argus.asLong());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Argus")) argus = BlockPos.of(tag.getLong("Argus"));
    }
}
