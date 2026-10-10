package de.allgemein.tx.monitoring;

import de.allgemein.tx.TaskController;
import de.allgemein.tx.TaskStats;

import java.util.Objects;

/**
 * Standard-MBean-Adapter für den {@link TaskController}.
 */
public final class TaskControllerAdapter implements TaskControllerMBean {

    private final TaskController controller;

    public TaskControllerAdapter(TaskController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    public long getActiveTasks() {
        return controller.stats().active();
    }

    @Override
    public long getCompletedTasks() {
        return controller.stats().completed();
    }

    @Override
    public long getFailedTasks() {
        return controller.stats().failed();
    }

    @Override
    public long getTimedOutTasks() {
        return controller.stats().timedOut();
    }

    @Override
    public long getRejectedTasks() {
        return controller.stats().rejected();
    }

    @Override
    public double getAvgDurationMillis() {
        return controller.stats().avgDurationMillis();
    }

    @Override
    public int getPoolSize() {
        return controller.poolSize();
    }

    @Override
    public int getQueueSize() {
        return controller.queueSize();
    }

    @Override
    public int getQueueRemainingCapacity() {
        return controller.queueRemainingCapacity();
    }
}
