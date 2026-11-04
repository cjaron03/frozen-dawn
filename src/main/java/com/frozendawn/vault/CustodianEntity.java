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
    private static final int TOUCH_COOLDOWN = 20;
    private static final EntityDataAccessor<Boolean> SHELL_OPEN = SynchedEntityData.defineId(CustodianEntity.class, EntityDataSerializers.BOOLEAN);
    private @Nullable BlockPos argus;
    private boolean awake;
    private float floor = 1;
    private int touched;

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
        if (argus != null && level().getBlockEntity(argus) instanceof VaultArgusBlockEntity be) be.custodianFell();
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
