package org.raincityvoices.ttrack.service.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.raincityvoices.ttrack.service.audio.model.AudioFormats;

import com.azure.cosmos.implementation.guava25.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChannelExtractingStream extends AudioInputStream {

    private static class ExtractingStream extends InputStream {

        private final AudioInputStream inputStream;
        private final int channelIndex;
        private final int bufferFrames;
        private final AudioFormat inputFormat;
        private final AudioFormat outputFormat;
        private final ByteBuffer inBuffer;
        private final ByteOrder byteOrder;

        public ExtractingStream(AudioInputStream inputStream, int channelIndex, int bufferFrames) {
            this.inputStream = inputStream;
            this.channelIndex = channelIndex;
            this.bufferFrames = bufferFrames;
            this.inputFormat = inputStream.getFormat();
            this.outputFormat = AudioFormats.forOutputChannels(inputFormat, 1);
            this.byteOrder = inputFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            this.inBuffer = ByteBuffer.allocate(bufferFrames * inputFormat.getFrameSize()).order(byteOrder);
            Preconditions.checkArgument(inputFormat.getSampleSizeInBits() == 16, 
                                        "Input sample size is " + inputFormat.getSampleSizeInBits() + "; only 16 supported.");
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("Single-byte read not supported for this stream.");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            inBuffer.rewind().limit(0);
            int numFrames = Math.min(len / outputFormat.getFrameSize(), bufferFrames);
            int bytesToRead = numFrames * inputFormat.getFrameSize();
            boolean debug = false;

            int readBytes = inputStream.read(inBuffer.array(), 0, bytesToRead);
            int readFrames = readBytes / inputFormat.getFrameSize();
            if (readBytes < 0) {
                // EOF. No data read.
                return -1;
            }
            inBuffer.limit(readBytes).rewind();
            if (debug) { MixUtils.logBuffer(inBuffer, "inBuffer"); }
            ByteBuffer outBuffer = ByteBuffer.wrap(b, off, len).order(byteOrder);
            extract(inBuffer, outBuffer);
            if (outBuffer.position() != readFrames * 2) {
                log.warn("outBuffer.position = {}; expected {}", outBuffer.position(), readFrames * 2);
            }
            return readFrames * outputFormat.getFrameSize();
        }

        private void extract(ByteBuffer inBytes, ByteBuffer outBytes) {
            ShortBuffer inSamples = inBytes.asShortBuffer();
            ShortBuffer outSamples = outBytes.asShortBuffer();
            while (inSamples.hasRemaining()) {
                for (int c = 0; c < inputFormat.getChannels(); ++c) {
                    short sample = inSamples.get();
                    if (c == channelIndex) {
                        outSamples.put(sample);
                    }
                }
            }
        }
    }

    public ChannelExtractingStream(AudioInputStream inputStream, int channelIndex, int bufferFrames) {
        super(new ExtractingStream(inputStream, channelIndex, bufferFrames), 
              AudioFormats.forOutputChannels(inputStream.getFormat(), 1), 
              AudioSystem.NOT_SPECIFIED);
    }
}
