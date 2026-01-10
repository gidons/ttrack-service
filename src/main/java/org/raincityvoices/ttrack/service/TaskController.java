package org.raincityvoices.ttrack.service;

import org.raincityvoices.ttrack.service.api.Task;
import org.raincityvoices.ttrack.service.exceptions.NotFoundException;
import org.raincityvoices.ttrack.service.storage.AsyncTaskDTO;
import org.raincityvoices.ttrack.service.storage.AsyncTaskStorage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@RestController
@CrossOrigin
@RequestMapping(path = {"/tasks", "/tasks/"}, produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@RequiredArgsConstructor
public class TaskController {

    private final AsyncTaskStorage taskStorage;

    @GetMapping({"/{id}", "/{id}/"})
    public Task describeTask(@PathVariable("id") String taskId) {
        AsyncTaskDTO dto = taskStorage.getTask(taskId);
        if (dto == null) {
            throw new NotFoundException("Task " + taskId + " does not exist.");
        }
        return Task.builder()
            .id(taskId)
            .status(dto.getStatus())
            .input(dto.getInput())
            .output(dto.getOutput())
            .scheduled(dto.getScheduled())
            .ended(dto.getEndTime())
            .build();
    }
    
}
