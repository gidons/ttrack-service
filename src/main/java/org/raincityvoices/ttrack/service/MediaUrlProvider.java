package org.raincityvoices.ttrack.service;

import java.net.URI;

import org.raincityvoices.ttrack.service.storage.songs.AudioTrackDTO;

public interface MediaUrlProvider {
    default URI getMediaUrl(AudioTrackDTO dto) {
        return getMediaUrl(dto.getSongId(), dto.getId());
    }
    URI getMediaUrl(String songId, String fileId);

    public static final MediaUrlProvider NOOP = new MediaUrlProvider() {
        @Override
        public URI getMediaUrl(String songId, String fileId) {
            return null;
        }
    };
}
