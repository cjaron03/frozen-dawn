package com.frozendawn.event;

public final class IceClawsLogic {

    static final double BASE_PLAYER_MOVEMENT_SPEED = 0.1D;
    private static final double BASE_CLIMB_VELOCITY = 0.2D;
    private static final double MIN_CLIMB_FACTOR = 0.35D;

    private IceClawsLogic() {
    }

    public static double getClimbVelocity(double movementSpeed) {
        double factor = Math.max(MIN_CLIMB_FACTOR, Math.min(1.0D, movementSpeed / BASE_PLAYER_MOVEMENT_SPEED));
        return BASE_CLIMB_VELOCITY * factor;
    }
}
