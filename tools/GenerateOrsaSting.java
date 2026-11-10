import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Random;

/**
 * The first boot's ORSA sting: a synthetic female voice sings the two syllables, "OR" on A4 rising a fourth to "SA" on
 * D5, over a soft female harmony (A major, then D major), with a bell at the rise and a low pad under it. Each voice is
 * additive: a falling harmonic series shaped by three female vowel formants, which glide from "o" through "r" to "a",
 * with a little breath; the "s" is filtered noise. It starts as the boot icon appears. Frozen
 * Dawn original, made from nothing but this code. The raw render is dry; tools/generate_orsa_sting.sh adds the room
 * and encodes it.
 *
 * Usage: java tools/GenerateOrsaSting.java <output wav>
 */
public final class GenerateOrsaSting {
    static final int RATE = 44100;
    static final double LENGTH = 4.0;

    // The syllables' times, in seconds.
    private static final double R_START = 0.42, R_END = 0.70;
    private static final double S_START = 0.74, S_PEAK = 0.84, S_END = 0.98;
    private static final double SA_START = 0.90, SA_PEAK = 1.45, SA_RELEASE = 2.25;

    // The lead sings the top line; the harmony sits under it, quieter.
    private static final double OR_LEAD = 440.00;                                     // A4
    private static final double SA_LEAD = 587.33;                                     // D5
    private static final double[] OR_CHORD = {220.00, 329.63, 554.37};                // A3 E4 C#5
    private static final double[] SA_CHORD = {293.66, 440.00, 739.99};                // D4 A4 F#5
    private static final double[] DETUNE_CENTS = {-6, 6};

    // Female formants (Hz) for "o", "r" and "a".
    private static final double[] O = {590, 920, 2710};
    private static final double[] R = {500, 1640, 1960};
    private static final double[] A_DARK = {800, 1150, 2800};
    private static final double[] A = {880, 1240, 2810};
    private static final double[] BANDWIDTH = {100, 120, 180};

    public static void main(String[] args) throws IOException {
        int n = (int) (RATE * LENGTH);
        double[] left = new double[n];
        double[] right = new double[n];
        Random random = new Random(0x0125A);
        voice(left, right, OR_LEAD, 0, 0, 0.3, true, 1.0, random);
        voice(left, right, SA_LEAD, 0, 0, 1.1, false, 1.0, random);
        for (int note = 0; note < OR_CHORD.length; note++) {
            for (int v = 0; v < DETUNE_CENTS.length; v++) {
                voice(left, right, OR_CHORD[note], DETUNE_CENTS[v], pan(note, v), random.nextDouble() * 6.28, true,
                        0.16, random);
            }
        }
        for (int note = 0; note < SA_CHORD.length; note++) {
            for (int v = 0; v < DETUNE_CENTS.length; v++) {
                voice(left, right, SA_CHORD[note], DETUNE_CENTS[v], pan(note, v), random.nextDouble() * 6.28, false,
                        0.16, random);
            }
        }
        sibilant(left, right, random);
        bell(left, right);
        pad(left, right);
        normalize(left, right, 0.89);
        Files.write(new File(args[0]).toPath(), wav(left, right));
    }

    private static double pan(int note, int voice) {
        return Math.max(-0.7, Math.min(0.7, (voice == 0 ? -0.45 : 0.45) + (note % 2 == 0 ? -0.1 : 0.1)));
    }

    /** One choir voice: harmonics under the vowel's formants, with vibrato and its syllable's envelope. */
    private static void voice(double[] left, double[] right, double f0, double cents, double pan, double phase0,
                              boolean or, double level, Random random) {
        double base = f0 * Math.pow(2, cents / 1200.0);
        int harmonics = (int) Math.min(40, 9000 / base);
        double breath = 0, breathLp = 0;
        double[] phases = new double[harmonics + 1];
        double vibratoPhase = phase0;
        double gainL = Math.sqrt(0.5 * (1 - pan)), gainR = Math.sqrt(0.5 * (1 + pan));
        double[] amps = new double[harmonics + 1];
        level *= or ? 0.9 : 1.0;
        for (int i = 0; i < left.length; i++) {
            double t = i / (double) RATE;
            double env = or ? orEnvelope(t) : saEnvelope(t);
            if (env <= 1e-5) {
                continue;
            }
            if (i % 64 == 0) {
                double[] formants = or ? orFormants(t) : saFormants(t);
                for (int h = 1; h <= harmonics; h++) {
                    amps[h] = Math.pow(h, -1.0) * formantGain(h * base, formants);
                }
            }
            double vibratoDepth = or ? 0.004 : 0.006 * smooth((t - SA_START - 0.3) / 0.5);
            vibratoPhase += 2 * Math.PI * 5.6 / RATE;
            double f = base * (1 + vibratoDepth * Math.sin(vibratoPhase));
            double sample = 0;
            for (int h = 1; h <= harmonics; h++) {
                phases[h] += 2 * Math.PI * f * h / RATE;
                sample += amps[h] * Math.sin(phases[h]);
            }
            // A little breath, the voice's air, low-passed under the tone.
            breath = random.nextGaussian();
            breathLp += 0.18 * (breath - breathLp);
            sample = sample + breathLp * 0.05;
            sample *= env * level;
            left[i] += sample * gainL;
            right[i] += sample * gainR;
        }
    }

