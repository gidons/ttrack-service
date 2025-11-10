package org.raincityvoices.ttrack.service.audio;

import javax.sound.sampled.AudioInputStream;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.WaveformSimilarityBasedOverlapAdd;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.resample.RateTransposer;

public class TarsosUtils {

    public static AudioDispatcher getPitchAndSpeedDispatcher(AudioInputStream input, int pitchShift, double speedFactor) {
        double pitchFactor = Math.pow(2.0, -pitchShift / 12.0);
        double overallFactor = pitchFactor * speedFactor;
        JVMAudioInputStream jvmIn = new JVMAudioInputStream(input);
        WaveformSimilarityBasedOverlapAdd wsola = new WaveformSimilarityBasedOverlapAdd(
            WaveformSimilarityBasedOverlapAdd.Parameters.musicDefaults(overallFactor, input.getFormat().getSampleRate()));
        AudioDispatcher dispatcher = new AudioDispatcher(jvmIn, wsola.getInputBufferSize(), wsola.getOverlap());
        wsola.setDispatcher(dispatcher);
        dispatcher.addAudioProcessor(wsola);
        dispatcher.addAudioProcessor(new RateTransposer(pitchFactor));
        return dispatcher;
    }
}
