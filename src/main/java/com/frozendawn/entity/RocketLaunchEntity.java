package com.frozendawn.entity;

import com.frozendawn.data.WinConditionState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.world.RocketLaunchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class RocketLaunchEntity extends Entity {
    public static final int STATE_IDLE = 0;
    public static final int STATE_LAUNCHING = 1;
    public static final int STATE_FINISHED = 2;

    public static final int COUNTDOWN_TICKS = 200;
    public static final int ASCENT_TICKS = 100;

    private static final EntityDataAccessor<BlockPos> DATA_PAD_CENTER =
            SynchedEntityData.defineId(RocketLaunchEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(RocketLaunchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FUEL_CELLS =
            SynchedEntityData.defineId(RocketLaunchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEQUENCE_TICKS =
            SynchedEntityData.defineId(RocketLaunchEntity.class, EntityDataSerializers.INT);

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
            if (nextTick >= COUNTDOWN_TICKS + ASCENT_TICKS) {
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
        if (level().getServer() != null) {
            WinConditionState state = WinConditionState.get(level().getServer());
            state.setLaunchInProgress(true);
            state.setLaunchSequenceStartTick(gameTime);
        }
    }

    public float getShakeAmount(float partialTick) {
        if (getLaunchState() != STATE_LAUNCHING) {
            return 0.015F;
        }
        int ticks = getSequenceTicks();
        if (ticks < 100) {
            return 0.025F;
        }
        if (ticks < COUNTDOWN_TICKS) {
            return 0.055F;
        }
        return 0.02F;
    }

    public boolean isCountdownHot() {
        int ticks = getSequenceTicks();
        return getLaunchState() == STATE_LAUNCHING && ticks >= 100 && ticks < COUNTDOWN_TICKS;
    }

    public double getScriptedYOffset(float partialTick) {
        if (getLaunchState() != STATE_LAUNCHING) {
            return 0.0D;
        }
        float ticks = getSequenceTicks() + partialTick;
        if (ticks <= COUNTDOWN_TICKS) {
            return 0.0D;
        }
        float ascentTicks = Math.min(ASCENT_TICKS, ticks - COUNTDOWN_TICKS);
        float progress = ascentTicks / ASCENT_TICKS;
        return progress * progress * 96.0D;
    }

    public void snapToScriptedPosition() {
        BlockPos padCenter = getPadCenter();
        if (padCenter == null || padCenter.equals(BlockPos.ZERO)) {
            return;
        }
        double x = padCenter.getX() + 0.5D;
        double y = padCenter.getY() + 1.0D + getScriptedYOffset(0.0F);
        double z = padCenter.getZ() + 0.5D;
        setPos(x, y, z);
        setOldPosAndRot();
    }

    public void showStatus(ServerPlayer player) {
        String p = "\u00A77[\u00A76ORSA\u00A77] ";
        player.sendSystemMessage(Component.literal(p + "\u00A7e--- Launch Vehicle ---"));
        player.sendSystemMessage(Component.literal(p + "\u00A77Fuel Cells: \u00A7f" + getFuelCells() + "/6"));
        if (isLaunching()) {
            player.sendSystemMessage(Component.literal(p + "\u00A77Status: \u00A76Launch Sequence Active"));
        } else {
            player.sendSystemMessage(Component.literal(p + "\u00A77Status: \u00A7aPad Ready"));
        }
    }
}
