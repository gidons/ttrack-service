package org.raincityvoices.ttrack.service.model;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.raincityvoices.ttrack.service.api.MixInfo;
import org.raincityvoices.ttrack.service.audio.MixUtils;
import org.raincityvoices.ttrack.service.audio.model.AudioPart;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.AudioTrackDTO.AudioTrackDTOBuilder;

import com.google.common.collect.ImmutableList;

public class TestData {

    public static final String SUNSHINE_SONG_ID = "60923a30";

    public static final String TEST_SONG_ID = "fedcba09";

    public static final File SUNSHINE_LEAD_WAV = new File("src/test/resources/sunshine-lead.wav");
    public static final File SUNSHINE_LEAD_MP3 = new File("src/test/resources/sunshine-lead.mp3");

    public static final AudioPart LEAD = new AudioPart("Lead");
    public static final AudioPart BASS = new AudioPart("Bass");
    public static final AudioPart TENOR = new AudioPart("Tenor");
    public static final AudioPart BARI = new AudioPart("Bari");

    public static final List<String> BBS_4_PART_NAMES = ImmutableList.of("Bass", "Bari", "Lead", "Tenor");

    public static final List<AudioPart> BBS_4_PARTS = ImmutableList.of(BASS, BARI, LEAD, TENOR);
    public static final List<AudioPart> BBS_NO_TENOR = ImmutableList.of(BASS, BARI, LEAD);

    public static final Instant NOW = Instant.now();
    public static final Instant EARLIER = Instant.now().minus(Duration.ofHours(1));

    public static final int DURATION_SEC = 45;

    public static AudioTrackDTO partTrackDto(String partName, boolean processed) {
        AudioTrackDTOBuilder builder = AudioTrackDTO.builder()
            .songId(TEST_SONG_ID)
            .id(partName)
            .created(EARLIER)
            .updated(EARLIER)
            .mediaLocation(TEST_SONG_ID + "/" + partName);
        if (processed) {
            builder
                .durationSec(DURATION_SEC)
                .updated(NOW);
        }
        return builder.build();
    }

    public static AudioTrackDTO mixTrackDto(String name) {
        MixInfo mixInfo = MixUtils.parseStereoMixInfo(name, BBS_4_PARTS);
        return AudioTrackDTO.builder()
            .songId(SUNSHINE_SONG_ID)
            .id(name)
            .parts(BBS_4_PART_NAMES)
            .audioMix(mixInfo.mix())
            .durationSec(DURATION_SEC)
            .created(EARLIER)
            .updated(EARLIER)
            .mediaLocation(TEST_SONG_ID + "/" + name)
            .pitchShift(-1)
            .speedFactor(1.5)
            .build();
    }

    public static ByteArrayInputStream dummyInputStream() {
        return new ByteArrayInputStream(new byte[100]);
    }
}
