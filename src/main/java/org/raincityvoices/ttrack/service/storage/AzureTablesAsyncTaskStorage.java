package org.raincityvoices.ttrack.service.storage;

import org.raincityvoices.ttrack.service.storage.mapper.TableEntityMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;

import lombok.extern.slf4j.Slf4j;

/**
 * Azure Tables implementation of AsyncTaskStorage.
 * Stores async task tracking records in the AsyncTasks table.
 */
@Slf4j
@Component
public class AzureTablesAsyncTaskStorage implements AsyncTaskStorage {
    
    private final TableClient tableClient;
    private final TableEntityMapper<AsyncTaskDTO> taskMapper = new TableEntityMapper<>(AsyncTaskDTO.class);
    
    public AzureTablesAsyncTaskStorage(TableClient asyncTasksTableClient) {
        this.tableClient = asyncTasksTableClient;
    }
    
    @Override
    public AsyncTaskDTO createTask(AsyncTaskDTO taskDto) {
        log.info("Creating async task: taskId={}, status={}", 
                 taskDto.getTaskId(), taskDto.getStatus());
        log.debug("Task details: {}", taskDto);
        
        try {
            TableEntity entity = taskMapper.toTableEntity(taskDto);
            log.debug("Table entity: {}", entity.getProperties());
            tableClient.createEntity(entity);
            log.info("Async task created successfully");
        } catch(Exception e) {
            log.error("Failed to create async task {}", taskDto.getTaskId(), e);
            throw new RuntimeException("Failed to create async task", e);
        }
        
        return taskDto;
    }
    
    @Override
    public AsyncTaskDTO updateTask(AsyncTaskDTO taskDto) {
        log.info("Updating async task: taskId={}, status={}", 
                 taskDto.getTaskId(), taskDto.getStatus());
        log.debug("Task details: {}", taskDto);
        
        try {
            TableEntity entity = taskMapper.toTableEntity(taskDto);
            log.debug("Table entity: {}", entity.getProperties());
            tableClient.upsertEntity(entity);
            log.info("Async task updated successfully");
        } catch(Exception e) {
            log.error("Failed to update async task {}", taskDto.getTaskId(), e);
            throw new RuntimeException("Failed to update async task", e);
        }
        
        return taskDto;
    }
    
    @Override
    public AsyncTaskDTO getTask(String taskId) {
        log.info("Retrieving async task: taskId={}", taskId);
        
        try {
            TableEntity entity = tableClient.getEntity(taskId, "");
            log.debug("Table entity: {}", entity.getProperties());
            return taskMapper.fromTableEntity(entity);
        } catch (TableServiceException e) {
            if (e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
                log.info("Async task not found: {}", taskId);
                return null;
            }
            log.error("Failed to get async task {}", taskId, e);
            throw new RuntimeException("Failed to retrieve async task", e);
        } catch(Exception e) {
            log.error("Failed to read async task {}", taskId, e);
            throw new RuntimeException("Failed to read async task", e);
        }
    }
}
