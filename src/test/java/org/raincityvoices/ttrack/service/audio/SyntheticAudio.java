package org.raincityvoices.ttrack.service.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Generates synthetic "choir" audio for tests, so that they don't depend on real recordings.
 *
 * <p>A voice is a legato melody of random equal-tempered notes, each a stack of harmonics with
 * vibrato. One voice is monophonic and has a clear pitch; a sum of several independent voices is
 * polyphonic, and a pitch detector can't find a single clear pitch in it. That is the same
 * distinction that separates the target channel from the other channel in a stereo
 * part-predominant track.
 */
public final class SyntheticAudio {

    public static final int SAMPLE_RATE = 44100;

    private SyntheticAudio() {}

    /**
     * @param seconds length of the voice.
     * @param lowMidi lowest MIDI note of the melody.
     * @param highMidi highest MIDI note of the melody.
     * @param seed determines the melody and vibrato phase; the same arguments always give the same voice.
     * @return mono samples in [-1, 1] at {@link #SAMPLE_RATE}, with a peak amplitude of about 0.3.
     */
    public static float[] voice(double seconds, int lowMidi, int highMidi, long seed) {
        return voice(SAMPLE_RATE, seconds, lowMidi, highMidi, seed);
    }

    public static float[] voice(int sampleRate, double seconds, int lowMidi, int highMidi, long seed) {
        Random random = new Random(seed);
        float[] out = new float[(int) (seconds * sampleRate)];
        double vibratoPhase = random.nextDouble() * 2 * Math.PI;
        double phase = 0;
        int note = lowMidi + random.nextInt(highMidi - lowMidi + 1);
        int i = 0;
        while (i < out.length) {
            // Next note: within a fourth of the previous one, now and then a rest.
            note = Math.max(lowMidi, Math.min(highMidi, note + random.nextInt(11) - 5));
            boolean rest = random.nextDouble() < 0.08;
            int length = (int) ((0.3 + random.nextDouble() * 0.4) * sampleRate);
            int fade = (int) (0.015 * sampleRate);
            for (int n = 0; n < length && i < out.length; n++, i++) {
                double t = (double) i / sampleRate;
                double midi = note + 0.25 * Math.sin(2 * Math.PI * 5.5 * t + vibratoPhase);
                phase += 2 * Math.PI * 440.0 * Math.pow(2, (midi - 69) / 12.0) / sampleRate;
                if (rest) {
                    continue;
                }
                double sample = 0;
                for (int k = 1; k <= 6; k++) {
                    sample += Math.sin(k * phase) / k;
                }
                double envelope = Math.min(1.0, Math.min(n, length - n) / (double) fade);
                out[i] = (float) (0.2 * sample * envelope);
            }
        }
        return out;
    }

    /** @return the sum of the signals, each scaled by its gain: {@code mix(a, ga, b, gb, ...)}. */
    public static float[] mix(Object... signalsAndGains) {
        float[] first = (float[]) signalsAndGains[0];
        float[] out = new float[first.length];
        for (int s = 0; s < signalsAndGains.length; s += 2) {
            float[] signal = (float[]) signalsAndGains[s];
            double gain = ((Number) signalsAndGains[s + 1]).doubleValue();
            for (int i = 0; i < out.length; i++) {
                out[i] += (float) (gain * signal[i]);
            }
        }
        return out;
    }

    /** Interleave two channels as 16-bit signed little-endian PCM. */
    public static byte[] pcm16(float[] left, float[] right) {
        ByteBuffer bb = ByteBuffer.allocate(left.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < left.length; i++) {
            bb.putShort(toShort(left[i]));
            bb.putShort(toShort(right[i]));
        }
        return bb.array();
    }

    public static byte[] pcm16(float[] mono) {
        ByteBuffer bb = ByteBuffer.allocate(mono.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : mono) {
            bb.putShort(toShort(v));
        }
        return bb.array();
    }

    private static short toShort(float v) {
        return (short) Math.round(Math.max(-1f, Math.min(1f, v)) * 32767);
    }

