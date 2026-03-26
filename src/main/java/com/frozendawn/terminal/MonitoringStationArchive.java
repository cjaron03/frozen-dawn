package com.frozendawn.terminal;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.world.CampPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class MonitoringStationArchive {

    public static final String[] PAGE_TITLES = {
            "LIVE TELEMETRY",
            "HISTORICAL DATA",
            "NETWORK STATUS",
            "INSTRUMENT STATUS",
            "LOCAL EVENT LOGS"
    };
    public static final int PAGE_COUNT = PAGE_TITLES.length;

    private static final LocalDate APOCALYPSE_START = LocalDate.of(2042, 6, 20);
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("MMM dd", Locale.US);
    private static final DateTimeFormatter DATE_FULL = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.US);
    private static final String[] WIND_DIRECTIONS = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    private MonitoringStationArchive() {
    }

    public static Snapshot create(ServerLevel level, BlockPos stationCenter, int rawPageIndex) {
        ApocalypseState state = ApocalypseState.get(level.getServer());
        int pageIndex = Math.floorMod(rawPageIndex, PAGE_COUNT);
        int totalDays = Math.max(1, state.getTotalDays());
        int currentDay = Math.max(0, Math.min(state.getCurrentDay(), totalDays));
        LocalDate currentDate = APOCALYPSE_START.plusDays(currentDay);
        long stationSeed = level.getSeed() ^ stationCenter.asLong() ^ 0x574558415243484CL;
        CampDirective transferSite = findNearestCamp(level, stationCenter);

        String title = PAGE_TITLES[pageIndex];

        List<String> bodyLines = switch (pageIndex) {
            case 0 -> buildTelemetryPage(stationCenter, stationSeed, currentDay, totalDays, currentDate);
            case 1 -> buildHistoricalPage(stationSeed, totalDays);
            case 2 -> buildNetworkPage(stationSeed, currentDay, currentDate);
            case 3 -> buildInstrumentPage(stationSeed, currentDay, currentDate);
            default -> buildEventPage(stationSeed, currentDay, currentDate);
        };

        List<String> auditLines = buildFooterLines(stationSeed, currentDate, pageIndex, transferSite);
        return new Snapshot(
                title,
                String.join("\n", bodyLines),
                String.join("\n", auditLines),
                pageIndex,
                PAGE_COUNT
        );
    }

    private static List<String> buildTelemetryPage(BlockPos stationCenter, long stationSeed, int currentDay,
                                                   int totalDays, LocalDate currentDate) {
        List<String> lines = new ArrayList<>();
        Metrics today = metricsForDay(currentDay, totalDays, stationSeed);
        Metrics yesterday = metricsForDay(Math.max(0, currentDay - 1), totalDays, stationSeed);

        lines.add("STATION: " + formatStationId(stationCenter));
        lines.add("OBS DATE: " + currentDate.format(DATE_FULL).toUpperCase(Locale.US));
        lines.add(String.format(Locale.US, "TEMP HIGH: %dC   LOW: %dC", today.highC(), today.lowC()));
        lines.add(String.format(Locale.US, "WIND: %d KTS %s   GUSTS: %d KTS", today.windKts(), today.windDir(), today.gustKts()));
        lines.add(String.format(Locale.US, "24H PRECIP: %.1f MM %s", today.precipMm(), today.precipType()));
        lines.add(String.format(Locale.US, "SNOW ACCUM: %d CM", today.snowCm()));
        lines.add(String.format(Locale.US, "PRESSURE: %d MB / %s", today.pressureMb(), pressureTrend(today, yesterday)));
        lines.add("RECENT OBS:");

        for (int i = 0; i < 4; i++) {
            int sampleDay = Math.max(0, currentDay - i);
            Metrics sample = metricsForDay(sampleDay, totalDays, stationSeed);
            LocalDate sampleDate = APOCALYPSE_START.plusDays(sampleDay);
            int hour = 6 + i * 6;
            lines.add(String.format(Locale.US, "%s %02d00Z  %dC  %02dKT %-3s  %dMB",
                    sampleDate.format(DATE_SHORT).toUpperCase(Locale.US),
                    hour,
                    sample.lowC() + Math.max(2, i),
                    sample.windKts(),
                    sample.windDir(),
                    sample.pressureMb()));
        }

        return lines;
    }

    private static List<String> buildHistoricalPage(long stationSeed, int totalDays) {
        List<String> lines = new ArrayList<>();
        lines.add("ARCHIVED CLIMATE COLLAPSE TREND");
        lines.add("DATE        HI / LO    WIND    PRESS");

        int[] sampleDays = {0, 12, 28, 45, 63, 79, 91};
        for (int day : sampleDays) {
            Metrics sample = metricsForDay(day, totalDays, stationSeed);
            LocalDate date = APOCALYPSE_START.plusDays(day);
            lines.add(String.format(Locale.US, "%s  %3d/%3dC  %02dKT %-3s %4d",
                    date.format(DATE_SHORT).toUpperCase(Locale.US),
                    sample.highC(),
                    sample.lowC(),
                    sample.windKts(),
                    sample.windDir(),
                    sample.pressureMb()));
        }

        lines.add("NOTES:");
        lines.add("Sun-angle correction no longer matches field reality.");
        lines.add("Surface inversion deepened week over week.");
        return lines;
    }

    private static List<String> buildNetworkPage(long stationSeed, int currentDay, LocalDate currentDate) {
        List<String> lines = new ArrayList<>();
        Random random = new Random(stationSeed ^ 0x4E4554574F524B4CL);
        LocalDate lastSyncDate = currentDate.minusDays(3L + random.nextInt(9));

        lines.add("UPLINK: OFFLINE / BUFFERING");
        lines.add("LAST SUCCESSFUL SYNC: " + lastSyncDate.format(DATE_FULL).toUpperCase(Locale.US) + " 03:14Z");
        lines.add(String.format(Locale.US, "QUEUED TRANSMISSIONS: %d", 96 + currentDay * 2 + random.nextInt(24)));
        lines.add(String.format(Locale.US, "CHECKSUM FAILURES: %d", 11 + random.nextInt(27)));
        lines.add(String.format(Locale.US, "RETRY BACKLOG: %d BLOCKS", 4 + random.nextInt(6)));
        lines.add("LATEST ERROR:");
        lines.add("ORSA RELAY DID NOT ACKNOWLEDGE PAYLOAD WINDOW");
        lines.add("AUTO-REQUEUE FLAGGED / MANUAL CLEARANCE PENDING");
        lines.add("QUEUE NOTE:");
        lines.add("CIVILIAN OBSERVER TRANSFER DIRECTIVE STILL UNSENT");
        return lines;
    }

    private static List<String> buildInstrumentPage(long stationSeed, int currentDay, LocalDate currentDate) {
        List<String> lines = new ArrayList<>();
        Random random = new Random(stationSeed ^ 0x494E535452554D4CL);
        LocalDate lastCalDate = currentDate.minusDays(8L + random.nextInt(15));

        lines.add("CALIBRATION STATUS");
        lines.add("THERMOMETER A: FAILED BELOW -80C");
        lines.add("THERMOMETER B: HAND-CORRECTED / +/- 2C");
        lines.add(String.format(Locale.US, "ANEMOMETER: ICING FAULT %02d / OVERSPEED EVENTS 3", 12 + random.nextInt(10)));
        lines.add(String.format(Locale.US, "BAROMETER: CAL DRIFT +%.1f MB", 0.4f + random.nextFloat() * 1.2f));
        lines.add("PRECIP GAUGE HEATER: OFFLINE");
        lines.add("SNOW STAKE CAMERA: LENS OBSCURED");
        lines.add("LAST FIELD CAL: " + lastCalDate.format(DATE_FULL).toUpperCase(Locale.US));
        lines.add(String.format(Locale.US, "MAINTENANCE BACKLOG: %d OPEN ITEMS", 4 + currentDay / 18));
        return lines;
    }

    private static List<String> buildEventPage(long stationSeed, int currentDay, LocalDate currentDate) {
        List<String> lines = new ArrayList<>();
        lines.add("LOCALIZED ANOMALY LOG");

        String[] events = {
                "WHITEOUT / VISIBILITY BELOW 2M / 43 MIN",
                "PRESSURE DROP 17MB IN UNDER 4 HRS",
                "WIND SPIKE EXCEEDED CHART LIMITER",
                "RIME ICE FORMED INSIDE SCREEN HOUSING",
                "AURORA VISIBLE AT NOON THROUGH CLOUD",
                "UNIDENTIFIED FOOTFALLS PAST WEST WINDOW"
        };

        for (int i = 0; i < 5; i++) {
            int day = Math.max(0, currentDay - 2 - i * 3);
            LocalDate date = APOCALYPSE_START.plusDays(day);
            Random random = new Random(stationSeed ^ (0x45564E5400L + day * 31L + i));
            String event = events[Math.floorMod(random.nextInt(), events.length)];
            lines.add(date.format(DATE_SHORT).toUpperCase(Locale.US) + "  " + event);
        }

        lines.add("OPERATOR NOTE:");
        lines.add("EVENTS FLAGGED LOCAL / NOT PRESENT IN ORSA PAPERWORK");
        return lines;
    }

    private static List<String> buildFooterLines(long stationSeed, LocalDate currentDate, int pageIndex, CampDirective transferSite) {
        List<String> lines = new ArrayList<>();
        Random random = new Random(stationSeed ^ 0x464F4F5445524CL);
        LocalDate lastLoginDate = currentDate.minusDays(1L + random.nextInt(4));

        lines.add("TRANSFER DIRECTIVE PENDING / PRIORITY RED");
        if (transferSite != null) {
            lines.add("DESIGNATED TRANSFER SITE: FIELD CAMP " + transferSite.designation());
            lines.add(String.format(Locale.US, "COORDS  X:%d  /  Z:%d", transferSite.pos().getX(), transferSite.pos().getZ()));
        } else {
            lines.add("DESIGNATED TRANSFER SITE: NO CAMP RESOLVED");
            lines.add("COORDS  X:---  /  Z:---");
        }
        lines.add("REMAINING CIVILIAN OBSERVERS REPORT FOR TRANSFER");
        lines.add("LAST SUCCESSFUL LOGIN: " + lastLoginDate.format(DATE_SHORT).toUpperCase(Locale.US));
        return lines;
    }

    private static Metrics metricsForDay(int day, int totalDays, long stationSeed) {
        double progress = Math.max(0.0D, Math.min(1.0D, day / (double) totalDays));
        Random random = new Random(stationSeed ^ (day * 341873128712L));

        int high = (int) Math.round(13.0D - progress * 95.0D + wave(day, stationSeed, 0.13D, 6.5D) + random.nextInt(5) - 2);
        int low = high - (8 + (int) Math.round(progress * 18.0D) + random.nextInt(5));
        int wind = Math.max(6, (int) Math.round(9.0D + progress * 52.0D + wave(day, stationSeed, 0.19D, 5.0D) + random.nextInt(4)));
        int gust = wind + 8 + random.nextInt(12);
        double precip = Math.max(0.0D, progress < 0.12D ? 0.0D : progress * 7.2D + random.nextDouble() * 2.6D - 0.8D);
        int snowCm = Math.max(0, (int) Math.round(Math.max(0.0D, progress - 0.18D) * 225.0D + wave(day, stationSeed, 0.07D, 11.0D)));
        int pressure = (int) Math.round(1018.0D - progress * 64.0D + wave(day, stationSeed, 0.11D, 7.0D) + random.nextInt(5) - 2);
        String dir = WIND_DIRECTIONS[Math.floorMod((int) ((stationSeed >>> 4) + day * 3L + random.nextInt(5)), WIND_DIRECTIONS.length)];
        String precipType = progress < 0.24D ? "RAIN" : (progress < 0.55D ? "SLEET" : "ICE PELLETS");
        return new Metrics(high, low, wind, gust, Math.round(precip * 10.0D) / 10.0D, snowCm, pressure, dir, precipType);
    }

    private static String pressureTrend(Metrics today, Metrics yesterday) {
        int delta = today.pressureMb() - yesterday.pressureMb();
        if (delta >= 4) {
            return "RISING SHARP";
        }
        if (delta >= 1) {
            return "RISING";
        }
        if (delta <= -4) {
            return "FALLING SHARP";
        }
        if (delta <= -1) {
            return "FALLING";
        }
        return "STEADY";
    }

    private static String formatStationId(BlockPos stationCenter) {
        int east = Math.abs(stationCenter.getX() / 16);
        int north = Math.abs(stationCenter.getZ() / 16);
        return "WX-" + east + "-" + north;
    }

    private static double wave(int day, long seed, double frequency, double amplitude) {
        return Math.sin(day * frequency + (seed & 0xFF) * 0.071D) * amplitude;
    }

    private static CampDirective findNearestCamp(ServerLevel level, BlockPos origin) {
        long seed = level.getSeed();
        int originRegionX = Math.floorDiv(origin.getX() >> 4, 24);
        int originRegionZ = Math.floorDiv(origin.getZ() >> 4, 24);
        CampDirective nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int drx = -4; drx <= 4; drx++) {
            for (int drz = -4; drz <= 4; drz++) {
                int regionX = originRegionX + drx;
                int regionZ = originRegionZ + drz;
                int[] pos = CampPlacement.getCampBlockPos(seed, regionX, regionZ);
                if (pos == null || !CampPlacement.isEligibleCampSite(level, pos[0], pos[1])) {
                    continue;
                }

                double distSq = origin.distSqr(new BlockPos(pos[0], origin.getY(), pos[1]));
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = new CampDirective(
                            new BlockPos(pos[0], 0, pos[1]),
                            formatCampDesignation(regionX, regionZ)
                    );
                }
            }
        }

        return nearest;
    }

    private static String formatCampDesignation(int regionX, int regionZ) {
        char letter = (char) ('A' + Math.floorMod(regionX, 26));
        int number = Math.floorMod(regionZ, 99) + 1;
        return letter + "-" + String.format(Locale.US, "%02d", number);
    }

    public record Snapshot(String title, String body, String auditLog, int pageIndex, int pageCount) {
    }

    private record Metrics(int highC, int lowC, int windKts, int gustKts, double precipMm, int snowCm,
                           int pressureMb, String windDir, String precipType) {
    }

    private record CampDirective(BlockPos pos, String designation) {
    }
}
