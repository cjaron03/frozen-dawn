import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The typed text trial (docs/aurora-intent-trial.md). Not game code. Run from the repository root:
 * java -cp <gson.jar>:<onnxruntime.jar> tools/intent_trial/IntentTrial.java build/intent-trial
 */
public final class IntentTrial {
    record Line(String text, String expected, String style, boolean dev) { }
    record Example(String intent, String text) { }
    record Ranked(String first, double firstScore, String second, double secondScore) { }

    interface Matcher {
        Ranked match(String text) throws Exception;
    }

    record Thresholds(double strong, double margin, double close) { }

    public static void main(String[] args) throws Exception {
        Path models = Path.of(args[0]), here = Path.of("tools/intent_trial");
        var gson = new Gson();
        var examples = new ArrayList<Example>();
        var intents = gson.fromJson(Files.readString(here.resolve("intents.json")), JsonObject.class);
        for (var intent : intents.entrySet()) for (var text : intent.getValue().getAsJsonArray()) examples.add(new Example(intent.getKey(), text.getAsString()));
        var lines = lines(gson.fromJson(Files.readString(here.resolve("phrasings.json")), JsonObject.class));
        System.out.printf("examples=%d lines=%d dev=%d test=%d%n%n", examples.size(), lines.size(), lines.stream().filter(Line::dev).count(), lines.stream().filter(l -> !l.dev()).count());

        var potion = new Potion(models.resolve("potion"));
        run("potion fp32", models, lines, examples, () -> potion.matcher(examples, 32), potion.size(32));
        run("potion fp16", models, lines, examples, () -> potion.matcher(examples, 16), potion.size(16));
        run("potion int8", models, lines, examples, () -> potion.matcher(examples, 8), potion.size(8));
        long ortJar = Files.size(models.resolve("ort").resolve(jarName(models))), ortTrimmed = trimmedJar(models.resolve("ort").resolve(jarName(models)));
        System.out.printf("onnxruntime jar: shipped %.1f MB, without debug symbols %.1f MB%n%n", ortJar / 1e6, ortTrimmed / 1e6);
        var vocab = new WordPiece(Files.readAllLines(models.resolve("minilm/vocab.txt")));
        for (var model : List.of("model.onnx", "model_qint8_arm64.onnx")) {
            long size = ortTrimmed + deflated(Files.readAllBytes(models.resolve("minilm").resolve(model))) + deflated(Files.readAllBytes(models.resolve("minilm/vocab.txt")));
            run("minilm " + model, models, lines, examples, () -> new Minilm(models.resolve("minilm").resolve(model), vocab).matcher(examples), size);
        }
        run("word and synonym", models, lines, examples, () -> new Words(examples), Files.size(here.resolve("intents.json")) / 3);
    }

