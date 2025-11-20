package org.raincityvoices.ttrack.service.tasks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for UploadPartTrackTask.
 * Contains information needed to restart the task: the temporary audio file path and original file name.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class UploadPartTrackMetadata implements TaskMetadata {
    
    /** Path to the temporary audio file. */
    private String audioTempFilePath;
    
    /** The original file name provided by the uploader. */
    private String originalFileName;
}
