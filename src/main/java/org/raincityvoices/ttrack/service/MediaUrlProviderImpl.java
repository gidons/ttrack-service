package org.raincityvoices.ttrack.service;

import java.net.URI;
import java.time.Duration;

import org.raincityvoices.ttrack.service.storage.AudioTrackDTO;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MediaUrlProviderImpl implements MediaUrlProvider {

    /** 
     * How long URLs used for AudioTrack.mediaUrl() will be valid for. 
     * TODO consider moving this value to configuration.
     */
    private static final Duration MEDIA_URL_VALIDITY = Duration.ofMinutes(60);

    private final MediaStorage mediaStorage;

    public URI getMediaUrl(AudioTrackDTO dto) {
        if (dto == null || dto.getMediaLocation() == null) { return null; }
        return URI.create(mediaStorage.getDownloadUrl(dto.getMediaLocation(), MEDIA_URL_VALIDITY));        
    }
}