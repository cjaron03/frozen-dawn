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

    public static final String[] PAGE_TITLES = {
            "UPLINK STATUS",
            "RELAY DIAGNOSTICS",
            "TRANSMISSION QUEUE",
            "SITE MAINTENANCE",
            "LOCAL TOWER LOGS",
            "ORSA COMMAND EYES ONLY"
    };
    public static final int PAGE_COUNT = PAGE_TITLES.length;
    public static final int COMMAND_PAGE = PAGE_COUNT - 1;
    public static final String COMMAND_ARCHIVE_PASSWORD = "BLACKGLASS";
    private static final String PROGRAM_SUCCESS_METRIC = "Program Success Metric: Relocation Throughput";

    private static final LocalDate APOCALYPSE_START = LocalDate.of(2042, 6, 20);
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

        String title = PAGE_TITLES[pageIndex];
        boolean passwordPrompt = pageIndex == COMMAND_PAGE && !commandArchiveUnlocked;
        List<String> bodyLines = switch (pageIndex) {
            case 0 -> buildUplinkStatusPage(tower, towerPos, currentDate, towerSeed);
            case 1 -> buildDiagnosticsPage(currentDate, towerSeed);
            case 2 -> buildTransmissionQueuePage(level, towerSeed);
            case 3 -> buildMaintenancePage(currentDate, towerSeed);
            case 4 -> buildLocalLogsPage(currentDate, towerSeed);
            default -> commandArchiveUnlocked
                    ? buildCommandPage(currentDate, towerSeed)
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

    private static List<String> buildCommandPage(LocalDate currentDate, long towerSeed) {
        List<String> lines = new ArrayList<>();
        lines.add("EXECUTIVE EXTRACT / INTERNAL HOLD");
        lines.add("MARS CIVIC BUILDOUT FAILED TO MEET DEBT SERVICE.");
        lines.add("VOLUNTARY EARTHSIDE TRANSFER NEVER FILLED CAPACITY.");
        lines.add("ATMOSPHERIC INTERVENTION WAS APPROVED AS A DEMAND CORRECTION.");
        lines.add("EARTH HABITABILITY LOSS WAS NOT AN ACCIDENT.");
        lines.add("CLIMATE DESTABILIZATION WAS THE LEVER.");
        lines.add("DISPLACEMENT CURVE NOW TRACKS WITH ORSA MARS OCCUPANCY.");
        lines.add("CIVILIAN LOSS REMAINS WITHIN PROJECTED EXTERNALITIES.");
        lines.add("BOARD DIRECTIVE:");
        lines.add("PRESERVE DENIABILITY / LIMIT ARCHIVE EXPOSURE");
        lines.add("LAST COMMAND REVIEW: " + currentDate.minusDays(5).format(DATE_FULL).toUpperCase(Locale.US));
        return lines;
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
