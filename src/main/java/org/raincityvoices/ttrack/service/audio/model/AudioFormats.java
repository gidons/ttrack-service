package org.raincityvoices.ttrack.service.audio.model;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

import com.google.common.base.Preconditions;

public class AudioFormats {

    public static final String WAV_TYPE = "audio/wav";
    public static final String WAV_EXT = ".wav";

    AudioFormat MONO_PCM_44_1KHZ = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false);
    AudioFormat STEREO_PCM_44_1KHZ = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
    AudioFormat QUATTRO_PCM_44_1KHZ = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 4, 8, 44100, false);

    AudioFileFormat WAV_MONO = new AudioFileFormat(AudioFileFormat.Type.WAVE, MONO_PCM_44_1KHZ, AudioSystem.NOT_SPECIFIED);
    AudioFileFormat WAV_STEREO = new AudioFileFormat(AudioFileFormat.Type.WAVE, STEREO_PCM_44_1KHZ, AudioSystem.NOT_SPECIFIED);
    AudioFileFormat WAV_QUATTRO = new AudioFileFormat(AudioFileFormat.Type.WAVE, QUATTRO_PCM_44_1KHZ, AudioSystem.NOT_SPECIFIED);

    public static AudioFormat forMix(AudioFormat original, AudioMix mix) {
        Preconditions.checkArgument(original.getChannels() == mix.numInputs());
        int numOutputChannels = mix.numOutputs();
        return forOutputChannels(original, numOutputChannels);
    }

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

    public static AudioFormat forOutputChannels(AudioFormat original, int numOutputChannels) {
        int newFrameSize = numOutputChannels * original.getSampleSizeInBits() / 8;
        return new AudioFormat(original.getEncoding(), original.getSampleRate(), original.getSampleSizeInBits(),
                numOutputChannels, newFrameSize, original.getFrameRate(), original.isBigEndian());
    }
}
