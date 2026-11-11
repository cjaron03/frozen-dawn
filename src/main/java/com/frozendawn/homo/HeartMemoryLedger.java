package com.frozendawn.homo;

import com.frozendawn.data.ReturnedHearthSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.ArrayList;
import java.util.List;

/**
 * The hive's account of one player, as the Heart's last memory node reads it back: what
 * the Hearth ledgers wrote down, never why (The Edge §13.2, §13.13).
 */
public record HeartMemoryLedger(
        int contactDay,
        int cameBack,
        int violationDay,
        int casualties,
        int casualtyDay,
        int deaths,
        boolean massacre) {
    public static final HeartMemoryLedger EMPTY = new HeartMemoryLedger(0, 0, 0, 0, 0, 0, false);
    private static final String NODE = "overlay.frozendawn.heart_memory.node_5.";
    private static final String NUMBER = "overlay.frozendawn.number.";
    private static final int NUMBER_WORDS = 12;
    private static final long DAY_TICKS = 24_000L;

    /** One line of the panel: a lang key and its arguments; a nested line is a translated word. */
    public record MemoryLine(String key, List<Object> args) {
        public MemoryLine(String key, Object... args) {
            this(key, List.of(args));
        }
    }

    public List<MemoryLine> lines() {
        List<MemoryLine> lines = new ArrayList<>();
        if (contactDay > 0) {
            lines.add(new MemoryLine(NODE + "arrived", contactDay));
        }
        if (cameBack >= 2) {
            lines.add(new MemoryLine(NODE + "came_back", cameBack));
        }
        boolean violent = violationDay > 0 || casualties > 0 || massacre;
        if (violationDay > 0) {
            lines.add(new MemoryLine(NODE + "crossed", violationDay));
        }
        if (massacre) {
            lines.add(new MemoryLine(NODE + "lost_all", lostDay()));
        } else if (casualties > 0) {
            lines.add(new MemoryLine(NODE + "lost", lostDay(), count(casualties)));
        }
        if (massacre || casualties > 0) {
            lines.add(new MemoryLine(NODE + "you_did"));
        }
        if (!violent && deaths == 1) {
            lines.add(new MemoryLine(NODE + "watched_once"));
        } else if (!violent && deaths >= 2) {
            lines.add(new MemoryLine(NODE + "watched", deaths, deaths));
        }
        lines.add(new MemoryLine(NODE + "erased"));
        return List.copyOf(lines);
    }

    /** The day a game time falls on, counting from Day 1. */
    public static int day(long gameTime) {
        return (int) Math.max(1L, Math.floorDiv(gameTime, DAY_TICKS) + 1L);
    }

    /** Snapshot the ledgers for this player; call before node five erases them. */
    public static HeartMemoryLedger of(ServerPlayer player) {
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(player.getServer());
        ReturnedHearthSavedData.PlayerHiveMemory memory =
                data.playerMemory(player.getUUID()).orElse(null);
        long violation = data.firstViolationGameTime(player.getUUID());
        int deaths = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        boolean massacre = HearthMemoryManager.isPermanentOrsathae(
                player.serverLevel(), player.getUUID());
        if (memory == null) {
            return new HeartMemoryLedger(0, 0, violation < 0L ? 0 : day(violation),
                    0, 0, deaths, massacre);
        }
        return new HeartMemoryLedger(
                memory.firstContactGameTime() < 0L ? 0 : day(memory.firstContactGameTime()),
                Math.max(0, memory.totalVisits() - 1),
                violation < 0L ? 0 : day(violation),
                memory.congregationCasualties(),
                memory.lastCongregationCasualtyGameTime() < 0L
                        ? 0 : day(memory.lastCongregationCasualtyGameTime()),
                deaths,
                massacre);
    }

    /** The casualty day, or the nearest earlier mark when a massacre left none recorded. */
    private int lostDay() {
        return casualtyDay > 0 ? casualtyDay
                : violationDay > 0 ? violationDay
                : Math.max(1, contactDay);
    }

    private static Object count(int n) {
        return n <= NUMBER_WORDS ? new MemoryLine(NUMBER + n) : n;
    }
}
