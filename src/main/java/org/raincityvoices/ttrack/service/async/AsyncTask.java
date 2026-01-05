package org.raincityvoices.ttrack.service.async;

import static lombok.AccessLevel.PUBLIC;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.raincityvoices.ttrack.service.storage.AsyncTaskDTO;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * - Input: information required to construct the task object, e.g. song/track ID, set by the caller.
 * - Result: the value returned by the task on completion.
 * @param <I> input parameters, passed to constructor.
 * @param <O> result type.
 */
@Slf4j
@RequiredArgsConstructor
@Getter(AccessLevel.PROTECTED)
@Accessors(fluent = true)
public abstract class AsyncTask<I extends AsyncTask.Input, O extends AsyncTask.Output> {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(fluent = false)
    public static class Input {
        private String songId;
    }
    public static class Output {}

    @Getter(PUBLIC)
    private final I input;
    @Autowired
    private AzureTablesAsyncTaskStorage asyncTaskStorage;
    @Autowired
    private Clock clock;
    
    @Getter(PUBLIC)
    private final String taskId = UUID.randomUUID().toString();
    private AsyncTaskDTO taskDto;

    public String songId() { return input().getSongId(); }

    /**
     * Perform synchronous initializations and validations on task creation, and then create the persistent record.
     * @throws Exception
     */
    public void initialize() throws Exception {
        log.info("Initializing task {} of type {}", taskId(), getTaskType());  
        doInitialize();
        createAsyncTaskRecord();
    }

    public abstract Class<I> getInputClass();

    public O execute() {
        MDC.put("correlationId", taskId());
        fetchTaskOrFail();
        log.info("Task {} waiting for lock...", taskId());
        try {
            taskDto.setStatus(AsyncTaskDTO.PENDING);
            asyncTaskStorage.updateTask(taskDto);
            if (!waitForLock()) {
                log.error("Task {} timed out waiting for lock.", taskId());
                throw new TimeoutException("Timed out waiting for lock.");
            }
        } catch (Exception e) {
            log.error("Failed while locking for task {}", taskId(), e);
            updateAsyncTaskFailed(e);
            throw new RuntimeException(e);
        }
        try {
            log.info("Starting task {}", this);
            updateAsyncTaskRunning();

            log.info("Task {} is now processing.", taskId());
            O output = process();
            updateAsyncTaskSucceeded(output);
            log.info("Completed task {}. Output: {}", this, output);
            return output;
        } catch (Exception e) {
            log.error("Task failed processing", e);
            updateAsyncTaskFailed(e);
            throw new RuntimeException(e);
        } finally {
            releaseLock();
        }
    }

    /**
     * Create and persist an async task record in the database.
     * The task is initially created with status SCHEDULED.
     */
    private void createAsyncTaskRecord() {
        taskDto = createTaskDTO(input);

        asyncTaskStorage.createTask(taskDto);
        log.info("Created async task record: {}", taskId);
    }

    protected AsyncTaskDTO createTaskDTO(I input) {
        return AsyncTaskDTO.builder()
                .taskId(taskId)
                .status(AsyncTaskDTO.SCHEDULED)
                .taskType(getTaskType())
                .scheduled(Instant.now(clock))
                .input(input)
                .build();
    }

    /**
     * Update async task status to RUNNING after initialization.
     */
    private void updateAsyncTaskRunning() {
        taskDto.setStatus(AsyncTaskDTO.RUNNING);
        taskDto.setStartTime(clock().instant());
        asyncTaskStorage.updateTask(taskDto);
        log.info("Updated async task to RUNNING: {}", taskDto.getTaskId());
    }

    private void fetchTaskOrFail() {
        taskDto = asyncTaskStorage().getTask(taskId);
        if (taskDto == null) {
            throw new RuntimeException("Async task with ID " + taskId + " not found in DB.");
        }
        log.info("taskDto: {}", taskDto);
    }

    /**
     * Update async task status to SUCCEEDED after successful processing.
     */
    private void updateAsyncTaskSucceeded(O output) {
        if (taskDto != null) {
            taskDto.setStatus(AsyncTaskDTO.SUCCEEDED);
            taskDto.setEndTime(clock().instant());
            taskDto.setOutput(output);
            asyncTaskStorage.updateTask(taskDto);
            log.info("Updated async task to SUCCEEDED: {}", taskDto.getTaskId());
        }
    }

    /**
     * Update async task status to FAILED with error details.
     */
    private void updateAsyncTaskFailed(Exception e) {
        updateAsyncTaskFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
    }

    private void updateAsyncTaskFailed(String errorDetails) {
        if (taskDto != null) {
            taskDto.setStatus(AsyncTaskDTO.FAILED);
            taskDto.setEndTime(Instant.now(clock));
            taskDto.setErrorDetails(errorDetails);
            asyncTaskStorage.updateTask(taskDto);
            log.info("Updated async task to FAILED: {}", taskDto.getTaskId());
        }
    }

    protected abstract String getTaskType();

    /**
     * Perform initializations and validations that can be done synchronously.
     */
    protected abstract void doInitialize() throws Exception;

    protected boolean waitForLock() throws Exception { return true; }
    protected void releaseLock() { return; }

    /**
     * Perform the main processing. This happens asynchronously.
     */
    protected abstract O process() throws Exception;

}
