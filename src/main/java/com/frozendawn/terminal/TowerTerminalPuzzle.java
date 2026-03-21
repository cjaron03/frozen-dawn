package com.frozendawn.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class TowerTerminalPuzzle {

    public static final int ROWS = 12;
    public static final int SEGMENTS = ROWS * 2;
    public static final int SEGMENT_LENGTH = 16;
    public static final int MAX_ATTEMPTS = 4;
    public static final int WORD_COUNT = 12;
    public static final int PAIR_COUNT = 8;
    private static final char[] GLYPHS = "!@#$%^&*()-+=/\\[]{}<>?:;,.|~".toCharArray();
    private static final char[] OPENERS = {'(', '[', '{', '<'};
    private static final char[] CLOSERS = {')', ']', '}', '>'};

    private static final String[] WORDS_5 = {
            "JARON", "TITAN", "ORBIT", "POLAR", "FROST", "DRIFT", "GLINT", "PROBE",
            "RADAR", "ARRAY", "TRACK", "CLEAR", "CLOUD", "FLARE", "PHASE", "SHARD"
    };

    private static final String[] WORDS_6 = {
            "EUROPA", "HUNTER", "SIGNAL", "BEACON", "WINTER", "LOCKED", "STATIC", "FROZEN",
            "VECTOR", "ANCHOR", "LOGGER", "UPLINK", "SWITCH", "RADIUM", "SHIVER", "CIPHER"
    };

    private static final String[] WORDS_7 = {
            "FALLOUT", "ARCHIVE", "THERMAL", "OUTPOST", "OFFLINE", "BASTION", "SCANNER", "STORAGE",
            "DAWNING", "CHILLER", "NEXUSES", "LANTERN", "OVERLAY", "MONITOR", "DISCORD", "FIREWAL"
    };

    private TowerTerminalPuzzle() {
    }

    public static Board create(long nonce) {
        Random random = new Random(nonce ^ 0x5EED5EEDCAFEL);
        WordPool pool = selectWordPool(random);
        String password = pool.words().get(random.nextInt(pool.words().size()));
        List<String> candidates = selectCandidates(pool.words(), password, pool.wordLength(), random);
        int passwordIndex = candidates.indexOf(password);
        if (passwordIndex < 0) {
            candidates.add(password);
            passwordIndex = candidates.size() - 1;
        }

        char[][] segments = new char[SEGMENTS][SEGMENT_LENGTH];
        for (int i = 0; i < SEGMENTS; i++) {
            fillNoise(segments[i], random);
        }

        List<Integer> segmentOrder = new ArrayList<>();
        for (int i = 0; i < SEGMENTS; i++) {
            segmentOrder.add(i);
        }
        Collections.shuffle(segmentOrder, random);

        List<WordToken> wordTokens = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            int segmentIndex = segmentOrder.remove(segmentOrder.size() - 1);
            String word = candidates.get(i);
            int start = random.nextInt(SEGMENT_LENGTH - pool.wordLength() + 1);
            writeToken(segments[segmentIndex], word, start);
            wordTokens.add(new WordToken(segmentIndex, start, pool.wordLength(), i));
        }

        List<PairToken> pairTokens = new ArrayList<>();
        List<PairReward> rewards = new ArrayList<>();
        int dudCount = PAIR_COUNT / 2;
        int resetCount = PAIR_COUNT - dudCount;
        for (int i = 0; i < dudCount; i++) {
            rewards.add(PairReward.REMOVE_DUD);
        }
        for (int i = 0; i < resetCount; i++) {
            rewards.add(PairReward.RESET_ATTEMPTS);
        }
        Collections.shuffle(rewards, random);
        for (int i = 0; i < PAIR_COUNT && !segmentOrder.isEmpty(); i++) {
            int segmentIndex = segmentOrder.remove(segmentOrder.size() - 1);
            int pairLen = 4 + random.nextInt(4);
            int start = random.nextInt(SEGMENT_LENGTH - pairLen + 1);
            int openerIndex = random.nextInt(OPENERS.length);
            char opener = OPENERS[openerIndex];
            char closer = CLOSERS[openerIndex];
            segments[segmentIndex][start] = opener;
            for (int j = start + 1; j < start + pairLen - 1; j++) {
                segments[segmentIndex][j] = randomGlyph(random);
            }
            segments[segmentIndex][start + pairLen - 1] = closer;
            PairReward reward = rewards.get(i % rewards.size());
            pairTokens.add(new PairToken(segmentIndex, start, pairLen, i, reward));
        }

        List<String> renderedSegments = new ArrayList<>(SEGMENTS);
        for (char[] segment : segments) {
            renderedSegments.add(new String(segment));
        }

        return new Board(pool.wordLength(), renderedSegments, candidates, wordTokens, pairTokens, passwordIndex);
    }

    public static int likeness(String guess, String password) {
        int likeness = 0;
        int max = Math.min(guess.length(), password.length());
        for (int i = 0; i < max; i++) {
            if (guess.charAt(i) == password.charAt(i)) {
                likeness++;
            }
        }
        return likeness;
    }

    private static WordPool selectWordPool(Random random) {
        int[] lengths = {5, 6, 7};
        int pick = lengths[random.nextInt(lengths.length)];
        return switch (pick) {
            case 5 -> new WordPool(5, List.of(WORDS_5));
            case 7 -> new WordPool(7, List.of(WORDS_7));
            default -> new WordPool(6, List.of(WORDS_6));
        };
    }

    private static List<String> selectCandidates(List<String> pool, String password, int wordLength, Random random) {
        Map<Integer, List<String>> buckets = new HashMap<>();
        for (String word : pool) {
            if (word.equals(password)) {
                continue;
            }
            buckets.computeIfAbsent(likeness(word, password), ignored -> new ArrayList<>()).add(word);
        }
        for (List<String> bucket : buckets.values()) {
            Collections.shuffle(bucket, random);
        }

        List<Integer> likenessOrder = new ArrayList<>();
        int low = 0;
        int high = wordLength;
        while (low <= high) {
            likenessOrder.add(high);
            if (low != high) {
                likenessOrder.add(low);
            }
            high--;
            low++;
        }

        List<String> selected = new ArrayList<>();
        selected.add(password);

        while (selected.size() < Math.min(WORD_COUNT, pool.size())) {
            boolean addedThisPass = false;
            for (int likeness : likenessOrder) {
                List<String> bucket = buckets.get(likeness);
                if (bucket == null || bucket.isEmpty()) {
                    continue;
                }
                selected.add(bucket.remove(bucket.size() - 1));
                addedThisPass = true;
                if (selected.size() >= Math.min(WORD_COUNT, pool.size())) {
                    break;
                }
            }
            if (!addedThisPass) {
                break;
            }
        }

        if (selected.size() < Math.min(WORD_COUNT, pool.size())) {
            List<String> leftovers = new ArrayList<>();
            for (List<String> bucket : buckets.values()) {
                leftovers.addAll(bucket);
            }
            Collections.shuffle(leftovers, random);
            for (String word : leftovers) {
                if (selected.size() >= Math.min(WORD_COUNT, pool.size())) {
                    break;
                }
                selected.add(word);
            }
        }

        Collections.shuffle(selected, random);
        return selected;
    }

    private static void fillNoise(char[] chars, Random random) {
        for (int i = 0; i < chars.length; i++) {
            chars[i] = randomGlyph(random);
        }
    }

    private static char randomGlyph(Random random) {
        return GLYPHS[random.nextInt(GLYPHS.length)];
    }

    private static void writeToken(char[] chars, String token, int start) {
        for (int i = 0; i < token.length(); i++) {
            chars[start + i] = token.charAt(i);
        }
    }

    public enum PairReward {
        REMOVE_DUD,
        RESET_ATTEMPTS
    }

    public record WordToken(int segmentIndex, int start, int length, int wordIndex) {
    }

    public record PairToken(int segmentIndex, int start, int length, int pairIndex, PairReward reward) {
        public int endExclusive() {
            return start + length;
        }
    }

    public record Board(int wordLength, List<String> segments, List<String> candidates,
                        List<WordToken> wordTokens, List<PairToken> pairTokens, int passwordIndex) {

        public String password() {
            return candidates.get(passwordIndex);
        }

        public String renderSegment(int segmentIndex, long removedMask, long usedPairMask) {
            char[] chars = segments.get(segmentIndex).toCharArray();
            for (WordToken token : wordTokens) {
                if (token.segmentIndex == segmentIndex && ((removedMask >> token.wordIndex) & 1L) != 0L) {
                    for (int i = 0; i < token.length; i++) {
                        chars[token.start + i] = '.';
                    }
                }
            }
            for (PairToken token : pairTokens) {
                if (token.segmentIndex == segmentIndex && ((usedPairMask >> token.pairIndex) & 1L) != 0L) {
                    for (int i = token.start; i < token.endExclusive(); i++) {
                        chars[i] = '.';
                    }
                }
            }
            return new String(chars);
        }

        public WordToken getWord(int wordIndex) {
            return wordTokens.stream().filter(token -> token.wordIndex == wordIndex).findFirst().orElse(null);
        }

        public PairToken getPair(int pairIndex) {
            return pairTokens.stream().filter(token -> token.pairIndex == pairIndex).findFirst().orElse(null);
        }
    }

    private record WordPool(int wordLength, List<String> words) {
    }
}
