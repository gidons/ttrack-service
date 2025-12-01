package org.raincityvoices.ttrack.service.storage;

import java.time.Instant;

import org.raincityvoices.ttrack.service.storage.mapper.PartitionKey;
import org.raincityvoices.ttrack.service.storage.mapper.Property;
import org.raincityvoices.ttrack.service.storage.mapper.Timestamp;
import org.raincityvoices.ttrack.service.tasks.TaskMetadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents an asynchronous task in the AsyncTasks table.
 * Tracks the lifecycle of audio processing tasks including their start time, end time, status, and metadata.
 * 
 * PartitionKey: combination of songId and trackId (format: "songId#trackId")
 * RowKey: unique opaque task ID (UUID or similar)
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AsyncTaskDTO extends BaseDTO {
    
    /** The task has been created and is waiting to be picked up for execution. */
    public static final String SCHEDULED = "SCHEDULED";
    /** The task was picked up and is waiting to lock the track. */
    public static final String PENDING = "PENDING";
    /** The task owns the track and is actively processing. */
    public static final String RUNNING = "RUNNING";
    /** The task has completed successfully. */
    public static final String SUCCEEDED = "SUCCEEDED";
    /** The task has aborted with an error. */
    public static final String FAILED = "FAILED";
    /** The task has aborted after timing out. */
    public static final String TIMEDOUT = "TIMEDOUT";
    /** Someone has requested that the task be canceled. */
    public static final String CANCELING = "CANCELING";
    /** The task has aborted due to requested cancelation. */
    public static final String CANCELED = "CANCELED";

    /** Partition key: unique and opaque. */
    @Getter(onMethod=@__(@PartitionKey))
    String taskId;

    public String getRowKey() { return ""; }
    public void setRowKey(String s) {}
    
    String status;
    
    /** Type of task (e.g., "CreateMixTrackTask", "ProcessUploadedTrackTask", "UploadPartTrackTask"). */
    String taskType;
    String songId;
    String trackId;
    Instant scheduled;
    Instant startTime;
    Instant endTime;
    
    /** Task-specific metadata serialized as JSON, needed to restart the task if necessary. */
    @Getter(onMethod=@__(@Property(type="json")))
    TaskMetadata metadata;
    
    /** 
     * Error message or exception details if the task failed. 
     * Null if task is PENDING, RUNNING, or SUCCEEDED. 
     */
    String errorDetails;
    
    /** Azure Table timestamp for tracking when the row was last modified. */
    @Getter(onMethod=@__(@Timestamp))
    Instant updated;
}
