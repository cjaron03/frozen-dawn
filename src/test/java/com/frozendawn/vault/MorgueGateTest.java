package com.frozendawn.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MorgueGateTest {
    private final MorgueGate gate = new MorgueGate();
    private final UUID me = new UUID(0, 1), you = new UUID(0, 2);

    private void blast(int ticks, UUID... bodies) { for (int i = 0; i < ticks; i++) gate.expose(Set.of(bodies)); }

    @Test void aBodyStartsWarm() {
        assertEquals(MorgueGate.BODY, gate.temperature(me));
    }

    @Test void theBlastLowersYourTemperatureAndNobodyElses() {
        blast(100, me);
        assertEquals(MorgueGate.BODY - 100 * MorgueGate.COOL, gate.temperature(me), .01);
        assertEquals(MorgueGate.BODY, gate.temperature(you));
    }

    @Test void itReachesTheLineWithAboutTwoHeartsLeft() {
        int ticks = (int) Math.ceil((MorgueGate.BODY - MorgueGate.LINE) / MorgueGate.COOL);
        int bites = ticks / MorgueGate.BITE_EVERY;
        assertTrue(ticks < 9 * MorgueGate.BITE_EVERY, "Short of the bite that leaves one heart: " + ticks);
        assertEquals(4, 20 - bites * MorgueGate.BITE_DAMAGE, 2.01, "From full hearts, some left");
    }

    @Test void outOfTheBlastYouWarmSlowly() {
        blast(100, me);
        float cold = gate.temperature(me);
        blast(100);
        assertEquals(cold + 100 * MorgueGate.WARM, gate.temperature(me), .01);
        assertTrue(MorgueGate.WARM < MorgueGate.COOL / 4, "Time to walk to the plate");
    }

    @Test void warmedBackUpYouAreForgotten() {
        blast(10, me);
        blast((int) (10 * MorgueGate.COOL / MorgueGate.WARM) + 2);
        assertEquals(MorgueGate.BODY, gate.temperature(me));
        assertEquals(0, gate.bodies());
    }

    @Test void theDeadStartTheirNextLifeWarm() {
        blast(100, me);
        gate.forget(me);
        assertEquals(MorgueGate.BODY, gate.temperature(me));
    }

    @Test void anEmptyPlateReadsNothing() {
        gate.tick(List.of());
        assertEquals(MorgueGate.NOBODY, gate.reading());
        assertFalse(gate.open());
    }

    @Test void theWarmAreReadAndKeptOut() {
        gate.tick(List.of(me));
        assertEquals(MorgueGate.BODY, gate.reading());
        assertFalse(gate.open());
    }

    @Test void coldEnoughItOpens() {
        blast(1000, me);
        gate.tick(List.of(me));
        assertTrue(gate.open());
        assertTrue(gate.reading() <= MorgueGate.LINE);
    }

    @Test void itStopsAtTheColdOfTheStore() {
        blast(100000, me);
        assertEquals(MorgueGate.COLDEST, gate.temperature(me));
    }

    @Test void justOverTheLineIsAlive() {
        blast((int) ((MorgueGate.BODY - MorgueGate.LINE) / MorgueGate.COOL) - 2, me);
        gate.tick(List.of(me));
        assertFalse(gate.open());
    }

    @Test void itOpensForAFewSecondsAfterYouStepOff() {
        blast(1000, me);
        gate.tick(List.of(me));
        for (int i = 1; i < MorgueGate.OPEN_TICKS; i++) { gate.tick(List.of()); assertTrue(gate.open(), "At " + i); }
        assertTrue(gate.reading() != MorgueGate.NOBODY, "The reading stands while the door does");
        gate.tick(List.of());
        assertFalse(gate.open());
        assertEquals(MorgueGate.NOBODY, gate.reading());
    }

    @Test void anyoneWarmOnThePlateShutsIt() {
        blast(1000, me);
        gate.tick(List.of(me));
        gate.tick(List.of(me, you));
        assertFalse(gate.open());
        assertEquals(MorgueGate.BODY, gate.reading());
    }

    @Test void theBlastBitesEveryTwoSeconds() {
        int bites = 0;
        for (long t = 1; t <= 400; t++) if (MorgueGate.bites(t)) bites++;
        assertEquals(10, bites);
        assertEquals(2, MorgueGate.BITE_DAMAGE, "About a heart");
    }

    @Test void resetForgetsEveryone() {
        blast(1000, me);
        gate.tick(List.of(me));
        gate.reset();
        assertFalse(gate.open());
        assertEquals(MorgueGate.NOBODY, gate.reading());
        assertEquals(MorgueGate.BODY, gate.temperature(me));
    }
}
