package org.raincityvoices.ttrack.tools;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.raincityvoices.ttrack.service.audio.AudioDebugger;
import org.raincityvoices.ttrack.service.audio.AudioMixingStream;
import org.raincityvoices.ttrack.service.audio.ChannelExtractingStream;
import org.raincityvoices.ttrack.service.audio.MixUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.audio.model.StereoMix;

import com.azure.cosmos.implementation.guava25.collect.ImmutableList;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Slf4j
public class WavProcessor {

    @Command(name = "info", description = "Display information about a WAV file")
    public static class FileInfo implements Callable<Integer> {

        @Parameters(index = "0", description = "The WAV file to analyze")
        private File wavFile;

        @Override
        public Integer call() throws Exception {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(wavFile);

            System.out.println("File: " + wavFile.getAbsolutePath());

            System.out.println("Type: " + fileFormat.getType());
            System.out.println("Format: " + fileFormat.getFormat());
            System.out.println("Frame length: " + fileFormat.getFrameLength());
            System.out.println("Properties: " + fileFormat.properties());
            return 0;
        }

    }

    @Command(name = "extract-channel", aliases = "ext", description="Extract a single channel from a multi-channel file")
    public static class ExtractChannel implements Callable<Integer> {

        static List<AudioPart> SUNSHINE_PARTS = ImmutableList.of("bass", "bari", "lead", "tenor").stream().map(s -> new AudioPart(s)).toList();

        @Option(names = { "-i", "input"}, description = "The WAV file to analyze")
        private File inFile;

        @Option(names = { "-c", "channel" }, description = "The 0-based index of the channel to extract")
        private int index;

        @Option(names = { "-o", "output" }, description = "The file to write the extracted channel to")
        private File outFile;

        @Option(names = { "-d", "debug" }, arity = "0..2", description = "Start and end times (in seconds) for debug output.")
        private Double[] debugRange;

        @Override
        public Integer call() throws Exception {
            final AudioDebugger.Settings debugSettings = getDebugSettings(debugRange);
            AudioInputStream in = AudioSystem.getAudioInputStream(inFile);
            AudioFormat format = in.getFormat();
            int numChannels = format.getChannels();
            if (numChannels <= 1) {
                log.info("Input file already has only one channel.");
                return 1;
            }
            if (index >= numChannels) {
                log.error("Input file has only {} channels", numChannels);
                return 2;
            }
            AudioInputStream decoded = AudioSystem.getAudioInputStream(AudioFormats.toPcm(format), in);
            ChannelExtractingStream stream = new ChannelExtractingStream(decoded, index, debugSettings);
            log.info("Writing output to " + outFile);
            AudioSystem.write(stream, Type.WAVE, outFile);
            return 0;
        }

    }

    @SuppressWarnings("null")
    private AudioDebugger.Settings getDebugSettings(Double[] debugRange) {
        final AudioDebugger.Settings debugSettings;
        switch(debugRange == null ? 0 : debugRange.length) {
            case 0: debugSettings = AudioDebugger.Settings.NONE; break;
            case 1: debugSettings = new AudioDebugger.Settings(debugRange[0], debugRange[0] + 1.0); break;
            case 2: debugSettings = new AudioDebugger.Settings(debugRange[0], debugRange[1]); break;
            default: throw new IllegalStateException();
        }
        log.debug("debugSettings: {}", debugSettings);
        return debugSettings;
    }

    @Command(name = "mix", description="Mix multiple input files to a single file")
    public static class MixFiles implements Callable<Integer> {

        @Option(names = { "-i", "input" }, description = "The input WAV file for each part", required = true)
        private Map<String, File> inFileByPartName;

        @Option(names = { "-m", "mix" }, description = "The mix to apply, e.g. 'lead-left' or 'full-mix'", required = true)
        private String mixName;

        @Option(names = { "-o", "output" }, description = "The file to write the mixed audio to", required = true)
        private File outFile;

        @Override
        public Integer call() throws Exception {
            List<AudioPart> parts = inFileByPartName.keySet().stream().map(AudioPart::new).toList();
            log.info("Input parts: {}", parts);
            List<File> inFilesInOrder = parts.stream().map(AudioPart::name).map(inFileByPartName::get).toList();
            log.info("Input files: {}", inFilesInOrder);
            StereoMix mix = MixUtils.parseStereoMix(mixName, parts);
            log.info("Mix: {}", mix);
            AudioInputStream inStreams[] = inFilesInOrder.stream().map(f -> openSafely(f)).toArray(n -> new AudioInputStream[n]);
            AudioInputStream mixingStream = AudioMixingStream.create(inStreams, mix, 1024);
            AudioSystem.write(mixingStream, Type.WAVE, outFile);
            return 0;
        }

        private static AudioInputStream openSafely(File inFile) {
            try {
                return AudioSystem.getAudioInputStream(inFile);
            } catch (UnsupportedAudioFileException | IOException e) {
                throw new RuntimeException("Failed to open input file " + inFile, e);
            }
        }
    }

    @Command(subcommands = {
        FileInfo.class,
        ExtractChannel.class,
        MixFiles.class
    })
    public static class Main {

    }

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new Main());
        System.exit(cl.execute(args));
    }
}
