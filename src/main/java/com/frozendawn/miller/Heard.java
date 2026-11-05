package com.frozendawn.miller;

import com.frozendawn.shepherd.ShepherdDialogue.Slot;
import com.frozendawn.shepherd.ShepherdTalk.Action;
import java.util.List;

/** What someone said or asked of her, as {@link MillerPattern#heard} takes it. An answer is not a deed: it has no cooldown, and the node already moved the score. */
public sealed interface Heard {
    /** {@code tone} is null for a click that only continues. {@code noted} and {@code keep} come from the node it led to. */
    record Reply(Slot tone, List<String> noted, String keep) implements Heard { }

    /** A button on her hub. */
    record Used(Action action) implements Heard { }
}
