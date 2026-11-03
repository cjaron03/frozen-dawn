package com.frozendawn.client.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;

/** Owner-selected C: a slight pitch rise and a dry, hollow throat tone. No audio assets are embedded. */
public final class BlindHurtTexture {
    public static final int RATE = 48000;
    private BlindHurtTexture() { }

    /** Fresh per-voice state; never modifies the original game's PCM or another entity's sound. */
    public static ByteBuffer process(ByteBuffer input, AudioFormat format, double pitch) {
        int channels = format.getChannels();
        if (format.getSampleSizeInBits() != 16 || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                || channels < 1 || channels > 2 || input.remaining() > 4_000_000 || !Double.isFinite(pitch) || pitch < .5 || pitch > 2
                || !Float.isFinite(format.getSampleRate()) || format.getSampleRate() < 8000 || format.getSampleRate() > 192000
                || input.remaining() % (channels * 2) != 0 || input.remaining() / (channels * 2) > format.getSampleRate() * 10)
            throw new IllegalArgumentException("Unsupported or oversized hurt audio");
        var data = input.asReadOnlyBuffer().order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int count = data.remaining() / (channels * 2);
        var mono = new double[count];
        for (int i = 0; i < count; i++) {
            for (int channel = 0; channel < channels; channel++) mono[i] += data.getShort() / (32768.0 * channels);
        }
        double step = format.getSampleRate() * pitch / RATE;
        int length = (int) Math.ceil(count / step);
        var out = ByteBuffer.allocateDirect(length * 2).order(ByteOrder.LITTLE_ENDIAN);
        var filters = new Filter[]{Filter.pass(85, true), Filter.peak(420, 1, -3),
                Filter.peak(1450, 1.8, 3), Filter.pass(5400, false)};
        double cutoff = Math.min(1, 1 / step);
        for (int i = 0; i < length; i++) {
            double position = i * step, value = 0, weight = 0;
            int center = (int) position;
            // Windowed sinc retains the familiar voice while suppressing resampling aliases.
            for (int j = center - 16; j <= center + 16; j++) {
                if (j < 0 || j >= count) continue;
                double distance = position - j;
                double coefficient = cutoff * sinc(distance * cutoff) * sinc(distance / 17);
                value += mono[j] * coefficient; weight += coefficient;
            }
            value = weight == 0 ? 0 : value / weight;
            for (var filter : filters) value = filter.apply(value);
            value *= 1.048824921671377; // Audition C's common RMS-matching gain, not per-clip normalization.
            out.putShort((short) Math.round(Math.max(-1, Math.min(32767.0 / 32768, value)) * 32768));
        }
        return out.flip();
    }
    private static double sinc(double x) { return Math.abs(x) < 1e-9 ? 1 : Math.sin(Math.PI * x) / (Math.PI * x); }

    /** Standard biquads; coefficients follow the Audio EQ Cookbook, not a copied implementation. */
    private static final class Filter {
        final double b0, b1, b2, a1, a2;
        double x1, x2, y1, y2;
        Filter(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0=b0/a0;this.b1=b1/a0;this.b2=b2/a0;this.a1=a1/a0;this.a2=a2/a0;
        }
        double apply(double x) {
            double y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2;x2=x1;x1=x;y2=y1;y1=y;return y;
        }
        static Filter pass(double f, boolean high) {
            double w=2*Math.PI*f/RATE,c=Math.cos(w),alpha=Math.sin(w)/(2/Math.sqrt(2));
            return high?new Filter((1+c)/2,-(1+c),(1+c)/2,1+alpha,-2*c,1-alpha)
                    :new Filter((1-c)/2,1-c,(1-c)/2,1+alpha,-2*c,1-alpha);
        }
        static Filter peak(double f,double q,double gain) {
            double w=2*Math.PI*f/RATE,c=Math.cos(w),alpha=Math.sin(w)/(2*q),a=Math.pow(10,gain/40);
            return new Filter(1+alpha*a,-2*c,1-alpha*a,1+alpha/a,-2*c,1-alpha/a);
        }
    }
}
