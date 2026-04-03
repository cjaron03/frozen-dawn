package com.frozendawn.block;

import com.frozendawn.client.ApocalypseClientData;
import com.frozendawn.client.FlagPhysicsHelper;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
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
    private int flutterSoundCooldown = 0;

    public OrsaFlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORSA_FLAG.get(), pos, state);
    }

    public void clientTick() {
        if (level == null || !level.isClientSide()) return;
        System.arraycopy(angles, 0, prevAngles, 0, angles.length);
        impulseStrength = FlagPhysicsHelper.tickSimulation(
                angles, angularVelocities, worldPosition, level.getGameTime(), impulseStrength
        );
        if (flutterSoundCooldown > 0) {
            flutterSoundCooldown--;
        }
        maybePlayFlutterSound();
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

    private void maybePlayFlutterSound() {
        if (level == null) {
            return;
        }

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        boolean phase6Early = PhaseManager.isPhase6Early(phase, progress);
        if (PhaseManager.isVacuumActive(phase, progress)) {
            return;
        }
        if (flutterSoundCooldown > 0) {
            return;
        }

        Player player = level.getNearestPlayer(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5,
                18.0,
                false
        );
        if (player == null) {
            return;
        }

        float motionStrength = FlagPhysicsHelper.computeMotionStrength(angles, angularVelocities);
        if (motionStrength < 0.22f) {
            return;
        }

        float phaseBoost = switch (phase) {
            case 4 -> 1.20f;
            case 5 -> 2.00f;
            case 6 -> phase6Early ? 2.35f : 0.75f;
            default -> 1.0f;
        };
        float distanceFactor = 1.0f - Mth.clamp(
                (float) (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) / (18.0 * 18.0)),
                0.0f,
                1.0f
        );
        float volume = Mth.clamp(
                (0.05f + motionStrength * 0.19f) * distanceFactor * phaseBoost,
                0.04f,
                phase == 6 && phase6Early ? 0.48f
                        : phase >= 5 ? 0.42f : 0.34f
        );
        float pitchBase = phase == 6 && phase6Early
                ? 0.92f
                : phase >= 4 ? 0.96f : 1.04f;
        float pitch = pitchBase + (level.random.nextFloat() - 0.5f) * 0.18f;

        level.playLocalSound(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.9,
                worldPosition.getZ() + 0.5,
                ModSounds.FLAG_FLUTTER.get(),
                SoundSource.BLOCKS,
                volume,
                pitch,
                false
        );

        int minCooldown = switch (phase) {
            case 4 -> 6;
            case 5 -> 2;
            case 6 -> phase6Early ? 1 : 10;
            default -> 9;
        };
        int maxCooldown = switch (phase) {
            case 4 -> 14;
            case 5 -> 6;
            case 6 -> phase6Early ? 4 : 18;
            default -> 20;
        };
        float normalizedMotion = Mth.clamp(motionStrength / 1.5f, 0.0f, 1.0f);
        flutterSoundCooldown = Mth.floor(Mth.lerp(1.0f - normalizedMotion, (float) minCooldown, (float) maxCooldown))
                + level.random.nextInt(phase >= 4 ? 4 : 6);
    }
}
