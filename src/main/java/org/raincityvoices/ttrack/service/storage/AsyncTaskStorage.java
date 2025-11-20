package org.raincityvoices.ttrack.service.storage;

/**
 * Abstraction for persistent storage of async task tracking data.
 * Implementations store task lifecycle information in a persistent table.
 */
public interface AsyncTaskStorage {
    
    /**
     * Create and persist a new async task record.
     * The task is initially created with status PENDING.
     * 
     * @param taskDto the task DTO to persist.
     * @return the created task DTO (potentially updated with timestamps or IDs).
     */
    AsyncTaskDTO createTask(AsyncTaskDTO taskDto);
    
    /**
     * Update an existing async task record.
     * Used to transition task status or record completion.
     * 
     * @param taskDto the updated task DTO.
     * @return the updated task DTO.
     */
    AsyncTaskDTO updateTask(AsyncTaskDTO taskDto);
    
    /**
     * Retrieve an async task by its partition key and task ID.
     * 
     * @param partitionKey the partition key (format: "songId#trackId").
     * @param taskId the row key (unique task ID).
     * @return the task DTO if found; null if not found.
     */
    AsyncTaskDTO getTask(String taskId);
}
