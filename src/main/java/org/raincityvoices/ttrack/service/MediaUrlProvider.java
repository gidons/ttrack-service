package org.raincityvoices.ttrack.service;

import java.net.URI;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;

public interface MediaUrlProvider {
    URI getMediaUrl(AudioTrackDTO dto);

    public static final MediaUrlProvider NOOP = dto -> null;
}
