package com.agent.core.task;

import com.agent.common.utils.JsonUtils;
import com.agent.model.task.Task;
import com.agent.model.task.TaskStatus;
import com.agent.persist.entity.TaskEntity;
import com.agent.persist.mapper.TaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task manager implementation.
 * Provides in-memory caching with MySQL persistence.
 */
@Slf4j
@Service
public class TaskManagerImpl implements TaskManager {

    /** In-memory task cache */
    private final Map<String, Task> taskCache = new ConcurrentHashMap<>();

    private final TaskMapper taskMapper;

    public TaskManagerImpl(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public void createTask(Task task) {
        taskCache.put(task.getTaskId(), task);

        // Persist to DB
        TaskEntity entity = toEntity(task);
        taskMapper.insert(entity);

        log.info("Task created: taskId={}, sessionId={}, intent={}",
                task.getTaskId(), task.getSessionId(), task.getIntent());
    }

    @Override
    public void updateTask(Task task) {
        taskCache.put(task.getTaskId(), task);

        TaskEntity entity = toEntity(task);
        taskMapper.updateById(entity);

        log.debug("Task updated: taskId={}, status={}", task.getTaskId(), task.getStatus());
    }

    @Override
    public Task getTask(String taskId) {
        Task task = taskCache.get(taskId);
        if (task == null) {
            TaskEntity entity = taskMapper.selectById(taskId);
            if (entity != null) {
                task = toModel(entity);
                taskCache.put(taskId, task);
            }
        }
        return task;
    }

    @Override
    public List<Task> getSessionTasks(String sessionId) {
        List<TaskEntity> entities = taskMapper.selectBySessionId(sessionId);
        return entities.stream().map(this::toModel).toList();
    }

    @Override
    public void updateTaskStatus(String taskId, TaskStatus status) {
        Task task = getTask(taskId);
        if (task != null) {
            task.setStatus(status);
            if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                task.setCompletedAt(LocalDateTime.now());
            }
            taskMapper.updateStatus(taskId, status.name());
        }
    }

    @Override
    public void cancelTask(String taskId) {
        updateTaskStatus(taskId, TaskStatus.CANCELLED);
        log.info("Task cancelled: taskId={}", taskId);
    }

    private TaskEntity toEntity(Task task) {
        TaskEntity entity = new TaskEntity();
        entity.setTaskId(task.getTaskId());
        entity.setSessionId(task.getSessionId());
        entity.setName(task.getName());
        entity.setStatus(task.getStatus().name());
        entity.setIntent(task.getIntent());
        entity.setStepsJson(JsonUtils.toJson(task.getSteps()));
        entity.setResultJson(task.getResult() != null ? JsonUtils.toJson(task.getResult()) : null);
        entity.setErrorMessage(task.getErrorMessage());
        entity.setCreatedAt(task.getCreatedAt());
        entity.setCompletedAt(task.getCompletedAt());
        return entity;
    }

    private Task toModel(TaskEntity entity) {
        return Task.builder()
                .taskId(entity.getTaskId())
                .sessionId(entity.getSessionId())
                .name(entity.getName())
                .status(TaskStatus.valueOf(entity.getStatus()))
                .intent(entity.getIntent())
                .steps(entity.getStepsJson() != null ?
                        JsonUtils.fromJson(entity.getStepsJson(),
                                new com.fasterxml.jackson.core.type.TypeReference<List<Task.TaskStep>>() {}) :
                        List.of())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