    private static double formantGain(double frequency, double[] formants) {
        double gain = 0.04;
        double[] weights = {1.0, 0.7, 0.35};
        for (int k = 0; k < 3; k++) {
            double d = (frequency - formants[k]) / BANDWIDTH[k];
            gain += weights[k] / (1 + d * d);
        }
        return gain;
    }

    private static double[] orFormants(double t) {
        return lerp(O, R, smooth((t - R_START) / (R_END - R_START)));
    }

    private static double[] saFormants(double t) {
        return lerp(A_DARK, A, smooth((t - SA_START) / 0.5));
    }

    private static double orEnvelope(double t) {
        return smooth(t / 0.12) * (1 - smooth((t - S_START) / (S_END - S_START))) * 0.85;
    }

    private static double saEnvelope(double t) {
        if (t < SA_START) {
            return 0;
        }
        double swell = 0.55 + 0.45 * smooth((t - SA_START) / (SA_PEAK - SA_START));
        double attack = smooth((t - SA_START) / 0.10);
        double release = t < SA_RELEASE ? 1 : Math.exp(-(t - SA_RELEASE) / 0.45);
        return attack * swell * release;
    }

    /** The "s": white noise through two high-pass poles and a gentle low-pass, peaking between the syllables. */
    private static void sibilant(double[] left, double[] right, Random random) {
        double hp1 = 0, hp2 = 0, lastIn = 0, lastHp1 = 0, lp = 0;
        double a = Math.exp(-2 * Math.PI * 4200.0 / RATE);
        double b = 1 - Math.exp(-2 * Math.PI * 9500.0 / RATE);
        for (int i = 0; i < left.length; i++) {
            double t = i / (double) RATE;
            double env = t < S_PEAK ? smooth((t - S_START + 0.02) / (S_PEAK - S_START + 0.02))
                    : 1 - smooth((t - S_PEAK) / (S_END - S_PEAK));
            double x = random.nextGaussian();
            hp1 = a * (hp1 + x - lastIn);
            lastIn = x;
            hp2 = a * (hp2 + hp1 - lastHp1);
            lastHp1 = hp1;
            lp += b * (hp2 - lp);
            double sample = lp * env * 0.11;
            left[i] += sample;
            right[i] += sample;
        }
    }

    /** A soft bell on the rise: D6 with a fifth above, decaying over two seconds. */
    private static void bell(double[] left, double[] right) {
        double[][] partials = {{1174.66, 0.035}, {1760.0, 0.015}, {2349.32, 0.008}};
        for (int i = 0; i < left.length; i++) {
            double t = i / (double) RATE - SA_START;
            if (t < 0) {
                continue;
            }
            double env = smooth(t / 0.01) * Math.exp(-t / 0.7);
            double sample = 0;
            for (double[] p : partials) {
                sample += p[1] * Math.sin(2 * Math.PI * p[0] * t);
            }
            left[i] += sample * env * 0.9;
            right[i] += sample * env;
        }
    }

    /** A low D pad under "SA". */
    private static void pad(double[] left, double[] right) {
        for (int i = 0; i < left.length; i++) {
            double t = i / (double) RATE;
            double env = saEnvelope(t);
            double sample = 0.07 * env * (Math.sin(2 * Math.PI * 73.42 * t) + 0.3 * Math.sin(2 * Math.PI * 146.83 * t));
            left[i] += sample;
            right[i] += sample;
        }
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        double[] out = new double[a.length];
        for (int k = 0; k < a.length; k++) {
            out[k] = a[k] + (b[k] - a[k]) * t;
        }
        return out;
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static void normalize(double[] left, double[] right, double peak) {
        double max = 1e-9;
        for (int i = 0; i < left.length; i++) {
            max = Math.max(max, Math.max(Math.abs(left[i]), Math.abs(right[i])));
        }
        for (int i = 0; i < left.length; i++) {
            left[i] *= peak / max;
            right[i] *= peak / max;
        }
    }

    private static byte[] wav(double[] left, double[] right) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int data = left.length * 4;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes());
        header.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2).putInt(RATE)
                .putInt(RATE * 4).putShort((short) 4).putShort((short) 16);
        header.put("data".getBytes()).putInt(data);
        out.write(header.array());
        ByteBuffer body = ByteBuffer.allocate(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < left.length; i++) {
            body.putShort((short) Math.round(Math.max(-1, Math.min(1, left[i])) * 32767));
            body.putShort((short) Math.round(Math.max(-1, Math.min(1, right[i])) * 32767));
        }
        out.write(body.array());
        return out.toByteArray();
    }
}
