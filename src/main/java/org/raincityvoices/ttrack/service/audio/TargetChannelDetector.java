package org.raincityvoices.ttrack.service.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.OptionalInt;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;

import be.tarsos.dsp.pitch.FastYin;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * Determines which channel of a stereo, part-predominant track carries the target part.
 *
 * <p>In such a track the target channel carries a single voice (monophonic), while the other
 * channel carries either the same voice or a blend that includes the other parts (polyphonic).
 * A pitch detector reports a pitch on monophonic material and fails on polyphonic material, so
 * the channel in which a pitch is found in markedly more windows is the target channel.
 *
 * <p>The audio is split into non-overlapping windows. A window is <em>active</em> in a channel if
 * its RMS reaches {@link #ENERGY_GATE_RMS}, and is counted if it is active in either channel. A
 * channel is <em>pitched</em> in a window if it is active and {@link FastYin} reports a pitch
 * between {@link #MIN_PITCH_HZ} and {@link #MAX_PITCH_HZ}. With {@code L} and {@code R} the
 * fractions of counted windows in which each channel is pitched and
 * {@code margin = (L - R) / (L + R)}, the channel with the larger fraction is the target provided
 * that {@code |margin| >= }{@link #MIN_MARGIN}, that the winner is pitched in at least
 * {@link #MIN_WINNER_PITCHED_FRACTION} of counted windows, and that at least
 * {@link #MIN_COUNTED_WINDOWS} windows were counted. Otherwise no channel is identified.
 *
 * <p>The constants are justified by an experiment on real choir tracks; see the design document
 * ("Target-channel identification"). In particular, {@code FastYin}'s default confidence threshold is
 * used as is: it gave identical results for any extra threshold up to 0.80 and worse results above.
 *
 * <p>The whole stream should be analyzed rather than an excerpt; intros are often silent or
 * instrumental, which makes short excerpts unreliable.
 */
@Slf4j
public final class TargetChannelDetector {

    /** Samples per channel in each analysis window (about 46 ms at 44.1 kHz; resolves pitches down to ~43 Hz). */
    public static final int WINDOW_SAMPLES = 2048;
    /** Windows quieter than this RMS (full scale = 1.0) in a channel are not analyzed in that channel. */
    public static final double ENERGY_GATE_RMS = 0.003;
    public static final double MIN_PITCH_HZ = 60;
    public static final double MAX_PITCH_HZ = 1400;
    /** Minimum windows counted; about 2.3 s of audio at 44.1 kHz. */
    public static final int MIN_COUNTED_WINDOWS = 50;
    /** Minimum relative difference between the channels' pitched fractions. */
    public static final double MIN_MARGIN = 0.20;
    /** Sanity guard: the winning channel must be pitched in at least this fraction of counted windows. */
    public static final double MIN_WINNER_PITCHED_FRACTION = 0.50;

    public enum Outcome {
        LEFT, RIGHT,
        /** Neither channel is clearly the target, e.g. both are part-predominant, or the channels are identical. */
        AMBIGUOUS,
        /** Too little audible audio to decide. */
        INSUFFICIENT_AUDIO
    }

    @Value
    @Accessors(fluent = true)
    public static class Result {
        Outcome outcome;
        int countedWindows;
        double leftPitchedFraction;
        double rightPitchedFraction;

        /** @return {@code (L - R) / (L + R)}, or 0 if neither channel was pitched anywhere. */
        public double margin() {
            double sum = leftPitchedFraction + rightPitchedFraction;
            return sum == 0 ? 0 : (leftPitchedFraction - rightPitchedFraction) / sum;
        }

        public boolean identified() {
            return outcome == Outcome.LEFT || outcome == Outcome.RIGHT;
        }

        /** @return the target channel index (0 = left, 1 = right), if one was identified. */
        public OptionalInt channel() {
            switch (outcome) {
                case LEFT: return OptionalInt.of(0);
                case RIGHT: return OptionalInt.of(1);
                default: return OptionalInt.empty();
            }
        }

        public String describe() {
            return String.format("%s (left pitched in %.0f%% and right in %.0f%% of %d analyzed windows)",
                outcome, leftPitchedFraction * 100, rightPitchedFraction * 100, countedWindows);
        }
    }

    private TargetChannelDetector() {}

    /**
     * Analyze the whole stream. The stream is consumed but not closed.
     *
     * @param stereo 16-bit signed PCM audio with exactly two channels.
     * @throws IllegalArgumentException if the stream's format is not 16-bit signed PCM stereo.
     */
    public static Result detect(AudioInputStream stereo) throws IOException {
        AudioFormat format = stereo.getFormat();
        if (format.getChannels() != 2 || format.getSampleSizeInBits() != 16
                || !Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            throw new IllegalArgumentException("Expected 16-bit signed PCM stereo audio but got " + format);
        }
        ByteOrder order = format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        float sampleRate = format.getSampleRate();
        FastYin leftYin = new FastYin(sampleRate, WINDOW_SAMPLES);
        FastYin rightYin = new FastYin(sampleRate, WINDOW_SAMPLES);

        byte[] bytes = new byte[WINDOW_SAMPLES * format.getFrameSize()];
        float[] left = new float[WINDOW_SAMPLES];
        float[] right = new float[WINDOW_SAMPLES];
        int counted = 0, leftPitched = 0, rightPitched = 0;

        // A trailing partial window is ignored.
        while (stereo.readNBytes(bytes, 0, bytes.length) == bytes.length) {
            ShortBuffer samples = ByteBuffer.wrap(bytes).order(order).asShortBuffer();
            double leftSum = 0, rightSum = 0;
            for (int i = 0; i < WINDOW_SAMPLES; i++) {
                left[i] = samples.get() / 32768f;
                right[i] = samples.get() / 32768f;
                leftSum += left[i] * left[i];
                rightSum += right[i] * right[i];
            }
            boolean leftActive = Math.sqrt(leftSum / WINDOW_SAMPLES) >= ENERGY_GATE_RMS;
            boolean rightActive = Math.sqrt(rightSum / WINDOW_SAMPLES) >= ENERGY_GATE_RMS;
            if (!leftActive && !rightActive) {
                continue;
            }
            counted++;
            if (leftActive && isPitched(leftYin.getPitch(left))) {
                leftPitched++;
            }
            if (rightActive && isPitched(rightYin.getPitch(right))) {
                rightPitched++;
            }
        }

        double leftFraction = counted == 0 ? 0 : (double) leftPitched / counted;
        double rightFraction = counted == 0 ? 0 : (double) rightPitched / counted;
        Result result = new Result(classify(counted, leftFraction, rightFraction), counted, leftFraction, rightFraction);
        log.info("Target channel analysis: {}", result.describe());
        return result;
    }

    private static boolean isPitched(PitchDetectionResult r) {
        double hz = r.getPitch();
        return hz >= MIN_PITCH_HZ && hz <= MAX_PITCH_HZ;
    }

    private static Outcome classify(int counted, double leftFraction, double rightFraction) {
        if (counted < MIN_COUNTED_WINDOWS) {
            return Outcome.INSUFFICIENT_AUDIO;
        }
        double sum = leftFraction + rightFraction;
        double margin = sum == 0 ? 0 : (leftFraction - rightFraction) / sum;
        boolean leftWins = margin >= 0;
        double winner = leftWins ? leftFraction : rightFraction;
        if (Math.abs(margin) < MIN_MARGIN || winner < MIN_WINNER_PITCHED_FRACTION) {
            return Outcome.AMBIGUOUS;
        }
        return leftWins ? Outcome.LEFT : Outcome.RIGHT;
    }
}
