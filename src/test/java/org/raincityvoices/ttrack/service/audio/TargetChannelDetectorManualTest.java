package org.raincityvoices.ttrack.service.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.raincityvoices.ttrack.service.audio.TargetChannelDetector.Outcome;
import org.raincityvoices.ttrack.service.audio.TargetChannelDetector.Result;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;

/**
 * Runs {@link TargetChannelDetector} on real stereo part-predominant tracks from the choir's library,
 * decoded through the same Java Sound path as production.
 *
 * <p>This is a manual test: the tracks are not part of the repository. Download them to
 * {@code data/<song>/} (that directory is ignored by git); each song's tests are skipped if its
 * directory is missing. Run it explicitly, e.g. {@code ./mvnw test -Dtest=TargetChannelDetectorManualTest}.
 *
 * <p>Each song directory holds one stereo MP3 per part; the part is predominant on the same channel
 * in all of them. Pairing two different parts' target channels gives stereo audio in which both
 * channels are part-predominant, which must not be resolved to either channel.
 */
class TargetChannelDetectorManualTest {

    private record Song(String dir, int targetChannel) {}

    private static final List<Song> SONGS = List.of(
        new Song("keep", 0),        // 48 kHz
        new Song("wonderful", 0),
        new Song("country", 1),
        new Song("ceimht", 0),      // more bleed from the other parts than usual
        new Song("down", 0));

    @TestFactory
    Stream<DynamicTest> identifiesTargetChannelOfRealTracks() {
        return SONGS.stream().flatMap(song -> {
            assumeSongPresent(song);
            return mp3s(song).stream().map(file -> DynamicTest.dynamicTest(
                song.dir() + "/" + file.getFileName(),
                () -> {
                    Result result = detect(file);
                    assertThat(result.outcome())
                        .describedAs(result.describe())
                        .isEqualTo(song.targetChannel() == 0 ? Outcome.LEFT : Outcome.RIGHT);
                }));
        });
    }

    @TestFactory
    Stream<DynamicTest> rejectsPairsOfPartPredominantChannels() {
        return SONGS.stream().flatMap(song -> {
            assumeSongPresent(song);
            List<Path> files = mp3s(song);
            List<DynamicTest> tests = new ArrayList<>();
            for (int a = 0; a < files.size(); a++) {
                for (int b = a + 1; b < files.size(); b++) {
                    Path fileA = files.get(a), fileB = files.get(b);
                    tests.add(DynamicTest.dynamicTest(song.dir() + "/" + fileA.getFileName() + " + " + fileB.getFileName(), () -> {
                        float[][] chA = decode(fileA);
                        float[][] chB = decode(fileB);
                        int n = Math.min(chA[0].length, chB[0].length);
                        float[] left = java.util.Arrays.copyOf(chA[song.targetChannel()], n);
                        float[] right = java.util.Arrays.copyOf(chB[song.targetChannel()], n);
                        Result result = TargetChannelDetector.detect(SyntheticAudio.stereoStream(sampleRate(fileA), left, right));
                        assertThat(result.outcome()).describedAs(result.describe()).isEqualTo(Outcome.AMBIGUOUS);
                    }));
                }
            }
            return tests.stream();
        });
    }

    private static void assumeSongPresent(Song song) {
        assumeTrue(Files.isDirectory(Path.of("data", song.dir())), "No data/" + song.dir() + " directory");
    }

    private static List<Path> mp3s(Song song) {
        try (Stream<Path> files = Files.list(Path.of("data", song.dir()))) {
            return files.filter(f -> f.toString().endsWith(".mp3")).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Result detect(Path file) throws Exception {
        try (AudioInputStream pcm = AudioFormats.toPcm16Stream(AudioSystem.getAudioInputStream(file.toFile()))) {
            return TargetChannelDetector.detect(pcm);
        }
    }

    private static float[][] decode(Path file) throws Exception {
        try (AudioInputStream pcm = AudioFormats.toPcm16Stream(AudioSystem.getAudioInputStream(file.toFile()))) {
            return SyntheticAudio.decodeChannels(pcm);
        }
    }

    private static int sampleRate(Path file) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
            return (int) in.getFormat().getSampleRate();
        }
    }
}
