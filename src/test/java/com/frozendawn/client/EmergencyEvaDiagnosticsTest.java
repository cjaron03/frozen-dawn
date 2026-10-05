package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EmergencyEvaDiagnosticsTest {
    @Test
    void busyDialogueDoesNotConsumeWarningAndCriticalSupersedesOldBands() {
        var diagnostics = fresh();
        assertEquals(EmergencyEvaDiagnostics.Message.SERVICE, diagnostics.pending(5000, true, false));
        // The shared ORSA channel was busy, so no acknowledgement occurred.
        assertEquals(EmergencyEvaDiagnostics.Message.SERVICE, diagnostics.pending(4900, true, false));
        assertEquals(EmergencyEvaDiagnostics.Message.CRITICAL, diagnostics.pending(1000, true, false));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.CRITICAL);
        advance(diagnostics, true, 700);
        assertEquals(EmergencyEvaDiagnostics.Message.SERVICE_CRITICAL, diagnostics.pending(800, true, false));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.SERVICE_CRITICAL);
        advance(diagnostics, true, 700);
        assertNull(diagnostics.pending(700, true, false));
        assertNull(diagnostics.pending(0, true, true));
    }

    @Test
    void rejoiningPartlySpentIssueSkipsHistoricalMessagesButKeepsFutureWarnings() {
        var diagnostics = new EmergencyEvaDiagnostics();
        diagnostics.reset(2000);
        advance(diagnostics, true, 300);
        assertNull(diagnostics.pending(1800, true, false));
        assertEquals(EmergencyEvaDiagnostics.Message.CRITICAL, diagnostics.pending(1100, true, false));
        diagnostics.reset(700);
        advance(diagnostics, true, 300);
        assertNull(diagnostics.pending(650, true, false));
        diagnostics.reset(12000);
        advance(diagnostics, true, 300);
        assertEquals(EmergencyEvaDiagnostics.Message.SERVICE, diagnostics.pending(6000, true, false));
    }

    @Test
    void sealWarningsFollowCurrentSealAndRepeatedOpeningsRespectCooldown() {
        var diagnostics = fresh();
        assertEquals(EmergencyEvaDiagnostics.Message.SEAL_OPEN, diagnostics.pending(9000, false, false));
        // Closing the seal before the message displays removes that warning.
        diagnostics.tick(true);
        assertNull(diagnostics.pending(8999, true, false));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.SEAL_OPEN);
        advance(diagnostics, false, 699);
        assertNull(diagnostics.pending(8000, false, false));
        diagnostics.tick(false);
        assertNull(diagnostics.pending(8000, false, false));
        diagnostics.tick(true);
        assertEquals(EmergencyEvaDiagnostics.Message.SEAL_OPEN, diagnostics.pending(7999, false, false));
    }

    @Test
    void moistureRequiresVisibleConditionAndCannotFloodOrDelayReserveWarning() {
        var diagnostics = fresh();
        assertNull(diagnostics.pending(9000, true, false));
        assertEquals(EmergencyEvaDiagnostics.Message.MOISTURE, diagnostics.pending(9000, true, true));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.MOISTURE);
        advance(diagnostics, true, 699);
        assertNull(diagnostics.pending(2000, true, true));
        diagnostics.tick(true);
        assertEquals(EmergencyEvaDiagnostics.Message.LOW, diagnostics.pending(1999, true, true));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.LOW);
        advance(diagnostics, true, 700);
        assertNull(diagnostics.pending(1900, true, true));
    }

    @Test
    void exertionMessageFollowsCurrentLoadAndDoesNotRepeatAfterRecovery() {
        var diagnostics = fresh();
        assertEquals(EmergencyEvaDiagnostics.Message.EXERTION, diagnostics.pending(9000, true, false, true));
        // Recover before a busy dialogue clears: the stale exertion message disappears.
        assertNull(diagnostics.pending(8900, true, false, false));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.EXERTION);
        advance(diagnostics, true, 700);
        assertNull(diagnostics.pending(8000, true, false, true));
        assertEquals(EmergencyEvaDiagnostics.Message.CRITICAL, diagnostics.pending(1000, true, true, true));
    }

    @Test
    void newHeatBypassesMaintenanceGapAndCanRewarnOnlyAfterRealRecovery() {
        var diagnostics = new EmergencyEvaDiagnostics();
        diagnostics.reset(12000, 0);
        advance(diagnostics, true, 300);
        assertEquals(EmergencyEvaDiagnostics.Message.COOLING,
                diagnostics.pending(7000, true, false, false, true, false));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.COOLING);
        assertEquals(EmergencyEvaDiagnostics.Message.THERMAL,
                diagnostics.pending(6900, true, true, true, true, true));
        // Recover before a busy display accepts the warning: stale heat is dropped.
        assertNull(diagnostics.pending(6800, true, false, false, true, false));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.THERMAL);
        for (int i = 0; i < 700; i++) diagnostics.tick(true, 600);
        assertNull(diagnostics.pending(6700, true, false, false, true, true));
        diagnostics.tick(true, 250);
        assertEquals(EmergencyEvaDiagnostics.Message.THERMAL,
                diagnostics.pending(6600, true, false, false, true, true));
    }

    @Test
    void resumedCoolingWarningIsSkippedButCurrentHeatAndReserveRemainLegible() {
        var diagnostics = new EmergencyEvaDiagnostics();
        diagnostics.reset(5000, 7200);
        advance(diagnostics, true, 300);
        assertNull(diagnostics.pending(4900, true, false, false, true, false));
        assertEquals(EmergencyEvaDiagnostics.Message.THERMAL,
                diagnostics.pending(4800, true, false, false, true, true));
        assertEquals(EmergencyEvaDiagnostics.Message.CRITICAL,
                diagnostics.pending(1000, true, false, false, true, true));
        assertNull(diagnostics.pending(0, true, true, true, true, true));
    }

    @Test
    void ambientAirSuppressesOxygenBandsWhilePowerKeepsItsOwnWarnings() {
        var diagnostics = new EmergencyEvaDiagnostics();
        diagnostics.reset(12000, 0, 12000);
        advance(diagnostics, true, 300);
        assertNull(diagnostics.pending(1000, true, false, false, false, false, 11000, true));
        assertEquals(EmergencyEvaDiagnostics.Message.CRITICAL,
                diagnostics.pending(1000, true, false, false, false, false, 11000, false));
        assertEquals(EmergencyEvaDiagnostics.Message.SERVICE_CRITICAL,
                diagnostics.pending(11000, true, false, false, false, false, 1000, true));
        assertNull(diagnostics.pending(11000, true, true, true, true, true, 0, true));
    }

    @Test
    void thermalShutdownWarningBypassesRoutineGapEvenWhenOxygenIsLower() {
        var diagnostics = new EmergencyEvaDiagnostics();
        diagnostics.reset(5000, 8000, 7000);
        advance(diagnostics, true, 300);
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.MOISTURE);
        assertEquals(EmergencyEvaDiagnostics.Message.SERVICE_CRITICAL,
                diagnostics.pending(0, true, false, false, true, false, 1200, false));
        diagnostics.acknowledge(EmergencyEvaDiagnostics.Message.SERVICE_CRITICAL);
        advance(diagnostics, true, 700);
        assertNull(diagnostics.pending(0, true, false, false, false, false, 1000, false));
    }

    private static EmergencyEvaDiagnostics fresh() {
        var diagnostics = new EmergencyEvaDiagnostics();
        diagnostics.reset(12000);
        advance(diagnostics, true, 300);
        return diagnostics;
    }

    private static void advance(EmergencyEvaDiagnostics diagnostics, boolean sealed, int ticks) {
        for (int i = 0; i < ticks; i++) diagnostics.tick(sealed);
    }
}
