package org.raincityvoices.ttrack.service.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.lang3.Range;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.audio.model.AudioMix;

import com.azure.cosmos.implementation.guava25.base.Preconditions;

import be.tarsos.dsp.io.TarsosDSPAudioFloatConverter;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AudioMixingStream extends AudioInputStream {

    private static class MixingStream extends InputStream {
        private final AudioInputStream[] inputStreams;
        private final AudioMix mix;
        private final int bufferFrames;
        /** Range, in seconds, of elapsed time in which to log debug information. */
        private final Range<Double> debugRange;
        private final AudioFormat inputFormat;
        private final AudioFormat outputFormat;
        private final ByteBuffer inBytes[];
        private final FloatBuffer inBuffers[];
        private final FloatBuffer outBuffer;
		private final TarsosDSPAudioFloatConverter converter;
        private int processedFrames = 0;

        public MixingStream(AudioInputStream[] inputStreams, AudioMix mix, int bufferFrames) {
            this(inputStreams, mix, bufferFrames, Range.of(0.0, 0.0));
        }

        MixingStream(AudioInputStream[] inputStreams, AudioMix mix, int bufferFrames, Range<Double> debugRange) {
            Preconditions.checkArgument(inputStreams.length == mix.numInputs());
            this.inputStreams = inputStreams;
            this.mix = mix;
            this.bufferFrames = bufferFrames;
            this.debugRange = debugRange;
            // TODO validate matching
            this.inputFormat = inputStreams[0].getFormat();
            Preconditions.checkArgument(inputFormat.getSampleSizeInBits() == 16, 
                                        "Input sample size is " + inputFormat.getSampleSizeInBits() + "; only 16 supported.");
            Preconditions.checkArgument(inputFormat.getChannels() == 1,
                                        "At least one stream has more than one channel.");
            this.outputFormat = AudioFormats.forOutputChannels(inputFormat, mix.numOutputs());
            this.inBytes = new ByteBuffer[numInputs()];
            this.inBuffers = new FloatBuffer[numInputs()];
            for (int i = 0; i < numInputs(); ++i) {
                this.inBytes[i] = ByteBuffer.allocate(bufferFrames * inputFormat.getFrameSize())
                    .order(inputFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                this.inBuffers[i] = FloatBuffer.allocate(bufferFrames * inputFormat.getChannels());
            }
            this.outBuffer = FloatBuffer.allocate(bufferFrames * outputFormat.getChannels());
            this.converter = TarsosDSPAudioFloatConverter.getConverter(JVMAudioInputStream.toTarsosDSPFormat(inputFormat));
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("Single-byte reads are not supported.");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            /* 
             * TODO this assumes that we can always read as many bytes as we need until EOF,
             * so all the input streams always stay in sync. May need to buffer them internally
             * so if we get less data for one stream we keep what we got from the others and only
             * read what we need to fill up to bytesToRead.
             */
            int numFrames = Math.min(len / outputFormat.getFrameSize(), bufferFrames);
            int bytesToRead = numFrames * inputFormat.getFrameSize();
            int minReadFrames = numFrames;
            boolean debug = debugRange.contains(elapsedSec());
            for (int i = 0; i < numInputs(); ++i) {
                ByteBuffer bb = inBytes[i];
                FloatBuffer fb = inBuffers[i];
                int readBytes = inputStreams[i].read(bb.array(), 0, bytesToRead);
                int readFrames = readBytes / format(i).getFrameSize();
                if (readBytes < 0) {
                    // EOF. No data read.
                    return -1;
                }
                bb.limit(readBytes).rewind();
                if (debug) { MixUtils.logBuffer(bb, "inBytes[" + i + "]"); }
                if (readFrames < minReadFrames) {
                    log.warn("Only read {} frames for stream {}", readFrames, i);
                    minReadFrames = readFrames;
                }
                converter.toFloatArray(bb.array(), fb.array(), readFrames);
                fb.limit(readFrames).rewind();
                if (debug) { MixUtils.logBuffer(fb, "inBuffer[" + i + "]"); }
            }
            int outSamples = minReadFrames * outputFormat.getChannels();
            outBuffer.limit(outSamples).rewind();
            mix.mix(inBuffers, outBuffer);
            if (outSamples != outBuffer.position()) {
                log.warn("outBuffer.position = {}; expected {}", outBuffer.position(), outSamples);
            }
            converter.toByteArray(outBuffer.array(), outSamples, b);
            // IF NEEDED FOR DEBUGGING
            ByteBuffer outBytes = ByteBuffer.wrap(b).limit(outSamples * 2);
            outBytes.order(inputFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            if(debug) { MixUtils.logBuffer(outBytes, "outBytes"); }
            processedFrames += minReadFrames;
            return minReadFrames * outputFormat.getFrameSize();
        }

        public int numInputs() {
            return mix.numInputs();
        }

        private AudioFormat format(int i) {
            return inputStreams[i].getFormat();
        }

        private double elapsedSec() {
            return processedFrames / inputFormat.getFrameRate();
        }
    }

    private AudioMixingStream(MixingStream mixingStream) {
        super(mixingStream, mixingStream.outputFormat, AudioSystem.NOT_SPECIFIED);
    }

    public static AudioMixingStream create(AudioInputStream inputStreams[], AudioMix mix, int bufferFrames) {
        MixingStream mixingStream = new MixingStream(inputStreams, mix, bufferFrames);
        return new AudioMixingStream(mixingStream);
    }

    public static AudioMixingStream create(AudioInputStream inputStreams[], AudioMix mix, int bufferFrames, Range<Double> debugRange) {
        MixingStream mixingStream = new MixingStream(inputStreams, mix, bufferFrames, debugRange);
        return new AudioMixingStream(mixingStream);
    }

    /**
     * Construct a new mixing stream that buffers one seecond of audio.
     */
    public static AudioMixingStream create(AudioInputStream inputStreams[], AudioMix mix) {
        return create(inputStreams, mix, (int) inputStreams[0].getFormat().getFrameRate());
    }
}
