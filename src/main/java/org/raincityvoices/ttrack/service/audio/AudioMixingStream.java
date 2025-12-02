package org.raincityvoices.ttrack.service.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

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
        private final AudioDebugger[] inDebuggers;
        private final AudioDebugger outDebugger;
        private final AudioFormat inputFormat;
        private final AudioFormat outputFormat;
        private final ByteBuffer inBytes[];
        private final FloatBuffer inBuffers[];
        private final FloatBuffer outBuffer;
		private final TarsosDSPAudioFloatConverter converter;

        public MixingStream(AudioInputStream[] inputStreams, AudioMix mix, int bufferFrames) {
            this(inputStreams, mix, bufferFrames, AudioDebugger.Settings.NONE);
        }

        MixingStream(AudioInputStream[] inputStreams, AudioMix mix, int bufferFrames, AudioDebugger.Settings debugSettings) {
            Preconditions.checkArgument(inputStreams.length == mix.numInputs());
            this.inputStreams = inputStreams;
            this.mix = mix;
            this.bufferFrames = bufferFrames;
            this.inDebuggers = new AudioDebugger[numInputs()];
            for (int i = 0; i < numInputs(); ++i) {
                this.inDebuggers[i] = new AudioDebugger("MixInput-" + i, inputStreams[i].getFormat(), debugSettings);
            }
            // TODO validate matching
            this.inputFormat = inputStreams[0].getFormat();
            Preconditions.checkArgument(inputFormat.getSampleSizeInBits() == 16, 
            "Input sample size is " + inputFormat.getSampleSizeInBits() + "; only 16 supported.");
            Preconditions.checkArgument(inputFormat.getChannels() == 1,
            "At least one stream has more than one channel.");
            this.outputFormat = AudioFormats.forOutputChannels(inputFormat, mix.numOutputs());
            this.outDebugger = new AudioDebugger("MixOutput", outputFormat, debugSettings);
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
            int numFrames = Math.min(len / outputFormat.getFrameSize(), bufferFrames);
            int bytesToRead = numFrames * inputFormat.getFrameSize();
            int minReadFrames = numFrames;
            for (int i = 0; i < numInputs(); ++i) {
                ByteBuffer bb = inBytes[i];
                FloatBuffer fb = inBuffers[i];
                int readBytes = readFromStream(i, bb.array(), 0, bytesToRead);
                if (readBytes < 0) {
                    // EOF
                    return -1;
                }
                int readFrames = readBytes / format(i).getFrameSize();
                bb.limit(readBytes).rewind();
                inDebuggers[i].logBuffer(bb);
                if (readFrames < minReadFrames) {
                    log.warn("Only read {} frames for stream {}; expected {}", readFrames, i, numFrames);
                    minReadFrames = readFrames;
                }
                converter.toFloatArray(bb.array(), fb.array(), readFrames);
                fb.limit(readFrames).rewind();
            }
            int outSamples = minReadFrames * outputFormat.getChannels();
            outBuffer.limit(outSamples).rewind();
            mix.mix(inBuffers, outBuffer);
            if (outSamples != outBuffer.position()) {
                log.warn("outBuffer.position = {}; expected {}", outBuffer.position(), outSamples);
            }
            converter.toByteArray(outBuffer.array(), outSamples, b);
            ByteBuffer outBytes = ByteBuffer.wrap(b).limit(outSamples * 2);
            outBytes.order(inputFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            outDebugger.logBuffer(outBytes);
            return minReadFrames * outputFormat.getFrameSize();
        }

        /**
         * Read exactly {@code len} bytes from the input stream into the buffer,
         * unless the stream hits EOF (in which all available bytes are read).
         * If the stream doesn't have enough bytes available, try again until they
         * are (or until EOF), blocking if necessary.
         * @return the number of bytes read.
         */
        private int readFromStream(int i, byte[] buf, int off, int len) throws IOException {
            int readBytes = inputStreams[i].read(buf, 0, len);
            log.debug("Read {} bytes from stream {}", readBytes, i);
            if (readBytes < 0) {
                log.info("EOF for stream {}", i);
                // EOF. No data read.
                return -1;
            }
            /**
             * TODO: this can potentially block while waiting for more input, which is sub-optimal.
             * A better solution would be to process the max frames we have from all streams, and
             * keep around whatever we didn't process, only fetching enough bytes from each stream
             * to add up to the requested bytes (if available).
             */
            while (readBytes < len) {
                int additionalBytes = inputStreams[i].read(buf, readBytes, len - readBytes);
                log.debug("Read {} additional bytes from stream {}", additionalBytes, i);
                if (additionalBytes < 0) {
                    break;
                }
                readBytes += additionalBytes;
            }
            return readBytes;
        }

        public int numInputs() {
            return mix.numInputs();
        }

        private AudioFormat format(int i) {
            return inputStreams[i].getFormat();
        }
    }

    private AudioMixingStream(MixingStream mixingStream) {
        super(mixingStream, mixingStream.outputFormat, AudioSystem.NOT_SPECIFIED);
    }

    public static AudioMixingStream create(AudioInputStream inputStreams[], AudioMix mix, int bufferFrames) {
        MixingStream mixingStream = new MixingStream(inputStreams, mix, bufferFrames);
        return new AudioMixingStream(mixingStream);
    }

    public static AudioMixingStream create(AudioInputStream inputStreams[], AudioMix mix, int bufferFrames, AudioDebugger.Settings debugSettings) {
        MixingStream mixingStream = new MixingStream(inputStreams, mix, bufferFrames, debugSettings);
        return new AudioMixingStream(mixingStream);
    }

    /**
     * Construct a new mixing stream that buffers one seecond of audio.
     */
    public static AudioMixingStream create(AudioInputStream inputStreams[], AudioMix mix) {
        return create(inputStreams, mix, (int) inputStreams[0].getFormat().getFrameRate());
    }
}
