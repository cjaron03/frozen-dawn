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

    @Test void priorityAndMasterProtectionAreStrict() {
        var manager = new AttentionManager(); manager.resize(5, 0, NO_EVICTION);
        for (var kind : AttentionManager.Kind.values()) manager.request(key(kind, kind.ordinal()), 0, NO_EVICTION);
        var dropped = new ArrayList<AttentionManager.Key>();
        for (int i = 0; i < 4; i++) manager.request(key(AttentionManager.Kind.MASTER_ENCOUNTER, 100 + i), 100, dropped::add);
        assertEquals(List.of(AttentionManager.Kind.RECONNAISSANCE, AttentionManager.Kind.PASSIVE_TRACKING,
                AttentionManager.Kind.ACTIVE_COMMITMENT, AttentionManager.Kind.SIEGE), dropped.stream().map(AttentionManager.Key::kind).toList());
        assertFalse(manager.request(key(AttentionManager.Kind.PASSIVE_TRACKING, 200), 10000, NO_EVICTION).admitted());
        assertEquals(5, manager.slots().size());
    }

    @Test void noSilentEvictionAndNoDwellResetOnPromotion() {
        var manager = new AttentionManager(); manager.resize(2, 0, NO_EVICTION);
        var tracking = key(AttentionManager.Kind.PASSIVE_TRACKING, 1);
        var commitment = key(AttentionManager.Kind.ACTIVE_COMMITMENT, 1);
        manager.request(tracking, 0, NO_EVICTION); manager.request(key(AttentionManager.Kind.MASTER_ENCOUNTER, 2), 0, NO_EVICTION);
        assertTrue(manager.replace(tracking, commitment, 50));
        assertThrows(IllegalStateException.class, () -> manager.request(key(AttentionManager.Kind.SIEGE, 3), 100, k -> { throw new IllegalStateException(); }));
        assertTrue(manager.contains(commitment)); assertEquals(2, manager.slots().size());
        assertTrue(manager.request(key(AttentionManager.Kind.SIEGE, 3), 100, k -> assertEquals(commitment, k)).admitted());
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

    @Test void loweringTierDrainsWithoutEvictingMastersOrBypassingDwell() {
        var manager = new AttentionManager(); manager.resize(5, 0, NO_EVICTION);
        for (int i = 0; i < 3; i++) manager.request(key(AttentionManager.Kind.MASTER_ENCOUNTER, i), 0, NO_EVICTION);
        var tracker = key(AttentionManager.Kind.PASSIVE_TRACKING, 4); manager.request(tracker, 0, NO_EVICTION);
        manager.resize(2, 99, NO_EVICTION); assertEquals(4, manager.slots().size());
        manager.resize(2, 100, k -> assertEquals(tracker, k)); assertEquals(3, manager.slots().size());
        assertFalse(manager.request(key(AttentionManager.Kind.SIEGE, 5), 200, NO_EVICTION).admitted());
        manager.release(key(AttentionManager.Kind.MASTER_ENCOUNTER, 0), 200); assertEquals(2, manager.slots().size());
    }
}
