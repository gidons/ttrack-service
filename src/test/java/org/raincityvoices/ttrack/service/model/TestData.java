package org.raincityvoices.ttrack.service.model;

import java.util.List;

import org.raincityvoices.ttrack.service.audio.model.AudioPart;

import com.google.common.collect.ImmutableList;

public class TestData {

    public static final AudioPart LEAD = new AudioPart("Lead");
    public static final AudioPart BASS = new AudioPart("Bass");
    public static final AudioPart TENOR = new AudioPart("Tenor");
    public static final AudioPart BARI = new AudioPart("Bari");

    public static final List<AudioPart> BBS_4_PARTS = ImmutableList.of(BASS, BARI, LEAD, TENOR);
    public static final List<AudioPart> BBS_NO_TENOR = ImmutableList.of(BASS, BARI, LEAD);

}
