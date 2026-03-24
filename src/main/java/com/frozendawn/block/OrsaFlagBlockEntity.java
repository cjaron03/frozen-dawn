package com.frozendawn.block;

import com.frozendawn.client.FlagPhysicsHelper;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the ORSA flag. Stores cloth simulation state
 * and runs client-side tick for wave animation.
 */
public class OrsaFlagBlockEntity extends BlockEntity {

    private final float[] prevAngles = new float[FlagPhysicsHelper.SEGMENTS];
    private final float[] angles = new float[FlagPhysicsHelper.SEGMENTS];
    private final float[] angularVelocities = new float[FlagPhysicsHelper.SEGMENTS];
    private float impulseStrength = 0.0f;

    public OrsaFlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORSA_FLAG.get(), pos, state);
    }

    public void clientTick() {
        if (level == null || !level.isClientSide()) return;
        System.arraycopy(angles, 0, prevAngles, 0, angles.length);
        impulseStrength = FlagPhysicsHelper.tickSimulation(
                angles, angularVelocities, worldPosition, level.getGameTime(), impulseStrength
        );
    }

    public void addImpulse(float amount) {
        impulseStrength = Math.min(1.5f, impulseStrength + amount);
    }

    public float[] getAngles() {
        return angles;
    }

    public float getRenderAngle(int index, float partialTick) {
        return Mth.lerp(partialTick, prevAngles[index], angles[index]);
    }
}
