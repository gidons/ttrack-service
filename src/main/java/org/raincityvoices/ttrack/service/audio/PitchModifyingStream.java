package org.raincityvoices.ttrack.service.audio;

import javax.sound.sampled.AudioInputStream;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.WaveformSimilarityBasedOverlapAdd;
import be.tarsos.dsp.WaveformSimilarityBasedOverlapAdd.Parameters;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.io.jvm.WaveformWriter;
import be.tarsos.dsp.resample.RateTransposer;

public class PitchModifyingStream {

    public PitchModifyingStream(AudioInputStream in, double factor, String fileName) {
        JVMAudioInputStream jvmIn = new JVMAudioInputStream(in);
        WaveformSimilarityBasedOverlapAdd wsola = new WaveformSimilarityBasedOverlapAdd(Parameters.musicDefaults(factor, in.getFormat().getSampleRate()));
        AudioDispatcher dispatcher = new AudioDispatcher(jvmIn, wsola.getInputBufferSize(), wsola.getOverlap());
        wsola.setDispatcher(dispatcher);
        dispatcher.addAudioProcessor(new RateTransposer(factor));
        dispatcher.addAudioProcessor(new WaveformWriter(jvmIn.getFormat(), fileName));
        dispatcher.run();
    }
}
