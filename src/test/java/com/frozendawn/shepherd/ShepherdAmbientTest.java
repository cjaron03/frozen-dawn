package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdAffinity.Say;
import com.frozendawn.shepherd.ShepherdAmbient.Trigger;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShepherdAmbientTest {
    private static List<ShepherdAmbient.Pool> parse(String json) {
        return ShepherdAmbient.parse(JsonParser.parseString(json));
    }

    @Test
    void aPoolParses() {
        var pools = parse("""
                {"pools":[{"trigger":"night","cooldown":100,"mood":"thinking","variants":[{"line":"a"},{"line":"b","heavy":"c"}]}]}""");
        assertEquals(1, pools.size());
        var pool = pools.getFirst();
        assertEquals(Trigger.NIGHT, pool.trigger());
        assertEquals(100, pool.cooldownTicks());
        assertEquals(Mood.THINKING, pool.mood());
        assertEquals(List.of(new Say("a"), new Say("b", "c")), pool.variants());
    }

    @Test
    void anUnknownTriggerIsSkipped() {
        var pools = parse("""
                {"pools":[{"trigger":"weather","cooldown":1,"mood":"neutral","variants":[{"line":"a"}]},
                          {"trigger":"night","cooldown":1,"mood":"neutral","variants":[{"line":"a"}]}]}""");
        assertEquals(1, pools.size(), "the good pool survives the bad one");
    }

    @Test
    void aPoolWithAMissingKeyIsSkipped() {
        assertTrue(parse("""
                {"pools":[{"trigger":"night","mood":"neutral","variants":[{"line":"a"}]}]}""").isEmpty());
    }

    @Test
    void aPoolWithNothingToSayIsSkipped() {
        assertTrue(parse("""
                {"pools":[{"trigger":"night","cooldown":1,"mood":"neutral","variants":[]}]}""").isEmpty());
    }

    @Test
    void aPoolCanSayWhereSheHasGotTo() {
        var pools = parse("""
                {"pools":[{"trigger":"bleed","from":"walls","cooldown":100,"mood":"thinking","variants":[{"line":"a"}]},
                {"trigger":"night","cooldown":100,"mood":"thinking","variants":[{"line":"b"}]}]}""");
        assertEquals("walls", pools.getFirst().from());
        assertNull(pools.getLast().from());
    }
}
