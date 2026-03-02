package com.frozendawn.entity.ai;

import com.frozendawn.entity.HollowEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

public class HollowWanderGoal extends Goal {

    private final HollowEntity hollow;
    private double targetX, targetY, targetZ;
    private int wanderTicks;

    public HollowWanderGoal(HollowEntity hollow) {
        this.hollow = hollow;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (hollow.isGrabbing()) return false;
        return hollow.getRandom().nextInt(20) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (hollow.isGrabbing()) return false;
        if (wanderTicks >= 200) return false;
        double distSq = hollow.distanceToSqr(targetX, targetY, targetZ);
        return distSq > 1.0;
    }

    @Override
    public void start() {
        RandomSource random = hollow.getRandom();

        for (int attempt = 0; attempt < 10; attempt++) {
            double dx = hollow.getX() + (random.nextDouble() - 0.5) * 16.0;
            double dz = hollow.getZ() + (random.nextDouble() - 0.5) * 16.0;

            // Hover 1-2 blocks above ground
            BlockPos groundPos = hollow.level().getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(dx, 0, dz));
            double dy = groundPos.getY() + 1.0 + random.nextDouble();

            // Reject positions with high block light
            BlockPos targetPos = BlockPos.containing(dx, dy, dz);
            if (hollow.level().getBrightness(LightLayer.BLOCK, targetPos) >= 10) continue;

            targetX = dx;
            targetY = dy;
            targetZ = dz;
            wanderTicks = 0;
            hollow.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 1.0);
            return;
        }

        // All attempts rejected — just stay put
        targetX = hollow.getX();
        targetY = hollow.getY();
        targetZ = hollow.getZ();
        wanderTicks = 200; // immediately stop
    }

    @Override
    public void tick() {
        wanderTicks++;
        hollow.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 1.0);
    }
}
