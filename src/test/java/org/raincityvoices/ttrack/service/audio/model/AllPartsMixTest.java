package org.raincityvoices.ttrack.service.audio.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AllPartsMixTest {

    private static final ObjectMapper MAPPER = JsonUtils.newMapper();

    @Test
    void testRoundTrip() throws JsonProcessingException {
        AllPartsMix mix = new AllPartsMix(4);
        String json = MAPPER.writeValueAsString(mix);
        log.info("JSON: " + json);
        // fail();
        AllPartsMix recon = MAPPER.readValue(json, AllPartsMix.class);
        assertEquals(mix, recon);
    }
}
