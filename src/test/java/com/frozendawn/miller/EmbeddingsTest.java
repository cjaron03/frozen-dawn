package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EmbeddingsTest {
    private static final Embeddings TABLE = Embeddings.load();

    private static double cosine(String a, String b) {
        float[] x = TABLE.vector(a), y = TABLE.vector(b);
        double dot = 0;
        for (int i = 0; i < x.length; i++) dot += x[i] * y[i];
        return dot;
    }

    private static double length(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    @Test void aLineBecomesAUnitVector() {
        assertFalse(TABLE.empty());
        assertEquals(1, length(TABLE.vector("Who is Kevin?")), 1e-5);
    }

    @Test void unknownTextIsAllZero() {
        assertEquals(0, length(TABLE.vector("")));
        assertEquals(0, length(TABLE.vector("\uE000\uE001")));
    }

    @Test void casingAndAccentsDoNotMatter() {
        assertArrayEquals(TABLE.vector("who is kevin"), TABLE.vector("WHO IS KÉVIN"));
    }

    /** From M0's own int8 path, tools/intent_trial/IntentTrial.java. */
    @Test void theTableMatchesTheTrial() {
        assertEquals(0.6409, cosine("how are you", "How do you know all this?"), 0.0005);
        assertEquals(0.7459, cosine("What is ORSA?", "Tell me about ORSA."), 0.0005);
        assertEquals(0.7261, cosine("who is kevin", "Tell me about Kevin."), 0.0005);
        assertEquals(0.8335, cosine("why is it so cold", "Why did it get so cold?"), 0.0005);
        assertEquals(0.4265, cosine("wat hapened to kevin", "What did Kevin do?"), 0.0005);
        assertEquals(0.2525, cosine("mugs", "Tell me about the gift shop."), 0.0005);
    }

    @Test void aDamagedTableIsEmptyNotFatal() throws IOException {
        byte[] table;
        byte[] vocab;
        try (var in = Embeddings.class.getResourceAsStream("/frozendawn/potion/table.bin"); var words = Embeddings.class.getResourceAsStream("/frozendawn/potion/vocab.txt")) {
            table = in.readAllBytes();
            vocab = words.readAllBytes();
        }
        for (byte[] bad : new byte[][] {new byte[0], new byte[3], Arrays.copyOf(table, table.length / 2), new byte[] {127, -1, -1, -1, 127, -1, -1, -1}}) {
            var damaged = Embeddings.read(new ByteArrayInputStream(bad), new ByteArrayInputStream(vocab));
            assertTrue(damaged.empty());
            assertEquals(0, length(damaged.vector("who is kevin")));
        }
        assertTrue(Embeddings.read(null, null).empty());
        assertTrue(Embeddings.read(new ByteArrayInputStream(table), new ByteArrayInputStream("[UNK]\nthree\n".getBytes())).empty());
    }
}
