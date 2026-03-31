package com.frozendawn.block;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.world.AlarmLightSweepSolver;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AlarmBeaconBlockEntity extends BlockEntity {

    private static final int ACTIVE_PHASE_MAX = 4;
    private static final int RUNDOWN_TICKS = 20;
    private static final float MAX_SPIN_VELOCITY = 18.0f; // 360 degrees / 20 ticks
    private static final float SPIN_DECELERATION = MAX_SPIN_VELOCITY / RUNDOWN_TICKS;

    private float spinAngle;
    private float previousSpinAngle;
    private float spinVelocity;
    private float previousSpinVelocity;
    private float targetSpinVelocity;
    private boolean powered;
    private int rundownTicksRemaining;
    private final LongSet activeLightPositions = new LongOpenHashSet();

    public AlarmBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALARM_BEACON.get(), pos, state);
        this.powered = state.getValue(AlarmBeaconBlock.ACTIVE);
        this.targetSpinVelocity = this.powered ? MAX_SPIN_VELOCITY : 0.0f;
        this.spinVelocity = this.targetSpinVelocity;
        this.previousSpinVelocity = this.spinVelocity;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean shouldBePowered = ApocalypseState.get(serverLevel.getServer()).getPhase() <= ACTIVE_PHASE_MAX;
        BlockState state = getBlockState();
        if (state.getValue(AlarmBeaconBlock.ACTIVE) != shouldBePowered) {
            serverLevel.setBlock(worldPosition, state.setValue(AlarmBeaconBlock.ACTIVE, shouldBePowered), 3);
        }

        tickSimulation(shouldBePowered);
        updateSweepLights();
    }

    public void clientTick() {
        if (level == null) {
            return;
        }

        tickSimulation(getBlockState().getValue(AlarmBeaconBlock.ACTIVE));
    }

    private void tickSimulation(boolean shouldBePowered) {
        previousSpinAngle = spinAngle;
        previousSpinVelocity = spinVelocity;

        if (shouldBePowered != powered) {
            float poweredAngle = getPoweredAngle(0.0f);
            powered = shouldBePowered;
            if (powered) {
                spinAngle = poweredAngle;
                previousSpinAngle = poweredAngle;
                spinVelocity = MAX_SPIN_VELOCITY;
                previousSpinVelocity = MAX_SPIN_VELOCITY;
            } else if (spinVelocity > 0.01f) {
                spinAngle = poweredAngle;
                previousSpinAngle = poweredAngle;
                spinVelocity = MAX_SPIN_VELOCITY;
                previousSpinVelocity = MAX_SPIN_VELOCITY;
                rundownTicksRemaining = RUNDOWN_TICKS;
            }
        }

        targetSpinVelocity = powered ? MAX_SPIN_VELOCITY : 0.0f;
        if (powered) {
            rundownTicksRemaining = 0;
            spinVelocity = MAX_SPIN_VELOCITY;
            spinAngle = getPoweredAngle(0.0f);
        } else if (spinVelocity > 0.0f) {
            spinVelocity = Math.max(0.0f, spinVelocity - SPIN_DECELERATION);
            if (rundownTicksRemaining > 0) {
                rundownTicksRemaining--;
            }
        } else {
            rundownTicksRemaining = 0;
        }

        if (!powered) {
            spinAngle = positiveDegrees(spinAngle + spinVelocity);
        }
    }

    private void updateSweepLights() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        float strength = getSpinStrength(1.0f);
        if (strength <= 0.02f) {
            clearSweepLights();
            return;
        }

        AlarmLightSweepSolver.SweepResult sweep = AlarmLightSweepSolver.solve(serverLevel, this, 0.0f);
        Long2IntOpenHashMap desiredLights = sweep.worldLights();

        LongSet staleLights = new LongOpenHashSet(activeLightPositions);
        for (Long2IntMap.Entry entry : desiredLights.long2IntEntrySet()) {
            long posLong = entry.getLongKey();
            staleLights.remove(posLong);
            BlockPos lightPos = BlockPos.of(posLong);
            BlockState currentState = serverLevel.getBlockState(lightPos);
            if (currentState.isAir() || currentState.is(Blocks.LIGHT)) {
                int desiredLevel = entry.getIntValue();
                if (!currentState.is(Blocks.LIGHT) || currentState.getValue(LightBlock.LEVEL) != desiredLevel) {
                    serverLevel.setBlock(lightPos,
                            Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, desiredLevel), 3);
                }
            }
        }

        for (long posLong : staleLights) {
            BlockPos lightPos = BlockPos.of(posLong);
            if (serverLevel.getBlockState(lightPos).is(Blocks.LIGHT)) {
                serverLevel.removeBlock(lightPos, false);
            }
        }

        activeLightPositions.clear();
        for (Long2IntMap.Entry entry : desiredLights.long2IntEntrySet()) {
            activeLightPositions.add(entry.getLongKey());
        }
    }

    public void clearSweepLights() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (long posLong : activeLightPositions) {
            BlockPos lightPos = BlockPos.of(posLong);
            if (serverLevel.getBlockState(lightPos).is(Blocks.LIGHT)) {
                serverLevel.removeBlock(lightPos, false);
            }
        }
        activeLightPositions.clear();
    }

    public float getSpinAngle(float partialTick) {
        if (powered && level != null) {
            return getPoweredAngle(partialTick);
        }
        float current = Mth.lerp(partialTick, previousSpinAngle, spinAngle);
        float previous = previousSpinAngle;
        if (current < previous - 180.0f) {
            current += 360.0f;
        } else if (current > previous + 180.0f) {
            current -= 360.0f;
        }
        return positiveDegrees(current);
    }

    public float getSpinStrength(float partialTick) {
        if (powered) {
            return 1.0f;
        }
        return Mth.clamp(Mth.lerp(partialTick, previousSpinVelocity, spinVelocity) / MAX_SPIN_VELOCITY, 0.0f, 1.0f);
    }

    public float getBeamIntensity(float partialTick) {
        float strength = getSpinStrength(partialTick);
        return strength * strength * (3.0f - 2.0f * strength);
    }

    public float getSoundStrength(float partialTick) {
        return Mth.lerp(0.15f, 1.0f, getBeamIntensity(partialTick));
    }

    public boolean isEffectivelyRunning(float partialTick) {
        return getSpinStrength(partialTick) > 0.02f;
    }

    public float getCombinedYawDegrees(float partialTick) {
        return positiveDegrees(baseYaw(getBlockState().getValue(AlarmBeaconBlock.FACING)) + getSpinAngle(partialTick));
    }

    public Vec3 getBeamDirection(float partialTick) {
        float yawRadians = getCombinedYawRadians(partialTick);
        return new Vec3(-Mth.sin(yawRadians), 0.0, -Mth.cos(yawRadians)).normalize();
    }

    public Vec3 getHeadWorldPos() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 1.14, worldPosition.getZ() + 0.5);
    }

    public float getCombinedYawRadians(float partialTick) {
        return (float) Math.toRadians(getCombinedYawDegrees(partialTick));
    }

    private float getPoweredAngle(float partialTick) {
        if (level == null) {
            return spinAngle;
        }
        return positiveDegrees((level.getGameTime() + partialTick) * MAX_SPIN_VELOCITY);
    }

    private static float baseYaw(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    private static float positiveDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("spinAngle", spinAngle);
        tag.putFloat("spinVelocity", spinVelocity);
        tag.putFloat("targetSpinVelocity", targetSpinVelocity);
        tag.putBoolean("powered", powered);
        tag.putInt("rundownTicksRemaining", rundownTicksRemaining);
        tag.putLongArray("activeLightPositions", activeLightPositions.toLongArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        spinAngle = tag.getFloat("spinAngle");
        previousSpinAngle = spinAngle;
        spinVelocity = tag.getFloat("spinVelocity");
        previousSpinVelocity = spinVelocity;
        targetSpinVelocity = tag.contains("targetSpinVelocity") ? tag.getFloat("targetSpinVelocity") : 0.0f;
        powered = tag.contains("powered") && tag.getBoolean("powered");
        rundownTicksRemaining = tag.getInt("rundownTicksRemaining");
        activeLightPositions.clear();
        for (long posLong : tag.getLongArray("activeLightPositions")) {
            activeLightPositions.add(posLong);
        }
    }
}
