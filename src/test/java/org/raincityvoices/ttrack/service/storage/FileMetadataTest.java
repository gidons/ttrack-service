package org.raincityvoices.ttrack.service.storage;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

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
}
