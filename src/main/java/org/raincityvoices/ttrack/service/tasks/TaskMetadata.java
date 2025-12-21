package org.raincityvoices.ttrack.service.tasks;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for task-specific metadata that can be persisted and used to restart a task if necessary.
 * Implementations should be JSON-serializable via Jackson.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "_type", include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProcessUploadedPartMetadata.class, name = "ProcessUploadedTrack"),
    @JsonSubTypes.Type(value = UploadPartTrackMetadata.class, name = "UploadPartTrack"),
    @JsonSubTypes.Type(value = CreateMixTrackMetadata.class, name = "CreateMixTrack"),
    @JsonSubTypes.Type(value = RefreshMixTrackTask.Metadata.class, name = "RefreshMixTrack"),
    @JsonSubTypes.Type(value = TaskMetadata.Empty.class, name = "EmptyMetadata")
})
public interface TaskMetadata {

    public class Empty implements TaskMetadata {}
}
