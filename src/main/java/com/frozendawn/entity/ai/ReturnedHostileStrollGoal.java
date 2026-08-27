package com.frozendawn.entity.ai;

import com.frozendawn.entity.ReturnedEntity;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;

/**
 * Keeps the original wandering behavior exclusive to ordinary hostile Returned.
 */
public final class ReturnedHostileStrollGoal extends RandomStrollGoal {
    private final ReturnedEntity returned;

    public ReturnedHostileStrollGoal(ReturnedEntity returned, double speed, int interval) {
        super(returned, speed, interval);
        this.returned = returned;
    }

    @Override
    public boolean canUse() {
        return !returned.isHearthBound() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !returned.isHearthBound() && super.canContinueToUse();
    }
}
