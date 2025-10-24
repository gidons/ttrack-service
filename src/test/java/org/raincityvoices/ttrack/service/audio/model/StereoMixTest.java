package org.raincityvoices.ttrack.service.audio.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.FloatBuffer;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.audio.MixUtils;
import org.raincityvoices.ttrack.service.model.TestData;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StereoMixTest {

    @Test
    public void mixesBuffersCorrectly() {
        StereoMix mix = new StereoMix(new float[] { 1.0f, 0.0f }, new float[] { 0.5f, 0.5f });

        FloatBuffer inBufs[] = new FloatBuffer[] {
            FloatBuffer.wrap(new float[] { 1.0f, 0.8f }),
            FloatBuffer.wrap(new float[] { 0.5f, 0.4f })
        };
        FloatBuffer outBuf = FloatBuffer.allocate(4);

        mix.mix(inBufs, outBuf);
        assertEquals(2, inBufs[0].position());
        assertEquals(2, inBufs[1].position());
        assertEquals(4, outBuf.limit());

        outBuf.position(0);

        assertEquals(1.0f, outBuf.get(), 1e-6);
        assertEquals(0.75f, outBuf.get(), 1e-6);

        assertEquals(0.8f, outBuf.get(), 1e-6);
        assertEquals(0.6f, outBuf.get(), 1e-6);
    }

    @Test
    public void leftAndRightFactorsMustBeSameLength() {
        assertThrows(IllegalArgumentException.class, () -> new StereoMix(new float[3], new float[4]));
    }

    @Test
    public void jsonRoundTripWithParts() throws JsonProcessingException {
        StereoMix mix = MixUtils.parseStereoMix("full-mix", TestData.BBS_4_PARTS);
        ObjectMapper mapper = JsonUtils.newMapper();
        String json = mapper.writeValueAsString(mix);
        System.out.println(json);
        AudioMix recon = mapper.readValue(json, AudioMix.class);
        assertInstanceOf(StereoMix.class, recon);
        assertEquals(mix, recon);
    }
}
