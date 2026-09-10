package com.frozendawn.entity.architect;

/** The intent at selection time, retained until the attempt ends. */
public enum BreakReason {
    STEP_UP_CEILING, STEP_DOWN_CLEARANCE, HEAD_CLEARANCE, CORRIDOR_NODE,
    IMMEDIATE_CANDIDATE, CONTACT_BREACH, SCAFFOLD, LAST_RESORT, DIG_DOWN, UNSPECIFIED
}
