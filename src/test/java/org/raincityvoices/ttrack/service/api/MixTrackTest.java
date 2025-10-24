package org.raincityvoices.ttrack.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.audio.model.MonoMix;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MixTrackTest {

    private final ObjectMapper mapper = JsonUtils.newMapper();

    @Test
    public void testJsonRoundTrip() throws JsonProcessingException {
        MixTrack original = MixTrack.builder()
            .songId(new SongId("the song"))
            .name("some mix")
            .parts(TestData.BBS_4_PARTS)
            .mix(new MonoMix(0.1f, 0.2f, 0.3f, 0.4f))
            .blobName("the blob")
            .created(Instant.now().minusSeconds(5))
            .updated(Instant.now())
            .build();

        String json = mapper.writeValueAsString(original);
        // assertEquals("", json);

        MixTrack recon = mapper.readValue(json, MixTrack.class);
        assertEquals("some mix", recon.name());
        assertEquals(original, recon);
    }
}
