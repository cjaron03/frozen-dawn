package com.frozendawn.debug.architect;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectWildernessProgressTest {
    @Test void jumpingAndCollisionJitterCannotHideAStall() {
        var progress = new ArchitectWildernessProgress();
        progress.reset(new Vec3(22.5, 65, 40.3));
        for (int tick = 1; tick <= 400; tick++) {
            assertEquals(tick, progress.update(new Vec3(22.5 + Math.sin(tick) * 0.01,
                    65 + Math.abs(Math.sin(tick)) * 1.2, 40.3)));
        }
        assertEquals(0, progress.update(new Vec3(22.5, 65, 39.9)));
        assertEquals(0, ArchitectWildernessProgress.horizontalDistance(new Vec3(0, 0, 0), new Vec3(0, 1, 0)));
    }

    @Test void slowWalkingAndDownhillTravelAccumulateHorizontalProgress() {
        var progress = new ArchitectWildernessProgress();
        progress.reset(Vec3.ZERO);
        for (int tick = 1; tick <= 400; tick++)
            assertTrue(progress.update(new Vec3(0, -tick * 0.01, tick * 0.01)) < 27);
        assertEquals(5, ArchitectWildernessProgress.horizontalDistance(Vec3.ZERO, new Vec3(3, 100, 4)));
    }
}
