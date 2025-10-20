package org.raincityvoices.ttrack.service.api;

import java.beans.Transient;

public interface AudioTrack {
    SongId songId();
    String trackId();
    String blobName();
    @Transient
    default boolean isProcessed() { return blobName() != null; }
}
