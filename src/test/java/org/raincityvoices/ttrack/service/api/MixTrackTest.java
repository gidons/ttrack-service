package org.raincityvoices.ttrack.service.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

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
            .mixInfo(MixInfo.builder()
                .name("some mix")
                .parts(TestData.BBS_4_PARTS)
                .mix(new MonoMix(0.1f, 0.2f, 0.3f, 0.4f))
                .build())
            .created(Instant.now().minusSeconds(5))
            .updated(Instant.now())
            .hasMedia(true)
            .build();

        String json = mapper.writeValueAsString(original);
        System.out.println(json);

        MixTrack recon = mapper.readValue(json, MixTrack.class);
        // hasMedia isn't present in the JSON, and isn't set automatically
        MixTrack expected = original.toBuilder().hasMedia(false).build();
        assertEquals("some mix", recon.name());
        assertEquals(expected, recon);
    }
}
