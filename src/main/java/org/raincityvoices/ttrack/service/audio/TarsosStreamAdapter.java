package org.raincityvoices.ttrack.service.audio;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.raincityvoices.ttrack.service.audio.AudioDebugger.Settings;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * An AudioInputStream that wraps around a TarsosDSP AudioDispatcher and buffers
 * the processed results. The dispatcher is run in a separate thread, and blocks
 * when the buffer is full.
 */
@Slf4j
public class TarsosStreamAdapter implements Closeable {

    private static final ByteBuffer END_MARKER = ByteBuffer.allocate(1);
    private final AudioFormat format;
    private final Thread dispatcherThread;
    private final ProcessingStream stream;
    private final AudioInputStream audioStream;
    /** 
     * Queue where audio data is stored until read; each entry in the queue is for one AudioEvent. 
     */
    private final BlockingQueue<ByteBuffer> bufferQueue;
    /** 
     * The last buffer removed from the queue. We have to keep this around in case we didn't use
     * all of it for the latest read.
     */
    private ByteBuffer currentBuffer;

    @RequiredArgsConstructor
    private class Processor implements AudioProcessor {

        private int byteCount = 0;

        @Override
        public boolean process(AudioEvent event) {
            log.debug("Processing audio event: time: {}; bytes: {}", event.getTimeStamp(), event.getBufferSize() * 2);
            byte[] bytes = event.getByteBuffer();
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            log.debug("Queuing buffer...");
            try {
                bufferQueue.put(buffer);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while queuing audio", e);
            }
            byteCount += bytes.length;
            log.debug("Buffer queued.");
            return true; // don't stop processing
        }

        @Override
        public void processingFinished() {
            log.debug("Processing finished: queueing END_MARKER. Total bytes processed: {}.", byteCount);
            try {
                bufferQueue.put(END_MARKER);  // ← Use put() to block until added
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while adding END_MARKER", e);
            }
            log.debug("END_MARKER added.");
        }

    }

    @RequiredArgsConstructor
    private class ProcessingStream extends InputStream {

        private final AudioDebugger debugger;
        private int totalBytesRead = 0;

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("Single-byte reads are not supported.");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int needed = len;
            int pOut = off;
            debugger.log("Requested bytes: {}", needed);
            if (currentBuffer == END_MARKER) {
                log.debug("END_MARKER found. Total bytes read: {}", totalBytesRead);
                return -1;
            }
            try {
                while (needed > 0 && currentBuffer != END_MARKER) {
                    debugger.log("Needed bytes: {}", needed);
                    if (currentBuffer.remaining() == 0) {
                        debugger.log("Waiting for buffer...");
                        currentBuffer = bufferQueue.take();
                        debugger.log("Got buffer: pos={} rem={} lim={}", currentBuffer.position(), currentBuffer.remaining(), currentBuffer.limit());
                    }
                    int bytesToGet = Math.min(needed, currentBuffer.remaining());
                    currentBuffer.get(b, pOut, bytesToGet);
                    pOut += bytesToGet;
                    needed -= bytesToGet;
                    totalBytesRead += bytesToGet;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for audio", e);
            }
            debugger.log("Needed bytes: {}", needed);
            return len - needed;
        }

    }

	public TarsosStreamAdapter(AudioDispatcher dispatcher) {
		this(dispatcher, AudioDebugger.Settings.NONE);
	}

	public TarsosStreamAdapter(AudioDispatcher dispatcher, Settings debuggerSettings) {
        this.format = JVMAudioInputStream.toAudioFormat(dispatcher.getFormat());
        this.bufferQueue = new ArrayBlockingQueue<>(10);
        this.currentBuffer = ByteBuffer.allocate(0);
        stream = new ProcessingStream(new AudioDebugger("TarsosStream", format, debuggerSettings));
        audioStream = new AudioInputStream(stream, format, AudioSystem.NOT_SPECIFIED);
        dispatcher.addAudioProcessor(new Processor());
        this.dispatcherThread = new Thread(String.format("TarsosStreamDispatcher-%x", hashCode())) {
            public void run() {
                log.info("Dispatcher thread running.");
                dispatcher.run();
                log.info("Dispatcher thread ended.");
            };
        };
        log.info("Starting dispatcher thread {}", dispatcherThread.getName());
        this.dispatcherThread.start();
    }

    public AudioInputStream getAudioInputStream() {
        return audioStream;
    }

    @Override
    public void close() throws IOException {
        try {
            log.info("Waiting for dispatcher thread {} to end...", dispatcherThread.getName());
            dispatcherThread.join(1000);
            if (dispatcherThread.isAlive()) {
                log.warn("Dispatcher thread {} did not complete in time, interrupting...", dispatcherThread.getName());
                dispatcherThread.interrupt();
            }
            log.info("Closing stream...");
            try {
                audioStream.close();
            } catch(Exception e) {
                log.warn("Caught exception while closing stream: {}", e.getMessage());
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for dispatcher thread {} to join.", dispatcherThread.getName());
        }        
    }
}
