package com.agent.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Task database entity.
 */
@Data
@TableName("t_task")
public class TaskEntity {

    @TableId(type = IdType.INPUT)
    private String taskId;

    private String sessionId;

    private String name;

    private String status;

    private String intent;

    /** Steps serialized as JSON */
    private String stepsJson;

    /** Result serialized as JSON */
    private String resultJson;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
