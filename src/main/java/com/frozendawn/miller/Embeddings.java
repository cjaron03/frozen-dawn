package com.frozendawn.miller;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;

/**
 * The table she reads words with: {@code potion-base-8M} at int8, kept as bytes. A line is the mean of its tokens' rows.
 * A missing or damaged table is logged and comes back empty, and every line is then all zero: never fatal.
 */
public final class Embeddings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR = "/frozendawn/potion/", UNKNOWN = "[UNK]", CONTINUES = "##";
    private static final int HEADER = 8, SCALE = 4;
    private static final Embeddings EMPTY = new Embeddings(new byte[0], 0, Map.of(), -1);

    private final ByteBuffer table;
    private final int width, unknown;
    private final Map<String, Integer> vocab;

    private Embeddings(byte[] table, int width, Map<String, Integer> vocab, int unknown) {
        this.table = ByteBuffer.wrap(table);
        this.width = width;
        this.vocab = vocab;
        this.unknown = unknown;
    }

    public static Embeddings load() {
        try (var table = Embeddings.class.getResourceAsStream(DIR + "table.bin"); var vocab = Embeddings.class.getResourceAsStream(DIR + "vocab.txt")) {
            return read(table, vocab);
        } catch (IOException e) {
            LOGGER.error("Could not read her table", e);
            return EMPTY;
        }
    }

    /** Rows, width, then each row's float scale and its bytes. */
    static Embeddings read(InputStream table, InputStream vocabulary) {
        try {
            if (table == null || vocabulary == null) throw new IOException("missing");
            byte[] bytes = table.readAllBytes();
            String[] tokens = new String(vocabulary.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            if (bytes.length < HEADER) throw new IOException("too short");
            var header = ByteBuffer.wrap(bytes);
            int rows = header.getInt(), width = header.getInt();
            if (rows != tokens.length || width <= 0 || bytes.length != HEADER + (long) rows * (SCALE + width)) throw new IOException("wrong shape");
            var vocab = new HashMap<String, Integer>(tokens.length * 2);
            for (int i = 0; i < tokens.length; i++) vocab.put(tokens[i], i);
            if (!vocab.containsKey(UNKNOWN)) throw new IOException("no unknown token");
            return new Embeddings(bytes, width, vocab, vocab.get(UNKNOWN));
        } catch (IOException e) {
            LOGGER.error("Her table is damaged, so nothing typed will match", e);
            return EMPTY;
        }
    }

    public boolean empty() {
        return width == 0;
    }

    /** Unit length, or all zero if no token is known. */
    public float[] vector(String text) {
        var mean = new float[width];
        if (empty()) return mean;
        int count = 0;
        for (int id : tokens(text)) {
            if (id == unknown) continue;
            count++;
            int row = HEADER + id * (SCALE + width);
            float scale = table.getFloat(row);
            for (int d = 0; d < width; d++) mean[d] += table.get(row + SCALE + d) * scale;
        }
        return count == 0 ? mean : unit(mean);
    }

    private static float[] unit(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        if (sum == 0) return v;
        float scale = (float) (1 / Math.sqrt(sum));
        for (int i = 0; i < v.length; i++) v[i] *= scale;
        return v;
    }

    /** Greedy WordPiece, longest piece first. A word with a piece nobody knows is one unknown token. */
    private List<Integer> tokens(String text) {
        var ids = new ArrayList<Integer>();
        for (String word : words(text)) {
            var pieces = new ArrayList<Integer>();
            int start = 0;
            while (start < word.length()) {
                Integer id = null;
                int end = word.length();
                for (; end > start; end--) {
                    id = vocab.get((start > 0 ? CONTINUES : "") + word.substring(start, end));
                    if (id != null) break;
                }
                if (id == null) {
                    pieces = null;
                    break;
                }
                pieces.add(id);
                start = end;
            }
            if (pieces == null) ids.add(unknown);
            else ids.addAll(pieces);
        }
        return ids;
    }

    /** BERT's basic tokenizer: lower case, accents off, split on space and punctuation. */
    private static List<String> words(String text) {
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        var words = new ArrayList<String>();
        var word = new StringBuilder();
        for (char c : plain.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                word.append(c);
                continue;
            }
            flush(words, word);
            if (!Character.isWhitespace(c)) words.add(String.valueOf(c));
        }
        flush(words, word);
        return words;
    }

    private static void flush(List<String> words, StringBuilder word) {
        if (word.length() > 0) words.add(word.toString());
        word.setLength(0);
    }
}
