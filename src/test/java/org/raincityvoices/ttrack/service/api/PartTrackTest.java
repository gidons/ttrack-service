package org.raincityvoices.ttrack.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PartTrackTest {
    
    private final ObjectMapper mapper = JsonUtils.newMapper();

    @Test
    public void testTrackIdIsPartName() {
        PartTrack track = PartTrack.builder()
            .songId(new SongId("the song"))
            .part(TestData.BARI)
            .build();

        assertEquals(track.trackId(), track.part().name());
    }

    @Test
    public void testJsonRoundTrip() throws JsonProcessingException {
        PartTrack original = PartTrack.builder()
            .songId(new SongId("the song"))
            .part(TestData.BARI)
            .created(Instant.now().minusSeconds(5))
            .updated(Instant.now())
            .build();

        String json = mapper.writeValueAsString(original);
        // assertEquals("", json);

        PartTrack recon = mapper.readValue(json, PartTrack.class);
        assertEquals(original, recon);

        JsonNode tree = mapper.readTree(json);
        assertEquals("/songs/the%20song/parts/Bari", tree.get("url").asText());
    }
}
