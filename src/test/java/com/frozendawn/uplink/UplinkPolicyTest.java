package com.frozendawn.uplink;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.uplink.UplinkPolicy.Mode;
import com.frozendawn.uplink.UplinkPolicy.Reward;
import org.junit.jupiter.api.Test;

class UplinkPolicyTest {
    @Test void modesMatchSpec() {
        assertEquals(2_000, Mode.LISTEN.watts);
        assertEquals(40_000, Mode.TRANSMIT.watts);
        assertEquals(3 * 60 * 20, Mode.TRANSMIT.ticks);
        assertEquals(60_000, Mode.BOOST.watts);
        assertEquals(Mode.TRANSMIT.ticks / 2, Mode.BOOST.ticks, "Boost: same reward, half the time");
        assertFalse(Mode.LISTEN.transmitting());
        assertEquals(Mode.OFF, Mode.byId(99));
    }

    @Test void cooldownIsOneInGameDay() {
        assertEquals(24_000, UplinkPolicy.COOLDOWN_TICKS);
        assertFalse(UplinkPolicy.cooledDown(100, 101));
        assertTrue(UplinkPolicy.cooledDown(24_000, 24_000));
        assertEquals(23_999, UplinkPolicy.cooldownRemaining(1, 24_000));
        assertEquals(0, UplinkPolicy.cooldownRemaining(30_000, 24_000));
    }

    @Test void archivePacing() {
        assertTrue(UplinkPolicy.archiveDue(0, false, 0), "#1 on the first transmission");
        assertFalse(UplinkPolicy.archiveDue(1, false, 6), "#2 waits for Maeve");
        assertTrue(UplinkPolicy.archiveDue(1, true, 6));
        assertFalse(UplinkPolicy.archiveDue(2, true, 5), "#3 waits for Phase 6");
        assertTrue(UplinkPolicy.archiveDue(2, true, 6));
        assertFalse(UplinkPolicy.archiveDue(3, true, 6), "Only three fragments");
        assertTrue(UplinkPolicy.humanReply(2));
        assertFalse(UplinkPolicy.humanReply(0));
    }

    @Test void dueFragmentOutranksOtherRewards() {
        for (double roll = 0; roll < 1; roll += .05) {
            assertEquals(Reward.ARCHIVE, UplinkPolicy.choose(0, false, 0, null, true, roll));
            assertNotEquals(Reward.ARCHIVE, UplinkPolicy.choose(1, false, 6, Reward.SUPPLY_DROP, true, roll));
            assertNotEquals(Reward.ARCHIVE, UplinkPolicy.choose(3, true, 6, null, true, roll));
        }
    }

    @Test void weightedRotationHalvesTheLastReward() {
        assertEquals(Reward.SUPPLY_DROP, UplinkPolicy.choose(3, true, 6, null, true, 0.59));
        assertEquals(Reward.STATION_MAP, UplinkPolicy.choose(3, true, 6, null, true, 0.61));
        // After a supply drop: weights 1.5 / 2, so the map takes more than half the range.
        assertEquals(Reward.STATION_MAP, UplinkPolicy.choose(3, true, 6, Reward.SUPPLY_DROP, true, 0.45));
        // After a map: weights 3 / 1.
        assertEquals(Reward.SUPPLY_DROP, UplinkPolicy.choose(3, true, 6, Reward.STATION_MAP, true, 0.74));
        assertEquals(Reward.SUPPLY_DROP, UplinkPolicy.choose(3, true, 6, Reward.SUPPLY_DROP, false, 0.99), "No undiscovered site falls back to a drop");
    }

    @Test void signalIsLaggedDecaysAndCaps() {
        assertEquals(0, UplinkPolicy.pressure(1, 0, UplinkPolicy.SIGNAL_LAG_TICKS, UplinkPolicy.SIGNAL_LAG_TICKS - 1), "Reaction is lagged");
        assertEquals(1, UplinkPolicy.pressure(1, 0, 0, 0), 1e-9);
        assertEquals(0.5, UplinkPolicy.pressure(1, 0, 0, UplinkPolicy.SIGNAL_HALF_LIFE_TICKS), 1e-9);
        assertTrue(UplinkPolicy.signal(true) > UplinkPolicy.signal(false), "Boost is louder");
        double score = 0; long at = 0;
        for (int i = 0; i < 20; i++) { score = UplinkPolicy.accumulate(score, at, i, true); at = i; }
        assertEquals(UplinkPolicy.MAX_SIGNAL, score, 1e-9);
        assertEquals(0, UplinkPolicy.pressure(Double.NaN, 0, 0, 10));
        assertEquals(1, UplinkPolicy.spawnMultiplier(0), 1e-9);
        assertEquals(1 + UplinkPolicy.MAX_SIGNAL * UplinkPolicy.SPAWN_GAIN, UplinkPolicy.spawnMultiplier(99), 1e-9);
    }

    @Test void sectorsUseMinecraftAxes() {
        assertEquals("N", UplinkPolicy.sector(0, -10));
        assertEquals("S", UplinkPolicy.sector(0, 10));
        assertEquals("E", UplinkPolicy.sector(10, 0));
        assertEquals("W", UplinkPolicy.sector(-10, 0));
        assertEquals("NE", UplinkPolicy.sector(10, -10));
        assertEquals("SW", UplinkPolicy.sector(-10, 10));
        assertEquals(300, UplinkPolicy.announcedDistance(310));
        assertEquals(50, UplinkPolicy.announcedDistance(3));
    }

    @Test void jitterKeepsTheTruePointInsideTheCircle() {
        for (int i = 0; i < 200; i++) {
            double angle = i * 0.731, fraction = (i * 0.137) % 1;
            var o = UplinkPolicy.jitter(70, 20, angle, fraction);
            assertTrue(Math.hypot(o[0], o[1]) <= 70 - 20 + 1, "offset " + o[0] + "," + o[1]);
        }
        var none = UplinkPolicy.jitter(10, 20, 1, 1);
        assertArrayEquals(new int[] {0, 0}, none);
    }

    @Test void mapScaleFitsReach() {
        assertEquals(0, UplinkPolicy.mapScale(40));
        assertEquals(1, UplinkPolicy.mapScale(100));
        assertEquals(2, UplinkPolicy.mapScale(250));
        assertEquals(3, UplinkPolicy.mapScale(500));
        assertEquals(4, UplinkPolicy.mapScale(5000));
    }
}
