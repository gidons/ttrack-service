package org.raincityvoices.ttrack.service.audio;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.sound.sampled.AudioFormat;

import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AudioDebugger {

    @Value
    @RequiredArgsConstructor
    public static class Settings {
        public static final int DEFAULT_MAX_LOG_LEN = 100;
        public Settings(double from, double to) { this(Range.of(from, to), DEFAULT_MAX_LOG_LEN); }
        public static final Settings NONE = new Settings(Range.of(-1.0, -1.0), 0);
        private final Range<Double> rangeSec;
        private final int maxLogLen;
    }

    private final String name;
    private final AudioFormat format;
    private final Settings settings;

    private int framesSeen = 0;

    public AudioDebugger(String name, AudioFormat format, Settings settings) {
        this.name = name;
        this.format = format;
        this.settings = settings;
        log.debug("AudioDebugger: {} {} {}", name, format, settings);
    }

    public void logBuffer(ByteBuffer buf) {
        log.debug("logBuffer: name={} elapsed={}", name, elapsedSec());
        // log.debug("buf: pos:{} limit:{} remaining:{}", buf.position(), buf.limit(), buf.remaining());
        int numShorts = buf.limit()/2;
        if (shouldLog()) {
            ShortBuffer sb = buf.asShortBuffer();
            short[] dest = new short[numShorts];
            sb.rewind();
            sb.get(dest);
            log.info("{} @ {}: {}", name, String.format("%.2f", elapsedSec()), formatSamples(dest));
        }
        framesSeen += numShorts / channels();
    }
    
    private String formatSamples(short[] samples) {
        int len = Math.min(samples.length, settings.getMaxLogLen());
        if (len <= 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        if (channels() > 1) {
            for (int i = 0; i < len; i += channels()) {
                sb.append('(');
                sb.append(samples[i]);
                for (int c = 1; c < channels(); c++) {
                    sb.append(' ');
                    sb.append(samples[i+c]);
                }
                sb.append(')');
            }
        } else {
            sb.append(samples[0]);
            for (int i = 1; i < len; i++) {
                sb.append(' ');
                sb.append(samples[i]);
            }
        }
        sb.append(']');
        return sb.toString();
    }
    
    public void logBuffer(FloatBuffer buf) {
        int numFloats = buf.limit();
        if (shouldLog()) {
            float[] dest = new float[buf.remaining()];
            buf.get(dest);
            buf.rewind();
            // TODO improve formatting to match short[] version
            log.info("{} @ {}: {}", name, String.format("%.2f", elapsedSec()), StringUtils.join(dest, ' '));
        }
        framesSeen += numFloats / channels();
    }
    
    public void advanceFrames(int numFrames) {
        framesSeen += numFrames;
    }

    public boolean shouldLog() {
        return settings.getRangeSec().contains(elapsedSec());
    }

    public void log(String message, Object... args) {
        if (shouldLog()) {
            String format = String.format("%s @ %.2f: %s", name, elapsedSec(), message);
            log.info(format, args);
        }
    }

    private double frameRate() { return format.getFrameRate(); }
    private double elapsedSec() { return framesSeen / frameRate(); }
    private int channels() { return format.getChannels(); }
}
