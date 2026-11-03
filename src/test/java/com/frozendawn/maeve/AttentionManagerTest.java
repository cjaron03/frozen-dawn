package com.frozendawn.maeve;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttentionManagerTest {
    private static AttentionManager.Key key(AttentionManager.Kind kind, int id) { return new AttentionManager.Key(kind, new UUID(0, id)); }
    private static final java.util.function.Consumer<AttentionManager.Key> NO_EVICTION = k -> fail("Unexpected eviction: " + k);

    @Test void tiersAndSharedSlotsAreBounded() {
        assertEquals(2, AttentionManager.capacity("cinematic")); assertEquals(3, AttentionManager.capacity("default"));
        assertEquals(5, AttentionManager.capacity("brutal")); assertEquals(3, AttentionManager.capacity("custom"));
        var manager = new AttentionManager(); manager.resize(2, 0, NO_EVICTION);
        var one = key(AttentionManager.Kind.PASSIVE_TRACKING, 1);
        assertTrue(manager.request(one, 0, NO_EVICTION).admitted());
        assertTrue(manager.request(one, 50, NO_EVICTION).admitted());
        assertEquals(1, manager.slots().size()); assertEquals(0, manager.slots().getFirst().admittedAt());
        manager.request(key(AttentionManager.Kind.PASSIVE_TRACKING, 2), 0, NO_EVICTION);
        assertFalse(manager.request(key(AttentionManager.Kind.SIEGE, 3), 99, NO_EVICTION).admitted());
        var dropped = new ArrayList<AttentionManager.Key>();
        assertTrue(manager.request(key(AttentionManager.Kind.SIEGE, 3), 100, dropped::add).admitted());
        assertEquals(List.of(one), dropped); assertEquals(2, manager.slots().size());
    }

    @Test void evictionPriorityIsStrict() {
        var manager = new AttentionManager(); manager.resize(4, 0, NO_EVICTION);
        for (var kind : AttentionManager.Kind.values()) manager.request(key(kind, kind.ordinal()), 0, NO_EVICTION);
        var dropped = new ArrayList<AttentionManager.Key>();
        for (int i = 0; i < 4; i++) manager.request(key(AttentionManager.Kind.SIEGE, 100 + i), 100, dropped::add);
        assertEquals(List.of(AttentionManager.Kind.RECONNAISSANCE, AttentionManager.Kind.PASSIVE_TRACKING,
                AttentionManager.Kind.ACTIVE_COMMITMENT, AttentionManager.Kind.SIEGE), dropped.stream().map(AttentionManager.Key::kind).toList());
        assertEquals(4, manager.slots().size());
    }

    @Test void noSilentEvictionAndNoDwellResetOnPromotion() {
        var manager = new AttentionManager(); manager.resize(2, 0, NO_EVICTION);
        var tracking = key(AttentionManager.Kind.PASSIVE_TRACKING, 1);
        var commitment = key(AttentionManager.Kind.ACTIVE_COMMITMENT, 1);
        manager.request(tracking, 0, NO_EVICTION); manager.request(key(AttentionManager.Kind.SIEGE, 2), 0, NO_EVICTION);
        assertTrue(manager.replace(tracking, commitment, 50));
        assertThrows(IllegalStateException.class, () -> manager.request(key(AttentionManager.Kind.SIEGE, 3), 100, k -> { throw new IllegalStateException(); }));
        assertTrue(manager.contains(commitment)); assertEquals(2, manager.slots().size());
        assertTrue(manager.request(key(AttentionManager.Kind.SIEGE, 3), 100, k -> assertEquals(commitment, k)).admitted());
    }

    @Test void newcomersCannotDisplaceHigherPriorityWork() {
        for (var focused : AttentionManager.Kind.values()) for (var incoming : AttentionManager.Kind.values()) {
            var manager = new AttentionManager(); manager.resize(2, 0, NO_EVICTION);
            var oldest = key(focused, 1); var other = key(focused, 2); var next = key(incoming, 3);
            manager.request(oldest, 0, NO_EVICTION); manager.request(other, 1, NO_EVICTION);
            var dropped = new ArrayList<AttentionManager.Key>();
            var result = manager.request(next, 200, dropped::add);
            if (incoming.ordinal() < focused.ordinal()) {
                assertFalse(result.admitted()); assertEquals("HIGHER_PRIORITY_FOCUSED", result.reason());
                assertTrue(dropped.isEmpty()); assertEquals(List.of(oldest, other), manager.slots().stream().map(AttentionManager.Slot::key).toList());
                manager.release(oldest, 201);
                assertTrue(manager.request(next, 202, NO_EVICTION).admitted(), "Deferred work can use newly freed capacity");
            } else {
                assertTrue(result.admitted()); assertEquals(List.of(oldest), dropped);
            }
            assertEquals(2, manager.slots().size());
        }
    }

    @Test void aFreshLowPrioritySlotDoesNotAllowEvictingMatureHigherPriorityWork() {
        var manager = new AttentionManager(); manager.resize(2, 0, NO_EVICTION);
        var held = key(AttentionManager.Kind.ACTIVE_COMMITMENT, 1);
        var fresh = key(AttentionManager.Kind.RECONNAISSANCE, 2);
        var watching = key(AttentionManager.Kind.PASSIVE_TRACKING, 3);
        manager.request(held, 0, NO_EVICTION); manager.request(fresh, 100, NO_EVICTION);
        assertFalse(manager.request(watching, 150, NO_EVICTION).admitted());
        assertTrue(manager.request(watching, 200, victim -> assertEquals(fresh, victim)).admitted());
        assertTrue(manager.contains(held));
    }

    @Test void completedWorkFreesImmediatelyAndHistoryIsBoundedAndClearable() {
        var manager = new AttentionManager();
        for (int i = 0; i < 100; i++) {
            var key = key(AttentionManager.Kind.PASSIVE_TRACKING, i);
            manager.request(key, i, NO_EVICTION); manager.release(key, i);
        }
        assertTrue(manager.slots().isEmpty()); assertEquals(16, manager.events().size());
        manager.clear(); assertTrue(manager.events().isEmpty());
    }

    @Test void loweringTierDrainsWithoutBypassingDwell() {
        var manager = new AttentionManager(); manager.resize(5, 0, NO_EVICTION);
        for (int i = 0; i < 3; i++) manager.request(key(AttentionManager.Kind.SIEGE, i), 0, NO_EVICTION);
        var tracker = key(AttentionManager.Kind.PASSIVE_TRACKING, 4); manager.request(tracker, 0, NO_EVICTION);
        manager.resize(2, 99, NO_EVICTION); assertEquals(4, manager.slots().size());
        var dropped = new ArrayList<AttentionManager.Key>();
        manager.resize(2, 100, dropped::add);
        assertEquals(List.of(tracker, key(AttentionManager.Kind.SIEGE, 0)), dropped);
        assertEquals(2, manager.slots().size());
    }

    @Test void aTrackerReassignedToSurveyGetsItsOwnMinimumDwell() {
        var manager = new AttentionManager(); manager.resize(2, 0, NO_EVICTION);
        var tracking = key(AttentionManager.Kind.PASSIVE_TRACKING, 1);
        var mission = key(AttentionManager.Kind.RECONNAISSANCE, 1);
        manager.request(tracking, 0, NO_EVICTION);
        manager.request(key(AttentionManager.Kind.SIEGE, 2), 0, NO_EVICTION);
        assertTrue(manager.replace(tracking, mission, 200));
        assertFalse(manager.request(key(AttentionManager.Kind.PASSIVE_TRACKING, 3), 299, NO_EVICTION).admitted());
        assertTrue(manager.request(key(AttentionManager.Kind.PASSIVE_TRACKING, 3), 300, victim -> assertEquals(mission, victim)).admitted());
    }
}
