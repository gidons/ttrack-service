package org.raincityvoices.ttrack.service.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.raincityvoices.ttrack.service.model.TestData.BBS_4_PARTS;
import static org.raincityvoices.ttrack.service.model.TestData.BBS_NO_TENOR;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.audio.model.StereoMix;

public class MixUtilsTest {
    private static final float DELTA = 1e-3f;

    @Test
    void WHEN_dominantPartFoctors_with_five_parts_THEN_returns_factors_with_requested_value_on_dominant_and_other_parts_equal() {
        assertArrayEquals(new float[] { 0.15f, 0.15f, 0.15f, 0.4f, 0.15f }, 
        MixUtils.dominantPartFactors(5, 3, 0.4f),
                          DELTA);
        assertArrayEquals(new float[] { 0.25f, 0.0f, 0.25f, 0.25f, 0.25f }, 
                          MixUtils.dominantPartFactors(5, 1, 0f),
                          DELTA);
    }

    @Test
    void WHEN_targetPartFoctors_with_five_parts_THEN_returns_factors_with_target_factor_weighted_and_other_parts_equal() {
        assertArrayEquals(new float[] { 0.1f, 0.1f, 0.1f, 0.6f, 0.1f }, 
                          MixUtils.targetPartFactors(5, 3, 6f),
                          DELTA);
        assertArrayEquals(new float[] { 0.25f, 0.0f, 0.25f, 0.25f, 0.25f }, 
                          MixUtils.targetPartFactors(5, 1, 0f),
                          DELTA);
        assertArrayEquals(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 0.0f }, 
                          MixUtils.targetPartFactors(5, 0, Float.POSITIVE_INFINITY),
                          DELTA);
    }
    
    @Test
    public void WHEN_dominantPartFoctors_with_one_part_THEN_always_returns_1_0() {
        assertArrayEquals(new float[] { 1.0f },
                          MixUtils.dominantPartFactors(1, 1, 0f),
                          DELTA);
        assertArrayEquals(new float[] { 1.0f },
                          MixUtils.dominantPartFactors(1, 1, 0.5f),
                          DELTA);
    }

    @Test
    void testEqualMixFactors() {
        assertArrayEquals(new float[] { 0.2f, 0.2f, 0.2f, 0.2f, 0.2f }, 
                          MixUtils.equalMixFactors(5),
                          DELTA);
        assertArrayEquals(new float[] { 0.3333333f, 0.3333333f, 0.3333333f }, 
                          MixUtils.equalMixFactors(3),
                          DELTA);
        assertArrayEquals(new float[] { 1.0f }, 
                          MixUtils.equalMixFactors(1),
                          DELTA);
    }

    @Test
    void testParseStereoMix() {
        testParseOneStereoMix("Full Mix", BBS_4_PARTS, 
                                0.25f, 0.25f, 0.25f, 0.25f,
                                0.25f, 0.25f, 0.25f, 0.25f);
        testParseOneStereoMix("balanced stereo", BBS_4_PARTS, 
                                0.25f, 0.25f, 0.25f, 0.25f,
                                0.25f, 0.25f, 0.25f, 0.25f);
        testParseOneStereoMix("Bari left", BBS_4_PARTS, 
                                0.0f, 1.0f, 0.0f, 0.0f,
                                0.3333f, 0.0f, 0.3333f, 0.3333f);
        testParseOneStereoMix("right bari", BBS_4_PARTS, 
                                0.3333f, 0.0f, 0.3333f, 0.3333f,
                                0.0f, 1.0f, 0.0f, 0.0f);
        testParseOneStereoMix("Bari predom", BBS_4_PARTS, 
                                1f/7, 4f/7, 1f/7, 1f/7,
                                1f/7, 4f/7, 1f/7, 1f/7);
        testParseOneStereoMix("bass dominant", BBS_NO_TENOR, 
                                0.6666f, 0.16666f, 0.16666f,
                                0.6666f, 0.16666f, 0.1666f);
        testParseOneStereoMix("solo lead", BBS_NO_TENOR, 
                                0.0f, 0.0f, 1.0f,
                                0.0f, 0.0f, 1.0f);
        testParseOneStereoMix("Lead - Only", BBS_NO_TENOR, 
                                0.0f, 0.0f, 1.0f,
                                0.0f, 0.0f, 1.0f);
        testParseOneStereoMix("Lead (missing)", BBS_NO_TENOR, 
                                0.5f, 0.5f, 0.0f,
                                0.5f, 0.5f, 0.0f);
        testParseOneStereoMix("No bass", BBS_NO_TENOR, 
                                0.0f, 0.5f, 0.5f,
                                0.0f, 0.5f, 0.5f);
    }

    void testParseOneStereoMix(String name, List<AudioPart> parts, float... expectedFactors) {
        StereoMix mix = MixUtils.parseStereoMix(name, parts);
        float[] expLeft = Arrays.copyOf(expectedFactors, parts.size());
        float[] expRight = Arrays.copyOfRange(expectedFactors, parts.size(), 2 * parts.size());
        assertArrayEquals(mix.leftFactors(), expLeft, DELTA);
        assertArrayEquals(mix.rightFactors(), expRight, DELTA);
    }
}
