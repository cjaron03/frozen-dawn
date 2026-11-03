package com.frozendawn.entity.blind;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlindRetreatGoalTest {
    @Test void heaterAndElevatedNoiseOriginsCannotSinkEscapeBelowTheWalkableFloor() {
        var position=new Vec3(13.5,-44,5.5);
        for(double height:new double[]{-45,-43.5,-41.5,0}) {
            var goal=BlindArchitectPolicy.retreatGoal(position,new Vec3(14.5,height,5.5),new Vec3(1,0,0));
            assertEquals(-44,goal.y);assertEquals(5.5,goal.x);assertEquals(5.5,goal.z);
        }
    }
    @Test void CoincidentOriginUsesHorizontalLookFallbackAtTheSameGroundLevel() {
        var position=new Vec3(13.5,-44,5.5);
        var goal=BlindArchitectPolicy.retreatGoal(position,position,new Vec3(0,.5,.5));
        assertEquals(-44,goal.y);assertEquals(-2.5,goal.z);
    }
}
