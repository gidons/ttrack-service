package org.raincityvoices.ttrack.service.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.audio.model.MonoMix;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TaskMetadataTest {
    private final ObjectMapper mapper = JsonUtils.newMapper();

    @Test
    public void testJsonRoundTrip() throws JsonProcessingException {
        CreateMixTrackMetadata original = CreateMixTrackMetadata.builder()
            .mixInfo(MixInfo.builder()
                .name("some mix")
                .parts(TestData.BBS_4_PARTS)
                .mix(new MonoMix(0.1f, 0.2f, 0.3f, 0.4f))
                .build())
            .build();

        String json = mapper.writeValueAsString(original);
        System.out.println(json);

        TaskMetadata recon = mapper.readValue(json, TaskMetadata.class);
        assertEquals(original, recon);
    }
}
