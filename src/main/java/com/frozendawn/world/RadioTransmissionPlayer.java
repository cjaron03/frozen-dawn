package com.frozendawn.world;

import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side tick-based sequencer that plays phonetic-fragment SoundEvents
 * through Minecraft's normal sound system to form coordinate readouts.
 * <p>
 * Functional radios play: static → "ORSA" → "field unit" → static →
 * "tower" → "signal" → "at" → "X" → [digits] → "Z" → [digits] → cutoff.
 * <p>
 * Broken radios play: static → "ORSA" → heavy static → "unable" →
 * "no lock" → heavy static → cutoff.
 */
public final class RadioTransmissionPlayer {

    private static final int WORD_GAP_TICKS = 8;
    private static final int STATIC_GAP_TICKS = 20;

    private final ServerLevel level;
    private final BlockPos radioPos;
    private final UUID playerId;
    private final List<TransmissionEntry> sequence;
    private int currentIndex;
    private int ticksUntilNext;
    private boolean done;
    private boolean successful;

    public RadioTransmissionPlayer(ServerLevel level, BlockPos radioPos,
                                   ServerPlayer player, boolean functional,
                                   BlockPos towerPos) {
        this.level = level;
        this.radioPos = radioPos;
        this.playerId = player.getUUID();
        this.sequence = functional
                ? buildFunctionalSequence(towerPos)
                : buildBrokenSequence();
        this.currentIndex = 0;
        this.ticksUntilNext = 10; // initial pause before first sound
    }

