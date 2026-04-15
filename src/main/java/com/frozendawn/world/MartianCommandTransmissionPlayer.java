package com.frozendawn.world;

import com.frozendawn.init.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.UUID;

/**
 * Plays the one-shot Martian Command transmission and feeds timed subtitle/action-bar lines.
 */
public final class MartianCommandTransmissionPlayer {
    private static final List<SubtitleEntry> SUBTITLES = List.of(
            new SubtitleEntry("message.frozendawn.mars_command.subtitle1", 3),
            new SubtitleEntry("message.frozendawn.mars_command.subtitle2", 58),
            new SubtitleEntry("message.frozendawn.mars_command.subtitle3", 76),
            new SubtitleEntry("message.frozendawn.mars_command.subtitle4", 106),
            new SubtitleEntry("message.frozendawn.mars_command.subtitle5", 212),
            new SubtitleEntry("message.frozendawn.mars_command.subtitle6", 298),
            new SubtitleEntry("message.frozendawn.mars_command.subtitle7", 414),
            new SubtitleEntry("message.frozendawn.mars_command.subtitle8", 426)
    );
    private static final int FINISH_TICK = 470;
    private static final int SUBTITLE_REFRESH_TICKS = 35;

    private final ServerLevel level;
    private final UUID playerId;
    private int age;
    private boolean started;
    private boolean done;
    private int lastDisplayedSubtitle = -1;
    private int lastDisplayTick = Integer.MIN_VALUE;

    public MartianCommandTransmissionPlayer(ServerLevel level, ServerPlayer player) {
        this.level = level;
        this.playerId = player.getUUID();
    }

    public void tick() {
        if (done) {
            return;
        }
        ServerPlayer player = getPlayer();
        if (player == null || player.isDeadOrDying() || player.level() != level) {
            done = true;
            return;
        }
        if (!started) {
            started = true;
            player.sendSystemMessage(Component.translatable("message.frozendawn.mars_command.header"));
            player.playNotifySound(ModSounds.RADIO_MARTIAN_COMMAND_MESSAGE.get(), SoundSource.RECORDS, 1.25f, 1.0f);
        }

        int activeSubtitle = activeSubtitleIndex();
        if (activeSubtitle >= 0
                && (activeSubtitle != lastDisplayedSubtitle || age - lastDisplayTick >= SUBTITLE_REFRESH_TICKS)) {
            player.displayClientMessage(Component.translatable(SUBTITLES.get(activeSubtitle).translationKey()), true);
            lastDisplayedSubtitle = activeSubtitle;
            lastDisplayTick = age;
        }

        age++;
        if (age > FINISH_TICK) {
            done = true;
        }
    }

    public boolean isDone() {
        return done;
    }

    private ServerPlayer getPlayer() {
        return level.getServer().getPlayerList().getPlayer(playerId);
    }

    private int activeSubtitleIndex() {
        int result = -1;
        for (int i = 0; i < SUBTITLES.size(); i++) {
            if (age >= SUBTITLES.get(i).startTick()) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }

    private record SubtitleEntry(String translationKey, int startTick) {
    }
}
