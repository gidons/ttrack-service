package org.raincityvoices.ttrack.service;

import java.net.URI;
import java.time.Duration;

import org.raincityvoices.ttrack.service.storage.media.MediaStorage;
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

    public URI getMediaUrl(String songId, String fileId) {
        return URI.create(mediaStorage.getDownloadUrl(mediaStorage.locationFor(songId, fileId), MEDIA_URL_VALIDITY));        
    }
}