    public void tick() {
        if (done) {
            return;
        }

        ServerPlayer player = getPlayer(level);
        if (player == null || player.isDeadOrDying() || player.level() != level) {
            done = true;
            return;
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            return;
        }

        if (currentIndex >= sequence.size()) {
            done = true;
            return;
        }

        TransmissionEntry entry = sequence.get(currentIndex);
        currentIndex++;

        level.playSound(null, radioPos, entry.sound(),
                SoundSource.BLOCKS, entry.volume(),
                entry.pitch());

        // Spark particles on voice clips
        if (entry.isVoice()) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    radioPos.getX() + 0.5, radioPos.getY() + 0.9,
                    radioPos.getZ() + 0.5,
                    1, 0.1, 0.05, 0.1, 0.01);
        }

        ticksUntilNext = entry.delayAfter();

        if (currentIndex >= sequence.size()) {
            done = true;
        }
    }

    public boolean isDone() {
        return done;
    }

    public boolean wasSuccessful() {
        return successful;
    }

    public ServerPlayer getPlayer(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayer(playerId);
    }

    private List<TransmissionEntry> buildFunctionalSequence(BlockPos towerPos) {
        List<TransmissionEntry> seq = new ArrayList<>();

        // Opening static
        seq.add(staticBurst(STATIC_GAP_TICKS));

        // "ORSA" ... "field unit"
        seq.add(voice(ModSounds.RADIO_VOICE_ORSA.get(), WORD_GAP_TICKS));
        seq.add(voice(ModSounds.RADIO_VOICE_FIELD_UNIT.get(), STATIC_GAP_TICKS));

        // Mid static
        seq.add(staticBurst(WORD_GAP_TICKS));

        // "tower" "signal" "at"
        seq.add(voice(ModSounds.RADIO_VOICE_TOWER.get(), WORD_GAP_TICKS));
        seq.add(voice(ModSounds.RADIO_VOICE_SIGNAL.get(), WORD_GAP_TICKS));
        seq.add(voice(ModSounds.RADIO_VOICE_AT.get(), WORD_GAP_TICKS));

        if (towerPos != null) {
            // Signal lock tone
            seq.add(new TransmissionEntry(ModSounds.RADIO_SIGNAL_LOCK.get(),
                    0.5f, 1.0f, WORD_GAP_TICKS, false));

            // "coordinates"
            seq.add(voice(ModSounds.RADIO_VOICE_COORDINATES.get(), STATIC_GAP_TICKS));

            // Mid static
            seq.add(staticMedium(WORD_GAP_TICKS));

            // "X" [digits]
            seq.add(voice(ModSounds.RADIO_VOICE_X_COORD.get(), WORD_GAP_TICKS));
            appendDigits(seq, towerPos.getX());

            // Brief static between coords
            seq.add(staticBurst(WORD_GAP_TICKS));

            // "Z" [digits]
            seq.add(voice(ModSounds.RADIO_VOICE_Z_COORD.get(), WORD_GAP_TICKS));
            appendDigits(seq, towerPos.getZ());

            // "repeat"
            seq.add(staticBurst(WORD_GAP_TICKS));
            seq.add(voice(ModSounds.RADIO_VOICE_REPEAT.get(), WORD_GAP_TICKS));

            // Repeat coordinates
            seq.add(voice(ModSounds.RADIO_VOICE_X_COORD.get(), WORD_GAP_TICKS));
            appendDigits(seq, towerPos.getX());
            seq.add(staticBurst(WORD_GAP_TICKS));
            seq.add(voice(ModSounds.RADIO_VOICE_Z_COORD.get(), WORD_GAP_TICKS));
            appendDigits(seq, towerPos.getZ());

            successful = true;
        } else {
            // No tower resolved yet
            seq.add(voice(ModSounds.RADIO_VOICE_NO_LOCK.get(), WORD_GAP_TICKS));
        }

        // Closing static + cutoff
        seq.add(staticBurst(WORD_GAP_TICKS));
        seq.add(new TransmissionEntry(ModSounds.RADIO_CUTOFF.get(),
                0.5f, 1.0f, 0, false));

        return seq;
    }

    private List<TransmissionEntry> buildBrokenSequence() {
        List<TransmissionEntry> seq = new ArrayList<>();

        // Opening heavy static
        seq.add(staticHeavy(STATIC_GAP_TICKS));

        // Garbled "ORSA"
        seq.add(voice(ModSounds.RADIO_VOICE_ORSA.get(), WORD_GAP_TICKS));

        // Heavy static
        seq.add(staticHeavy(STATIC_GAP_TICKS));

        // "unable"
        seq.add(voice(ModSounds.RADIO_VOICE_UNABLE.get(), WORD_GAP_TICKS));

        // Heavy static
        seq.add(staticHeavy(WORD_GAP_TICKS));

        // "no lock"
        seq.add(voice(ModSounds.RADIO_VOICE_NO_LOCK.get(), STATIC_GAP_TICKS));

        // Final heavy static + cutoff
        seq.add(staticHeavy(WORD_GAP_TICKS));
        seq.add(new TransmissionEntry(ModSounds.RADIO_CUTOFF.get(),
                0.5f, 0.8f, 0, false));

        return seq;
    }

    /**
     * Decomposes a coordinate into individual digits and appends voice events.
     * For example, 1234 becomes "one" "two" "three" "four".
     * Negative values are preceded by "negative".
     */
    private void appendDigits(List<TransmissionEntry> seq, int value) {
        if (value < 0) {
            seq.add(voice(ModSounds.RADIO_VOICE_NEGATIVE.get(), WORD_GAP_TICKS));
            value = Math.abs(value);
        }

        String digits = String.valueOf(value);
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(i) - '0';
            SoundEvent digitSound = digitToSound(digit);
            int delay = (i < digits.length() - 1) ? WORD_GAP_TICKS - 2 : WORD_GAP_TICKS;
            seq.add(voice(digitSound, delay));
        }
    }

    private static SoundEvent digitToSound(int digit) {
        return switch (digit) {
            case 0 -> ModSounds.RADIO_VOICE_ZERO.get();
            case 1 -> ModSounds.RADIO_VOICE_ONE.get();
            case 2 -> ModSounds.RADIO_VOICE_TWO.get();
            case 3 -> ModSounds.RADIO_VOICE_THREE.get();
            case 4 -> ModSounds.RADIO_VOICE_FOUR.get();
            case 5 -> ModSounds.RADIO_VOICE_FIVE.get();
            case 6 -> ModSounds.RADIO_VOICE_SIX.get();
            case 7 -> ModSounds.RADIO_VOICE_SEVEN.get();
            case 8 -> ModSounds.RADIO_VOICE_EIGHT.get();
            case 9 -> ModSounds.RADIO_VOICE_NINE.get();
            default -> ModSounds.RADIO_VOICE_ZERO.get();
        };
    }

    private static TransmissionEntry voice(SoundEvent sound, int delayAfter) {
        return new TransmissionEntry(sound, 0.7f, 1.0f, delayAfter, true);
    }

    private static TransmissionEntry staticBurst(int delayAfter) {
        return new TransmissionEntry(ModSounds.RADIO_STATIC_BURST.get(),
                0.4f, 0.8f, delayAfter, false);
    }

    private static TransmissionEntry staticMedium(int delayAfter) {
        return new TransmissionEntry(ModSounds.RADIO_STATIC_MEDIUM.get(),
                0.35f, 0.9f, delayAfter, false);
    }

    private static TransmissionEntry staticHeavy(int delayAfter) {
        return new TransmissionEntry(ModSounds.RADIO_STATIC_HEAVY.get(),
                0.5f, 0.7f, delayAfter, false);
    }

    private record TransmissionEntry(SoundEvent sound, float volume, float pitch,
                                     int delayAfter, boolean isVoice) {
    }
}
