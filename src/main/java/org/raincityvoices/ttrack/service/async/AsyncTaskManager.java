package org.raincityvoices.ttrack.service.async;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
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

    /**
     * Like {@link #schedule}, but the task doesn't start executing until all the given prerequisites 
     * have completed. A prerequisite that failed does not prevent the task from running; whether the
     * failure matters is for the task to decide.
     * <p>
     * Waiting occupies a pool thread. That can't deadlock, because the pool's queue is FIFO and the
     * prerequisites were submitted before this task, so each has already started (or finished) by the
     * time this task gets a thread.
     */
    public <I extends AsyncTask.Input, O extends AsyncTask.Output, T extends AsyncTask<I, O>> TaskExec<T,O> scheduleAfter(
            Collection<? extends Future<?>> prerequisites, Class<T> taskClass, Object ... constructorArgs) {
        T task = appContext.getBean(taskClass, constructorArgs);
        try {
            task.initialize();
        } catch (Exception e) {
            log.error("Failed to initialize task {}", task, e);
            throw new RuntimeException("Failed to initialize task " + task, e);
        }
        List<Future<?>> waitFor = List.copyOf(prerequisites);
        TaskExec<T,O> exec = new TaskExec<>(task, executor.submit(() -> {
            for (Future<?> prerequisite : waitFor) {
                try {
                    prerequisite.get();
                } catch (ExecutionException e) {
                    log.info("A prerequisite of task {} failed; proceeding anyway.", task, e);
                }
            }
            return task.execute();
        }));
        log.info("Scheduled task {} to run after {} other task(s)", task, waitFor.size());
        return exec;
    }

}
