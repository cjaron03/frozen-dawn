package com.frozendawn.entity;

import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.init.ModToolTiers;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class HollowEntity extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> DATA_GRABBING =
            SynchedEntityData.defineId(HollowEntity.class, EntityDataSerializers.BOOLEAN);

    private int grabTicks = 0;
    private int grabCooldown = 0;
    private int iceBlocksPlaced = 0;

    // Direct drift movement (navigation doesn't work with noPhysics)
    private double driftTargetX, driftTargetY, driftTargetZ;
    private int driftTicks = 0;
    private int fleeingTicks = 0; // >0 = sprinting away from Frost Ward Torch, skip normal drift

    public HollowEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.MOVEMENT_SPEED, 0.15)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GRABBING, false);
    }

    @Override
    protected void registerGoals() {
        // No goals — movement is driven directly in aiStep() since noPhysics
        // breaks pathfinding navigation
    }

    // --- Grab State ---

    public boolean isGrabbing() {
        return entityData.get(DATA_GRABBING);
    }

    private void setGrabbing(boolean grabbing) {
        entityData.set(DATA_GRABBING, grabbing);
    }

    // --- AI Step ---

    @Override
    public void aiStep() {
        super.aiStep();

        // Client-side: swirling white smoke tornado
        if (level().isClientSide()) {
            double cx = getX();
            double cy = getY();
            double cz = getZ();
            float age = tickCount;

            // Two helix arms spinning in opposite directions
            for (int arm = 0; arm < 2; arm++) {
                float offset = arm * (float) Math.PI;
                // 3 particles per arm, stacked vertically
                for (int i = 0; i < 3; i++) {
                    float heightFrac = i / 3.0f; // 0.0 to ~0.66
                    float y = (float) cy + heightFrac * 1.8f;
                    // Radius narrows toward top (tornado shape)
                    float radius = 0.5f - heightFrac * 0.25f;
                    // Spin angle — faster at bottom, slower at top
                    float spin = age * (0.15f - heightFrac * 0.04f) + offset + heightFrac * 2.0f;
                    double px = cx + Math.cos(spin) * radius;
                    double pz = cz + Math.sin(spin) * radius;
                    // Slight upward drift velocity
                    level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            px, y, pz,
                            0.0, 0.01, 0.0);
                }
            }
            // A few extra wisps at the base for density
            if (tickCount % 3 == 0) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = 0.3 + random.nextDouble() * 0.3;
                level().addParticle(ParticleTypes.CLOUD,
                        cx + Math.cos(angle) * r, cy + 0.1, cz + Math.sin(angle) * r,
                        0.0, 0.02, 0.0);
            }
        }

        if (!level().isClientSide()) {
            long gameTick = level().getGameTime();

            // Grab cooldown
            if (grabCooldown > 0) grabCooldown--;

            // Grab mechanic
            if (isPassenger()) {
                grabTicks++;

                if (getVehicle() instanceof Player player) {
                    // Player swinging = break free and damage the Hollow
                    if (player.swinging) {
                        float dmg = 2.0f;
                        ItemStack weapon = player.getMainHandItem();
                        if (weapon.getItem() instanceof TieredItem tiered
                                && tiered.getTier() == ModToolTiers.ACHERONITE) {
                            dmg = 6.0f;
                        }
                        releasePlayer();
                        hurt(damageSources().playerAttack(player), dmg);
                    } else {
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, true, false));

                        // Ramp up freeze ticks — shows icy overlay and deals freeze damage
                        int freezeTicks = Math.min(player.getTicksFrozen() + 5, 300);
                        player.setTicksFrozen(freezeTicks);

                        // Frost particles around grabbed player
                        if (grabTicks % 5 == 0 && level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                                    player.getX(), player.getY() + 1.0, player.getZ(), 6,
                                    0.5, 0.8, 0.5, 0.02);
                        }

                        if (grabTicks % 10 == 0 && iceBlocksPlaced < 6) {
                            placeEntombIce(player);
                        }

                        if (iceBlocksPlaced >= 6 || grabTicks >= 80) {
                            releasePlayer();
                        }
                    }
                }
            } else {
                // --- Flee countdown (Frost Ward Torch sprint) ---
                if (fleeingTicks > 0) {
                    fleeingTicks--;
                    // Keep current momentum — don't override with drift
                } else {
                    LivingEntity directedTarget = getTarget();
                    if (directedTarget != null && directedTarget.isAlive()) {
                        driftTargetX = directedTarget.getX();
                        driftTargetY = directedTarget.getY() + 0.35D;
                        driftTargetZ = directedTarget.getZ();
                        driftTicks = Math.max(driftTicks, 12);
                    }
                    // --- Normal drift movement ---
                    driftTicks--;
                    if (driftTicks <= 0) {
                        pickNewDriftTarget();
                    }

                    double dx = driftTargetX - getX();
                    double dy = driftTargetY - getY();
                    double dz = driftTargetZ - getZ();
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (dist > 0.5) {
                        double speed = 0.03; // Slow ghostly drift
                        Vec3 vel = new Vec3(dx / dist * speed, dy / dist * speed, dz / dist * speed);
                        setDeltaMovement(vel);
                    } else {
                        setDeltaMovement(Vec3.ZERO);
                    }
                }

                // Light avoidance — override drift target
                if (gameTick % 10 == 0) {
                    // Frost Ward Torch: flee at 16-block radius (fast, like creeper from cat)
                    boolean fleeing = false;
                    BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
                    int scanRadius = 16;
                    for (int sx = -scanRadius; sx <= scanRadius && !fleeing; sx += 2) {
                        for (int sy = -4; sy <= 4 && !fleeing; sy += 2) {
                            for (int sz = -scanRadius; sz <= scanRadius && !fleeing; sz += 2) {
                                scanPos.set(blockPosition().getX() + sx, blockPosition().getY() + sy, blockPosition().getZ() + sz);
                                BlockState torchState = level().getBlockState(scanPos);
                                if (torchState.is(ModBlocks.FROST_WARD_TORCH.get()) || torchState.is(ModBlocks.FROST_WARD_WALL_TORCH.get())) {
                                    // Sprint away from torch (fast burst, like creeper from cat)
                                    double awayX = getX() - scanPos.getX();
                                    double awayZ = getZ() - scanPos.getZ();
                                    double awayDist = Math.sqrt(awayX * awayX + awayZ * awayZ);
                                    if (awayDist < 0.01) { awayX = 1; awayZ = 0; awayDist = 1; }
                                    double fleeSpeed = 0.55;
                                    setDeltaMovement(new Vec3(
                                            awayX / awayDist * fleeSpeed,
                                            0.05,
                                            awayZ / awayDist * fleeSpeed));
                                    fleeingTicks = 40; // 2 seconds of uninterrupted sprint
                                    fleeing = true;

                                    // Grant "Later Casper" advancement to nearest player
                                    if (level() instanceof ServerLevel serverLevel) {
                                        ServerPlayer nearest = (ServerPlayer) serverLevel.getNearestPlayer(
                                                getX(), getY(), getZ(), 32.0, false);
                                        if (nearest != null) {
                                            WorldTickHandler.grantAdvancement(nearest, "later_casper");
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!fleeing) {
                        int blockLight = level().getBrightness(LightLayer.BLOCK, blockPosition());
                        if (blockLight >= 10) {
                            driftTargetX = getX() + (random.nextDouble() - 0.5) * 16.0;
                            driftTargetZ = getZ() + (random.nextDouble() - 0.5) * 16.0;
                            driftTargetY = getY() + 2.0;
                            driftTicks = 100;
                        }
                    }
                }

                // Grab nearby player
                if (grabCooldown <= 0) {
                    LivingEntity directedTarget = getTarget();
                    if (directedTarget != null && directedTarget.isAlive()
                            && distanceToSqr(directedTarget) <= 2.7D * 2.7D
                            && !(directedTarget instanceof Player)) {
                        directedTarget.hurt(damageSources().mobAttack(this), 3.0F);
                        directedTarget.setTicksFrozen(
                                Math.min(300, directedTarget.getTicksFrozen() + 80));
                        grabCooldown = 40;
                        playSound(ModSounds.HOLLOW_GRAB.get(), 0.9F,
                                0.72F + random.nextFloat() * 0.18F);
                    }
                    List<Player> nearbyPlayers = level().getEntitiesOfClass(Player.class,
                            getBoundingBox().inflate(3.0), p -> !p.isSpectator() && !p.isCreative());

                    if (!nearbyPlayers.isEmpty()) {
                        startGrab(nearbyPlayers.get(0));
                    }
                }
            }

            // Heater burn
            if (gameTick % 20 == 0) {
                Set<BlockPos> heaters = HeaterRegistry.getHeaters(level());
                for (BlockPos heaterPos : heaters) {
                    if (blockPosition().closerToCenterThan(heaterPos.getCenter(), 8.0)) {
                        hurt(damageSources().onFire(), 4.0f);
                        break;
                    }
                }
            }
        }
    }

    private void pickNewDriftTarget() {
        driftTargetX = getX() + (random.nextDouble() - 0.5) * 16.0;
        driftTargetZ = getZ() + (random.nextDouble() - 0.5) * 16.0;

        // Hover just above ground (0.2-0.7 blocks)
        BlockPos ground = level().getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(driftTargetX, 0, driftTargetZ));
        driftTargetY = ground.getY() + 0.2 + random.nextDouble() * 0.5;

        driftTicks = 100 + random.nextInt(100); // 5-10 seconds per drift
    }

    private void startGrab(Player target) {
        if (startRiding(target, true)) {
            setGrabbing(true);
            grabTicks = 0;
            iceBlocksPlaced = 0;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3, true, false));
            playSound(ModSounds.HOLLOW_GRAB.get(), 1.0f, 0.8f + random.nextFloat() * 0.4f);
        }
    }

    public void setHeartScavengerTarget(LivingEntity target) {
        if (target != null && target.isAlive()) {
            setTarget(target);
            driftTargetX = target.getX();
            driftTargetY = target.getY() + 0.35D;
            driftTargetZ = target.getZ();
            driftTicks = 20;
        }
    }

    private void releasePlayer() {
        if (getVehicle() instanceof Player player) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        stopRiding();
        setGrabbing(false);
        grabCooldown = 600; // 30 second cooldown
    }

    private void placeEntombIce(Player player) {
        BlockPos playerPos = player.blockPosition();
        // Offsets around the player for ice entombment
        BlockPos[] offsets = {
                playerPos.north(), playerPos.south(),
                playerPos.east(), playerPos.west(),
                playerPos.above(), playerPos.above().above()
        };

        for (BlockPos pos : offsets) {
            if (iceBlocksPlaced >= 6) break;
            if (level().getBlockState(pos).isAir()) {
                level().setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 3);
                iceBlocksPlaced++;
                playSound(ModSounds.HOLLOW_ENTOMB.get(), 0.6f, 1.0f + random.nextFloat() * 0.2f);
                return; // One per call
            }
        }
    }

    // --- Damage Handling ---

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Projectile immunity
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return false;

        // Immune to freeze
        if (source.is(DamageTypeTags.IS_FREEZING)) return false;

        // Fire damage: full damage
        if (!source.is(DamageTypeTags.IS_FIRE)) {
            // Melee damage: check for Acheronite tier
            if (source.getEntity() instanceof Player player) {
                ItemStack weapon = player.getMainHandItem();
                if (weapon.getItem() instanceof TieredItem tiered
                        && tiered.getTier() == ModToolTiers.ACHERONITE) {
                    // Full damage from Acheronite
                } else {
                    amount *= 0.5f;
                }
            }
        }

        // Release player on any damage
        if (isPassenger()) {
            releasePlayer();
        }

        return super.hurt(source, amount);
    }

    // --- Death ---

    @Override
    protected void tickDeath() {
        remove(RemovalReason.KILLED);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 1.0, getZ(), 20,
                    0.5, 0.8, 0.5, 0.05);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() + 1.0, getZ(), 10,
                    0.3, 0.5, 0.3, 0.02);
        }
    }

    // --- Sounds ---

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.HOLLOW_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HOLLOW_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HOLLOW_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        // Low pitched — deep ghostly drone
        return 0.4f + random.nextFloat() * 0.15f;
    }

    // --- Misc ---

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }
}
