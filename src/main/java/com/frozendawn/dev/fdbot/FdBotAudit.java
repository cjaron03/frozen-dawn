package com.frozendawn.dev.fdbot;

import com.frozendawn.FrozenDawn;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Audit trail for {@code /fdbot}. Every use is one game-log line prefixed with {@code [FDBOT]}.
 *
 * <p>The in-memory flag is not written to the world and nothing in Maeve or MACS reads it.
 * Decision code is intentionally untouched. Test reports tell a bot-assisted session apart by
 * the log prefix; {@link #isBotAssisted(UUID)} is available for a reader that wants the same
 * fact without parsing the log. The flag resets when the server stops.
 */
public final class FdBotAudit {
    public static final String PREFIX = "[FDBOT]";

    private static final Set<UUID> ASSISTED = ConcurrentHashMap.newKeySet();

    private FdBotAudit() {
    }

    public static boolean isBotAssisted(UUID player) {
        return player != null && ASSISTED.contains(player);
    }

    static void mark(UUID player) {
        if (player != null) {
            ASSISTED.add(player);
        }
    }

    static void log(ServerPlayer player, String args, boolean ok, String result) {
        mark(player.getUUID());
        FrozenDawn.LOGGER.info("{} player={} uuid={} args={} ok={} result={}",
                PREFIX, player.getGameProfile().getName(), player.getUUID(), args, ok, result);
    }

    static void logConsole(String args, String result) {
        FrozenDawn.LOGGER.info("{} player=console uuid= args={} ok=false result={}",
                PREFIX, args, result);
    }
}
