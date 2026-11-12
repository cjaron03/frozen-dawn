package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EndingOrbitTest {
    @Test
    void theViewCenterIsTheSubPoint() {
        double[] point = EndingOrbit.surface(0.0, 0.0, -20.0, 8.0);
        assertNotNull(point);
        assertEquals(-20.0, point[0], 1e-9);
        assertEquals(8.0, point[1], 1e-9);
    }

    @Test
    void northIsUpAndEastIsRight() {
        double[] center = EndingOrbit.surface(0.0, 0.0, -20.0, 8.0);
        assertTrue(EndingOrbit.surface(0.0, 0.3, -20.0, 8.0)[0] > center[0], "up the screen is north");
        assertTrue(EndingOrbit.surface(0.3, 0.0, -20.0, 8.0)[1] > center[1], "right on the screen is east");
    }

    @Test
    void beyondTheLimbIsSpace() {
        assertNull(EndingOrbit.surface(0.8, 0.8, -20.0, 8.0));
        assertNotNull(EndingOrbit.surface(0.0, 0.99, -20.0, 8.0));
    }

    @Test
    void theEarthTurnsWestToEast() {
        assertTrue(EndingOrbit.longitude(EndingOrbit.EARTH_END) > EndingOrbit.longitude(EndingOrbit.EARTH_START),
                "the sub-point moves east, so the surface slides toward the right");
    }

    @Test
    void theBurnCountsDownToTheFade() {
        assertEquals("0:18", EndingOrbit.burnClock(EndingOrbit.EARTH_START));
        assertEquals("0:00", EndingOrbit.burnClock(EndingOrbit.BURN_TICK));
        assertEquals("0:00", EndingOrbit.burnClock(EndingOrbit.EARTH_END + 100));
        assertEquals(1.0F, EndingOrbit.earthAlpha(EndingOrbit.BURN_TICK - 1));
        assertTrue(EndingOrbit.earthAlpha(EndingOrbit.BURN_TICK + 20) < 1.0F, "the view fades once the burn fires");
    }

    @Test
    void theEarthIsGoneBeforeMarsAppears() {
        assertEquals(0.0F, EndingOrbit.earthAlpha(EndingOrbit.EARTH_START));
        assertEquals(0.0F, EndingOrbit.earthAlpha(EndingOrbit.EARTH_END));
        assertEquals(0.0F, EndingOrbit.earthAlpha(EndingOrbit.MARS_START));
        assertTrue(EndingOrbit.EARTH_END < EndingOrbit.MARS_START, "a held black between the two");
    }

    @Test
    void everyLifeSupportLabelHasText() throws IOException {
        try (var in = EndingOrbitTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            assertNotNull(in, "en_us.json on the classpath");
            var lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : EndingOrbit.LANG_KEYS) {
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), key);
            }
        }
    }
}