    private static String jarName(Path models) throws IOException {
        try (var files = Files.list(models.resolve("ort"))) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".jar")).findFirst().orElseThrow();
        }
    }

    /** Dev and test take turns inside each intent, so both halves hold every intent and style. */
    private static List<Line> lines(JsonObject json) {
        var lines = new ArrayList<Line>();
        for (var intent : json.getAsJsonObject("in_scope").entrySet()) {
            boolean dev = true;
            for (var style : intent.getValue().getAsJsonObject().entrySet()) for (var text : style.getValue().getAsJsonArray()) {
                lines.add(new Line(text.getAsString(), intent.getKey(), style.getKey(), dev));
                dev = !dev;
            }
        }
        for (var kind : json.getAsJsonObject("out_of_scope").entrySet()) {
            boolean dev = true;
            for (var text : kind.getValue().getAsJsonArray()) {
                lines.add(new Line(text.getAsString(), null, kind.getKey(), dev));
                dev = !dev;
            }
        }
        return lines;
    }

    interface Build {
        Matcher get() throws Exception;
    }

    private static void run(String name, Path models, List<Line> lines, List<Example> examples, Build build, long addedBytes) throws Exception {
        System.gc();
        long heapBefore = usedHeap(), start = System.nanoTime();
        var matcher = build.get();
        double loadMs = (System.nanoTime() - start) / 1e6;
        System.gc();
        long heap = usedHeap() - heapBefore;

        var ranked = new LinkedHashMap<Line, Ranked>();
        for (var line : lines) ranked.put(line, matcher.match(line.text()));
        var thresholds = tune(ranked);
        var test = new LinkedHashMap<Line, Ranked>();
        ranked.forEach((line, rank) -> { if (!line.dev()) test.put(line, rank); });

        // Latency: warm first, then every line in turn, one thread.
        for (int i = 0; i < 5; i++) for (var line : lines) matcher.match(line.text());
        var times = new ArrayList<Long>();
        for (int i = 0; i < 10; i++) for (var line : lines) {
            long t = System.nanoTime();
            matcher.match(line.text());
            times.add(System.nanoTime() - t);
        }
        Collections.sort(times);

        System.out.println("## " + name);
        System.out.printf("thresholds strong=%.3f margin=%.3f close=%.3f%n", thresholds.strong(), thresholds.margin(), thresholds.close());
        report("test", test, thresholds);
        report("dev", dev(ranked), thresholds);
        var styles = new LinkedHashMap<String, int[]>();
        test.forEach((line, rank) -> {
            if (line.expected() == null) return;
            var count = styles.computeIfAbsent(line.style(), k -> new int[2]);
            count[1]++;
            if (rank.first().equals(line.expected())) count[0]++;
        });
        var by = new StringBuilder("top-1 by style:");
        styles.forEach((style, count) -> by.append(String.format(" %s %d/%d", style, count[0], count[1])));
        System.out.println(by);
        var oos = new LinkedHashMap<String, int[]>();
        test.forEach((line, rank) -> {
            if (line.expected() != null) return;
            var count = oos.computeIfAbsent(line.style(), k -> new int[3]);
            count[band(rank, thresholds)]++;
        });
        var bands = new StringBuilder("out of scope strong/close/none:");
        oos.forEach((style, count) -> bands.append(String.format(" %s %d/%d/%d", style, count[0], count[1], count[2])));
        System.out.println(bands);
        System.out.printf("load %.0f ms, heap %.1f MB, p50 %.3f ms, p99 %.3f ms, added jar %.1f MB%n%n", loadMs, heap / 1e6,
                times.get(times.size() / 2) / 1e6, times.get(times.size() * 99 / 100) / 1e6, addedBytes / 1e6);
        var misses = new StringBuilder();
        test.forEach((line, rank) -> {
            boolean bad = line.expected() == null ? band(rank, thresholds) == 0 : !rank.first().equals(line.expected());
            if (bad) misses.append(String.format("%s | %s -> %s %.3f, %s %.3f%n", line.text(), line.expected(), rank.first(), rank.firstScore(), rank.second(), rank.secondScore()));
        });
        Files.writeString(models.resolve("misses-" + name.replaceAll("[^a-z0-9]+", "-") + ".txt"), misses.toString());
    }

    private static Map<Line, Ranked> dev(Map<Line, Ranked> ranked) {
        var dev = new LinkedHashMap<Line, Ranked>();
        ranked.forEach((line, rank) -> { if (line.dev()) dev.put(line, rank); });
        return dev;
    }

    private static long usedHeap() {
        var runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /** 0 strong, 1 close, 2 none. */
    private static int band(Ranked rank, Thresholds t) {
        if (rank.firstScore() >= t.strong() && rank.firstScore() - rank.secondScore() >= t.margin()) return 0;
        return rank.firstScore() >= t.close() ? 1 : 2;
    }

    /** On the dev half only: the most Strong answers that keep wrong and out-of-scope Strong within 3 percent, then a Close line that lets a quarter of out-of-scope through at most. */
    private static Thresholds tune(Map<Line, Ranked> ranked) {
        var dev = dev(ranked);
        long scoped = dev.keySet().stream().filter(l -> l.expected() != null).count(), outside = dev.size() - scoped;
        double top = dev.values().stream().mapToDouble(Ranked::firstScore).max().orElse(1);
        Thresholds best = new Thresholds(top + 1, 0, top + 1);
        int bestRight = -1;
        for (double strong = 0; strong <= top; strong += 0.005) for (double margin = 0; margin <= 0.2001; margin += 0.01) {
            var t = new Thresholds(strong, margin, strong);
            int right = 0, wrong = 0, falseStrong = 0;
            for (var entry : dev.entrySet()) {
                if (band(entry.getValue(), t) != 0) continue;
                if (entry.getKey().expected() == null) falseStrong++;
                else if (entry.getValue().first().equals(entry.getKey().expected())) right++;
                else wrong++;
            }
            if (wrong <= 0.03 * scoped && falseStrong <= 0.03 * outside && right > bestRight) {
                bestRight = right;
                best = t;
            }
        }
        double close = best.strong();
        for (double candidate = best.strong(); candidate >= 0; candidate -= 0.005) {
            final double c = candidate;
            long through = dev.entrySet().stream().filter(e -> e.getKey().expected() == null && e.getValue().firstScore() >= c).count();
            if (through <= 0.25 * outside) close = candidate;
        }
        return new Thresholds(best.strong(), best.margin(), Math.min(close, best.strong()));
    }

    private static void report(String half, Map<Line, Ranked> set, Thresholds t) {
        int n = 0, top1 = 0, top2 = 0, strongRight = 0, strongWrong = 0, closeHit = 0, closeMiss = 0, none = 0, oos = 0, oosStrong = 0, oosClose = 0, oosNone = 0;
        for (var entry : set.entrySet()) {
            var line = entry.getKey();
            var rank = entry.getValue();
            int band = band(rank, t);
            if (line.expected() == null) {
                oos++;
                if (band == 0) oosStrong++; else if (band == 1) oosClose++; else oosNone++;
                continue;
            }
            n++;
            boolean first = rank.first().equals(line.expected()), second = first || rank.second().equals(line.expected());
            if (first) top1++;
            if (second) top2++;
            if (band == 0) { if (first) strongRight++; else strongWrong++; }
            else if (band == 1) { if (second) closeHit++; else closeMiss++; }
            else none++;
        }
        System.out.printf("%s in-scope n=%d top1 %.1f%% top2 %.1f%% | strong right %.1f%% strong wrong %.1f%% close-with-answer %.1f%% close-without %.1f%% none %.1f%% | out-of-scope n=%d strong %.1f%% close %.1f%% none %.1f%%%n",
                half, n, pct(top1, n), pct(top2, n), pct(strongRight, n), pct(strongWrong, n), pct(closeHit, n), pct(closeMiss, n), pct(none, n), oos, pct(oosStrong, oos), pct(oosClose, oos), pct(oosNone, oos));
    }

    private static double pct(int a, int b) {
        return b == 0 ? 0 : 100.0 * a / b;
    }

    private static Ranked rank(Map<String, Double> scores) {
        var order = new ArrayList<>(scores.entrySet());
        order.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()));
        return new Ranked(order.get(0).getKey(), order.get(0).getValue(), order.get(1).getKey(), order.get(1).getValue());
    }

    /** Best cosine to any of an intent's examples, the rows being unit length. */
    private static Matcher nearest(List<Example> examples, Embedder embedder) throws Exception {
        var vectors = new float[examples.size()][];
        for (int i = 0; i < vectors.length; i++) vectors[i] = embedder.embed(examples.get(i).text());
        return text -> {
            float[] query = embedder.embed(text);
            var scores = new HashMap<String, Double>();
            for (int i = 0; i < vectors.length; i++) {
                double dot = 0;
                for (int d = 0; d < query.length; d++) dot += query[d] * vectors[i][d];
                scores.merge(examples.get(i).intent(), dot, Math::max);
            }
            return rank(scores);
        };
    }

    interface Embedder {
        float[] embed(String text) throws Exception;
    }

    private static float[] unit(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        if (sum == 0) return v;
        float scale = (float) (1 / Math.sqrt(sum));
        for (int i = 0; i < v.length; i++) v[i] *= scale;
        return v;
    }

    /** BERT's basic tokenizer and greedy WordPiece: lower case, accents off, split on space and punctuation, longest piece first. */
    static final class WordPiece {
        private final Map<String, Integer> vocab = new HashMap<>();
        final int unk, cls, sep;

        WordPiece(List<String> tokens) {
            for (int i = 0; i < tokens.size(); i++) vocab.put(tokens.get(i), i);
            unk = vocab.get("[UNK]");
            cls = vocab.getOrDefault("[CLS]", -1);
            sep = vocab.getOrDefault("[SEP]", -1);
        }

        List<Integer> ids(String text) {
            var ids = new ArrayList<Integer>();
            for (String word : basic(text)) {
                int start = 0;
                var pieces = new ArrayList<Integer>();
                while (start < word.length()) {
                    Integer id = null;
                    int end = word.length();
                    for (; end > start; end--) {
                        id = vocab.get((start > 0 ? "##" : "") + word.substring(start, end));
                        if (id != null) break;
                    }
                    if (id == null) { pieces = null; break; }
                    pieces.add(id);
                    start = end;
                }
                if (pieces == null) ids.add(unk); else ids.addAll(pieces);
            }
            return ids;
        }

        private static List<String> basic(String text) {
            String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            var words = new ArrayList<String>();
            var word = new StringBuilder();
            for (char c : plain.toCharArray()) {
                if (Character.isWhitespace(c)) { flush(words, word); continue; }
                if (Character.isLetterOrDigit(c)) { word.append(c); continue; }
                flush(words, word);
                words.add(String.valueOf(c));
            }
            flush(words, word);
            return words;
        }

        private static void flush(List<String> words, StringBuilder word) {
            if (word.length() > 0) words.add(word.toString());
            word.setLength(0);
        }
    }

    /** Model2Vec: a table lookup and a mean. Nothing else runs. */
    static final class Potion {
        private final float[][] table;
        private final WordPiece pieces;
        private final Path dir;

        Potion(Path dir) throws IOException {
            this.dir = dir;
            byte[] file = Files.readAllBytes(dir.resolve("model.safetensors"));
            var buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
            int header = (int) buffer.getLong();
            var json = new Gson().fromJson(new String(file, 8, header, StandardCharsets.UTF_8), JsonObject.class).getAsJsonObject("embeddings");
            var shape = json.getAsJsonArray("shape");
            table = new float[shape.get(0).getAsInt()][shape.get(1).getAsInt()];
            buffer.position(8 + header);
            for (float[] row : table) for (int d = 0; d < row.length; d++) row[d] = buffer.getFloat();
            var vocab = new Gson().fromJson(Files.readString(dir.resolve("tokenizer.json")), JsonObject.class).getAsJsonObject("model").getAsJsonObject("vocab");
            var tokens = new String[vocab.size()];
            vocab.entrySet().forEach(e -> tokens[e.getValue().getAsInt()] = e.getKey());
            pieces = new WordPiece(Arrays.asList(tokens));
        }

        /** The table as it would ship at this width: rows round-tripped through it, int8 with a scale per row. */
        private float[][] at(int bits) {
            var rows = new float[table.length][];
            for (int r = 0; r < rows.length; r++) {
                float[] row = table[r].clone();
                if (bits == 16) for (int d = 0; d < row.length; d++) row[d] = Float.float16ToFloat(Float.floatToFloat16(row[d]));
                if (bits == 8) {
                    float max = 0;
                    for (float x : row) max = Math.max(max, Math.abs(x));
                    float scale = max == 0 ? 1 : max / 127;
                    for (int d = 0; d < row.length; d++) row[d] = Math.round(row[d] / scale) * scale;
                }
                rows[r] = row;
            }
            return rows;
        }

        Matcher matcher(List<Example> examples, int bits) throws Exception {
            var rows = at(bits);
            return nearest(examples, text -> {
                var mean = new float[rows[0].length];
                int count = 0;
                for (int id : pieces.ids(text)) {
                    if (id == pieces.unk) continue;
                    count++;
                    float[] row = rows[id];
                    for (int d = 0; d < mean.length; d++) mean[d] += row[d];
                }
                return count == 0 ? mean : unit(mean);
            });
        }

        long size(int bits) throws IOException {
            var bytes = new ByteArrayOutputStream();
            var rows = table;
            var buffer = ByteBuffer.allocate(rows.length * rows[0].length * (bits / 8) + (bits == 8 ? rows.length * 4 : 0)).order(ByteOrder.LITTLE_ENDIAN);
            for (float[] row : rows) {
                float max = 0;
                for (float x : row) max = Math.max(max, Math.abs(x));
                float scale = max == 0 ? 1 : max / 127;
                if (bits == 8) buffer.putFloat(scale);
                for (float x : row) {
                    if (bits == 32) buffer.putFloat(x);
                    else if (bits == 16) buffer.putShort(Float.floatToFloat16(x));
                    else buffer.put((byte) Math.round(x / scale));
                }
            }
            bytes.write(buffer.array());
            return deflated(bytes.toByteArray()) + deflated(Files.readAllBytes(dir.resolve("vocab.txt")));
        }
    }

    /** Sentence embeddings through ONNX Runtime, on one thread. */
    static final class Minilm {
        private final OrtEnvironment env = OrtEnvironment.getEnvironment();
        private final OrtSession session;
        private final WordPiece pieces;

        Minilm(Path model, WordPiece pieces) throws Exception {
            this.pieces = pieces;
            var options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(1);
            session = env.createSession(model.toString(), options);
        }

        Matcher matcher(List<Example> examples) throws Exception {
            return nearest(examples, this::embed);
        }

        private float[] embed(String text) throws Exception {
            var tokens = new ArrayList<Integer>();
            tokens.add(pieces.cls);
            tokens.addAll(pieces.ids(text));
            tokens.add(pieces.sep);
            int n = tokens.size();
            var ids = new long[1][n];
            var mask = new long[1][n];
            var types = new long[1][n];
            for (int i = 0; i < n; i++) { ids[0][i] = tokens.get(i); mask[0][i] = 1; }
            var inputs = new HashMap<String, OnnxTensor>();
            inputs.put("input_ids", OnnxTensor.createTensor(env, ids));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, mask));
            if (session.getInputNames().contains("token_type_ids")) inputs.put("token_type_ids", OnnxTensor.createTensor(env, types));
            try (var result = session.run(inputs)) {
                float[][] hidden = ((float[][][]) result.get(0).getValue())[0];
                var mean = new float[hidden[0].length];
                for (float[] row : hidden) for (int d = 0; d < mean.length; d++) mean[d] += row[d] / n;
                return unit(mean);
            } finally {
                inputs.values().forEach(OnnxTensor::close);
            }
        }
    }

    /** Stems, a hand synonym table, close spellings, and TF-IDF cosine to each example. */
    static final class Words implements Matcher {
        private static final List<String> STOP = List.of("a", "an", "the", "is", "are", "was", "were", "do", "does", "did", "you", "your", "i", "me", "my", "it", "its", "of", "to", "in", "on", "at", "for", "and", "or", "so", "this", "that", "with", "about", "what", "whats", "who", "whos", "why", "how", "when", "tell", "s", "t", "they", "them", "their", "there", "be", "have", "has", "had");
        private static final List<List<String>> SYNONYMS = List.of(
                List.of("frozen", "freeze", "freezing", "cold", "ice", "temperature"),
                List.of("evacuation", "evacuate", "exodus", "shuttle", "launch", "seat"),
                List.of("survivor", "alive", "left", "anyone", "anybody", "someone", "colonist", "mars"),
                List.of("memory", "registry", "loaded", "stored", "information", "know", "learn"),
                List.of("newsletter", "horizon", "birthday"),
                List.of("shop", "store", "merch", "merchandise", "souvenir", "mug", "buy", "sell"),
                List.of("seven", "seventh", "7", "7th"),
                List.of("floor", "department", "division", "chart"),
                List.of("bulletin", "pamphlet", "notice", "document"),
                List.of("joke", "italic", "sarcastic", "snarky", "note"),
                List.of("volunteer", "reassigned", "sweep", "roster"),
                List.of("door", "doors", "grease"),
                List.of("medic", "doctor", "medical", "certificate"),
                List.of("clerk", "manifest", "paperwork", "inventory", "item", "typed"),
                List.of("book", "novel", "reading", "read", "deathwand"),
                List.of("enoch", "okoye", "technician"),
                List.of("recall", "recalled", "pulled", "leave"),
                List.of("cascade", "failure", "vasquez", "incident"),
                List.of("board", "transcript", "session", "14b", "minutes", "meeting"),
                List.of("directive", "ink", "pen", "annotate", "annotation", "margin"),
                List.of("vault", "place"),
                List.of("blank", "empty"),
                List.of("charge", "ran", "run", "running", "paid", "approved", "signed"));
        private final Map<String, String> canon = new HashMap<>();
        private final Map<String, Double> idf = new HashMap<>();
        private final List<Map<String, Double>> vectors = new ArrayList<>();
        private final List<Example> examples;

        Words(List<Example> examples) {
            this.examples = examples;
            for (var group : SYNONYMS) for (String word : group) canon.put(stem(word), stem(group.getFirst()));
            var docs = examples.stream().map(e -> terms(e.text(), false)).toList();
            var seen = new HashMap<String, Integer>();
            for (var doc : docs) doc.stream().distinct().forEach(term -> seen.merge(term, 1, Integer::sum));
            seen.forEach((term, count) -> idf.put(term, Math.log(1.0 + docs.size() / (double) count)));
            for (var doc : docs) vectors.add(vector(doc));
        }

        private static String stem(String word) {
            if (word.length() > 5 && word.endsWith("ing")) return word.substring(0, word.length() - 3);
            if (word.length() > 4 && word.endsWith("ed")) return word.substring(0, word.length() - 2);
            if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
            return word;
        }

        private List<String> terms(String text, boolean correct) {
            var terms = new ArrayList<String>();
            for (String word : text.toLowerCase(Locale.ROOT).replace("'", "").split("[^a-z0-9]+")) {
                if (word.isEmpty() || STOP.contains(word)) continue;
                String term = stem(word);
                term = canon.getOrDefault(term, term);
                if (correct && !idf.containsKey(term)) term = closest(term);
                terms.add(term);
            }
            return terms;
        }

        /** A typo: the one known term within one edit, or two for a long word. */
        private String closest(String term) {
            if (term.length() < 4) return term;
            String best = term;
            int reach = term.length() >= 7 ? 2 : 1;
            for (String known : idf.keySet()) {
                int d = distance(term, known);
                if (d <= reach) { best = known; reach = d - 1; }
            }
            return best;
        }

        private static int distance(String a, String b) {
            int[] row = new int[b.length() + 1];
            for (int j = 0; j < row.length; j++) row[j] = j;
            for (int i = 1; i <= a.length(); i++) {
                int diagonal = row[0];
                row[0] = i;
                for (int j = 1; j <= b.length(); j++) {
                    int next = row[j];
                    row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), diagonal + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                    diagonal = next;
                }
            }
            return row[b.length()];
        }

        private Map<String, Double> vector(List<String> terms) {
            var vector = new HashMap<String, Double>();
            for (String term : terms) vector.merge(term, idf.getOrDefault(term, 0.0), Double::sum);
            double length = Math.sqrt(vector.values().stream().mapToDouble(v -> v * v).sum());
            if (length > 0) vector.replaceAll((k, v) -> v / length);
            return vector;
        }

        @Override public Ranked match(String text) {
            var query = vector(terms(text, true));
            var scores = new HashMap<String, Double>();
            for (var example : examples) scores.putIfAbsent(example.intent(), 0.0);
            for (int i = 0; i < vectors.size(); i++) {
                double dot = 0;
                for (var entry : query.entrySet()) dot += entry.getValue() * vectors.get(i).getOrDefault(entry.getKey(), 0.0);
                scores.merge(examples.get(i).intent(), dot, Math::max);
            }
            return rank(scores);
        }
    }

    private static long deflated(byte[] bytes) throws IOException {
        var counted = new long[1];
        try (var out = new DeflaterOutputStream(new OutputStream() {
            @Override public void write(int b) { counted[0]++; }
            @Override public void write(byte[] b, int off, int len) { counted[0] += len; }
        }, new Deflater(Deflater.BEST_COMPRESSION))) {
            out.write(bytes);
        }
        return counted[0];
    }

    /** The shipped jar carries macOS debug symbols a mod would strip. Re-zipped without them, as a fair floor. */
    private static long trimmedJar(Path jar) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (InputStream in = Files.newInputStream(jar); var zip = new ZipInputStream(in); var out = new ZipOutputStream(bytes)) {
            out.setLevel(Deflater.BEST_COMPRESSION);
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.isDirectory() || entry.getName().contains(".dSYM/")) continue;
                out.putNextEntry(new ZipEntry(entry.getName()));
                zip.transferTo(out);
                out.closeEntry();
            }
        }
        return bytes.size();
    }
}
