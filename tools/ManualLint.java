import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Layout linter for the ORSA Field Manual (Patchouli book frozen_dawn_guide).
 *
 * Predicts how Patchouli 1.21.1-92 wraps every text block without launching the
 * game. The parser and line breaker are ports of Patchouli's BookTextParser and
 * TextLayouter (including its RESIZE re-layout), and glyph advances are read
 * from the vanilla default font bitmaps, so the predicted lines match the
 * in-game book rather than an approximation of it.
 *
 * Run from the repository root:
 *   java tools/ManualLint.java                 per-entry report, exit 1 on errors
 *   java tools/ManualLint.java --entry heating/thermal_heater --dump
 *   java tools/ManualLint.java --stats         density table for an organization pass
 *
 * Options:
 *   --resources <jar>   vanilla client resources jar (default: the moddev
 *                       client-extra jar, then the NeoForm client jar)
 *   --mode <m>          RESIZE (Patchouli default), OVERFLOW or TRUNCATE
 *   --entry <id>        only lint entries whose id contains this text
 *   --dump              print the predicted lines of every linted page
 *   --stats             print per-entry density instead of issues
 *
 * Reads the vanilla font only to measure glyph widths; nothing is copied.
 */
public final class ManualLint {

    static final Path BOOK = Path.of("src/main/resources/assets/frozendawn/patchouli_books/frozen_dawn_guide/en_us");
    static final Path BOOK_JSON = Path.of("src/main/resources/data/frozendawn/patchouli_books/frozen_dawn_guide/book.json");

    // Patchouli GuiBook geometry, page-local coordinates.
    static final int PAGE_WIDTH = 116;
    static final int LINE_HEIGHT = 9;
    static final int LAYOUT_BOTTOM = 156;      // TextLayouter's hard-coded page bottom
    // orsa_book.png: clean paper ends at page-local row 149; rows 150-154 are the
    // grey bevel and the frame starts at 155. Glyph ink must stay on clean paper.
    static final int PAPER_BOTTOM = 150;
    static final int GLYPH_HEIGHT = 8;
    static final int TOP_PADDING = 18;
    static final int LEFT_PAGE_X = 15;
    // GuiButtonEntry: 116 wide, name drawn at x + 12, read marker drawn at x + 111.
    // The last advance includes a 1 px gap, so a name of this width ends its ink at x + 110.
    static final int LIST_NAME_WIDTH = 111 - 12 + 1;

    // Ornaments in the bottom-right corner of each page of orsa_book.png,
    // page-local {x0, y0, x1, y1} inclusive.
    static final int[] MARK_LEFT_PAGE = {106, 144, 115, 146};
    static final int[] MARK_RIGHT_PAGE = {109, 141, 116, 149};

    enum Mode { RESIZE, OVERFLOW, TRUNCATE }

    enum Severity { ERROR, WARN, INFO }

    record Issue(Severity severity, String kind, String detail) {
    }

