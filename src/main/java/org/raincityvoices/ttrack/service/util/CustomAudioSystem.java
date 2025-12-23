package org.raincityvoices.ttrack.service.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.AudioFileWriter;

import com.azure.cosmos.implementation.guava25.collect.ImmutableList;

import lombok.extern.slf4j.Slf4j;

/**
 * This class implements a subset of the functionality of Java's AudioSystem, but
 * unlike the original, providers are included explicitly, instead of being discovered at runtime.
 * This is useful both for optimization, but also to exclude certain bad providers.
 */
@Slf4j
public class CustomAudioSystem {

    private static final List<AudioFileReader> READERS = getReaders();
    private static final List<AudioFileWriter> WRITERS = getWriters();

    static List<AudioFileReader> getReaders() {
        ServiceLoader<AudioFileReader> loader = ServiceLoader.load(AudioFileReader.class);
        return StreamSupport.stream(loader.spliterator(), false)
            // filter out Tritonus readers; the WaveAudioFileReader has a bug where a stream with
            // embedded byteLength of -1 (AudioSystem.NOT_SPECIFIED) gets read as having length 7.
            .filter(r -> !r.getClass().getName().startsWith("org.tritonus"))
            .collect(toImmutableList());
    }

    static List<AudioFileWriter> getWriters() {
        return ImmutableList.copyOf(ServiceLoader.load(AudioFileWriter.class));
    }

    private interface ProviderFunction<P, T> {
        T get(P provider) throws UnsupportedAudioFileException, IOException; 
    }

    private static <T, P> T tryAllProviders(List<P> providers, ProviderFunction<P,T> action) throws UnsupportedAudioFileException, IOException {
        for (final P provider : providers) {
            try {
                T result = action.get(provider);
                log.info("Successfully handled by {}", provider.getClass());
                return result;
            } catch(final UnsupportedAudioFileException e) {
                log.debug("Provider {} failed.", provider.getClass());
            } catch(final IllegalArgumentException e) {
                log.debug("Provider {} failed.", provider.getClass());
            }
        }
        throw new UnsupportedAudioFileException("File of unsupported format");
    }

    public static AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException {
        Objects.requireNonNull(file);
        log.info("getAudioFileFormat({})", file);
        return tryAllProviders(READERS, p -> p.getAudioFileFormat(file));
    }

    public static AudioInputStream getAudioInputStream(File file) throws IOException, UnsupportedAudioFileException {
        Objects.requireNonNull(file);
        log.info("getAudioInputStream({})", file);
        return tryAllProviders(READERS, p -> p.getAudioInputStream(file));
    }

    public static int write(final AudioInputStream stream,
                            final AudioFileFormat.Type fileType,
                            final OutputStream out) throws IOException, UnsupportedAudioFileException {
        Objects.requireNonNull(stream);
        Objects.requireNonNull(fileType);
        Objects.requireNonNull(out);
        log.info("write({},{},{})", stream, fileType, out);
        return tryAllProviders(WRITERS, p -> p.write(stream, fileType, out));
    }

    public static int write(final AudioInputStream stream,
                            final AudioFileFormat.Type fileType,
                            final File out) throws IOException, UnsupportedAudioFileException {
        Objects.requireNonNull(stream);
        Objects.requireNonNull(fileType);
        Objects.requireNonNull(out);
        log.info("write({},{},{})", stream, fileType, out);
        return tryAllProviders(WRITERS, p -> p.write(stream, fileType, out));
    }
}
