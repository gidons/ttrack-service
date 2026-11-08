package org.raincityvoices.ttrack.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.async.AsyncTaskManager;
import org.raincityvoices.ttrack.service.async.AsyncTaskManager.TaskExec;
import org.raincityvoices.ttrack.service.async.AudioTrackTask;
import org.raincityvoices.ttrack.service.async.ProcessUploadedPartTask;
import org.raincityvoices.ttrack.service.audio.SyntheticAudio;
import org.raincityvoices.ttrack.service.audio.SyntheticAudio.Choir;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.storage.async.AsyncTaskDTO;
import org.raincityvoices.ttrack.service.storage.async.AsyncTaskStorage;
import org.raincityvoices.ttrack.service.storage.files.FileMetadata;
import org.raincityvoices.ttrack.service.storage.media.MediaContent;
import org.raincityvoices.ttrack.service.storage.media.MediaStorage;
import org.raincityvoices.ttrack.service.storage.songs.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.songs.SongDTO;
import org.raincityvoices.ttrack.service.storage.songs.SongStorage;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.raincityvoices.ttrack.service.util.Temp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import vavi.sound.sampled.mp3.MpegAudioFileWriter;

/**
 * Tests {@link ProcessUploadedPartTask} against Azurite: the media "uploaded" for a part is seeded
 * directly in storage, the task is run, and the stored result and the task's recorded outcome are checked.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ProcessUploadedPartTaskIntegTest extends AzuriteIntegrationTestBase {

    private static final double SECONDS = 15;
    private static final int TIMEOUT_SEC = 120;

    @Autowired private AsyncTaskManager taskManager;
    @Autowired private SongStorage songStorage;
    @Autowired private MediaStorage mediaStorage;
    @Autowired private AsyncTaskStorage taskStorage;
    @Autowired private FileManager fileManager;

    private final Choir choir = new Choir(SECONDS);
    private String songId;

    @BeforeEach
    void createSong() {
        // Each test has its own song, so tests don't interfere with each other.
        songId = UUID.randomUUID().toString().substring(0, 8);
        songStorage.writeSong(SongDTO.builder().id(songId).title("Test Song").shortTitle("Test").build());
    }

    @Test
    void extractsTheChannelSpecifiedByTheUploader() throws Exception {
        float[] left = choir.partPredominant(choir.target);
        float[] right = choir.balancedMix();
        byte[] wav = SyntheticAudio.stereoWav(left, right);
        float[][] uploaded = SyntheticAudio.decodeChannels(SyntheticAudio.stereoStream(left, right));

        // The specified channel is used even if it isn't the one that automatic identification would choose.
        AudioTrackDTO leftPart = storeUpload("Lead", wav, "lead.wav");
        run(leftPart, 0).result().get(TIMEOUT_SEC, TimeUnit.SECONDS);
        AudioTrackDTO rightPart = storeUpload("Tenor", wav, "tenor.wav");
        run(rightPart, 1).result().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        float[][] storedLeft = readStored("Lead");
        assertThat(storedLeft).hasDimensions(1, uploaded[0].length);
        assertThat(storedLeft[0]).isEqualTo(uploaded[0]);
        float[][] storedRight = readStored("Tenor");
        assertThat(storedRight).hasDimensions(1, uploaded[1].length);
        assertThat(storedRight[0]).isEqualTo(uploaded[1]);
        assertThat(songStorage.describePart(songId, "Lead").getDurationSec()).isBetween(14, 16);
        assertThat(songStorage.describePart(songId, "Lead").getCurrentTaskId()).isNull();
    }

    @Test
    void identifiesTheTargetChannelWhenNoneIsSpecified() throws Exception {
        float[] left = choir.balancedMix();
        float[] right = choir.partPredominant(choir.target);
        float[][] uploaded = SyntheticAudio.decodeChannels(SyntheticAudio.stereoStream(left, right));
        AudioTrackDTO part = storeUpload("Lead", SyntheticAudio.stereoWav(left, right), "lead.wav");

        TaskExec<ProcessUploadedPartTask, AudioTrackTask.Output> exec = run(part);
        exec.result().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(taskStorage.getTask(exec.task().taskId()).getStatus()).isEqualTo(AsyncTaskDTO.SUCCEEDED);
        assertThat(readStored("Lead")[0]).isEqualTo(uploaded[1]);
    }

    @Test
    void keepsMonoUploadAsIs() throws Exception {
        byte[] wav = SyntheticAudio.monoWav(choir.target);
        AudioTrackDTO part = storeUpload("Lead", wav, "lead.wav");

        // A channel given for a mono upload has no effect.
        run(part, 1).result().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertThat(readStored("Lead")[0]).isEqualTo(
            SyntheticAudio.decodeChannels(AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav)))[0]);
        assertThat(songStorage.describePart(songId, "Lead").getDurationSec()).isBetween(14, 16);
    }

    @Test
    void extractsFromStereoMp3AndStoresMp3() throws Exception {
        float[] left = choir.partPredominant(choir.target);
        float[] right = choir.balancedMix();
        AudioTrackDTO part = storeUpload("Lead", encodeMp3(left, right), "lead.mp3");

        run(part).result().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        String location = mediaStorage.locationFor(songId, "Lead");
        assertThat(mediaStorage.getMediaMetadata(location).contentType()).isEqualTo(AudioFormats.MP3_TYPE);
        float[][] stored = readStored("Lead");
        assertThat(stored).hasNumberOfRows(1);
        assertThat((double) stored[0].length / SyntheticAudio.SAMPLE_RATE).isCloseTo(SECONDS, org.assertj.core.data.Offset.offset(0.5));
        // The audio went through two MP3 encodings (the upload and the extracted channel), and each delays
        // the signal by a few thousand samples, so search a wide range of alignments.
        int start = 5 * SyntheticAudio.SAMPLE_RATE;
        double withLeft = SyntheticAudio.maxCorrelation(left, stored[0], start, SyntheticAudio.SAMPLE_RATE, 8000);
        double withRight = SyntheticAudio.maxCorrelation(right, stored[0], start, SyntheticAudio.SAMPLE_RATE, 8000);
        assertThat(withLeft).isGreaterThan(0.95);
        assertThat(withRight).isLessThan(withLeft - 0.2);
    }

    @Test
    void rejectsStereoUploadWhenNeitherChannelIsClearlyThePart() throws Exception {
        float[] left = choir.partPredominant(choir.target);
        float[] right = choir.partPredominant(choir.otherTarget);
        AudioTrackDTO part = storeUpload("Lead", SyntheticAudio.stereoWav(left, right), "lead.wav");

        assertRejected("Lead", run(part), "Unable to determine which channel");
    }

    @Test
    void rejectsFileThatIsNotAudio() throws Exception {
        AudioTrackDTO part = storeUpload("Lead", "This is not audio.".getBytes(StandardCharsets.UTF_8), "lead.wav");

        assertRejected("Lead", run(part), "not a recognizable audio file");
    }

    @Test
    void rejectsAudioWithMoreThanTwoChannels() throws Exception {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 3, 6, 44100, false);
        byte[] pcm = new byte[3 * 6 * 44100];
        AudioInputStream threeChannels = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / 6);
        AudioTrackDTO part = storeUpload("Lead", SyntheticAudio.toWav(threeChannels), "lead.wav");

        assertRejected("Lead", run(part), "3 channels");
    }

    // Helpers

    private AudioTrackDTO storeUpload(String part, byte[] media, String fileName) throws IOException {
        String location = mediaStorage.locationFor(songId, part);
        mediaStorage.putMedia(location, new MediaContent(new ByteArrayInputStream(media), FileMetadata.builder().fileName(fileName).build()));
        return songStorage.writeTrack(AudioTrackDTO.builder().songId(songId).id(part).mediaLocation(location).build());
    }

    private TaskExec<ProcessUploadedPartTask, AudioTrackTask.Output> run(AudioTrackDTO part) {
        return taskManager.schedule(ProcessUploadedPartTask.class, part);
    }

    private TaskExec<ProcessUploadedPartTask, AudioTrackTask.Output> run(AudioTrackDTO part, int channel) {
        return taskManager.schedule(ProcessUploadedPartTask.class, part, channel);
    }

    /** @return the decoded channels of the media currently stored for the part. */
    private float[][] readStored(String part) throws Exception {
        MediaContent media = mediaStorage.getMedia(mediaStorage.locationFor(songId, part));
        try (InputStream in = new BufferedInputStream(media.stream());
             AudioInputStream pcm = AudioFormats.toPcm16Stream(AudioSystem.getAudioInputStream(in))) {
            return SyntheticAudio.decodeChannels(pcm);
        }
    }

    private byte[] encodeMp3(float[] left, float[] right) throws Exception {
        try (Temp.File file = Temp.file("test-stereo-")) {
            fileManager.writeAudio(AudioFormats.toMp3Stream(SyntheticAudio.stereoStream(left, right)), MpegAudioFileWriter.MP3, file);
            return Files.readAllBytes(file.toPath());
        }
    }

    /** Asserts that the task failed with a message containing the given text, leaving the part without media. */
    private void assertRejected(String part, TaskExec<ProcessUploadedPartTask, AudioTrackTask.Output> exec, String expectedMessage) {
        assertThatThrownBy(() -> exec.result().get(TIMEOUT_SEC, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);

        AsyncTaskDTO task = taskStorage.getTask(exec.task().taskId());
        assertThat(task.getStatus()).isEqualTo(AsyncTaskDTO.FAILED);
        assertThat(task.getErrorDetails()).contains(expectedMessage);
        AudioTrackDTO track = songStorage.describePart(songId, part);
        assertThat(track.hasMedia()).isFalse();
        assertThat(track.getCurrentTaskId()).isNull();
        assertThat(mediaStorage.exists(mediaStorage.locationFor(songId, part))).isFalse();
    }
}
