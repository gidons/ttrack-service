package org.raincityvoices.ttrack.service.audio.model;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.google.common.base.Preconditions;

import vavi.sound.sampled.mp3.Mp3LameFormatConversionProvider;

public class AudioFormats {

    public static final String WAV_TYPE = "audio/wav";
    public static final String WAV_EXT = ".wav";
    public static final String MP3_TYPE = "audio/mpeg";
    public static final String MP3_EXT = ".mp3";

    public static final AudioFormat MONO_PCM_44_1KHZ = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
    public static final AudioFormat MONO_PCM_48KHZ = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 1, 2, 48000, false);
    public static final AudioFormat STEREO_PCM_44_1KHZ = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
    public static final AudioFormat QUATTRO_PCM_44_1KHZ = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 4, 8, 44100, false);

    public static final AudioFileFormat WAV_MONO = new AudioFileFormat(AudioFileFormat.Type.WAVE, MONO_PCM_44_1KHZ, AudioSystem.NOT_SPECIFIED);
    public static final AudioFileFormat WAV_STEREO = new AudioFileFormat(AudioFileFormat.Type.WAVE, STEREO_PCM_44_1KHZ, AudioSystem.NOT_SPECIFIED);
    public static final AudioFileFormat WAV_QUATTRO = new AudioFileFormat(AudioFileFormat.Type.WAVE, QUATTRO_PCM_44_1KHZ, AudioSystem.NOT_SPECIFIED);

    /**
     * Given an AudioFormat that may or may not be PCM, return a PCM format that matches the
     * same audio characteristics (sample rate and channels). The output format will use
     * 16-bit signed PCM encoding.
     */
    public static AudioFormat toPcm(AudioFormat original) {
        return new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 
            original.getSampleRate(), 
            16, 
            original.getChannels(),
            original.getChannels() * 2,
            original.getSampleRate(),
            false);
    }

    public static AudioFormat toMp3(AudioFormat original) {
        return new AudioFormat(
            Mp3LameFormatConversionProvider.MPEG1L3, 
            original.getSampleRate(), 
            AudioSystem.NOT_SPECIFIED, 
            original.getChannels(), 
            AudioSystem.NOT_SPECIFIED, 
            AudioSystem.NOT_SPECIFIED, 
            false);        
    }

    public static AudioInputStream toPcmStream(AudioInputStream original) {
        return AudioSystem.getAudioInputStream(toPcm(original.getFormat()), original);
    }

    public static AudioInputStream toMp3Stream(AudioInputStream original) {
        return AudioSystem.getAudioInputStream(toMp3(original.getFormat()), original);
    }

    /**
     * Given a PCM-like (fixed frame and sample size) AudioFormat, return a new AudioFormat that 
     * has the same characteristics, but a different number of channels. This new format can be 
     * used when extracting channels from a multi-channel input, or when combining multiple
     * inputs (with the same format) into a single multi-channel output.
     */
    public static AudioFormat forOutputChannels(AudioFormat original, int numOutputChannels) {
        Preconditions.checkArgument(original.getFrameSize() > 0 && original.getSampleSizeInBits() > 0, "Original audio format doesn't specify sample and/or frame size.");
        int newFrameSize = numOutputChannels * original.getSampleSizeInBits() / 8;
        return new AudioFormat(original.getEncoding(), original.getSampleRate(), original.getSampleSizeInBits(),
                numOutputChannels, newFrameSize, original.getFrameRate(), original.isBigEndian());
    }
}
