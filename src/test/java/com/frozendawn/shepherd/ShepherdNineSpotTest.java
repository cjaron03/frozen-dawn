package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Where Nine lies in the pit, as BlastPitPlacement lays it out, and the sheet behind the lamp. */
class ShepherdNineSpotTest {
    private static final BlockPos PIT = new BlockPos(100, 70, -200);

    /** Every place she can lie, by refusing each in turn. */
    private static List<BlockPos> all() {
        Set<BlockPos> taken = new HashSet<>();
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos spot = ShepherdNine.spot(PIT, pos -> !taken.contains(pos)); spot != null; spot = ShepherdNine.spot(PIT, pos -> !taken.contains(pos))) {
            spots.add(spot);
            taken.add(spot);
        }
        return spots;
    }

    @Test
    void spotAvoidsThePadTowerAndTrench() {
        var spots = all();
        assertEquals(8, spots.size());
        for (var spot : spots) {
            int x = spot.getX() - PIT.getX(), z = spot.getZ() - PIT.getZ();
            assertEquals(PIT.getY() - 2, spot.getY(), "On the floor, three blocks down");
            assertFalse(Math.abs(x) <= 5 && Math.abs(z) <= 5, "Pad and trench: " + spot);
            assertFalse(x >= -13 && x <= -7 && Math.abs(z) <= 5, "Tower: " + spot);
            double ring = Math.sqrt(x * x + z * z);
            assertTrue(ring > 9.5 && ring <= 14, "On that ring of floor, inside the warm zone: " + ring);
        }
    }

    @Test
    void spotTakesTheNextFreeFloor() {
        var first = ShepherdNine.spot(PIT, pos -> true);
        assertEquals(PIT.offset(9, -2, 7), first);
        var second = ShepherdNine.spot(PIT, pos -> !pos.equals(first));
        assertNotNull(second);
        assertNotEquals(first, second);
        assertNull(ShepherdNine.spot(PIT, pos -> false), "Nowhere free: it waits");
    }

    private static JsonObject entry() throws IOException {
        try (var in = ShepherdNineSpotTest.class.getResourceAsStream("/assets/frozendawn/patchouli_books/frozen_dawn_guide/en_us/entries/orsa_intel/tithonus_register.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void theRegisterIsFiled() throws IOException {
        var entry = entry();
        assertEquals("frozendawn:found_tithonus_register", entry.get("advancement").getAsString());
        assertEquals("Frame Register", entry.get("name").getAsString());
        assertNotNull(getClass().getResource("/data/frozendawn/advancement/found_tithonus_register.json"));
        var pages = entry.getAsJsonArray("pages");
        assertEquals(4, pages.size(), "The file's three sheets, the last over two pages of the manual");
        var last = pages.get(2).getAsJsonObject().get("text").getAsString();
        assertTrue(last.contains("Verdict, all twelve: pattern transfer only, subject not recovered."), last);
        var first = pages.get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(first.contains("Issuing division:$(br)Budget line:$(br)Approver signature:"), "Blank, not redacted: " + first);
    }
}
