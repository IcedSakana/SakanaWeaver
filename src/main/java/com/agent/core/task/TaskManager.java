package com.agent.core.task;

import com.agent.model.task.Task;
import com.agent.model.task.TaskStatus;

import java.util.List;

/**
 * Task manager interface.
 * Manages task lifecycle within sessions.
 * Each plan from intent recognition is mapped to a Task.
 */
public interface TaskManager {

    /**
     * Create a new task.
     *
     * @param task the task to create
     */
    void createTask(Task task);

    /**
     * Update an existing task.
     *
     * @param task the task to update
     */
    void updateTask(Task task);

    /**
     * Get a task by ID.
     *
     * @param taskId task identifier
     * @return task or null
     */
    Task getTask(String taskId);

    /**
     * Get all tasks for a session.
     *
     * @param sessionId session identifier
     * @return list of tasks
     */
    List<Task> getSessionTasks(String sessionId);

    /**
     * Update task status.
     *
     * @param taskId task identifier
     * @param status new status
     */
    void updateTaskStatus(String taskId, TaskStatus status);

    /**
     * Cancel a running task.
     *
     * @param taskId task identifier
     */
    void cancelTask(String taskId);
}
