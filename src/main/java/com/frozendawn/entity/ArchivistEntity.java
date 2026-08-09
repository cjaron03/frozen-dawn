package com.frozendawn.entity;

import com.frozendawn.data.ArchivistSavedData;
import com.frozendawn.homo.ArchivistPolicy;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.item.OrsaIdBadgeItem;
import com.frozendawn.world.ArchivistManager;
import com.frozendawn.world.MarkedPursuitManager;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** A passive Returned remnant that preserves objects without understanding them. */
public final class ArchivistEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_MARKED_DEATH_TICKS =
            SynchedEntityData.defineId(ArchivistEntity.class, EntityDataSerializers.INT);
    private static final int SCREAM_TICK = 24;
    private static final int APPLY_MARK_TICK = 100;
    private static final int COLLAPSE_TICK = 110;
    private static final double MOVE_SPEED = 1.0D;
    private final NonNullList<ItemStack> carried = NonNullList.withSize(
            ArchivistPolicy.CARRIED_CAPACITY, ItemStack.EMPTY);
    @Nullable
    private UUID siteId;
    private long regionKey;
    @Nullable
    private UUID targetItemId;
    private int targetStallTicks;
    private double lastTargetDistance = Double.MAX_VALUE;
    private int taskRefreshTicks;
    private int sortCooldown = 160;
    private int preferredSortSlot = -1;
    @Nullable
    private UUID excludedItemId;
    private int excludedItemTicks;
    private int sobCooldown = 120;
    @Nullable
    private UUID markedKillerId;
    private boolean markedApplied;
    private boolean deathCleanupDone;

    public ArchivistEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
        setCustomName(Component.literal("The Archivist"));
        setCustomNameVisible(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MARKED_DEATH_TICKS, 0);
    }

    public int getMarkedDeathTicks() {
        return entityData.get(DATA_MARKED_DEATH_TICKS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 48.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.MOVEMENT_SLOWDOWN)
                && super.canBeAffected(effect);
    }

    public void bind(long regionKey, UUID siteId) {
        this.regionKey = regionKey;
        this.siteId = siteId;
    }

    public Optional<UUID> siteId() {
        return Optional.ofNullable(siteId);
    }

    public long regionKey() {
        return regionKey;
    }

    public int carriedCount() {
        return (int) carried.stream().filter(stack -> !stack.isEmpty()).count();
    }

    public void seedInitialLoad(long seed) {
        if (carriedCount() > 0) {
            return;
        }
        int desired = 8 + Math.floorMod((int) seed, 5);
        int badgeCount = 1 + Math.floorMod((int) (seed >>> 8), 2);
        int cursor = 0;
        carried.set(cursor++, new ItemStack(ModItems.TATTERED_CLOTHING_SCRAP.get(), 2));
        carried.set(cursor++, new ItemStack(ModItems.ICE_SHARD.get(), 3));
        for (int badge = 0; badge < badgeCount && cursor < desired; badge++) {
            carried.set(cursor++, OrsaIdBadgeItem.createNamed(seed, badge));
        }
        net.minecraft.world.item.Item[] salvage = {
                net.minecraft.world.item.Items.PAPER,
                net.minecraft.world.item.Items.STRING,
                net.minecraft.world.item.Items.IRON_NUGGET,
                net.minecraft.world.item.Items.LEATHER,
                net.minecraft.world.item.Items.GLASS_BOTTLE,
                net.minecraft.world.item.Items.BONE,
                net.minecraft.world.item.Items.FLINT,
                net.minecraft.world.item.Items.COPPER_INGOT
        };
        for (int index = 0; cursor < desired; cursor++, index++) {
            carried.set(cursor, new ItemStack(
                    salvage[Math.floorMod((int) seed + index * 3, salvage.length)],
                    1 + Math.floorMod((int) (seed >>> (index & 7)), 2)));
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (getMarkedDeathTicks() > 0) {
            if (level() instanceof ServerLevel server) {
                tickMarkedDeath(server);
            }
            return;
        }
        setYHeadRot(getYRot());
        setXRot(0.0F);
        if (!(level() instanceof ServerLevel server) || siteId == null) {
            return;
        }
        if (tickCount % 16 == 0 && getRandom().nextInt(5) == 0) {
            double yaw = Math.toRadians(getYRot());
            double amberX = getX() - Math.sin(yaw) * 0.32D;
            double amberZ = getZ() + Math.cos(yaw) * 0.32D;
            server.sendParticles(ParticleTypes.SMALL_FLAME,
                    amberX, getY() + 1.45D, amberZ,
                    1, 0.03D, 0.04D, 0.03D, 0.0D);
        }
        if (tickCount % 240 == 0 && getRandom().nextInt(3) == 0) {
            playSound(ModSounds.ARCHIVIST_PACK.get(), 0.22F,
                    0.75F + getRandom().nextFloat() * 0.10F);
        }
        if (--sobCooldown <= 0) {
            playSound(ModSounds.ARCHIVIST_SOB.get(), 0.92F,
                    0.92F + getRandom().nextFloat() * 0.09F);
            sobCooldown = 260 + getRandom().nextInt(261);
        }
        if (tickCount % 40 == 0) {
            ArchivistSavedData.get(server.getServer()).updateArchivist(
                    regionKey, getUUID(), blockPosition());
        }
        if (--taskRefreshTicks > 0) {
            return;
        }
        taskRefreshTicks = 10;
        if (excludedItemTicks > 0) {
            excludedItemTicks -= 10;
            if (excludedItemTicks <= 0) {
                excludedItemId = null;
            }
        }

        if (hasCarriedItem()) {
            if (ArchivistManager.siteHasCapacity(server, siteId)) {
                returnToSite(server);
            } else {
                getNavigation().stop();
                if (--sortCooldown <= 0) {
                    ArchivistManager.rearrangeOneRelic(server, siteId, getRandom());
                    sortCooldown = 160 + getRandom().nextInt(161);
                    playSound(ModSounds.ARCHIVIST_SORT.get(), 0.55F, 0.88F);
                }
            }
            return;
        }
        if (tickTargetItem(server)) {
            return;
        }
        if (--sortCooldown <= 0) {
            ArchivistManager.SortTask task = ArchivistManager.takeRelicForSorting(
                    server, siteId, getRandom());
            if (task != null && store(task.stack())) {
                preferredSortSlot = task.destinationSlot();
                sortCooldown = 160 + getRandom().nextInt(161);
                returnToSite(server);
                return;
            }
            sortCooldown = 80;
        }
        findItemTarget(server);
        if (targetItemId == null) {
            ArchivistManager.site(server, siteId).ifPresent(site -> {
                if (distanceToSqr(site.anchor().getCenter()) > 36.0D) {
                    getNavigation().moveTo(site.anchor().getX() + 0.5D,
                            site.anchor().getY(), site.anchor().getZ() + 0.5D,
                            MOVE_SPEED);
                } else {
                    getNavigation().stop();
                }
            });
        }
    }

    private void returnToSite(ServerLevel server) {
        ArchivistSavedData.SiteRecord site = ArchivistManager.site(server, siteId)
                .orElse(null);
        if (site == null) {
            return;
        }
        Vec3 target = preferredSortSlot >= 0
                ? ArchivistPolicy.slotPosition(site.anchor(), preferredSortSlot)
                : site.anchor().getCenter();
        if (distanceToSqr(target) > 3.2D * 3.2D) {
            getNavigation().moveTo(target.x, target.y, target.z, MOVE_SPEED);
            return;
        }
        getNavigation().stop();
        int slot = firstCarriedSlot();
        if (slot < 0) {
            return;
        }
        ItemStack stack = carried.get(slot);
        boolean deposited = preferredSortSlot >= 0
                ? ArchivistManager.depositAt(server, siteId, stack, preferredSortSlot)
                : ArchivistManager.deposit(server, siteId, stack);
        if (deposited) {
            carried.set(slot, ItemStack.EMPTY);
            preferredSortSlot = -1;
            playSound(ModSounds.ARCHIVIST_SORT.get(), 0.72F,
                    0.92F + getRandom().nextFloat() * 0.12F);
        }
    }

    private boolean tickTargetItem(ServerLevel server) {
        if (targetItemId == null) {
            return false;
        }
        if (!(server.getEntity(targetItemId) instanceof ItemEntity item)
                || !item.isAlive() || item.getItem().isEmpty()) {
            clearItemTarget();
            return false;
        }
        double distance = distanceToSqr(item);
        if (distance <= 2.1D) {
            ItemStack stack = item.getItem().copy();
            if (store(stack)) {
                item.discard();
                playSound(ModSounds.ARCHIVIST_PACK.get(), 0.58F,
                        0.96F + getRandom().nextFloat() * 0.08F);
            }
            clearItemTarget();
            return true;
        }
        getNavigation().moveTo(item, MOVE_SPEED);
        if (distance >= lastTargetDistance - 0.25D) {
            targetStallTicks += 10;
        } else {
            targetStallTicks = 0;
        }
        lastTargetDistance = distance;
        if (targetStallTicks >= ArchivistPolicy.ITEM_STALL_TICKS) {
            excludedItemId = targetItemId;
            excludedItemTicks = ArchivistPolicy.ITEM_STALL_TICKS;
            clearItemTarget();
        }
        return true;
    }

    private void findItemTarget(ServerLevel server) {
        if (carriedCount() >= ArchivistPolicy.CARRIED_CAPACITY
                || siteId == null
                || !ArchivistManager.siteHasCapacity(server, siteId)) {
            return;
        }
        targetItemId = server.getEntitiesOfClass(ItemEntity.class,
                        getBoundingBox().inflate(ArchivistPolicy.COLLECTION_RADIUS),
                        item -> item.isAlive() && item.tickCount
                                >= ArchivistPolicy.ITEM_PICKUP_GRACE_TICKS
                                && !item.getItem().isEmpty()
                                && !item.getUUID().equals(excludedItemId))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr))
                .map(ItemEntity::getUUID).orElse(null);
        targetStallTicks = 0;
        lastTargetDistance = Double.MAX_VALUE;
    }

    private void clearItemTarget() {
        targetItemId = null;
        targetStallTicks = 0;
        lastTargetDistance = Double.MAX_VALUE;
    }

    private boolean store(ItemStack stack) {
        for (int i = 0; i < carried.size(); i++) {
            if (carried.get(i).isEmpty()) {
                carried.set(i, stack.copy());
                return true;
            }
        }
        return false;
    }

    private boolean hasCarriedItem() {
        return firstCarriedSlot() >= 0;
    }

    private int firstCarriedSlot() {
        for (int i = 0; i < carried.size(); i++) {
            if (!carried.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void die(DamageSource source) {
        if (getMarkedDeathTicks() <= 0) {
            ServerPlayer killer = playerKiller(source);
            if (killer != null) {
                beginMarkedDeath(killer);
                return;
            }
        } else {
            return;
        }
        completeDeath(source);
    }

    @Nullable
    private ServerPlayer playerKiller(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        LivingEntity credit = getKillCredit();
        return credit instanceof ServerPlayer player ? player : null;
    }

    private void beginMarkedDeath(ServerPlayer killer) {
        markedKillerId = killer.getUUID();
        markedApplied = false;
        entityData.set(DATA_MARKED_DEATH_TICKS, 1);
        setHealth(1.0F);
        setInvulnerable(true);
        setNoAi(true);
        setSprinting(false);
        getNavigation().stop();
        clearItemTarget();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
    }

    private void tickMarkedDeath(ServerLevel server) {
        int ticks = getMarkedDeathTicks() + 1;
        entityData.set(DATA_MARKED_DEATH_TICKS, ticks);
        setInvulnerable(true);
        setNoAi(true);
        setSprinting(false);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);

        ServerPlayer killer = markedKillerId == null ? null
                : server.getServer().getPlayerList().getPlayer(markedKillerId);
        if (killer != null && killer.level() == level()) {
            face(killer);
        }
        if (ticks < SCREAM_TICK && ticks % 3 == 0) {
            server.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 1.0D, getZ(),
                    2, 0.28D, 0.60D, 0.28D, 0.015D);
        }
        if (ticks == SCREAM_TICK) {
            playSound(ModSounds.ARCHIVIST_SCREAM.get(), 1.65F, 1.0F);
            server.sendParticles(ParticleTypes.WHITE_ASH,
                    getX(), getY() + 1.25D, getZ(),
                    28, 0.52D, 0.82D, 0.52D, 0.045D);
        }
        if (!markedApplied && ticks >= APPLY_MARK_TICK) {
            markedApplied = true;
            if (killer != null && killer.isAlive()) {
                MarkedPursuitManager.apply(killer);
                server.sendParticles(ParticleTypes.SNOWFLAKE,
                        killer.getX(), killer.getY() + 1.0D, killer.getZ(),
                        52, 0.48D, 0.78D, 0.48D, 0.07D);
            }
        }
        if (ticks >= COLLAPSE_TICK) {
            setInvulnerable(false);
            setNoAi(false);
            setHealth(0.0F);
            DamageSource source = killer != null
                    ? damageSources().playerAttack(killer)
                    : damageSources().genericKill();
            completeDeath(source);
        }
    }

    private void face(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getEyeY() - getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        setXRot(Mth.clamp(pitch, -55.0F, 55.0F));
    }

    private void completeDeath(DamageSource source) {
        if (deathCleanupDone) {
            return;
        }
        deathCleanupDone = true;
        if (level() instanceof ServerLevel server) {
            for (ItemStack stack : carried) {
                if (!stack.isEmpty()) {
                    ItemEntity item = new ItemEntity(server, getX(), getY() + 0.8D,
                            getZ(), stack.copy());
                    item.setDeltaMovement((getRandom().nextDouble() - 0.5D) * 0.25D,
                            0.18D + getRandom().nextDouble() * 0.16D,
                            (getRandom().nextDouble() - 0.5D) * 0.25D);
                    server.addFreshEntity(item);
                }
            }
            ArchivistManager.onArchivistDeath(server, this);
            playSound(ModSounds.ARCHIVIST_DEATH.get(), 1.15F, 0.92F);
        }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("archivistRegion", regionKey);
        if (siteId != null) {
            tag.putUUID("archivistSite", siteId);
        }
        tag.putInt("preferredSortSlot", preferredSortSlot);
        tag.putInt("markedDeathTicks", getMarkedDeathTicks());
        tag.putBoolean("markedApplied", markedApplied);
        tag.putBoolean("deathCleanupDone", deathCleanupDone);
        if (markedKillerId != null) {
            tag.putUUID("markedKiller", markedKillerId);
        }
        ListTag list = new ListTag();
        for (ItemStack stack : carried) {
            if (!stack.isEmpty()) {
                list.add(stack.save(registryAccess()));
            }
        }
        tag.put("carried", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        regionKey = tag.getLong("archivistRegion");
        siteId = tag.hasUUID("archivistSite") ? tag.getUUID("archivistSite") : null;
        preferredSortSlot = tag.contains("preferredSortSlot", Tag.TAG_INT)
                ? tag.getInt("preferredSortSlot") : -1;
        entityData.set(DATA_MARKED_DEATH_TICKS,
                tag.getInt("markedDeathTicks"));
        markedApplied = tag.getBoolean("markedApplied");
        deathCleanupDone = tag.getBoolean("deathCleanupDone");
        markedKillerId = tag.hasUUID("markedKiller")
                ? tag.getUUID("markedKiller") : null;
        if (getMarkedDeathTicks() > 0) {
            setHealth(Math.max(1.0F, getHealth()));
            setInvulnerable(true);
            setNoAi(true);
        }
        ListTag list = tag.getList("carried", Tag.TAG_COMPOUND);
        for (int i = 0; i < carried.size() && i < list.size(); i++) {
            carried.set(i, ItemStack.parseOptional(registryAccess(), list.getCompound(i)));
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ARCHIVIST_HURT.get();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.ARCHIVIST_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 260;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        playSound(ModSounds.ARCHIVIST_STEP.get(), 0.34F,
                0.92F + getRandom().nextFloat() * 0.08F);
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
    public boolean hurt(DamageSource source, float amount) {
        if (getMarkedDeathTicks() > 0 || source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }
}
