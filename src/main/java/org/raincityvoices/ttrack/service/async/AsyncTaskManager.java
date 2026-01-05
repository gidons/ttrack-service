package org.raincityvoices.ttrack.service.async;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AsyncTaskManager implements ApplicationContextAware {

    @Value
    @Accessors(fluent = true)
    public static class TaskExec<T extends AsyncTask<? extends AsyncTask.Input, O>, O extends AsyncTask.Output> {
        T task;
        Future<O> result;
    }

    private ApplicationContext appContext;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appContext = applicationContext;
    }

    public <I extends AsyncTask.Input, O extends AsyncTask.Output, T extends AsyncTask<I, O>> TaskExec<T,O> schedule(Class<T> taskClass, Object ... constructorArgs) {
        T task = appContext.getBean(taskClass, constructorArgs);
        try {
            task.initialize();
        } catch (Exception e) {
            log.error("Failed to initialize task {}", task, e);
            throw new RuntimeException("Failed to initialize task " + task, e);
        }
        TaskExec<T,O> exec = new TaskExec<>(task, executor.submit(task::execute));
        log.info("Scheduled task {}", task);
        return exec;
    }

}
