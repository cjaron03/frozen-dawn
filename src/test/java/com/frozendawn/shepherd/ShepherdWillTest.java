package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.Attention;
import com.frozendawn.miller.PatternLaws;
import com.frozendawn.network.ShepherdLinePayload;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ShepherdWillTest {
    private static JsonObject lang() throws IOException {
        try (var in = ShepherdWillTest.class.getResourceAsStream("/assets/frozendawn/lang/en_us.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<String> keepKinds() throws IOException {
        var kinds = new ArrayList<String>();
        try (var in = ShepherdWillTest.class.getResourceAsStream("/data/frozendawn/shepherd/dialogue/first_meeting.json")) {
            for (var node : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("nodes")) {
                var keep = node.getAsJsonObject().get("keep");
                if (keep != null) kinds.add(keep.getAsString());
            }
        }
        return kinds;
    }

    @Test
    void everyKeepsakeKindHasItsLine() throws Exception {
        var lang = lang();
        var kinds = keepKinds();
        assertFalse(kinds.isEmpty());
        for (Deed deed : Deed.values()) if (!deed.lines().isEmpty()) kinds.add(deed.name().toLowerCase(Locale.ROOT));
        for (String kind : kinds) {
            var line = ShepherdWill.recall(kind);
            assertNotNull(line, kind);
            assertTrue(lang.has(line.key()), line.key());
            assertTrue(lang.get(line.key()).getAsString().contains("%s"), line.key() + " carries the day");
        }
    }

    @Test
    void everyWallHasItsLine() throws Exception {
        var lang = lang();
        for (PatternLaws wall : PatternLaws.values()) {
            var line = ShepherdWill.wall(wall.name().toLowerCase(Locale.ROOT));
            assertTrue(lang.has(line.key()), line.key());
        }
        assertEquals(Mood.THINKING, ShepherdWill.wall(Attention.NOTICING).mood());
        assertTrue(lang.has(ShepherdWill.wall(Attention.NOTICING).key()));
    }

    private static ShepherdLinePayload throughTheWire(ShepherdLinePayload line) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        ShepherdLinePayload.STREAM_CODEC.encode(buffer, line);
        return ShepherdLinePayload.STREAM_CODEC.decode(buffer);
    }

    @Test
    void aLineCarriesItsDayOrNothing() {
        assertEquals("12", throughTheWire(new ShepherdLinePayload("k", Mood.SAD, "12")).arg());
        assertNull(throughTheWire(new ShepherdLinePayload("k", Mood.SAD, null)).arg());
    }

    @Test
    void herWallsNeverCountOrLookOutward() throws Exception {
        for (var line : lang().entrySet()) {
            if (!line.getKey().matches("shepherd\\.(wall|self|boot)\\..*")) continue;
            String text = line.getValue().getAsString().toLowerCase(Locale.ROOT);
            for (String word : List.of("six", "fair", "rules")) assertFalse(text.contains(word), line.getKey());
        }
    }
}
