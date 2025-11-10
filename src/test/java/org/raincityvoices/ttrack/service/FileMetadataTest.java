package org.raincityvoices.ttrack.service;

import org.junit.jupiter.api.Test;
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
    void testFromAudioFileFormat() {
        // TODO
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
