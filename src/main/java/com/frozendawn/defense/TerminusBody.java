package com.frozendawn.defense;

/**
 * A mob whose own death tears something down (what it built, an encounter it holds open). Stopped by the Terminus, it
 * does not run its own death, so the Terminus asks it to tear down and go, with no effects of its own.
 */
public interface TerminusBody {
    void removeStopped();
}
