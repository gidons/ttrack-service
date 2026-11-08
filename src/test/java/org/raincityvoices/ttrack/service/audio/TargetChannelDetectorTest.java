package org.raincityvoices.ttrack.service.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.audio.SyntheticAudio.Choir;
import org.raincityvoices.ttrack.service.audio.TargetChannelDetector.Outcome;
import org.raincityvoices.ttrack.service.audio.TargetChannelDetector.Result;

class TargetChannelDetectorTest {

    private static final double SECONDS = 15;

    private final Choir choir = new Choir(SECONDS);

    @Test
    void identifiesTargetOnLeft() throws IOException {
        Result result = TargetChannelDetector.detect(
            SyntheticAudio.stereoStream(choir.partPredominant(choir.target), choir.balancedMix()));

        assertThat(result.outcome()).isEqualTo(Outcome.LEFT);
        assertThat(result.channel()).hasValue(0);
        assertThat(result.identified()).isTrue();
        assertThat(result.margin()).isGreaterThan(TargetChannelDetector.MIN_MARGIN);
    }

    @Test
    void identifiesTargetOnRight() throws IOException {
        Result result = TargetChannelDetector.detect(
            SyntheticAudio.stereoStream(choir.balancedMix(), choir.partPredominant(choir.target)));

        assertThat(result.outcome()).isEqualTo(Outcome.RIGHT);
        assertThat(result.channel()).hasValue(1);
        assertThat(result.margin()).isLessThan(-TargetChannelDetector.MIN_MARGIN);
    }

    @Test
    void identifiesTargetWhenOtherChannelHasOnlyOtherParts() throws IOException {
        Result result = TargetChannelDetector.detect(
            SyntheticAudio.stereoStream(choir.others, choir.partPredominant(choir.target)));

        assertThat(result.outcome()).isEqualTo(Outcome.RIGHT);
    }

    @Test
    void identifiesTargetAtOtherSampleRates() throws IOException {
        int rate = 48000;
        Choir choir48k = new Choir(rate, SECONDS);

        Result result = TargetChannelDetector.detect(
            SyntheticAudio.stereoStream(rate, choir48k.partPredominant(choir48k.target), choir48k.balancedMix()));

        assertThat(result.outcome()).isEqualTo(Outcome.LEFT);
    }

    @Test
    void rejectsWhenBothChannelsArePartPredominant() throws IOException {
        Result result = TargetChannelDetector.detect(SyntheticAudio.stereoStream(
            choir.partPredominant(choir.target), choir.partPredominant(choir.otherTarget)));

        assertThat(result.outcome()).isEqualTo(Outcome.AMBIGUOUS);
        assertThat(result.identified()).isFalse();
        assertThat(result.channel()).isEmpty();
        assertThat(Math.abs(result.margin())).isLessThan(TargetChannelDetector.MIN_MARGIN);
    }

    @Test
    void rejectsWhenBothChannelsAreBalancedMixes() throws IOException {
        Result result = TargetChannelDetector.detect(SyntheticAudio.stereoStream(choir.balancedMix(), choir.balancedMix()));

        assertThat(result.outcome()).isEqualTo(Outcome.AMBIGUOUS);
    }

    @Test
    void rejectsIdenticalChannels() throws IOException {
        float[] channel = choir.partPredominant(choir.target);

        Result result = TargetChannelDetector.detect(SyntheticAudio.stereoStream(channel, channel));

        assertThat(result.outcome()).isEqualTo(Outcome.AMBIGUOUS);
    }

    @Test
    void rejectsTooLittleAudio() throws IOException {
        float[] shortLeft = Arrays.copyOf(choir.partPredominant(choir.target), SyntheticAudio.SAMPLE_RATE);
        float[] shortRight = Arrays.copyOf(choir.balancedMix(), SyntheticAudio.SAMPLE_RATE);

        Result result = TargetChannelDetector.detect(SyntheticAudio.stereoStream(shortLeft, shortRight));

        assertThat(result.outcome()).isEqualTo(Outcome.INSUFFICIENT_AUDIO);
    }

    @Test
    void rejectsSilence() throws IOException {
        float[] silence = new float[(int) (SECONDS * SyntheticAudio.SAMPLE_RATE)];

        Result result = TargetChannelDetector.detect(SyntheticAudio.stereoStream(silence, silence));

        assertThat(result.outcome()).isEqualTo(Outcome.INSUFFICIENT_AUDIO);
        assertThat(result.countedWindows()).isZero();
        assertThat(result.margin()).isZero();
    }

    @Test
    void rejectsNonStereoInput() {
        assertThatThrownBy(() -> TargetChannelDetector.detect(SyntheticAudio.monoStream(choir.target)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
