package com.frozendawn.entity;

import com.frozendawn.data.WinConditionState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.RocketLaunchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class RocketLaunchEntity extends Entity {
    public static final int STATE_IDLE = 0;
    public static final int STATE_LAUNCHING = 1;
    public static final int STATE_FINISHED = 2;

    public static final int COUNTDOWN_TICKS = 200;
    public static final int LIFTOFF_TRACK_TICKS = 50;
    public static final int ASCENT_TICKS = 260;
    public static final int ATMOSPHERE_EXIT_TICKS = 120;
    public static final int FADE_TICKS = 70;

    private static final EntityDataAccessor<BlockPos> DATA_PAD_CENTER =
            SynchedEntityData.defineId(RocketLaunchEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(RocketLaunchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FUEL_CELLS =
            SynchedEntityData.defineId(RocketLaunchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEQUENCE_TICKS =
            SynchedEntityData.defineId(RocketLaunchEntity.class, EntityDataSerializers.INT);

    private UUID pendingLaunchPlayerId;
    private long pendingLaunchConfirmUntil;

    public RocketLaunchEntity(EntityType<RocketLaunchEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.blocksBuilding = true;
    }

    public RocketLaunchEntity(Level level, BlockPos padCenter) {
        this(ModEntities.ROCKET_LAUNCH.get(), level);
        setPadCenter(padCenter);
        snapToScriptedPosition();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PAD_CENTER, BlockPos.ZERO);
        builder.define(DATA_STATE, STATE_IDLE);
        builder.define(DATA_FUEL_CELLS, 0);
        builder.define(DATA_SEQUENCE_TICKS, 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        BlockPos padCenter = getPadCenter();
        tag.putInt("padCenterX", padCenter.getX());
        tag.putInt("padCenterY", padCenter.getY());
        tag.putInt("padCenterZ", padCenter.getZ());
        tag.putInt("state", getLaunchState());
        tag.putInt("fuelCells", getFuelCells());
        tag.putInt("sequenceTicks", getSequenceTicks());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setPadCenter(new BlockPos(tag.getInt("padCenterX"), tag.getInt("padCenterY"), tag.getInt("padCenterZ")));
        setLaunchState(tag.getInt("state"));
        setFuelCells(tag.getInt("fuelCells"));
        setSequenceTicks(tag.getInt("sequenceTicks"));
        clearLaunchConfirmation();
        snapToScriptedPosition();
    }

    @Override
    public void tick() {
        super.tick();
        snapToScriptedPosition();

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (getLaunchState() == STATE_LAUNCHING) {
            int nextTick = getSequenceTicks() + 1;
            setSequenceTicks(nextTick);
            if (nextTick == 1) {
                serverLevel.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.7F);
            } else if (nextTick == COUNTDOWN_TICKS) {
                serverLevel.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 2.0F, 0.55F);
            }
            RocketLaunchManager.spawnLaunchParticles(serverLevel, this);
            if (nextTick >= getTotalSequenceTicks()) {
                RocketLaunchManager.finishLaunch(serverLevel, this);
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = serverPlayer.getItemInHand(hand);
        return RocketLaunchManager.handleRocketInteraction(serverPlayer, this, stack, hand);
    }

    @Override
    public boolean shouldBeSaved() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return isIdle() && getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        double x = getX();
        double y = getY() + 3.72D;
        double z = getZ() - 0.92D;
        return new Vec3(x, y, z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos padCenter = getPadCenter();
        if (padCenter == null || padCenter.equals(BlockPos.ZERO)) {
            return super.getDismountLocationForPassenger(passenger);
        }
        return Vec3.atCenterOf(padCenter.relative(Direction.SOUTH, 2)).add(0.0D, 0.1D, 0.0D);
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    public BlockPos getPadCenter() {
        return entityData.get(DATA_PAD_CENTER);
    }

    public void setPadCenter(BlockPos padCenter) {
        entityData.set(DATA_PAD_CENTER, padCenter);
    }

    public int getLaunchState() {
        return entityData.get(DATA_STATE);
    }

    public void setLaunchState(int state) {
        entityData.set(DATA_STATE, state);
    }

    public int getFuelCells() {
        return entityData.get(DATA_FUEL_CELLS);
    }

    public void setFuelCells(int fuelCells) {
        entityData.set(DATA_FUEL_CELLS, Math.max(0, Math.min(6, fuelCells)));
        if (!level().isClientSide() && level().getServer() != null) {
            WinConditionState.get(level().getServer()).setRocketFuelCellsLoaded(getFuelCells());
        }
    }

    public int getSequenceTicks() {
        return entityData.get(DATA_SEQUENCE_TICKS);
    }

    public void setSequenceTicks(int ticks) {
        entityData.set(DATA_SEQUENCE_TICKS, ticks);
    }

    public boolean isIdle() {
        return getLaunchState() == STATE_IDLE;
    }

    public boolean isLaunching() {
        return getLaunchState() == STATE_LAUNCHING;
    }

    public void beginLaunch(long gameTime) {
        setLaunchState(STATE_LAUNCHING);
        setSequenceTicks(0);
        clearLaunchConfirmation();
        if (level().getServer() != null) {
            WinConditionState state = WinConditionState.get(level().getServer());
            state.setLaunchInProgress(true);
            state.setLaunchSequenceStartTick(gameTime);
        }
    }

    public float getShakeAmount(float partialTick) {
        if (getLaunchState() != STATE_LAUNCHING) {
            return 0.0F;
        }
        int ticks = getSequenceTicks();
        if (ticks < 100) {
            return 0.012F;
        }
        if (ticks < COUNTDOWN_TICKS) {
            return 0.036F;
        }
        return 0.02F;
    }

    public boolean isCountdownHot() {
        int ticks = getSequenceTicks();
        return getLaunchState() == STATE_LAUNCHING && ticks >= 100 && ticks < COUNTDOWN_TICKS;
    }

    public static int getTotalSequenceTicks() {
        return COUNTDOWN_TICKS + LIFTOFF_TRACK_TICKS + ASCENT_TICKS + ATMOSPHERE_EXIT_TICKS + FADE_TICKS;
    }

    public double getScriptedYOffset(float partialTick) {
        if (getLaunchState() != STATE_LAUNCHING) {
            return 0.0D;
        }
        float ticks = getSequenceTicks() + partialTick;
        if (ticks <= COUNTDOWN_TICKS) {
            return 0.0D;
        }

        float postCountdown = ticks - COUNTDOWN_TICKS;
        if (postCountdown <= LIFTOFF_TRACK_TICKS) {
            float progress = postCountdown / LIFTOFF_TRACK_TICKS;
            return Mth.square(progress) * 12.0D;
        }

        postCountdown -= LIFTOFF_TRACK_TICKS;
        if (postCountdown <= ASCENT_TICKS) {
            float progress = postCountdown / ASCENT_TICKS;
            float eased = 1.0F - (float) Math.pow(1.0F - progress, 2.4F);
            return 12.0D + eased * 118.0D;
        }

        postCountdown -= ASCENT_TICKS;
        float exitProgress = Math.min(1.0F, postCountdown / ATMOSPHERE_EXIT_TICKS);
        float easedExit = Mth.sqrt(exitProgress);
        return 130.0D + easedExit * 94.0D;
    }

    public void snapToScriptedPosition() {
        BlockPos padCenter = getPadCenter();
        if (padCenter == null || padCenter.equals(BlockPos.ZERO)) {
            return;
        }
        double x = padCenter.getX() + 0.5D;
        double y = padCenter.getY() + 0.22D + getScriptedYOffset(0.0F);
        double z = padCenter.getZ() + 0.5D;
        setPos(x, y, z);
        setOldPosAndRot();
    }

    public boolean isLaunchConfirmationArmed(ServerPlayer player, long gameTime) {
        return pendingLaunchPlayerId != null
                && pendingLaunchPlayerId.equals(player.getUUID())
                && gameTime <= pendingLaunchConfirmUntil;
    }

    public void armLaunchConfirmation(ServerPlayer player, long confirmUntil) {
        pendingLaunchPlayerId = player.getUUID();
        pendingLaunchConfirmUntil = confirmUntil;
    }

    public void clearLaunchConfirmation() {
        pendingLaunchPlayerId = null;
        pendingLaunchConfirmUntil = 0L;
    }

    public void showStatus(ServerPlayer player) {
        String p = "\u00A77[\u00A76ORSA\u00A77] ";
        player.sendSystemMessage(Component.literal(p + "\u00A7e--- Launch Vehicle ---"));
        player.sendSystemMessage(Component.literal(p + "\u00A77Fuel Cells: \u00A7f" + getFuelCells() + "/6"));
        if (isLaunching()) {
            player.sendSystemMessage(Component.literal(p + "\u00A77Status: \u00A76Launch Sequence Active"));
        } else if (!getPassengers().isEmpty()) {
            player.sendSystemMessage(Component.literal(p + "\u00A77Status: \u00A7aCrew Aboard"));
        } else {
            player.sendSystemMessage(Component.literal(p + "\u00A77Status: \u00A7aPad Ready"));
        }
    }
}