    public static void main(String[] args) throws Exception {
        Path resources = null;
        Mode mode = Mode.RESIZE;
        String filter = null;
        boolean dump = false;
        boolean stats = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--resources" -> resources = Path.of(args[++i]);
                case "--mode" -> mode = Mode.valueOf(args[++i].toUpperCase(Locale.ROOT));
                case "--entry" -> filter = args[++i];
                case "--dump" -> dump = true;
                case "--stats" -> stats = true;
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if (resources == null) {
            resources = findResourcesJar();
        }

        Font font = new Font(resources);
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("$(list", "$(li");
        macros.put("/$", "$()");
        macros.put("<br>", "$(br)");
        macros.put("$(item)", "$(#b0b)");
        macros.put("$(thing)", "$(#490)");
        Map<String, Object> book = asMap(Json.parse(Files.readString(BOOK_JSON)));
        if (book.get("macros") instanceof Map<?, ?> custom) {
            custom.forEach((k, v) -> macros.put((String) k, (String) v));
        }
        Linter linter = new Linter(font, mode, macros, dump);

        List<Report> reports = new ArrayList<>();
        if (filter == null) {
            Report landing = new Report("book.json", "landing text", 0);
            linter.lintBlock(landing, "landing", 0, (String) book.get("landing_text"),
                    LEFT_PAGE_X, TOP_PADDING + 25, true);
            reports.add(landing);
        }

        List<Path> categories;
        try (Stream<Path> s = Files.list(BOOK.resolve("categories"))) {
            categories = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        Map<String, Integer> categoryOrder = new HashMap<>();
        for (Path p : categories) {
            String id = stripJson(BOOK.resolve("categories").relativize(p));
            Map<String, Object> cat = asMap(Json.parse(Files.readString(p)));
            categoryOrder.put("frozendawn:" + id, num(cat.get("sortnum")));
            if (filter != null && !("categories/" + id).contains(filter)) {
                continue;
            }
            Report r = new Report("categories/" + id, (String) cat.get("name"), 0);
            linter.lintTitle(r, "category name", (String) cat.get("name"));
            linter.lintBlock(r, "description", 0, (String) cat.get("description"),
                    LEFT_PAGE_X, TOP_PADDING + 22, true);
            reports.add(r);
        }

        List<Path> entries;
        try (Stream<Path> s = Files.walk(BOOK.resolve("entries"))) {
            entries = s.filter(p -> p.toString().endsWith(".json")).toList();
        }
        List<Report> entryReports = new ArrayList<>();
        for (Path p : entries) {
            String id = stripJson(BOOK.resolve("entries").relativize(p));
            if (filter != null && !id.contains(filter)) {
                continue;
            }
            Map<String, Object> entry = asMap(Json.parse(Files.readString(p)));
            String category = (String) entry.get("category");
            Report r = new Report(id, (String) entry.get("name"), num(entry.get("sortnum")));
            r.categoryOrder = categoryOrder.getOrDefault(category, 99);
            linter.lintEntry(r, entry);
            entryReports.add(r);
        }
        entryReports.sort((a, b) -> a.categoryOrder != b.categoryOrder
                ? Integer.compare(a.categoryOrder, b.categoryOrder)
                : a.id.substring(0, a.id.indexOf('/')).equals(b.id.substring(0, b.id.indexOf('/')))
                ? Integer.compare(a.sortnum, b.sortnum)
                : a.id.compareTo(b.id));
        reports.addAll(entryReports);

        if (stats) {
            printStats(entryReports);
            return;
        }
        int errors = printReport(reports, mode, font);
        System.exit(errors > 0 ? 1 : 0);
    }

    static Path findResourcesJar() throws IOException {
        Path local = Path.of("build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar");
        if (Files.exists(local)) {
            return local;
        }
        Path cache = Path.of(System.getProperty("user.home"), ".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar");
        if (Files.exists(cache)) {
            return cache;
        }
        throw new IllegalStateException("No vanilla resources jar found. Run a Gradle build once or pass --resources <jar>.");
    }

    static String stripJson(Path relative) {
        String s = relative.toString().replace('\\', '/');
        return s.substring(0, s.length() - ".json".length());
    }

    // ---------------------------------------------------------------- report

    static final class Report {
        final String id;
        final String name;
        final int sortnum;
        int categoryOrder;
        int pages;
        int textPages;
        int words;
        int naturalLines;
        int maxNaturalLines;
        final Map<String, List<Issue>> issues = new LinkedHashMap<>();

        Report(String id, String name, int sortnum) {
            this.id = id;
            this.name = name;
            this.sortnum = sortnum;
        }

        void add(String where, Severity severity, String kind, String detail) {
            issues.computeIfAbsent(where, k -> new ArrayList<>()).add(new Issue(severity, kind, detail));
        }

        long count(Severity s) {
            return issues.values().stream().flatMap(List::stream).filter(i -> i.severity == s).count();
        }
    }

    static int printReport(List<Report> reports, Mode mode, Font font) {
        Map<String, Integer> kinds = new TreeMap<>();
        int errors = 0;
        int warnings = 0;
        int dirty = 0;
        System.out.println("ORSA Field Manual layout lint (Patchouli overflow mode " + mode + ")");
        System.out.println();
        for (Report r : reports) {
            if (r.issues.isEmpty()) {
                continue;
            }
            dirty++;
            System.out.printf("%s  \"%s\"%n", r.id, r.name);
            for (Map.Entry<String, List<Issue>> e : r.issues.entrySet()) {
                for (Issue i : e.getValue()) {
                    System.out.printf("  %-5s %-7s %-16s %s%n", i.severity, e.getKey(), i.kind, i.detail);
                    kinds.merge(i.severity + " " + i.kind, 1, Integer::sum);
                    if (i.severity == Severity.ERROR) {
                        errors++;
                    } else if (i.severity == Severity.WARN) {
                        warnings++;
                    }
                }
            }
            System.out.println();
        }
        System.out.printf("%d of %d entries/categories have issues: %d errors, %d warnings%n",
                dirty, reports.size(), errors, warnings);
        kinds.forEach((k, v) -> System.out.printf("  %4d  %s%n", v, k));
        if (!font.missing.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            font.missing.forEach(cp -> sb.append(String.format(" U+%04X", cp)));
            System.out.println("Glyphs absent from the bitmap fonts (measured as 6 px):" + sb);
        }
        return errors;
    }

    static void printStats(List<Report> reports) {
        System.out.printf("%-44s %5s %5s %6s %7s %7s%n", "entry", "pages", "text", "words", "w/page", "maxLn");
        for (Report r : reports) {
            System.out.printf("%-44s %5d %5d %6d %7d %7d%n", r.id, r.pages, r.textPages, r.words,
                    r.textPages == 0 ? 0 : r.words / r.textPages, r.maxNaturalLines);
        }
    }

    // ---------------------------------------------------------------- linter

    static final class Linter {
        final Font font;
        final Mode mode;
        final Map<String, String> macros;
        final boolean dump;

        Linter(Font font, Mode mode, Map<String, String> macros, boolean dump) {
            this.font = font;
            this.mode = mode;
            this.macros = macros;
            this.dump = dump;
        }

        void lintEntry(Report r, Map<String, Object> entry) {
            lintTitle(r, "name", r.name);
            int listWidth = r.name == null ? 0 : font.width(r.name, false);
            if (listWidth > LIST_NAME_WIDTH) {
                r.add("name", Severity.ERROR, "list name wide", "\"" + r.name + "\" is " + listWidth
                        + " px; the chapter list fits " + LIST_NAME_WIDTH + " before the read marker");
            }
            List<?> pages = (List<?>) entry.get("pages");
            r.pages = pages.size();
            for (int i = 0; i < pages.size(); i++) {
                Map<String, Object> page = asMap(pages.get(i));
                String where = "p" + (i + 1);
                String type = (String) page.get("type");
                String title = (String) page.get("title");
                String text = (String) page.get("text");
                boolean left = i % 2 == 0;
                // PageText draws the entry name on page 0 and ignores the page title.
                boolean titleShown = !(i == 0 && "patchouli:text".equals(type));
                if (titleShown && title != null && !title.isEmpty()) {
                    lintTitle(r, where, title);
                }
                switch (type) {
                    case "patchouli:text" -> {
                        r.textPages++;
                        if (text == null || visibleText(text).isBlank()) {
                            r.add(where, Severity.ERROR, "blank page", "text page with no visible text");
                            continue;
                        }
                        int top = i == 0 ? 22 : title != null && !title.isEmpty() ? 12 : -4;
                        lintBlock(r, where, i, text, 0, top, left);
                    }
                    case "patchouli:crafting" -> {
                        if (page.get("recipe2") != null) {
                            r.add(where, Severity.INFO, "unchecked", "double recipe page text not modelled");
                        } else if (text != null) {
                            lintBlock(r, where, i, text, 0, 4 + 78 - 23, left);
                        }
                    }
                    case "patchouli:image" -> {
                        if (text != null) {
                            lintBlock(r, where, i, text, 0, 120, left);
                        }
                    }
                    case "patchouli:empty" -> r.add(where, Severity.WARN, "empty page", "patchouli:empty page");
                    default -> {
                        if (text != null) {
                            r.add(where, Severity.INFO, "unchecked", "text on " + type + " not modelled");
                        }
                    }
                }
            }
        }

        void lintTitle(Report r, String where, String title) {
            if (title == null) {
                return;
            }
            int w = font.width(title, false);
            if (w > PAGE_WIDTH) {
                r.add(where, Severity.ERROR, "title too wide", "\"" + title + "\" is " + w + " px, page is " + PAGE_WIDTH);
            }
            if (!title.equals(title.strip())) {
                r.add(where, Severity.WARN, "title spacing", "\"" + title + "\" has leading or trailing spaces");
            }
            lintCharacters(r, where, title);
        }

        void lintCharacters(Report r, String where, String text) {
            text.codePoints().filter(Linter::isEmoji).distinct().forEach(cp ->
                    r.add(where, Severity.ERROR, "emoji", String.format("U+%04X in \"%s\"", cp, shorten(text))));
            if (text.indexOf('\n') >= 0) {
                r.add(where, Severity.WARN, "raw newline", "literal newline; use $(br)");
            }
            text.codePoints().filter(cp -> !font.has(cp) && cp != '\n').distinct().forEach(cp ->
                    r.add(where, Severity.WARN, "unifont glyph", String.format("U+%04X falls back to Unifont", cp)));
        }

        static boolean isEmoji(int cp) {
            return cp >= 0x1F000 && cp <= 0x1FAFF || cp >= 0x2600 && cp <= 0x27BF && cp != 0x2744
                    || cp == 0xFE0F || cp == 0x200D;
        }

        /**
         * Lays out one text block as Patchouli would. originX/originY are the
         * coordinates handed to BookTextRenderer; for entry pages they are page
         * local, for the landing and category pages they are screen local.
         */
        void lintBlock(Report r, String where, int pageIndex, String text, int originX, int originY, boolean leftPage) {
            if (text == null) {
                return;
            }
            lintCharacters(r, where, text);
            Parser parser = new Parser(font, macros);
            List<Span> spans = parser.parse(text);
            parser.problems.forEach(p -> r.add(where, Severity.ERROR, "markup", p));
            parser.notes.forEach(p -> r.add(where, Severity.INFO, "markup", p));

            Layouter natural = new Layouter(font, originX, originY, Mode.OVERFLOW);
            natural.layout(spans);
            Layouter shown = new Layouter(font, originX, originY, mode);
            shown.layout(spans);

            List<Line> naturalLines = Line.group(natural.words, font);
            List<Line> lines = Line.group(shown.words, font);
            int localShift = originX == 0 ? 0 : TOP_PADDING;
            int localShiftX = originX == 0 ? 0 : LEFT_PAGE_X;

            r.words += visibleText(text).split("\\s+").length;
            int naturalCount = naturalLines.isEmpty() ? 0 : (naturalLines.get(naturalLines.size() - 1).y - originY) / LINE_HEIGHT + 1;
            r.naturalLines += naturalCount;
            r.maxNaturalLines = Math.max(r.maxNaturalLines, naturalCount);

            // 1. Natural overflow: Patchouli's own page bottom.
            int room = (LAYOUT_BOTTOM - originY) / LINE_HEIGHT;
            if (naturalCount > room) {
                String effect = switch (mode) {
                    case RESIZE -> String.format("RESIZE shrinks the page to %d%% text", Math.round(shown.scale() * 100));
                    case TRUNCATE -> "TRUNCATE drops the last " + (naturalCount - room) + " lines";
                    case OVERFLOW -> "text runs off the paper";
                };
                r.add(where, Severity.ERROR, "overflow", String.format("%d lines, room for %d; %s; last line \"%s\"",
                        naturalCount, room, effect, shorten(naturalLines.get(naturalLines.size() - 1).text)));
            }

            if (lines.isEmpty()) {
                return;
            }
            float scale = shown.scale();
            Word first = shown.words.get(0);
            float fx = first.x;
            float fy = first.y;

            // 2. Ink past the paper, and 3. collisions with the corner ornament.
            int[] mark = leftPage ? MARK_LEFT_PAGE : MARK_RIGHT_PAGE;
            for (int li = 0; li < lines.size(); li++) {
                Line line = lines.get(li);
                if (line.text.isBlank()) {
                    continue;
                }
                float y0 = fy + (line.y - fy) * scale - localShift;
                float y1 = y0 + GLYPH_HEIGHT * scale;
                float x0 = fx + (line.x0 - fx) * scale - localShiftX;
                float x1 = fx + (line.x1 - fx) * scale - localShiftX;
                if (y1 > PAPER_BOTTOM + 0.01f) {
                    r.add(where, Severity.ERROR, "past paper", String.format("line %d at y %.0f runs past the paper edge: \"%s\"",
                            li + 1, y0, shorten(line.text)));
                } else if (y1 - 1 >= mark[1] && y0 <= mark[3] && x1 - 1 >= mark[0] && x0 <= mark[2]) {
                    r.add(where, Severity.ERROR, "page mark", String.format("line %d (\"%s\") ends at x %.0f over the corner mark",
                            li + 1, shorten(line.text), x1));
                }
            }

            // 4. Word splits, leading spaces and blank-line runs.
            for (int li = 0; li < lines.size(); li++) {
                Line line = lines.get(li);
                Word head = line.firstVisible();
                if (head != null && li > 0 && Character.isWhitespace(head.text.charAt(0))) {
                    r.add(where, Severity.ERROR, "leading space", String.format("line %d starts with a space: \"%s\"",
                            li + 1, shorten(line.text)));
                }
                if (li == 0) {
                    continue;
                }
                Line prev = lines.get(li - 1);
                Word tail = prev.lastVisible();
                if (head == null || tail == null || tail.paragraph != head.paragraph) {
                    continue;
                }
                String para = shown.paragraphs.get(head.paragraph);
                int cut = head.offset;
                if (cut <= 0 || cut >= para.length()) {
                    continue;
                }
                char a = para.charAt(cut - 1);
                char b = para.charAt(cut);
                if (!Character.isWhitespace(a) && !Character.isWhitespace(b) && "-‐–—/".indexOf(a) < 0) {
                    String before = para.substring(Math.max(0, cut - 12), cut);
                    String after = para.substring(cut, Math.min(para.length(), cut + 12));
                    String cause = natural.isNaturalBreak(head.paragraph, cut)
                            ? "break iterator allows it"
                            : "RESIZE re-layout offset drift";
                    r.add(where, Severity.ERROR, "mid-word split", String.format("line %d: \"%s|%s\" (%s)",
                            li + 1, before.strip(), after.strip(), cause));
                }
            }
            int run = 0;
            for (int li = 1; li < lines.size(); li++) {
                int gap = (lines.get(li).y - lines.get(li - 1).y) / LINE_HEIGHT - 1;
                if (gap >= 2) {
                    run++;
                    r.add(where, Severity.WARN, "blank lines", String.format("%d blank lines before \"%s\"",
                            gap, shorten(lines.get(li).text)));
                }
            }
            Line firstText = lines.stream().filter(l -> !l.text.isBlank()).findFirst().orElse(lines.get(0));
            if (firstText.y > originY && !where.equals("landing")) {
                r.add(where, Severity.WARN, "leading break", "text starts with a line break");
            }
            if (Character.isWhitespace(visibleText(text).isEmpty() ? 'x' : visibleText(text).charAt(0))) {
                r.add(where, Severity.ERROR, "leading space", "text starts with a space");
            }
            if (parser.trailingBreaks > 0) {
                r.add(where, Severity.WARN, "trailing break", "text ends with a line break");
            }

            if (dump) {
                System.out.printf("--- %s %s (scale %.2f)%n", r.id, where, scale);
                for (Line line : lines) {
                    float y0 = fy + (line.y - fy) * scale - localShift;
                    System.out.printf("  y%5.1f x%5.1f  %s%n", y0, fx + (line.x1 - fx) * scale - localShiftX, line.text);
                }
            }
        }
    }

    static String visibleText(String text) {
        return text.replaceAll("\\$\\([^)]*\\)", "");
    }

    static String shorten(String s) {
        s = s.strip();
        return s.length() <= 40 ? s : s.substring(0, 37) + "...";
    }

    // ---------------------------------------------------------------- font

    /** Vanilla default font advances (space provider, then the include/default bitmaps in order). */
    static final class Font {
        final Map<Integer, Integer> advances = new HashMap<>();
        final TreeSet<Integer> missing = new TreeSet<>();

        Font(Path jar) throws IOException {
            advances.put((int) ' ', 4);
            advances.put(0x200C, 0);
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Map<String, Object> include = asMap(Json.parse(read(zip, "assets/minecraft/font/include/default.json")));
                for (Object o : (List<?>) include.get("providers")) {
                    Map<String, Object> provider = asMap(o);
                    if (!"bitmap".equals(provider.get("type"))) {
                        continue;
                    }
                    String file = ((String) provider.get("file")).replace("minecraft:", "");
                    BufferedImage image;
                    try (InputStream in = zip.getInputStream(entry(zip, "assets/minecraft/textures/" + file))) {
                        image = ImageIO.read(in);
                    }
                    List<?> rows = (List<?>) provider.get("chars");
                    int[][] grid = rows.stream().map(row -> ((String) row).codePoints().toArray()).toArray(int[][]::new);
                    int cellW = image.getWidth() / grid[0].length;
                    int cellH = image.getHeight() / grid.length;
                    int height = provider.containsKey("height") ? num(provider.get("height")) : 8;
                    float scale = (float) height / cellH;
                    Map<Integer, Integer> own = new HashMap<>();
                    for (int row = 0; row < grid.length; row++) {
                        for (int col = 0; col < grid[row].length; col++) {
                            int cp = grid[row][col];
                            if (cp == 0) {
                                continue;
                            }
                            int width = glyphWidth(image, cellW, cellH, col, row);
                            own.put(cp, (int) (0.5 + (double) (width * scale)) + 1);
                        }
                    }
                    own.forEach(advances::putIfAbsent);
                }
            }
        }

        static int glyphWidth(BufferedImage image, int cellW, int cellH, int col, int row) {
            for (int i = cellW - 1; i >= 0; i--) {
                for (int k = 0; k < cellH; k++) {
                    if ((image.getRGB(col * cellW + i, row * cellH + k) >>> 24) != 0) {
                        return i + 1;
                    }
                }
            }
            return 0;
        }

        boolean has(int cp) {
            return advances.containsKey(cp);
        }

        float advance(int cp, boolean bold) {
            Integer a = advances.get(cp);
            if (a == null) {
                missing.add(cp);
                a = 6;
            }
            return a + (bold ? 1 : 0);
        }

        /** Font.width(Component) for a literal with a bold or plain style, honouring section-sign codes. */
        int width(String s, boolean bold) {
            float w = 0;
            boolean b = bold;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '§') {
                    if (i + 1 >= s.length()) {
                        break;
                    }
                    char f = Character.toLowerCase(s.charAt(++i));
                    if ("0123456789abcdef".indexOf(f) >= 0) {
                        b = false;
                    } else if (f == 'l') {
                        b = true;
                    } else if (f == 'r') {
                        b = bold;
                    }
                    continue;
                }
                w += advance(c, b);
            }
            return (int) Math.ceil(w);
        }

        static String read(ZipFile zip, String name) throws IOException {
            try (InputStream in = zip.getInputStream(entry(zip, name))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        static ZipEntry entry(ZipFile zip, String name) {
            ZipEntry e = zip.getEntry(name);
            if (e == null) {
                throw new IllegalStateException(name + " missing from " + zip.getName());
            }
            return e;
        }
    }

    // ---------------------------------------------------------------- parser (port of BookTextParser)

    record Span(String text, boolean bold, int lineBreaks, int spacingLeft, int spacingRight) {
    }

    static final class Parser {
        static final Pattern COMMAND = Pattern.compile("\\$\\(([^)]*)\\)");
        final Font font;
        final Map<String, String> macros;
        final List<String> problems = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final Deque<Boolean> bold = new ArrayDeque<>();
        int lineBreaks;
        int spacingLeft;
        int spacingRight;
        int trailingBreaks;

        Parser(Font font, Map<String, String> macros) {
            this.font = font;
            this.macros = macros;
            bold.push(false);
        }

        List<Span> parse(String raw) {
            String text = raw;
            for (int i = 0; i < 10; i++) {
                String next = text;
                for (Map.Entry<String, String> e : macros.entrySet()) {
                    next = next.replace(e.getKey(), e.getValue());
                }
                if (next.equals(text)) {
                    break;
                }
                text = next;
            }
            List<Span> spans = new ArrayList<>();
            Matcher m = COMMAND.matcher(text);
            int last = 0;
            while (m.find()) {
                spans.add(span(text.substring(last, m.start())));
                last = m.end();
                String processed;
                try {
                    processed = command(m.group(1));
                } catch (IllegalStateException e) {
                    problems.add("$(" + m.group(1) + ") " + e.getMessage() + "; renders [ERROR]");
                    spans.add(new Span("[ERROR]", false, lineBreaks, spacingLeft, spacingRight));
                    lineBreaks = spacingLeft = spacingRight = 0;
                    continue;
                }
                if (!processed.isEmpty()) {
                    spans.add(span(processed));
                }
            }
            spans.add(span(text.substring(last)));
            trailingBreaks = 0;
            for (int i = spans.size() - 1; i >= 0 && spans.get(i).text.isEmpty(); i--) {
                trailingBreaks += spans.get(i).lineBreaks;
            }
            trailingBreaks += lineBreaks;
            return spans;
        }

        Span span(String text) {
            Span s = new Span(text, bold.peek(), lineBreaks, spacingLeft, spacingRight);
            lineBreaks = spacingLeft = spacingRight = 0;
            return s;
        }

        String command(String cmd) {
            if (cmd.length() == 1 && "0123456789abcdef".indexOf(cmd.charAt(0)) >= 0) {
                return "";
            }
            if (cmd.startsWith("#") && (cmd.length() == 4 || cmd.length() == 7)) {
                return "";
            }
            if (cmd.matches("li\\d?")) {
                int dist = cmd.length() > 2 ? Character.digit(cmd.charAt(2), 10) : 1;
                lineBreaks = 1;
                spacingLeft = dist * 4;
                spacingRight = font.width(" ", false);
                return "§0" + (dist % 2 == 0 ? '◦' : '•');
            }
            int colon = cmd.indexOf(':');
            if (colon > 0) {
                String fn = cmd.substring(0, colon);
                switch (fn) {
                    case "l", "c", "command" -> {
                        bold.push(bold.peek());
                        return "";
                    }
                    case "t", "tooltip" -> {
                        return "";
                    }
                    case "k" -> {
                        notes.add("$(" + cmd + ") keybind text measured as \"N/A\"; width depends on bindings");
                        return "N/A";
                    }
                    default -> {
                        problems.add("$(" + cmd + ") unknown function; renders [MISSING FUNCTION: " + fn + "]");
                        return "[MISSING FUNCTION: " + fn + "]";
                    }
                }
            }
            switch (cmd) {
                case "br" -> lineBreaks = 1;
                case "br2", "2br", "p" -> lineBreaks = 2;
                case "/l", "/c" -> {
                    if (bold.size() <= 1) {
                        throw new IllegalStateException("closes a link that a reset already closed");
                    }
                    bold.pop();
                }
                case "/t", "k", "obf", "m", "strike", "n", "underline", "o", "italic", "italics", "nocolor" -> {
                }
                case "l", "bold" -> {
                    bold.pop();
                    bold.push(true);
                }
                case "", "reset", "clear" -> {
                    bold.clear();
                    bold.push(false);
                }
                case "playername" -> {
                    return "Playername";
                }
                default -> {
                    problems.add("$(" + cmd + ") is not a Patchouli command; it renders literally");
                    return "$(" + cmd + ")";
                }
            }
            return "";
        }
    }

    // ---------------------------------------------------------------- layouter (port of TextLayouter)

    record Word(String text, int x, int y, boolean bold, int paragraph, int offset) {
    }

    static final class Layouter {
        final Font font;
        final int pageX;
        final int pageY;
        final int basePageWidth = PAGE_WIDTH;
        final Mode mode;
        final List<Word> words = new ArrayList<>();
        final List<String> paragraphs = new ArrayList<>();
        final List<BreakIterator> iterators = new ArrayList<>();
        final List<Tail> pending = new ArrayList<>();
        int y;
        int pageWidth;
        int smallestOverstep;
        int lineStart;
        int widthSoFar;
        // Offset of each span of the current paragraph, for word bookkeeping only.
        final Map<Span, Integer> spanOffsets = new IdentityHashMap<>();

        Layouter(Font font, int pageX, int pageY, Mode mode) {
            this.font = font;
            this.pageX = pageX;
            this.pageY = pageY;
            this.mode = mode;
        }

        final class Tail {
            final Span span;
            final int start;
            final int width;
            final int length;

            Tail(Span span, int start) {
                this.span = span;
                this.start = start;
                this.width = font.width(span.text.substring(start), span.bold) + span.spacingLeft + span.spacingRight;
                this.length = span.text.length() - start;
            }

            Word position(int x, int y, int length) {
                return new Word(span.text.substring(start, start + length), x + span.spacingLeft, y, span.bold,
                        paragraphs.size() - 1, spanOffsets.get(span) + start);
            }

            Tail tail(int offset) {
                return new Tail(span, start + offset);
            }
        }

        void layout(List<Span> spans) {
            pageWidth = basePageWidth;
            int guard = 0;
            do {
                y = pageY;
                words.clear();
                paragraphs.clear();
                iterators.clear();
                smallestOverstep = Integer.MAX_VALUE;
                List<Span> paragraph = new ArrayList<>();
                for (Span span : spans) {
                    if (span.lineBreaks > 0) {
                        layoutParagraph(paragraph);
                        widthSoFar = 0;
                        y += span.lineBreaks * LINE_HEIGHT;
                        paragraph.clear();
                    }
                    paragraph.add(span);
                }
                if (!paragraph.isEmpty()) {
                    layoutParagraph(paragraph);
                }
            } while (mode == Mode.RESIZE && overflow() * scale() > 1.0F && adjustScale() && ++guard < 500);
            if (mode == Mode.TRUNCATE) {
                words.removeIf(w -> w.y + LINE_HEIGHT > LAYOUT_BOTTOM);
            }
        }

        boolean adjustScale() {
            pageWidth = 1 + Math.min(smallestOverstep, (int) ((float) basePageWidth * overflow()));
            return true;
        }

        float scale() {
            return (float) basePageWidth / (float) pageWidth;
        }

        float overflow() {
            return (float) (y + LINE_HEIGHT - pageY) / (float) (LAYOUT_BOTTOM - pageY);
        }

        void layoutParagraph(List<Span> paragraph) {
            StringBuilder sb = new StringBuilder();
            spanOffsets.clear();
            for (Span s : paragraph) {
                spanOffsets.put(s, sb.length());
                sb.append(s.text);
            }
            paragraphs.add(sb.toString());
            BreakIterator iterator = BreakIterator.getLineInstance(Locale.of("en_us"));
            iterator.setText(sb.toString());
            iterators.add(iterator);
            lineStart = 0;
            for (Span span : paragraph) {
                layoutSpan(iterator, span);
            }
            flush();
        }

        void layoutSpan(BreakIterator iterator, Span span) {
            Tail last = new Tail(span, 0);
            widthSoFar += last.width;
            pending.add(last);
            int guard = 0;
            while (widthSoFar > pageWidth && ++guard < 1000) {
                breakLine(iterator);
                widthSoFar = 0;
                for (Tail t : pending) {
                    widthSoFar += t.width;
                }
            }
        }

        void breakLine(BreakIterator iterator) {
            int width = 0;
            int offset = 0;
            for (Tail t : pending) {
                width += t.width;
                offset += t.length;
            }
            Tail last = pending.get(pending.size() - 1);
            width -= last.width;
            offset -= last.length;
            char[] characters = last.span.text.toCharArray();
            for (int i = last.start; i < characters.length; i++) {
                width += font.width(String.valueOf(characters[i]), last.span.bold);
                if (last.span.bold) {
                    width++;
                }
                if (width > pageWidth) {
                    smallestOverstep = Math.min(width, smallestOverstep);
                    int overflowOffset = lineStart + offset + i - last.start;
                    int breakOffset = overflowOffset + 1;
                    if (!Character.isWhitespace(characters[i])) {
                        breakOffset = iterator.preceding(breakOffset);
                    }
                    if (breakOffset <= lineStart) {
                        breakOffset = overflowOffset - 1;
                    }
                    breakLine(breakOffset);
                    return;
                }
            }
            // Patchouli leaves lineStart unchanged here. On a RESIZE re-layout the
            // stale widthSoFar from the previous pass lands in this branch, and
            // every later break in the paragraph is computed from the wrong offset.
            flush();
            y += LINE_HEIGHT;
        }

        void flush() {
            if (!pending.isEmpty()) {
                int x = pageX;
                for (Tail t : pending) {
                    words.add(t.position(x, y, t.length));
                    x += t.width;
                }
                pending.clear();
            }
        }

        void breakLine(int textOffset) {
            int offset = lineStart;
            int x = pageX;
            int index;
            for (index = 0; index < pending.size(); index++) {
                Tail t = pending.get(index);
                if (offset + t.length >= textOffset) {
                    words.add(t.position(x, y, textOffset - offset));
                    pending.set(index, t.tail(textOffset - offset));
                    break;
                }
                words.add(t.position(x, y, t.length));
                offset += t.length;
                x += t.width;
            }
            for (int i = index - 1; i >= 0; i--) {
                pending.remove(i);
            }
            lineStart = textOffset;
            y += LINE_HEIGHT;
        }

        boolean isNaturalBreak(int paragraph, int offset) {
            return paragraph < iterators.size() && iterators.get(paragraph).isBoundary(offset);
        }
    }

    // ---------------------------------------------------------------- lines

    static final class Line {
        final int y;
        final List<Word> words = new ArrayList<>();
        String text = "";
        int x0;
        int x1;

        Line(int y) {
            this.y = y;
        }

        static List<Line> group(List<Word> words, Font font) {
            Map<Integer, Line> byY = new TreeMap<>();
            for (Word w : words) {
                byY.computeIfAbsent(w.y, Line::new).words.add(w);
            }
            for (Line line : byY.values()) {
                StringBuilder sb = new StringBuilder();
                line.x0 = Integer.MAX_VALUE;
                for (Word w : line.words) {
                    sb.append(w.text);
                    if (!w.text.isEmpty()) {
                        line.x0 = Math.min(line.x0, w.x);
                        line.x1 = Math.max(line.x1, w.x + font.width(w.text.stripTrailing(), w.bold));
                    }
                }
                line.text = sb.toString().replaceAll("§.", "");
                if (line.x0 == Integer.MAX_VALUE) {
                    line.x0 = line.x1;
                }
            }
            return new ArrayList<>(byY.values());
        }

        Word firstVisible() {
            return words.stream().filter(w -> !w.text.isEmpty()).findFirst().orElse(null);
        }

        Word lastVisible() {
            Word last = null;
            for (Word w : words) {
                if (!w.text.isEmpty()) {
                    last = w;
                }
            }
            return last;
        }
    }

    // ---------------------------------------------------------------- json

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    /** Minimal JSON reader; the book files are plain JSON. */
    static final class Json {
        final String s;
        int i;

        Json(String s) {
            this.s = s;
        }

        static Object parse(String s) {
            Json j = new Json(s);
            Object v = j.value();
            j.ws();
            if (j.i != s.length()) {
                throw j.error("trailing data");
            }
            return v;
        }

        Object value() {
            ws();
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                String k = string();
                ws();
                expect(':');
                m.put(k, value());
                ws();
                if (s.charAt(i) == ',') {
                    i++;
                } else {
                    expect('}');
                    return m;
                }
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(value());
                ws();
                if (s.charAt(i) == ',') {
                    i++;
                } else {
                    expect(']');
                    return l;
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(e);
                }
            }
        }

        Number number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (start == i) {
                throw error("unexpected character");
            }
            return Double.parseDouble(s.substring(start, i));
        }

        Object literal(String word, Object v) {
            if (!s.startsWith(word, i)) {
                throw error("expected " + word);
            }
            i += word.length();
            return v;
        }

        void expect(char c) {
            if (s.charAt(i) != c) {
                throw error("expected '" + c + "'");
            }
            i++;
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        IllegalStateException error(String msg) {
            return new IllegalStateException(msg + " at offset " + i);
        }
    }
}
