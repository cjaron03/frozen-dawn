package com.frozendawn.entity.architect;

/**
 * Server-authoritative mutable brain state for the Architect.
 * Synced entity data is derived from this state and should remain render-facing only.
 */
public final class ArchitectBrainState {

    private int currentAction;
    private int reevalCooldown;
    private int actionHoldTicks;
    private int meleeCommitTicks;
    private boolean roamingAfterTargetLoss;

    public ArchitectBrainState(int initialAction) {
        this.currentAction = initialAction;
    }

    public int getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(int currentAction) {
        this.currentAction = currentAction;
    }

    public int getReevalCooldown() {
        return reevalCooldown;
    }

    public void setReevalCooldown(int reevalCooldown) {
        this.reevalCooldown = reevalCooldown;
    }

    public int getActionHoldTicks() {
        return actionHoldTicks;
    }

    public void setActionHoldTicks(int actionHoldTicks) {
        this.actionHoldTicks = actionHoldTicks;
    }

    public int getMeleeCommitTicks() {
        return meleeCommitTicks;
    }

    public void setMeleeCommitTicks(int meleeCommitTicks) {
        this.meleeCommitTicks = meleeCommitTicks;
    }

    public boolean isRoamingAfterTargetLoss() {
        return roamingAfterTargetLoss;
    }

    public void setRoamingAfterTargetLoss(boolean roamingAfterTargetLoss) {
        this.roamingAfterTargetLoss = roamingAfterTargetLoss;
    }
}
