package org.raincityvoices.ttrack.service.audio;

import static java.lang.Float.POSITIVE_INFINITY;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.audio.model.StereoMix;
import org.raincityvoices.ttrack.service.util.JsonUtils;

import com.azure.cosmos.implementation.guava25.collect.ImmutableList;
import com.azure.cosmos.implementation.guava25.collect.Sets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MixUtils {

    private static final float PREDOMINANT_WEIGHT = 4.0f;

    private static final ObjectMapper MAPPER = JsonUtils.newMapper();

    public static List<MixInfo> getParseableMixes(List<AudioPart> parts) {
        if (parts.isEmpty()) {
            return List.of();
        }
        ImmutableList.Builder<String> names = ImmutableList.builder();
        names.add("Full Mix");
        for (AudioPart part : parts) {
            names
                .add(part.name() + " Solo")
                .add(part.name() + " Left")
                .add(part.name() + " Dominant")
                .add(part.name() + " Missing");
        }
        for (Set<AudioPart> duet : Sets.combinations(Set.copyOf(parts), 2)) {
            names.add(duet.stream().map(AudioPart::name).collect(Collectors.joining(" ")) + " Duet");
        }

        return names.build().stream().map(n -> MixUtils.parseStereoMixInfo(n, parts)).toList();
    }

    // public static List<MixInfo> getMixPackage(String description, int pitchShift, double speedFactor) {
    // }

    public static MixInfo parseStereoMixInfo(String description, List<AudioPart> parts) {
        return MixInfo.builder()
            .name(description)
            .parts(parts)
            .mix(parseStereoMix(description, parts))
            .build();
    }

    /** 
     * Supported mix description formats:
     * - "full" or "full <whatever>"
     * - "<part> <type>" or "<type> <part>"
     *   Where type is "solo"/"only", "dominant"/"predominant", "left"/"stereo", "right", or "missing"
     * - "<part1> <part2>" with or without "duet". Part 1 will be left, Part 2 right.
     */
    public static StereoMix parseStereoMix(String description, List<AudioPart> allParts) {
        log.info("Parsing stereo mix name: '{}'", description);
        if (description.trim().startsWith("{")) {
            try {
                return MAPPER.readValue(description, StereoMix.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Unable to parse JSON as StereoMix: " + description, e);
            }
        }
        int numParts = allParts.size();
        List<String> tokens = List.of(description.split("\\W+", 0));
        List<Integer> partIndexes = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String token : tokens) {
            OptionalInt partIndex = IntStream.range(0, numParts)
                            .filter(i -> allParts.get(i).name().equalsIgnoreCase(token))
                            .findFirst();
            if (partIndex.isPresent()) {
                partIndexes.add(partIndex.getAsInt());
            } else {
                other.add(token.toLowerCase());
            }
        }
        final float[] leftFactors;
        final float[] rightFactors;

        if (partIndexes.size() == 2) {
            // duet
            int leftIndex = partIndexes.get(0);
            int rightIndex = partIndexes.get(1);
            log.debug("duet: {}, {}", leftIndex, rightIndex);
            leftFactors = targetPartFactors(numParts, leftIndex, POSITIVE_INFINITY);
            rightFactors = targetPartFactors(numParts, rightIndex, POSITIVE_INFINITY);
        } else if (partIndexes.isEmpty() && (other.contains("full") || other.contains("balanced"))) {
            log.debug("full mix");
            leftFactors = equalMixFactors(numParts);
            rightFactors = equalMixFactors(numParts);
        } else if (partIndexes.size() == 1 && other.size() >= 1) {
            int partIndex = partIndexes.get(0);
            String type = other.get(0);
            log.debug("target: {} {} {}", allParts.get(partIndex), partIndex, type);
            switch(type.toLowerCase()) {
            case "solo":
            case "only":
                leftFactors = targetPartFactors(numParts, partIndex, POSITIVE_INFINITY);
                rightFactors = leftFactors;
                break;
            case "left":
            case "stereo":
                leftFactors = targetPartFactors(numParts, partIndex, POSITIVE_INFINITY);
                rightFactors = targetPartFactors(numParts, partIndex, 0.0F);
                break;
            case "right":
                leftFactors = targetPartFactors(numParts, partIndex, 0.0F);
                rightFactors = targetPartFactors(numParts, partIndex, POSITIVE_INFINITY);
                break;
            case "dominant":
            case "predom":
            case "predominant":
                leftFactors = targetPartFactors(numParts, partIndex, PREDOMINANT_WEIGHT);
                rightFactors = leftFactors;
                break;
            case "missing":
            case "no":
                leftFactors = targetPartFactors(numParts, partIndex, 0.0f);
                rightFactors = leftFactors;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized mix type: " + type);
            }
        } else {
            throw new IllegalArgumentException("Unable to parse mix name: " + description);
        }
        StereoMix mix = new StereoMix(leftFactors, rightFactors);
        log.info("Result: {}", mix);
        return mix;
    }

    public static StereoMix partSolo(String name, List<AudioPart> allParts, AudioPart soloPart) {
        int soloPartIndex = allParts.indexOf(soloPart);
        if (soloPartIndex < 0) {
            throw new IllegalArgumentException("Part " + soloPart + " not in list");
        }
        return new StereoMix( 
            dominantPartFactors(allParts.size(), soloPartIndex, 1.0F),
            dominantPartFactors(allParts.size(), soloPartIndex, 1.0F));
    }

    public static float[] targetPartFactors(int numParts, int targetPartIndex, float targetPartWeight) {
        final float targetFactor;
        final float otherFactor;
        if (targetPartWeight == POSITIVE_INFINITY) {
            targetFactor = 1.0f;
        } else {
            float totalWeight = targetPartWeight + numParts - 1;
            targetFactor = targetPartWeight / totalWeight;
        }
        otherFactor = (1.0f - targetFactor) / (numParts - 1);
        float[] factors = new float[numParts];
        Arrays.fill(factors, otherFactor);
        factors[targetPartIndex] = targetFactor;
        return factors;
    }

    public static float[] dominantPartFactors(int numParts, int dominantPartIndex, float dominantPartFactor) {
        float[] factors = new float[numParts];
        if (numParts == 1) {
            factors[0] = 1.0f;
            return factors;
        }
        float otherPartFactor = (1.0f - dominantPartFactor) / (numParts - 1);
        for (int i = 0; i < numParts; i++) {
            factors[i] = (i == dominantPartIndex) ? dominantPartFactor : otherPartFactor;
        }
        return factors;
    }

    public static float[] equalMixFactors(int numParts) {
        return targetPartFactors(numParts, 0, 1.0f);
        // float[] factors = new float[numParts];
        // float factor = 1.0f / numParts;
        // Arrays.fill(factors, factor);
        // return factors;
    }

    public static void logBuffer(ByteBuffer buf, String name) {
        int numShorts = buf.limit()/2;
        ShortBuffer sb = buf.asShortBuffer();
        short[] dest = new short[numShorts];
        sb.rewind();
        sb.get(dest);
        log.debug("{} @ {}/{}: {}", name, sb.position(), sb.limit(), StringUtils.join(dest, ' '));
    }

    public static void logBuffer(FloatBuffer buf, String name) {
        float[] dest = new float[buf.remaining()];
        buf.get(dest);
        buf.rewind();
        log.debug("{} @ {}/{}: {}", name, buf.position(), buf.limit(), StringUtils.join(dest, ' '));
    }

    private MixUtils() { /* prevent instantiation */ }
}
