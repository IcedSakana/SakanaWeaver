package com.agent.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Session database entity.
 */
@Data
@TableName("t_session")
public class SessionEntity {

    @TableId(type = IdType.INPUT)
    private String sessionId;

    private String userId;

    private String status;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;
}
