package org.raincityvoices.ttrack.service.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.IntStream;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.audio.model.AudioFormats;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FileMetadataTest {

    private static final ObjectMapper MAPPER = JsonUtils.newMapper();

    @Test
    void testJsonRoundTrip() throws Exception {
        FileMetadata original = FileMetadata.builder()
            .fileName("somefile.mp3")
            .lengthBytes(12346)
            .contentType("audio/mpeg")
            .durationSec(123)
            .etag("some-random-etag")
            .build();

        String json = MAPPER.writeValueAsString(original);
        System.out.println(json);

        FileMetadata recon = MAPPER.readValue(json, FileMetadata.class);
        assert original.equals(recon);
    }

    @Test
    void testFromWavAudioFileFormat() throws UnsupportedAudioFileException, IOException {
        FileMetadata actual = FileMetadata.fromAudioFileFormat(AudioSystem.getAudioFileFormat(TestData.SUNSHINE_LEAD_WAV));
        assertEquals(AudioFormats.WAV_TYPE, actual.contentType());
        assertEquals(123f, actual.durationSec(), 0.5);
        assertEquals(11814080, actual.lengthBytes());
    }

    @Test
    void testFromMp3AudioFileFormat() throws UnsupportedAudioFileException, IOException {
        FileMetadata actual = FileMetadata.fromAudioFileFormat(AudioSystem.getAudioFileFormat(TestData.SUNSHINE_LEAD_MP3));
        assertEquals(AudioFormats.MP3_TYPE, actual.contentType());
        assertEquals(123f, actual.durationSec(), 0.5);
        assertEquals(985004, actual.lengthBytes());
    }
    
    @Test
    void testFromBlobProperties() {
        // TODO
    }
    
    @Test
    void testFromMultipartFile() {
        // TODO
    }

    @Test
    // TODO TEMP TEMP TEMP
    void testBuffers() {
        ByteBuffer a = ByteBuffer.allocate(100);
        ByteBuffer b = ByteBuffer.allocate(100);
        IntStream.range(0, 100).forEach(i -> a.put((byte)i));
        IntStream.range(0,20).forEach(i -> b.put((byte)i));
        b.put(b.position(), a, 0, 80);
        assertEquals(100, b.position());
    }
}
