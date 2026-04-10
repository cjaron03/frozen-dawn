package com.frozendawn.event;

import com.frozendawn.phase.PhaseManager;

public final class BlizzardGogglesLogic {

    private BlizzardGogglesLogic() {
    }

    public static boolean isVisionActive(int phase, float progress) {
        return phase == 5 || (PhaseManager.isPhase6Active(phase) && progress <= PhaseManager.PHASE6_MID_START);
    }
}
