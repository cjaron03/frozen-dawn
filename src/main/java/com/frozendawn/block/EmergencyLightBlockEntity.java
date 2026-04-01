package com.frozendawn.block;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EmergencyLightBlockEntity extends BlockEntity {

    private static final int ACTIVE_PHASE_MAX = 4;

    public EmergencyLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EMERGENCY_LIGHT.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int phase = ApocalypseState.get(serverLevel.getServer()).getPhase();
        int desiredStage = phase <= ACTIVE_PHASE_MAX ? computePowerStage(serverLevel.getGameTime()) : 0;
        BlockState state = getBlockState();
        if (state.getValue(EmergencyLightBlock.POWER_STAGE) != desiredStage) {
            serverLevel.setBlock(worldPosition, state.setValue(EmergencyLightBlock.POWER_STAGE, desiredStage), 3);
        }
    }

    private int computePowerStage(long gameTime) {
        long seed = mix64(worldPosition.asLong() ^ 0x6A09E667F3BCC909L);
        long localTime = gameTime + Math.floorMod(seed, 97L);

        long dropoutWindow = Math.floorDiv(localTime, 160L);
        int dropoutTick = (int) Math.floorMod(localTime, 160L);
        long dropoutSeed = mix64(seed ^ (dropoutWindow * 0xBF58476D1CE4E5B9L));
        if ((dropoutSeed & 31L) == 0L) {
            if (dropoutTick < 3) {
                return 0;
            }
            if (dropoutTick < 7) {
                return 1;
            }
            if (dropoutTick < 12) {
                return 2;
            }
        }

        long sputterWindow = Math.floorDiv(localTime, 72L);
        int sputterTick = (int) Math.floorMod(localTime, 72L);
        long sputterSeed = mix64(seed ^ (sputterWindow * 0x94D049BB133111EBL));
        if ((sputterSeed & 7L) == 0L) {
            if (sputterTick < 2) {
                return 1;
            }
            if (sputterTick < 5) {
                return 2;
            }
        }

        long dimWindow = Math.floorDiv(localTime, 48L);
        int dimTick = (int) Math.floorMod(localTime, 48L);
        long dimSeed = mix64(seed ^ (dimWindow * 0x369DEA0F31A53F85L));
        if ((dimSeed & 3L) == 0L && dimTick < 6) {
            return 2;
        }

        return 3;
    }

    private static long mix64(long value) {
        long x = value + 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
