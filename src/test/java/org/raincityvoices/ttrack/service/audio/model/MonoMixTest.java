package org.raincityvoices.ttrack.service.audio.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.FloatBuffer;

import org.junit.jupiter.api.Test;

public class MonoMixTest {

    @Test
    public void mixesSamplesCorrectly() {
        float[] factors = new float[] { 0.3f, 0.7f };

        MonoMix mix = new MonoMix(factors);

        float[] sample = new float[] { 1.0f, 0.5f };

        float raw = mix.mixOne(sample);
        assertEquals(1.0f * 0.3f + 0.5f * 0.7f, raw, 1e-6f);
    }

    @Test
    public void mixesBuffersCorrectly() {
        float[] factors = new float[] { 0.3f, 0.7f };

        MonoMix mix = new MonoMix(factors);

        FloatBuffer inBufs[] = new FloatBuffer[] {
            FloatBuffer.wrap(new float[] { 1.0f, 0.8f }),
            FloatBuffer.wrap(new float[] { 0.5f, 0.4f })
        };
        FloatBuffer outBuf = FloatBuffer.allocate(2);
        mix.mix(inBufs, outBuf);

        assertEquals(2, inBufs[0].position());
        assertEquals(2, inBufs[1].position());
        assertEquals(2, outBuf.limit());

        outBuf.position(0);
        assertEquals(1.0f * 0.3f + 0.5f * 0.7f, outBuf.get(), 1e-6);
        assertEquals(0.8f * 0.3f + 0.4f * 0.7f, outBuf.get(), 1e-6);
    }

    @Test
    public void mixFactorsOutOfRangeThrows() {
        float[] factors = new float[] { -0.1f };

        assertThrows(IllegalStateException.class, () -> new MonoMix(factors));
    }

    @Test
    public void mixFactorsSumNotOneThrows() {
        float[] factors = new float[] { 0.2f, 0.2f };

        assertThrows(IllegalStateException.class, () -> new MonoMix(factors));
    }
}
