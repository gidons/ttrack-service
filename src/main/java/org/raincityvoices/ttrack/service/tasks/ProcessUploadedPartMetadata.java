package org.raincityvoices.ttrack.service.tasks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for ProcessUploadedTrackTask.
 * Contains information needed to restart the task: the media location and original file name.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ProcessUploadedPartMetadata implements TaskMetadata {
    
    /** The media location (blob path) of the uploaded file. */
    private String mediaLocation;
}
