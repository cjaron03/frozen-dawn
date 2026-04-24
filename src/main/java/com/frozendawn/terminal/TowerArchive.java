package com.frozendawn.terminal;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.world.BlastPitPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class TowerArchive {

    private static final String[] DIRECTORY_PAGE_TITLES = {
            "UPLINK STATUS",
            "RELAY DIAGNOSTICS",
            "TRANSMISSION QUEUE",
            "SITE MAINTENANCE",
            "LOCAL TOWER LOGS",
            "ORSA COMMAND EYES ONLY"
    };
    private static final String[] BLACKGLASS_SEGMENT_TITLES = {
            "RECOVERY INDEX",
            "EXODUS LOSSES",
            "CREATE DEMAND",
            "BLACKGLASS ARRAYS",
            "SURVIVOR LEVERAGE",
            "MARS COMMAND",
            "DENIABILITY",
            "BOARD MOTION"
    };
    private static final String[] BLACKGLASS_SEGMENT_TIMECODES = {
            "00:00", "00:14", "01:54", "03:00", "04:11", "05:34", "06:45", "07:39"
    };
    public static final int DIRECTORY_PAGE_COUNT = DIRECTORY_PAGE_TITLES.length;
    public static final int COMMAND_PAGE = DIRECTORY_PAGE_COUNT - 1;
    public static final int PAGE_COUNT = COMMAND_PAGE + BLACKGLASS_SEGMENT_TITLES.length;
    public static final String[] PAGE_TITLES = buildPageTitles();
    public static final String COMMAND_ARCHIVE_PASSWORD = "BLACKGLASS";
    private static final String PROGRAM_SUCCESS_METRIC = "Program Success Metric: Relocation Throughput";
    public static final String BLACKGLASS_TITLE = "BLACKGLASS ARCHIVE RECOVERY";

    private static final LocalDate APOCALYPSE_START = LocalDate.of(2077, 6, 20);
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("MMM dd", Locale.US);
    private static final DateTimeFormatter DATE_FULL = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.US);

    private TowerArchive() {
    }

    public static Snapshot create(ServerLevel level, OrsaStructureState.TowerRecord tower, int rawPageIndex,
                                  boolean commandArchiveUnlocked, String commandAuthStatus) {
        ApocalypseState apocalypseState = ApocalypseState.get(level.getServer());
        int totalDays = Math.max(1, apocalypseState.getTotalDays());
        int currentDay = Math.max(0, Math.min(apocalypseState.getCurrentDay(), totalDays));
        LocalDate currentDate = APOCALYPSE_START.plusDays(currentDay);
        BlockPos towerPos = tower.anchorPos();
        long towerSeed = level.getSeed() ^ tower.id() ^ towerPos.asLong() ^ 0x544F574152434849L;
        int pageIndex = Math.floorMod(rawPageIndex, PAGE_COUNT);

        String title = archiveTitle(pageIndex, commandArchiveUnlocked);
        boolean passwordPrompt = pageIndex >= COMMAND_PAGE && !commandArchiveUnlocked;
        List<String> bodyLines = switch (pageIndex) {
            case 0 -> buildUplinkStatusPage(tower, towerPos, currentDate, towerSeed);
            case 1 -> buildDiagnosticsPage(currentDate, towerSeed);
            case 2 -> buildTransmissionQueuePage(level, towerSeed);
            case 3 -> buildMaintenancePage(currentDate, towerSeed);
            case 4 -> buildLocalLogsPage(currentDate, towerSeed);
            default -> commandArchiveUnlocked
                    ? buildCommandSegmentPage(pageIndex - COMMAND_PAGE)
                    : buildCommandPromptPage(commandAuthStatus);
        };

        List<String> footerLines = buildFooterLines(currentDate, towerSeed, level);
        return new Snapshot(
                title,
                String.join("\n", bodyLines),
                String.join("\n", footerLines),
                pageIndex,
                PAGE_COUNT,
                passwordPrompt
        );
    }

    private static List<String> buildUplinkStatusPage(OrsaStructureState.TowerRecord tower, BlockPos towerPos,
                                                      LocalDate currentDate, long towerSeed) {
        Random random = new Random(towerSeed ^ 0x55504C494E4B4CL);
        List<String> lines = new ArrayList<>();
        lines.add("NODE: " + formatTowerId(tower));
        lines.add(String.format(Locale.US, "ANCHOR: X:%d  Y:%d  Z:%d", towerPos.getX(), towerPos.getY(), towerPos.getZ()));
        lines.add("ARRAY STATE: ALIGNED / STORE-AND-FORWARD");
        lines.add(String.format(Locale.US, "LAST EARTHSIDE ACK: %s 19:%02dZ",
                currentDate.minusDays(1).format(DATE_FULL).toUpperCase(Locale.US), 10 + random.nextInt(40)));
        lines.add("SATELLITE HANDSHAKE: LOST");
        lines.add(String.format(Locale.US, "PHASE ERROR: %.2f DEG", 0.38 + random.nextDouble() * 1.71));
        lines.add(String.format(Locale.US, "QUEUE DEPTH: %d PRIORITY BUNDLES", 14 + random.nextInt(9)));
        lines.add("ROUTING CLASSES:");
        lines.add("GROUNDSIDE THERMAL RECEIVER");
        lines.add("SOL-04 CIVIC INTAKE RELAY");
        lines.add(PROGRAM_SUCCESS_METRIC);
        return lines;
    }

    private static List<String> buildDiagnosticsPage(LocalDate currentDate, long towerSeed) {
        Random random = new Random(towerSeed ^ 0x444941474E4F534CL);
        List<String> lines = new ArrayList<>();
        lines.add("RELAY HEALTH SNAPSHOT");
        lines.add(String.format(Locale.US, "TRANSPONDER BIAS: +%.2f DB", 2.15 + random.nextDouble() * 1.4));
        lines.add(String.format(Locale.US, "DISH SERVO DRIFT: %.2f ARC-MIN", 0.9 + random.nextDouble() * 1.8));
        lines.add(String.format(Locale.US, "CRYO CAP BANK: %d%%", 21 + random.nextInt(17)));
        lines.add(String.format(Locale.US, "CHECKSUM FAILURES: %d", 17 + random.nextInt(16)));
        lines.add(String.format(Locale.US, "RETRY LATENCY: %d MS AVG", 820 + random.nextInt(540)));
        lines.add("ENCRYPTION LAYER: VALID / REMOTE PEER ABSENT");
        lines.add("AUTOMATED NOTE:");
        lines.add("NO UPLINK PARTNER HAS ANSWERED SINCE " + currentDate.minusDays(2).format(DATE_SHORT).toUpperCase(Locale.US));
        return lines;
    }

    private static List<String> buildTransmissionQueuePage(ServerLevel level, long towerSeed) {
        Random random = new Random(towerSeed ^ 0x5155455545545854L);
        BlockPos blastTarget = BlastPitPlacement.ensureBlastPitResolved(level);
        List<String> lines = new ArrayList<>();
        lines.add("PRIORITY BUNDLE A / EARTHSIDE");
        lines.add("TARGET CLASS: SUBSURFACE THERMAL RECEIVER");
        if (blastTarget != null) {
            lines.add(String.format(Locale.US, "GROUND REF: X:%d  Y:%d  Z:%d",
                    blastTarget.getX(), blastTarget.getY(), blastTarget.getZ()));
        } else {
            lines.add("GROUND REF: X:---  Y:---  Z:---");
        }
        lines.add(String.format(Locale.US, "BURIAL MODEL: %dM BELOW GRADE", 86 + random.nextInt(18)));
        lines.add("PAYLOAD: IGNITER BUS / CORE CURVE / HEAT SURVEY");
        lines.add("STATUS: QUEUED / NEVER ACKNOWLEDGED");
        lines.add("");
        lines.add("PRIORITY BUNDLE B / INTERPLANETARY");
        lines.add("TARGET CLASS: ARES CIVIC INTAKE ARRAY");
        lines.add("PRIMARY BODY: MARS");
        lines.add("SOLAR INDEX: FOURTH PLANET FROM SOL");
        lines.add("SEMI-MAJOR AXIS: 1.523679 AU");
        lines.add("PERIHELION: 206.7M KM / APHELION: 249.2M KM");
        lines.add("QUEUE NOTE: CIVIC BERTHS HELD OPEN");
        return lines;
    }

    private static List<String> buildMaintenancePage(LocalDate currentDate, long towerSeed) {
        Random random = new Random(towerSeed ^ 0x4D41494E544E434CL);
        List<String> lines = new ArrayList<>();
        lines.add("SERVICE BACKLOG");
        lines.add("LADDER RUN 03: ICE BUILDUP / MANUAL CHIP REQUIRED");
        lines.add("DISH ARRAY: STARBOARD TENSIONER OVER SPEC");
        lines.add(String.format(Locale.US, "HEATER BANK: %d OF 4 CELLS ONLINE", 1 + random.nextInt(2)));
        lines.add("TOP ROOM FILTERS: CLOGGED WITH METAL DUST");
        lines.add("POWER NOTE:");
        lines.add("KEEP LOCAL BATTERY RESERVE ABOVE 18% BEFORE RETRY");
        lines.add("LAST CREWED SERVICE: " + currentDate.minusDays(6).format(DATE_FULL).toUpperCase(Locale.US));
        return lines;
    }

    private static List<String> buildLocalLogsPage(LocalDate currentDate, long towerSeed) {
        String[] entries = {
                "ICE STATIC WALKED THE ARRAY FOR 11 MIN",
                "GROUND POWER SAGGED / AUTO BUFFER HELD",
                "UNKNOWN LIGHT SOUTH OF FENCE / NO TRANSPONDER",
                "QUEUE RETRIED TWELVE TIMES WITH NO REMOTE ANSWER",
                "WIND LOAD FORCED MANUAL LOCK ON NORTH DISH BRACE",
                "SOMETHING HIT THE LOWER STAIRS AFTER MIDNIGHT"
        };
        List<String> lines = new ArrayList<>();
        lines.add("LOCAL OPERATIONS LOG");
        for (int i = 0; i < 5; i++) {
            LocalDate date = currentDate.minusDays(i * 2L);
            Random random = new Random(towerSeed ^ (0x4C4F475300L + i * 131L));
            lines.add(date.format(DATE_SHORT).toUpperCase(Locale.US) + "  " + entries[random.nextInt(entries.length)]);
        }
        lines.add("HUMAN NOTE:");
        lines.add("IF THE QUEUE MOVES WITHOUT ME, IT WASN'T ME.");
        return lines;
    }

    private static List<String> buildCommandPromptPage(String authStatus) {
        List<String> lines = new ArrayList<>();
        lines.add("ORSA COMMAND EYES ONLY");
        lines.add("LEVEL SIGMA AUTHORIZATION REQUIRED");
        lines.add("EXECUTIVE LIABILITY MATERIAL");
        lines.add("UNAUTHORIZED ACCESS WILL BE LOGGED");
        if (authStatus != null && !authStatus.isBlank()) {
            lines.add(authStatus);
        }
        lines.add("ENTER OVERRIDE BELOW");
        return lines;
    }

    private static List<String> buildCommandSegmentPage(int rawSegmentIndex) {
        int segmentIndex = Math.floorMod(rawSegmentIndex, BLACKGLASS_SEGMENT_TITLES.length);
        return switch (segmentIndex) {
            case 0 -> List.of(
                    "BLACKGLASS ARCHIVE RECOVERY                                16:02:46 [235/1873]",
                    "ORSA EXECUTIVE BOARD - STRATEGIC CONTINUITY SESSION 14-B",
                    "DATE: NINE MONTHS BEFORE GLOBAL PHASE DECLARATION",
                    "STATUS: DECRYPTED AUDIO TRANSCRIPT",
                    "SOURCE: CONFERENCE TABLE RECORDER, EXECUTIVE LEVEL",
                    "",
                    "[00:00:03] Recording begins.",
                    "",
                    "[00:00:07] CHAIRMAN VALE:",
                    "This session is off calendar. No assistants, no minutes, no compliance observer."
            );
            case 1 -> List.of(
                    "[00:00:14] CFO REN:",
                    "Then I'll be direct. Exodus is bleeding us.",
                    "",
                    "[00:00:18] MISSION DIRECTOR HOLLIS:",
                    "The fleet is built.",
                    "",
                    "[00:00:20] CFO REN:",
                    "The fleet is idle. Mars has oxygen, water, soil stabilization, orbital shielding,",
                    "and nobody willing to pay relocation rates because Earth still looks survivable",
                    "on a quarterly chart.",
                    "",
                    "[00:00:34] LEGAL DIRECTOR SATO:",
                    "Careful with that wording.",
                    "",
                    "[00:00:37] CFO REN:",
                    "Fine. Demand is below projection.",
                    "",
                    "[00:00:42] COLONIAL ASSETS DIRECTOR KLINE:",
                    "We promised investors a migration market. We promised governments triage",
                    "infrastructure. We promised exclusive development rights on Mars before the",
                    "first civilian charter.",
                    "",
                    "[00:00:55] CHAIRMAN VALE:",
                    "And we have them.",
                    "",
                    "[00:00:57] KLINE:",
                    "On paper. Paper does not fill seats.",
                    "",
                    "[00:01:03] HOLLIS:",
                    "People do not abandon a planet because another one is available. They abandon",
                    "it when staying becomes irrational.",
                    "",
                    "[00:01:13] Silence.",
                    "",
                    "[00:01:19] SATO:",
                    "Say what you mean.",
                    "",
                    "[00:01:24] HOLLIS:",
                    "We have spent twelve years selling rescue to people who do not believe they",
                    "need rescuing.",
                    "",
                    "[00:01:33] CFO REN:",
                    "Because they don't.",
                    "",
                    "[00:01:36] HOLLIS:",
                    "Not yet.",
                    "",
                    "[00:01:41] CHAIRMAN VALE:",
                    "The climate models already show instability.",
                    "",
                    "[00:01:45] HOLLIS:",
                    "Instability is not panic. Instability is debate. Debate is delay. Delay kills",
                    "Exodus."
            );
            case 2 -> List.of(
                    "[00:01:54] KLINE:",
                    "So what are you proposing?",
                    "",
                    "[00:02:01] HOLLIS:",
                    "We create demand.",
                    "",
                    "[00:02:06] Silence.",
                    "",
                    "[00:02:13] SATO:",
                    "That sentence cannot exist in discovery.",
                    "",
                    "[00:02:17] HOLLIS:",
                    "Then delete it from discovery.",
                    "",
                    "[00:02:23] CFO REN:",
                    "Define demand.",
                    "",
                    "[00:02:27] HOLLIS:",
                    "Cold is persuasive. Hunger is persuasive. A failed grid is persuasive. A",
                    "government with twenty million freezing citizens does not negotiate seat",
                    "pricing. It signs.",
                    "",
                    "[00:02:43] CHAIRMAN VALE:",
                    "We are not discussing an extinction event.",
                    "",
                    "[00:02:47] HOLLIS:",
                    "No. We are discussing urgency.",
                    "",
                    "[00:02:51] SATO:",
                    "Manufactured urgency.",
                    "",
                    "[00:02:54] HOLLIS:",
                    "Managed urgency."
            );
            case 3 -> List.of(
                    "[00:03:00] KLINE:",
                    "The atmospheric intervention project was shelved.",
                    "",
                    "[00:03:05] HOLLIS:",
                    "Publicly. The blackglass arrays were never dismantled. The polar injection",
                    "stations were never decommissioned. The geothermal dampers still answer to",
                    "ORSA keys.",
                    "",
                    "[00:03:18] CFO REN:",
                    "You are talking about lowering the global thermal floor.",
                    "",
                    "[00:03:23] HOLLIS:",
                    "Temporarily.",
                    "",
                    "[00:03:25] CFO REN:",
                    "How temporarily?",
                    "",
                    "[00:03:28] HOLLIS:",
                    "Long enough.",
                    "",
                    "[00:03:31] SATO:",
                    "Long enough for what?",
                    "",
                    "[00:03:34] HOLLIS:",
                    "For Earth to become the problem and Mars to become the solution.",
                    "",
                    "[00:03:42] Silence.",
                    "",
                    "[00:03:49] CHAIRMAN VALE:",
                    "Casualties?",
                    "",
                    "[00:03:54] HOLLIS:",
                    "If evacuation begins at Phase Two, controllable. If governments delay, severe.",
                    "",
                    "[00:04:03] KLINE:",
                    "They will delay.",
                    "",
                    "[00:04:06] HOLLIS:",
                    "Then they will sign faster the second time."
            );
            case 4 -> List.of(
                    "[00:04:11] CFO REN:",
                    "This is not rescue anymore.",
                    "",
                    "[00:04:15] HOLLIS:",
                    "It was never rescue. Rescue is what you call a product when the customer is",
                    "afraid.",
                    "",
                    "[00:04:26] SATO:",
                    "I want it recorded that Legal objects to any language implying causation.",
                    "",
                    "[00:04:32] CHAIRMAN VALE:",
                    "Recorded where?",
                    "",
                    "[00:04:36] Silence.",
                    "",
                    "[00:04:41] SATO:",
                    "Point taken.",
                    "",
                    "[00:04:47] KLINE:",
                    "And the people who cannot pay?",
                    "",
                    "[00:04:51] CFO REN:",
                    "Governments subsidize essential personnel. Labor cohorts. Breeding demographics.",
                    "Technical classes.",
                    "",
                    "[00:04:59] HOLLIS:",
                    "The rest become leverage.",
                    "",
                    "[00:05:04] CHAIRMAN VALE:",
                    "No.",
                    "",
                    "[00:05:06] HOLLIS:",
                    "Yes. The people left behind are not a failure of Exodus. They are proof of need.",
                    "",
                    "[00:05:17] KLINE:",
                    "You are talking about turning survivors into advertising.",
                    "",
                    "[00:05:22] HOLLIS:",
                    "I am talking about conversion metrics."
            );
            case 5 -> List.of(
                    "[00:05:27] Silence.",
                    "",
                    "[00:05:34] CFO REN:",
                    "What about Mars Command?",
                    "",
                    "[00:05:37] HOLLIS:",
                    "They do not need to know the trigger mechanism.",
                    "",
                    "[00:05:41] SATO:",
                    "They will know Earth froze.",
                    "",
                    "[00:05:44] HOLLIS:",
                    "Everyone will know Earth froze.",
                    "",
                    "[00:05:48] SATO:",
                    "And if someone on Mars asks why?",
                    "",
                    "[00:05:52] CHAIRMAN VALE:",
                    "Then we give them the same answer we give Earth.",
                    "",
                    "[00:05:58] KLINE:",
                    "Natural cascade.",
                    "",
                    "[00:06:01] CFO REN:",
                    "Solar minimum.",
                    "",
                    "[00:06:06] HOLLIS:",
                    "Satellite failure, if they still believe in one.",
                    "",
                    "[00:06:12] CHAIRMAN VALE:",
                    "And Kevin?",
                    "",
                    "[00:06:16] Silence.",
                    "",
                    "[00:06:22] HOLLIS:",
                    "Kevin is useful where he is.",
                    "",
                    "[00:06:26] CFO REN:",
                    "He knows too much.",
                    "",
                    "[00:06:29] HOLLIS:",
                    "Exactly. Put him in the public failure path. Let him chase the signal. Let him",
                    "write warnings nobody receives. History loves a dead whistleblower. It hates a",
                    "living accountant."
            );
            case 6 -> List.of(
                    "[00:06:45] SATO:",
                    "That is grotesque.",
                    "",
                    "[00:06:48] HOLLIS:",
                    "That is governance.",
                    "",
                    "[00:06:55] CHAIRMAN VALE:",
                    "If this proceeds, the board needs deniability.",
                    "",
                    "[00:07:00] SATO:",
                    "The board needs innocence.",
                    "",
                    "[00:07:04] CFO REN:",
                    "The board needs solvency.",
                    "",
                    "[00:07:09] KLINE:",
                    "And Mars?",
                    "",
                    "[00:07:12] HOLLIS:",
                    "Mars gets settlers. Investors get ownership. Earth gets a story simple enough",
                    "to survive the people who die in it.",
                    "",
                    "[00:07:26] Silence."
            );
            default -> List.of(
                    "[00:07:39] CHAIRMAN VALE:",
                    "Motion language?",
                    "",
                    "[00:07:43] SATO:",
                    "\"Authorize acceleration of atmospheric stabilization dependencies in support of",
                    "Exodus readiness.\"",
                    "",
                    "[00:07:53] CFO REN:",
                    "That means nothing.",
                    "",
                    "[00:07:56] SATO:",
                    "Correct.",
                    "",
                    "[00:08:02] CHAIRMAN VALE:",
                    "All in favor?",
                    "",
                    "[00:08:08] Multiple voices:",
                    "Aye.",
                    "",
                    "[00:08:13] SATO:",
                    "Abstain.",
                    "",
                    "[00:08:16] CHAIRMAN VALE:",
                    "The motion carries.",
                    "",
                    "[00:08:22] HOLLIS:",
                    "When the cold comes, they will call us murderers if they know.",
                    "",
                    "[00:08:28] CHAIRMAN VALE:",
                    "Then make sure they call us saviors first.",
                    "",
                    "[00:08:36] Recording ends."
            );
        };
    }

    public static String directoryTitle(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= DIRECTORY_PAGE_TITLES.length) {
            return "ARCHIVE PAGE " + (pageIndex + 1);
        }
        return DIRECTORY_PAGE_TITLES[pageIndex];
    }

    public static String archiveTitle(int pageIndex, boolean commandArchiveUnlocked) {
        if (pageIndex >= COMMAND_PAGE && commandArchiveUnlocked) {
            return BLACKGLASS_TITLE;
        }
        if (pageIndex >= 0 && pageIndex < DIRECTORY_PAGE_TITLES.length) {
            return DIRECTORY_PAGE_TITLES[pageIndex];
        }
        return "ARCHIVE PAGE " + (pageIndex + 1);
    }

    public static boolean isBlackglassPage(int pageIndex, boolean passwordPrompt) {
        return pageIndex >= COMMAND_PAGE && !passwordPrompt;
    }

    public static int blackglassSegmentIndex(int pageIndex) {
        return Math.floorMod(pageIndex - COMMAND_PAGE, BLACKGLASS_SEGMENT_TITLES.length);
    }

    public static int blackglassSegmentCount() {
        return BLACKGLASS_SEGMENT_TITLES.length;
    }

    public static String blackglassSegmentTitle(int segmentIndex) {
        return BLACKGLASS_SEGMENT_TITLES[Math.floorMod(segmentIndex, BLACKGLASS_SEGMENT_TITLES.length)];
    }

    public static String blackglassSegmentTimecode(int segmentIndex) {
        return BLACKGLASS_SEGMENT_TIMECODES[Math.floorMod(segmentIndex, BLACKGLASS_SEGMENT_TIMECODES.length)];
    }

    private static String[] buildPageTitles() {
        String[] titles = new String[PAGE_COUNT];
        System.arraycopy(DIRECTORY_PAGE_TITLES, 0, titles, 0, DIRECTORY_PAGE_TITLES.length);
        for (int i = 1; i < BLACKGLASS_SEGMENT_TITLES.length; i++) {
            titles[COMMAND_PAGE + i] = BLACKGLASS_SEGMENT_TITLES[i];
        }
        return titles;
    }

    private static List<String> buildFooterLines(LocalDate currentDate, long towerSeed, ServerLevel level) {
        Random random = new Random(towerSeed ^ 0x464F4F54455254L);
        int queueDepth = 14 + random.nextInt(9);
        List<String> lines = new ArrayList<>();
        lines.add("ORSA PRIORITY ROUTING SUMMARY");
        lines.add("LAST HANDSHAKE: SEP 20");
        lines.add("QUEUE DEPTH: " + queueDepth + " / EXEC HOLD ACTIVE");
        lines.add("GROUND RECEIVER: BUFFERING");
        lines.add("SOL-04 ROUTE: OPEN / REMOTE RESPONSE ABSENT");
        return lines;
    }

    private static String formatTowerId(OrsaStructureState.TowerRecord tower) {
        int sector = tower.sectorIndex();
        char letter = (char) ('A' + Math.floorMod(sector, 26));
        int number = Math.floorMod(sector / 3, 99) + 1;
        return "CT-" + letter + "-" + String.format(Locale.US, "%02d", number);
    }

    public record Snapshot(String title, String body, String auditLog, int pageIndex, int pageCount,
                           boolean passwordPrompt) {
    }
}
