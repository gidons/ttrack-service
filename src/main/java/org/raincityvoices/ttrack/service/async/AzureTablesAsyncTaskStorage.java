package org.raincityvoices.ttrack.service.async;

import org.raincityvoices.ttrack.service.storage.AsyncTaskDTO;
import org.raincityvoices.ttrack.service.storage.AsyncTaskStorage;
import org.raincityvoices.ttrack.service.storage.BaseTablesDAO;
import org.springframework.stereotype.Component;

import com.azure.data.tables.TableClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Azure Tables implementation of AsyncTaskStorage.
 * Stores async task tracking records in the AsyncTasks table.
 */
@Slf4j
@Component
public class AzureTablesAsyncTaskStorage implements AsyncTaskStorage {
    
    private final BaseTablesDAO<AsyncTaskDTO> dao;
    
    public AzureTablesAsyncTaskStorage(TableClient asyncTasksTableClient) {
        this.dao = new BaseTablesDAO<>(AsyncTaskDTO.class, asyncTasksTableClient);
    }
    
    @Override
    public AsyncTaskDTO createTask(AsyncTaskDTO taskDto) {
        log.info("Creating async task: taskId={}, status={}", taskDto.getTaskId(), taskDto.getStatus());
        log.debug("Task details: {}", taskDto);
        if (taskDto.hasETag()) {
            throw new IllegalArgumentException("Attempting to create a task with a DTO that has an ETag");
        }
        
        dao.put(taskDto);

        return taskDto;
    }
    
    @Override
    public AsyncTaskDTO updateTask(AsyncTaskDTO taskDto) {
        log.info("Updating async task: taskId={}, status={}, etag={}", 
            taskDto.getTaskId(), taskDto.getStatus(), taskDto.getETag());
        log.debug("Task details: {}", taskDto);
        if (!taskDto.hasETag()) {
            throw new IllegalArgumentException("Attempting to update a task with a DTO that has no ETag");
        }

        dao.put(taskDto);
        
        return taskDto;
    }
    
    @Override
    public AsyncTaskDTO getTask(String taskId) {
        log.info("Retrieving async task: taskId={}", taskId);
        
        return dao.get(taskId, "");
    }
    
    public boolean deleteTask(String taskId) {
        log.info("Attempting to delete async task: taskId={}", taskId);
        
        return dao.delete(taskId, "");
    }
}
