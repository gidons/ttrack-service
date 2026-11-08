package org.raincityvoices.ttrack.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.function.Supplier;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.api.Song;
import org.raincityvoices.ttrack.service.audio.SyntheticAudio;
import org.raincityvoices.ttrack.service.audio.SyntheticAudio.Choir;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Tests uploading part media over HTTP: the request parameters that select the target channel of a
 * stereo upload, and what the stored part looks like once the (asynchronous) processing is done.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PartUploadIntegTest extends AzuriteIntegrationTestBase {

    private static final double SECONDS = 15;
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final Choir choir = new Choir(SECONDS);

    @Test
    void singleUploadUsesTheSpecifiedChannel() throws Exception {
        String songId = createSong();
        // The part is predominant on the left, but the uploader asks for the right channel.
        float[] left = choir.partPredominant(choir.target);
        float[] right = choir.balancedMix();
        float[][] uploaded = SyntheticAudio.decodeChannels(SyntheticAudio.stereoStream(left, right));

        ResponseEntity<String> response = putPart(songId, "Lead", "?channel=1", SyntheticAudio.stereoWav(left, right));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(awaitMonoPart(songId, "Lead")[0]).isEqualTo(uploaded[1]);
    }

    @Test
    void singleUploadIdentifiesTheChannelWhenNoneIsSpecified() throws Exception {
        String songId = createSong();
        float[] left = choir.balancedMix();
        float[] right = choir.partPredominant(choir.target);
        float[][] uploaded = SyntheticAudio.decodeChannels(SyntheticAudio.stereoStream(left, right));

        putPart(songId, "Lead", "", SyntheticAudio.stereoWav(left, right));

        assertThat(awaitMonoPart(songId, "Lead")[0]).isEqualTo(uploaded[1]);
    }

    @Test
    void invalidChannelIsRejectedBeforeAnythingIsStored() throws Exception {
        String songId = createSong();
        byte[] wav = SyntheticAudio.stereoWav(choir.partPredominant(choir.target), choir.balancedMix());

        assertThat(putPart(songId, "Lead", "?channel=2", wav).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(putPart(songId, "Lead", "?channel=-1", wav).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(putPart(songId, "Lead", "?channel=left", wav).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(rest.getForEntity("/songs/{id}/parts", String.class, songId).getBody()).isEqualTo("[]");
    }

    @Test
    void multipleUploadHandlesEachFileSeparately() throws Exception {
        String songId = createSong();
        // Lead: part on the left, and the uploader says so. Bass: part on the right, nobody says so.
        float[] leadLeft = choir.partPredominant(choir.target);
        float[] leadRight = choir.balancedMix();
        float[] bassLeft = choir.balancedMix();
        float[] bassRight = choir.partPredominant(choir.otherTarget);
        float[][] lead = SyntheticAudio.decodeChannels(SyntheticAudio.stereoStream(leadLeft, leadRight));
        float[][] bass = SyntheticAudio.decodeChannels(SyntheticAudio.stereoStream(bassLeft, bassRight));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("part0", "Lead");
        body.add("file0", file(SyntheticAudio.stereoWav(leadLeft, leadRight), "lead.wav"));
        body.add("channel0", "0");
        body.add("part1", "Bass");
        body.add("file1", file(SyntheticAudio.stereoWav(bassLeft, bassRight), "bass.wav"));
        ResponseEntity<String> response = putMultipart("/songs/{id}/parts", body, songId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(awaitMonoPart(songId, "Lead")[0]).isEqualTo(lead[0]);
        assertThat(awaitMonoPart(songId, "Bass")[0]).isEqualTo(bass[1]);

        // The song's mixes are rebuilt from the parts, and that must wait until the uploads have been
        // processed: a mix can only be made from mono parts. The "All" mix has one channel per part.
        float[][] allMix = awaitAudio("/songs/{id}/mixes/All/media", songId);
        assertThat(allMix).hasNumberOfRows(2);
    }

    @Test
    void invalidChannelInMultipleUploadIsRejectedBeforeAnythingIsStored() throws Exception {
        String songId = createSong();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("part0", "Lead");
        body.add("file0", file(SyntheticAudio.stereoWav(choir.partPredominant(choir.target), choir.balancedMix()), "lead.wav"));
        body.add("channel0", "5");

        assertThat(putMultipart("/songs/{id}/parts", body, songId).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity("/songs/{id}/parts", String.class, songId).getBody()).isEqualTo("[]");
    }

    @Test
    void uploadThatCantBeResolvedToOneChannelEndsUpWithoutMedia() throws Exception {
        String songId = createSong();
        byte[] wav = SyntheticAudio.stereoWav(
            choir.partPredominant(choir.target), choir.partPredominant(choir.otherTarget));

        // The upload itself is accepted, since the media is only examined afterwards...
        assertThat(putPart(songId, "Lead", "", wav).getStatusCode()).isEqualTo(HttpStatus.OK);

        // ... and then the part is left without media.
        await("the rejected part to lose its media", () ->
            rest.getForEntity("/songs/{id}/parts/{part}/media", byte[].class, songId, "Lead").getStatusCode() == HttpStatus.NOT_FOUND
                ? Boolean.TRUE : null);
    }

    // Helpers

    private String createSong() {
        return postJson("/songs", Song.builder().title("Upload Test").shortTitle("Upload").build(), Song.class).getId().value();
    }

    private ResponseEntity<String> putPart(String songId, String part, String query, byte[] wav) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("audioFile", file(wav, part.toLowerCase() + ".wav"));
        return putMultipart("/songs/{id}/parts/{part}" + query, body, songId, part);
    }

    private ResponseEntity<String> putMultipart(String url, MultiValueMap<String, Object> body, Object... uriVars) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class, uriVars);
    }

    private static ByteArrayResource file(byte[] bytes, String fileName) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    /** Wait until the part's media is mono, i.e. the upload has been processed, and return its channels. */
    private float[][] awaitMonoPart(String songId, String part) {
        return await("part " + part + " to be processed to mono", () -> {
            float[][] channels = fetchAudio("/songs/{id}/parts/{part}/media", songId, part);
            return channels != null && channels.length == 1 ? channels : null;
        });
    }

    /** Wait until the given media can be downloaded and decoded, and return its channels. */
    private float[][] awaitAudio(String url, Object... uriVars) {
        return await(url + " to be available", () -> fetchAudio(url, uriVars));
    }

    /** @return the decoded channels of the downloaded audio, or null if it isn't available (yet). */
    private float[][] fetchAudio(String url, Object... uriVars) {
        ResponseEntity<byte[]> response = rest.getForEntity(url, byte[].class, uriVars);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        try (AudioInputStream pcm = AudioFormats.toPcm16Stream(
                AudioSystem.getAudioInputStream(new ByteArrayInputStream(response.getBody())))) {
            return SyntheticAudio.decodeChannels(pcm);
        } catch (Exception e) {
            return null;
        }
    }

    /** Poll until the supplier returns a non-null result. */
    private static <T> T await(String description, Supplier<T> supplier) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (true) {
            T result = supplier.get();
            if (result != null) {
                return result;
            }
            if (System.nanoTime() > deadline) {
                return fail("Timed out waiting for " + description);
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fail("Interrupted while waiting for " + description);
            }
        }
    }
}
