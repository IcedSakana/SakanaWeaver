package com.agent.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent context database entity.
 * Stores serialized agent context for dump/load operations.
 */
@Data
@TableName("t_agent_context")
public class AgentContextEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    /** Agent context serialized as JSON (can be large) */
    private String contextData;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
