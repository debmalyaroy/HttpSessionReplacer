package com.test.session;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.Closeable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SessionConfigurationService;

/**
 * Support class that provides methods for launching and scheduling of tasks.
 * The implementation will use either managed thread factory when supported by
 * JEE container, or {@link Executors#defaultThreadFactory()} if the managed one
 * was not available.
 * <p>
 * The implementation also provides metrics about number of thread in pool and
 * number of active threads.
 * </p>
 */
public final class TaskExecutorProcess implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutorProcess.class);

    private static final String NAMESPACE = "pool-%s-";

    // Discard and log tasks for which there are no free threads.
    private static final RejectedExecutionHandler DISCARD_AND_LOG = (r, executor) -> LOGGER.error("Discarding submitted task: {}", r);

    // Log uncaught exceptions while thread execution
    private static final UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER = (t, e) -> LOGGER.error("Uncaught exeception occured while execting thread ", t, e);

    private static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);
    private static final int WAIT_FOR_SHUTDOWN = 10;
    private static final int CORE_THREADS_IN_POOL = 4;
    private static final int SCHEDULER_THREADS_IN_POOL = 2;
    private static final int THREAD_KEEPALIVE_TIME = 10;
    private static final int MAXIMUM_THREADS_IN_POOL = 40;
    private static final int MAXIMUM_WORK_QUEUE_SIZE = 100;

    private static TaskExecutorProcess singletonInstance;

    private final SessionConfigurationService configurationService;

    private ThreadPoolExecutor executor;
    private ScheduledThreadPoolExecutor scheduledExecutor;

    private TaskExecutorProcess(SessionConfigurationService configurationService) {
        this.configurationService = configurationService;

        // creates new thread from pool and add namespace to the thread name.
        ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(MAXIMUM_WORK_QUEUE_SIZE);

        executor = new ThreadPoolExecutor(CORE_THREADS_IN_POOL, MAXIMUM_THREADS_IN_POOL, THREAD_KEEPALIVE_TIME, SECONDS,
                workQueue, createThread(), new ThreadPoolExecutor.CallerRunsPolicy());
        scheduledExecutor = new ScheduledThreadPoolExecutor(SCHEDULER_THREADS_IN_POOL, createThread(), DISCARD_AND_LOG);
    }

    public synchronized static TaskExecutorProcess getInstance(SessionConfigurationService configurationService) {
        if (singletonInstance == null) {
            singletonInstance = new TaskExecutorProcess(configurationService);
        }

        return singletonInstance;
    }

    @Override
    public void close() {
        LOGGER.info("Shutting down the executor.");

        executor.shutdown();
        scheduledExecutor.shutdown();

        try {
            executor.awaitTermination(WAIT_FOR_SHUTDOWN, SECONDS);
            scheduledExecutor.awaitTermination(WAIT_FOR_SHUTDOWN, SECONDS);
        } catch (Exception e) {
            LOGGER.error("Task termination thread was interrupted.", e);
        }
    }

    /**
     * Submits a Runnable task for execution and returns a Future representing
     * that task. The Future's {@code get} method will return {@code null} upon
     * <em>successful</em> completion.
     * 
     * Creates and executes a periodic action that becomes enabled first after
     * the given initial delay, and subsequently with the given period; that is
     * executions will commence after {@code initialDelay} then
     * {@code initialDelay+period}, then {@code initialDelay + 2 * period}, and
     * so on. If any execution of the task encounters an exception, subsequent
     * executions are suppressed. Otherwise, the task will only terminate via
     * cancellation or termination of the executor. If any execution of this
     * task takes longer than its period, then subsequent executions may start
     * late, but will not concurrently execute.
     *
     * @param task
     *            the task to submit
     * @param scheduleLater
     * @param initialDelay
     *            the time to delay first execution
     * @param period
     *            the period between successive executions
     * @param unit
     *            the time unit of the initialDelay and period parameters
     * @return a Future representing pending completion of the task
     *         a ScheduledFuture representing pending completion of the task,
     *         and whose {@code get()} method will throw an exception upon
     *         cancellation
     * @throws RejectedExecutionException
     *             if the task cannot be scheduled for execution
     * @throws NullPointerException
     *             if the task is null
     * @throws IllegalArgumentException
     *             if period less than or equal to zero
     */
    public Future<?> submit(Runnable task, boolean scheduleLater, long initialDelay, long period, TimeUnit unit) {
        return scheduleLater ? scheduledExecutor.scheduleAtFixedRate(task, initialDelay, period, unit) : executor.submit(task);
    }

    private ThreadFactory createThread() {
        return (r) -> {
            Thread thread = new Thread(r, (String.format(NAMESPACE, configurationService.getNamespace()) + THREAD_COUNT.getAndIncrement()));
            thread.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);

            return thread;
        };
    }
}