    public static AudioInputStream stereoStream(float[] left, float[] right) {
        return stereoStream(SAMPLE_RATE, left, right);
    }

    public static AudioInputStream stereoStream(int sampleRate, float[] left, float[] right) {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, 2, 4, sampleRate, false);
        return new AudioInputStream(new ByteArrayInputStream(pcm16(left, right)), format, left.length);
    }

    public static AudioInputStream monoStream(float[] mono) {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
        return new AudioInputStream(new ByteArrayInputStream(pcm16(mono)), format, mono.length);
    }

    /** @return the bytes of a stereo WAV file. */
    public static byte[] stereoWav(float[] left, float[] right) throws IOException {
        return toWav(stereoStream(left, right));
    }

    /** @return the bytes of a mono WAV file. */
    public static byte[] monoWav(float[] mono) throws IOException {
        return toWav(monoStream(mono));
    }

    public static byte[] toWav(AudioInputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }

    /** Decode the samples of a 16-bit PCM WAV (or any stream convertible to it) into one array per channel. */
    public static float[][] decodeChannels(AudioInputStream stream) throws IOException {
        AudioFormat format = stream.getFormat();
        byte[] bytes = stream.readAllBytes();
        int channels = format.getChannels();
        int frames = bytes.length / format.getFrameSize();
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        float[][] out = new float[channels][frames];
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < channels; c++) {
                out[c][i] = bb.getShort() / 32768f;
            }
        }
        return out;
    }

    /**
     * A synthetic four-part song: a {@link #target} part to be isolated, another part
     * ({@link #otherTarget}) that is the target in a different recording, and the {@link #others},
     * which are the remaining parts.
     */
    public static final class Choir {
        public final float[] target;
        public final float[] otherTarget;
        public final float[] others;

        public Choir(double seconds) {
            this(SAMPLE_RATE, seconds);
        }

        public Choir(int sampleRate, double seconds) {
            target = voice(sampleRate, seconds, 55, 67, 1);
            otherTarget = voice(sampleRate, seconds, 48, 60, 5);
            others = mix(
                voice(sampleRate, seconds, 60, 72, 2), 1.0,
                voice(sampleRate, seconds, 52, 64, 3), 1.0,
                voice(sampleRate, seconds, 40, 52, 4), 1.0);
        }

        /** A channel in which the given part is predominant and the other parts are faintly present. */
        public float[] partPredominant(float[] part) {
            return mix(part, 1.0, others, 0.15);
        }

        /** A channel in which all the parts are present at similar levels. */
        public float[] balancedMix() {
            return mix(target, 0.5, others, 0.5);
        }
    }

    /**
     * @return the largest normalized correlation between {@code length} samples of {@code reference}
     * starting at {@code start}, and the same stretch of {@code candidate} shifted by up to
     * {@code maxLag} samples either way. Useful when {@code candidate} went through a codec that
     * delays the signal.
     */
    public static double maxCorrelation(float[] reference, float[] candidate, int start, int length, int maxLag) {
        double best = -1;
        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double ab = 0, aa = 0, bb = 0;
            for (int i = start; i < start + length; i++) {
                int j = i + lag;
                if (j < 0 || j >= candidate.length || i >= reference.length) {
                    continue;
                }
                ab += reference[i] * candidate[j];
                aa += reference[i] * reference[i];
                bb += candidate[j] * candidate[j];
            }
            if (aa > 0 && bb > 0) {
                best = Math.max(best, ab / Math.sqrt(aa * bb));
            }
        }
        return best;
    }

    /** @return the normalized correlation of two signals, over their common length. */
    public static double correlation(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double ab = 0, aa = 0, bb = 0;
        for (int i = 0; i < n; i++) {
            ab += a[i] * b[i];
            aa += a[i] * a[i];
            bb += b[i] * b[i];
        }
        return aa == 0 || bb == 0 ? 0 : ab / Math.sqrt(aa * bb);
    }
}
